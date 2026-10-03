package com.clearoo.app.domain

/** How full the phone is. */
enum class StorageLevel { OK, LOW, FULL }

object StorageRules {
    private const val MB = 1024L * 1024
    /** Android starts warning about storage at about 5%, capped at 500 MB. */
    const val FULL_CAP = 500 * MB
    const val LOW_CAP = 2048 * MB

    fun level(freeBytes: Long, totalBytes: Long): StorageLevel {
        if (totalBytes <= 0) return StorageLevel.OK
        return when {
            freeBytes < minOf(totalBytes / 20, FULL_CAP) -> StorageLevel.FULL
            freeBytes < minOf(totalBytes / 10, LOW_CAP) -> StorageLevel.LOW
            else -> StorageLevel.OK
        }
    }
}
