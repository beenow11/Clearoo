package com.clearoo.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRulesTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    private fun app(pkg: String, used: Long, installed: Long = now - 400 * day, size: Long = 100, system: Boolean = false) =
        AppMeta(pkg, pkg, used, installed, size, system)

    @Test
    fun `idle time counts from the later of last use and install`() {
        assertEquals(10, AppRules.idleDays(app("a", used = now - 10 * day), now))
        assertEquals(3, AppRules.idleDays(app("b", used = 0, installed = now - 3 * day), now))
        assertEquals(0, AppRules.idleDays(app("c", used = now + day), now))
    }

    @Test
    fun `unused leaves out system apps, itself and recent ones, biggest first`() {
        val apps = listOf(
            app("old-small", used = now - 90 * day, size = 10),
            app("old-big", used = now - 40 * day, size = 999),
            app("recent", used = now - 2 * day, size = 5_000),
            app("never-opened-new", used = 0, installed = now - 5 * day),
            app("system", used = now - 300 * day, system = true),
            app("com.roolabs.clearoo", used = now - 300 * day),
        )
        assertEquals(listOf("old-big", "old-small"), AppRules.unused(apps, now, 30, "com.roolabs.clearoo").map { it.packageName })
        assertEquals(listOf("old-small"), AppRules.unused(apps, now, 60, "com.roolabs.clearoo").map { it.packageName })
    }
}
