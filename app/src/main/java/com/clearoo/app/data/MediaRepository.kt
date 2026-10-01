package com.clearoo.app.data

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val takenAtMillis: Long,
    val displayName: String,
    val album: String?,
    val durationMs: Long,
)

data class GallerySummary(val count: Int, val bytes: Long)

class MediaRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val mediaSelection =
        "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (" +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

    private data class Candidate(val id: Long, val size: Long)

    suspend fun summary(): GallerySummary = withContext(Dispatchers.IO) {
        val all = candidates()
        GallerySummary(all.size, all.sumOf { it.size })
    }

    /** A random batch of photos/videos, skipping anything in [exclude]. */
    suspend fun randomBatch(count: Int, exclude: Set<Long>): List<MediaItem> = withContext(Dispatchers.IO) {
        val picked = candidates().filter { it.id !in exclude }.shuffled().take(count)
        if (picked.isEmpty()) return@withContext emptyList()
        val details = details(picked.map { it.id })
        picked.mapNotNull { details[it.id] }
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

    private fun candidates(): List<Candidate> {
        val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.MediaColumns.SIZE)
        val out = ArrayList<Candidate>()
        resolver.query(filesUri, projection, mediaSelection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (c.moveToNext()) out += Candidate(c.getLong(idCol), c.getLong(sizeCol))
        }
        return out
    }

    private fun details(ids: List<Long>): Map<Long, MediaItem> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
        )
        val selection = "$mediaSelection AND ${MediaStore.Files.FileColumns._ID} IN (${ids.joinToString(",")})"
        val out = HashMap<Long, MediaItem>()
        resolver.query(filesUri, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val isVideo = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val taken = c.getLong(takenCol).takeIf { it > 0 } ?: (c.getLong(addedCol) * 1000)
                out[id] = MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(
                        if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id,
                    ),
                    isVideo = isVideo,
                    sizeBytes = c.getLong(sizeCol),
                    takenAtMillis = taken,
                    displayName = c.getString(nameCol) ?: "",
                    album = c.getString(albumCol),
                    durationMs = c.getLong(durationCol),
                )
            }
        }
        return out
    }
}
