package com.clearoo.app.domain

/** An installed app, as the unused-apps list sees it. Pure data, no Android types. */
data class AppMeta(
    val packageName: String,
    val label: String,
    /** Last time its screen was shown; 0 if Android has no record. */
    val lastUsedMillis: Long,
    val installedMillis: Long,
    val sizeBytes: Long,
    val isSystem: Boolean,
)

object AppRules {
    private const val DAY_MS = 24L * 60 * 60 * 1000
    /** Choices offered for "not opened in…". */
    val THRESHOLD_DAYS = listOf(30, 60, 90)

    /**
     * Since when the app has sat unused. An app installed recently and never opened counts from
     * its install, so nothing installed yesterday is called unused.
     */
    fun idleSince(app: AppMeta): Long = maxOf(app.lastUsedMillis, app.installedMillis)

    fun idleDays(app: AppMeta, now: Long): Long = ((now - idleSince(app)) / DAY_MS).coerceAtLeast(0)

    /** Apps the user can uninstall that weren't opened in [days] days, biggest first. */
    fun unused(apps: List<AppMeta>, now: Long, days: Int, self: String): List<AppMeta> =
        apps.filter { !it.isSystem && it.packageName != self && idleDays(it, now) >= days }
            .sortedWith(compareByDescending<AppMeta> { it.sizeBytes }.thenBy { it.label.lowercase() })
}
