package com.clearoo.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import com.clearoo.app.domain.AlbumRules
import com.clearoo.app.domain.Blur
import com.clearoo.app.domain.Deck
import com.clearoo.app.domain.DeckRules
import com.clearoo.app.domain.Duplicates
import com.clearoo.app.domain.MediaMeta
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class MediaItem(
    val meta: MediaMeta,
    val uri: Uri,
    val durationMs: Long,
    /** Why this card is in the deck, e.g. "👯 Look-alike 2/3". */
    val badge: String? = null,
) {
    val id get() = meta.id
    val isVideo get() = meta.isVideo
    val sizeBytes get() = meta.sizeBytes
    val takenAtMillis get() = meta.takenAtMillis
    val displayName get() = meta.displayName
    val album get() = meta.album
    val isBroken get() = DeckRules.isBroken(meta)
}

data class GallerySummary(val count: Int, val bytes: Long)

/** What a scan of an album found: blurry photos (blurriest first) and groups of duplicates. */
data class AlbumScan(val blurry: List<Long>, val duplicates: List<List<Long>>)

data class MoveResult(val moved: Int, val failed: Int)

class MediaRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val mediaSelection =
        "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (" +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

    private val lock = Mutex()
    private var cache: List<MediaItem>? = null
    /** Blur scores by id; survive deck reloads so photos are only scanned once per run. */
    private val blurScores = ConcurrentHashMap<Long, Double>()
    /** Duplicate-finding hashes by id, filled by album scans. */
    private val hashes = ConcurrentHashMap<Long, Long>()
    /** Files that failed to open this run (on a card, or in a scan). */
    private val brokenIds = HashSet<Long>()
    /** Files already checked by the broken-file scan. */
    private val probed: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /** Forget the cached gallery, e.g. after items were trashed. */
    fun invalidate() {
        cache = null
    }

    /** Remembers that [id] could not be opened, so it moves to the Broken deck. */
    fun markBroken(id: Long) {
        synchronized(brokenIds) { brokenIds += id }
    }

    private fun isKnownBroken(id: Long) = synchronized(brokenIds) { id in brokenIds }

    /** Gallery items with what this run learned about broken files. */
    private suspend fun metas(): List<MediaMeta> = all().map { if (isKnownBroken(it.id)) it.meta.copy(isBroken = true) else it.meta }

    /** Gallery items taken between two times, oldest first; for picking an album's photos. */
    suspend fun itemsInRange(startMillis: Long, endMillis: Long): List<MediaItem> {
        val byId = all().associateBy { it.id }
        return AlbumRules.inRange(metas(), startMillis, endMillis).mapNotNull { byId[it.id] }
    }

    /**
     * Checks every photo in an album for blur and duplicates. Results are cached, so opening
     * the album again is quick. [onProgress] gets (done, total).
     */
    suspend fun scanAlbum(ids: Set<Long>, onProgress: (Int, Int) -> Unit): AlbumScan = withContext(Dispatchers.IO) {
        val photos = itemsByIds(ids)
            .filter { !it.isVideo && !it.isBroken && !isKnownBroken(it.id) }
            .sortedBy { it.takenAtMillis }
        photos.forEachIndexed { i, item ->
            if (item.id !in blurScores || item.id !in hashes) {
                runCatching { thumbStats(item.uri) }
                    .onSuccess { (blur, hash) ->
                        blurScores[item.id] = blur
                        hashes[item.id] = hash
                    }
                    .onFailure { markBroken(item.id) }
            }
            onProgress(i + 1, photos.size)
        }
        val blurry = photos.mapNotNull { p -> blurScores[p.id]?.takeIf { it < Blur.THRESHOLD }?.let { p.id to it } }
            .sortedBy { it.second }
            .map { it.first }
        val duplicates = Duplicates.groups(photos.mapNotNull { p -> hashes[p.id]?.let { p.id to it } })
        AlbumScan(blurry, duplicates)
    }

    /** Cards for one of an album's decks: Everything (in date order), Blurry or Duplicates. */
    suspend fun albumBatch(deck: Deck, ids: Set<Long>, scan: AlbumScan?, exclude: Set<Long>, count: Int): List<MediaItem> {
        val byId = itemsByIds(ids).associateBy { it.id }
        return when (deck) {
            Deck.BLURRY -> scan?.blurry.orEmpty()
                .filter { it !in exclude }
                .take(count)
                .mapNotNull { byId[it]?.copy(badge = "😵 Looks blurry") }
            Deck.DUPLICATES -> {
                // Whole groups at a time, so copies always sit next to each other.
                val out = ArrayList<MediaItem>()
                for (group in scan?.duplicates.orEmpty()) {
                    if (out.size >= count) break
                    val left = group.filter { it !in exclude }
                    if (left.isEmpty()) continue
                    left.forEachIndexed { i, id ->
                        byId[id]?.let { out += it.copy(badge = "👯 Duplicate ${i + 1}/${left.size}") }
                    }
                }
                out
            }
            else -> byId.values.filter { it.id !in exclude }.sortedBy { it.takenAtMillis }.take(count)
        }
    }

    /** System request for write access to [items], needed before moving them. */
    fun writeRequest(items: List<MediaItem>): IntentSender =
        MediaStore.createWriteRequest(resolver, items.map { it.uri }).intentSender

    /** Moves [items] into [relativePath] (e.g. "Pictures/London/"). Call after [writeRequest] was allowed. */
    suspend fun moveTo(items: List<MediaItem>, relativePath: String): MoveResult = withContext(Dispatchers.IO) {
        var moved = 0
        var failed = 0
        for (item in items) {
            if (item.meta.relativePath == relativePath) continue
            val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) }
            val ok = runCatching { resolver.update(item.uri, values, null, null) > 0 }.getOrDefault(false)
            if (ok) moved++ else failed++
        }
        invalidate()
        MoveResult(moved, failed)
    }

    /** Items still in the gallery, for restoring a saved bin. */
    suspend fun itemsByIds(ids: Set<Long>): List<MediaItem> = all().filter { it.id in ids }

    suspend fun summary(): GallerySummary {
        val all = all()
        return GallerySummary(all.size, all.sumOf { it.sizeBytes })
    }

    /** Count and size per deck. Blurry is left out: it is only known after a scan. Broken counts what is known so far. */
    suspend fun deckSummaries(now: Long): Map<Deck, GallerySummary> = withContext(Dispatchers.Default) {
        val metas = metas()
        Deck.entries.filter { it != Deck.BLURRY && it.onHome }.associateWith { deck ->
            val members = DeckRules.members(deck, metas, now)
            GallerySummary(members.size, members.sumOf { it.sizeBytes })
        }
    }

    suspend fun batch(deck: Deck, exclude: Set<Long>, count: Int, now: Long): List<MediaItem> {
        val byId = all().associateBy { it.id }
        if (deck == Deck.BLURRY) return blurryBatch(metas(), byId, exclude, count)
        if (deck == Deck.BROKEN) probeForBroken(metas(), exclude)
        val metas = metas()
        return withContext(Dispatchers.Default) {
            DeckRules.pick(deck, metas, exclude, count, now).mapNotNull { p ->
                byId[p.meta.id]?.copy(meta = p.meta, badge = p.badge)
            }
        }
    }

    /**
     * System request that moves the items to the trash (recoverable for ~30 days),
     * or deletes them for good when [permanent] is set.
     */
    fun deleteRequest(items: List<MediaItem>, permanent: Boolean): IntentSender {
        val uris = items.map { it.uri }
        val pending = if (permanent) {
            MediaStore.createDeleteRequest(resolver, uris)
        } else {
            MediaStore.createTrashRequest(resolver, uris, true)
        }
        return pending.intentSender
    }

    /**
     * Tries to open a sample of files that look most likely to be broken (chat and old media
     * first). Anything that can't be opened is marked broken.
     */
    private suspend fun probeForBroken(metas: List<MediaMeta>, exclude: Set<Long>) = withContext(Dispatchers.IO) {
        val byId = all().associateBy { it.id }
        metas
            .filter { it.id !in exclude && it.id !in probed && !DeckRules.isBroken(it) }
            .sortedWith(compareByDescending<MediaMeta> { DeckRules.isChatMedia(it) }.thenBy { it.takenAtMillis })
            .take(PROBE_SAMPLE)
            .forEach { m ->
                probed += m.id
                val item = byId[m.id] ?: return@forEach
                if (runCatching { resolver.loadThumbnail(item.uri, Size(PROBE_PX, PROBE_PX), null) }.isFailure) markBroken(m.id)
            }
    }

    /** Scores a random sample of photos and returns the blurriest. */
    private suspend fun blurryBatch(
        metas: List<MediaMeta>,
        byId: Map<Long, MediaItem>,
        exclude: Set<Long>,
        count: Int,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val candidates = metas.filter { it.id !in exclude && !it.isFavorite && DeckRules.isBlurCandidate(it) }
        candidates.filter { it.id !in blurScores }.shuffled().take(BLUR_SAMPLE).forEach { m ->
            byId[m.id]?.let { item ->
                blurScores[m.id] = runCatching { blurScore(item.uri) }.getOrElse {
                    markBroken(m.id)
                    Double.MAX_VALUE
                }
            }
        }
        candidates
            .mapNotNull { m -> blurScores[m.id]?.takeIf { it < Blur.THRESHOLD }?.let { m to it } }
            .sortedBy { it.second }
            .take(count)
            .mapNotNull { (m, _) -> byId[m.id]?.copy(badge = "😵 Looks blurry") }
    }

    private fun blurScore(uri: Uri): Double = thumbStats(uri).first

    /** Blur score and duplicate hash, from one small thumbnail. */
    private fun thumbStats(uri: Uri): Pair<Double, Long> {
        var bitmap = resolver.loadThumbnail(uri, Size(BLUR_PX, BLUR_PX), null)
        if (bitmap.config == Bitmap.Config.HARDWARE) bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val blur = luma(bitmap).let { Blur.laplacianVariance(it, bitmap.width, bitmap.height) }
        val tiny = Bitmap.createScaledBitmap(bitmap, Duplicates.HASH_W, Duplicates.HASH_H, true)
        return blur to Duplicates.dHash(luma(tiny))
    }

    private fun luma(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return IntArray(pixels.size) { i ->
            val c = pixels[i]
            (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        }
    }

    private suspend fun all(): List<MediaItem> = lock.withLock {
        cache ?: withContext(Dispatchers.IO) { query() }.also { cache = it }
    }

    private fun query(): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.IS_FAVORITE,
        )
        val out = ArrayList<MediaItem>()
        resolver.query(filesUri, projection, mediaSelection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val favoriteCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val isVideo = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val meta = MediaMeta(
                    id = id,
                    isVideo = isVideo,
                    sizeBytes = c.getLong(sizeCol),
                    takenAtMillis = c.getLong(takenCol).takeIf { it > 0 } ?: (c.getLong(addedCol) * 1000),
                    displayName = c.getString(nameCol) ?: "",
                    album = c.getString(albumCol),
                    relativePath = c.getString(pathCol),
                    isFavorite = c.getInt(favoriteCol) == 1,
                )
                val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                out += MediaItem(meta, ContentUris.withAppendedId(collection, id), c.getLong(durationCol))
            }
        }
        return out
    }

    private companion object {
        const val BLUR_SAMPLE = 120
        const val BLUR_PX = 256
        const val PROBE_SAMPLE = 150
        const val PROBE_PX = 64
    }
}
