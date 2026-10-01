package com.clearoo.app.data

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import com.clearoo.app.domain.Blur
import com.clearoo.app.domain.Deck
import com.clearoo.app.domain.DeckRules
import com.clearoo.app.domain.MediaMeta
import kotlinx.coroutines.Dispatchers
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
}

data class GallerySummary(val count: Int, val bytes: Long)

class MediaRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val mediaSelection =
        "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (" +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

    private val lock = Mutex()
    private var cache: List<MediaItem>? = null
    /** Blur scores by id; survive deck reloads so photos are only scanned once per run. */
    private val blurScores = HashMap<Long, Double>()

    /** Forget the cached gallery, e.g. after items were trashed. */
    fun invalidate() {
        cache = null
    }

    suspend fun summary(): GallerySummary {
        val all = all()
        return GallerySummary(all.size, all.sumOf { it.sizeBytes })
    }

    /** Count and size per deck. Blurry is left out: it is only known after a scan. */
    suspend fun deckSummaries(now: Long): Map<Deck, GallerySummary> = withContext(Dispatchers.Default) {
        val metas = all().map { it.meta }
        Deck.entries.filter { it != Deck.BLURRY }.associateWith { deck ->
            val members = DeckRules.members(deck, metas, now)
            GallerySummary(members.size, members.sumOf { it.sizeBytes })
        }
    }

    suspend fun batch(deck: Deck, exclude: Set<Long>, count: Int, now: Long): List<MediaItem> {
        val all = all()
        val byId = all.associateBy { it.id }
        val metas = all.map { it.meta }
        if (deck == Deck.BLURRY) return blurryBatch(metas, byId, exclude, count)
        return withContext(Dispatchers.Default) {
            DeckRules.pick(deck, metas, exclude, count, now).mapNotNull { p -> byId[p.meta.id]?.copy(badge = p.badge) }
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

    /** Scores a random sample of photos and returns the blurriest. */
    private suspend fun blurryBatch(
        metas: List<MediaMeta>,
        byId: Map<Long, MediaItem>,
        exclude: Set<Long>,
        count: Int,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val candidates = metas.filter { it.id !in exclude && !it.isFavorite && DeckRules.isBlurCandidate(it) }
        candidates.filter { it.id !in blurScores }.shuffled().take(BLUR_SAMPLE).forEach { m ->
            byId[m.id]?.let { blurScores[m.id] = runCatching { blurScore(it.uri) }.getOrDefault(Double.MAX_VALUE) }
        }
        candidates
            .mapNotNull { m -> blurScores[m.id]?.takeIf { it < Blur.THRESHOLD }?.let { m to it } }
            .sortedBy { it.second }
            .take(count)
            .mapNotNull { (m, _) -> byId[m.id]?.copy(badge = "😵 Looks blurry") }
    }

    private fun blurScore(uri: Uri): Double {
        var bitmap = resolver.loadThumbnail(uri, Size(BLUR_PX, BLUR_PX), null)
        if (bitmap.config == Bitmap.Config.HARDWARE) bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val luma = IntArray(pixels.size) { i ->
            val c = pixels[i]
            (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        }
        return Blur.laplacianVariance(luma, w, h)
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
    }
}
