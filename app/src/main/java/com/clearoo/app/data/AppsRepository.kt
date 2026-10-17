package com.clearoo.app.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.core.graphics.drawable.toBitmap
import com.clearoo.app.domain.AppMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Installed apps with when they were last opened and how much space they take.
 * Needs "Usage access" (PACKAGE_USAGE_STATS), which the user grants in Android's settings.
 * Nothing read here leaves the phone.
 */
class AppsRepository(context: Context) {
    private val app = context.applicationContext
    private val pm = app.packageManager

    fun hasUsageAccess(): Boolean {
        val ops = app.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            app.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Android's Usage access screen, opened on Clearoo where the phone supports it. */
    fun usageAccessIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun usageAccessFallbackIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Every app with a launcher icon, which is what people think of as "their apps". */
    suspend fun apps(now: Long): List<AppMeta> = withContext(Dispatchers.IO) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packages = pm.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.distinct()
        val lastUsed = lastUsed(now)
        val storage = app.getSystemService(StorageStatsManager::class.java)
        packages.mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                val installed = pm.getPackageInfo(pkg, 0).firstInstallTime
                val size = runCatching {
                    val stats = storage.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle())
                    stats.appBytes + stats.dataBytes + stats.cacheBytes
                }.getOrDefault(0L)
                AppMeta(
                    packageName = pkg,
                    label = pm.getApplicationLabel(info).toString(),
                    lastUsedMillis = lastUsed[pkg] ?: 0L,
                    installedMillis = installed,
                    sizeBytes = size,
                    isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }.getOrNull()
        }
    }

    /** Last time each app was on screen, from Android's usage history (kept for about a year). */
    private fun lastUsed(now: Long): Map<String, Long> {
        val usage = app.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val stats = runCatching { usage.queryAndAggregateUsageStats(now - YEAR_MS, now) }.getOrNull() ?: return emptyMap()
        return stats.mapValues { (_, s) -> maxOf(s.lastTimeVisible, s.lastTimeUsed) }
    }

    fun icon(pkg: String): Bitmap? = runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96) }.getOrNull()

    /** Android's own uninstall confirmation for [pkg]. */
    fun uninstallIntent(pkg: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))

    /** The app's settings page, where it can also be uninstalled; used if the direct route fails. */
    fun detailsIntent(pkg: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))

    fun isInstalled(pkg: String): Boolean = runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess

    private companion object {
        const val YEAR_MS = 365L * 24 * 60 * 60 * 1000
    }
}
