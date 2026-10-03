package com.clearoo.app.util

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.clearoo.app.domain.StorageLevel
import com.clearoo.app.domain.StorageRules

data class StorageInfo(val freeBytes: Long, val totalBytes: Long) {
    val level: StorageLevel get() = StorageRules.level(freeBytes, totalBytes)
}

object Device {
    /** Free space where photos live; null if it can't be read. */
    fun storage(): StorageInfo? = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        StorageInfo(stat.availableBytes, stat.totalBytes)
    }.getOrNull()

    /** Old or low-memory phones: don't autoplay video, which can get the app killed. */
    fun isLowMemory(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        if (am.isLowRamDevice) return true
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return info.totalMem in 1 until LOW_MEMORY_BYTES || info.lowMemory
    }

    private const val LOW_MEMORY_BYTES = 3L * 1024 * 1024 * 1024
}
