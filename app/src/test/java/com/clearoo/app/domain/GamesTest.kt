package com.clearoo.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GamesTest {
    @Test
    fun `worlds and milestones follow the hop count`() {
        assertEquals("Meadow", ClimbRules.worldFor(0).name)
        assertEquals("Desert", ClimbRules.worldFor(100).name)
        assertEquals("Space", ClimbRules.worldFor(5_000).name)
        assertEquals("Snow peaks", ClimbRules.nextWorld(150)?.name)
        assertNull(ClimbRules.nextWorld(700))
        assertEquals(25, ClimbRules.nextMilestone(0))
        assertEquals(50, ClimbRules.nextMilestone(25))
        assertEquals(listOf(25, 50), ClimbRules.milestonesPassed(20, 51))
        assertEquals(emptyList<Int>(), ClimbRules.milestonesPassed(26, 30))
    }

    @Test
    fun `photos fly up into view and fall away when left alone`() {
        val rng = Random(7)
        var s = PouchGame.start(height = 180f, queue = listOf(1L, 2L, 3L))
        s = PouchGame.step(s, 0.05f, rng)
        repeat(20) { s = PouchGame.step(s, 0.05f, rng) }
        assertTrue(s.flyers.isNotEmpty())
        val peak = s.flyers.first()
        assertTrue("rises into the top half", (0 until 60).map { s = PouchGame.step(s, 0.05f, rng); s.flyers.firstOrNull { it.id == peak.id }?.y ?: 999f }.min() < 90f)
        repeat(400) { s = PouchGame.step(s, 0.05f, rng) }
        assertTrue(s.over)
        assertEquals(3, s.skipped)
        assertTrue(s.pouch.isEmpty())
    }

    @Test
    fun `a swipe through a photo drops it in the pouch, with combos`() {
        val a = Flyer(0, 10L, x = 10f, y = 50f, vx = 0f, vy = 0f, spin = 0f)
        val b = Flyer(1, 11L, x = 50f, y = 50f, vx = 0f, vy = 0f, spin = 0f)
        val c = Flyer(2, 12L, x = 10f, y = 120f, vx = 0f, vy = 0f, spin = 0f)
        val s = PouchState(height = 180f, queue = emptyList(), flyers = listOf(a, b, c))
        // One swipe straight through a and b, well away from c.
        val cut = PouchGame.slice(s, 0f, 64f, 100f, 64f, newSwipe = true)
        assertEquals(listOf(10L, 11L), cut.pouch)
        assertEquals(2, cut.bestCombo)
        assertFalse(cut.flyers.first { it.id == 2 }.sliced)
        // Slicing the same photos again does nothing.
        assertEquals(cut.pouch, PouchGame.slice(cut, 0f, 64f, 100f, 64f, newSwipe = true).pouch)
    }

    @Test
    fun `segment to circle check`() {
        assertTrue(PouchGame.crosses(0f, 0f, 10f, 0f, 5f, 2f, 3f))
        assertFalse(PouchGame.crosses(0f, 0f, 10f, 0f, 5f, 5f, 3f))
        assertFalse(PouchGame.crosses(0f, 0f, 10f, 0f, 20f, 0f, 3f))
    }

    @Test
    fun `round never shows more than three photos at once`() {
        val rng = Random(3)
        var s = PouchGame.start(180f, (1L..200L).toList())
        repeat(1300) {
            s = PouchGame.step(s, 0.05f, rng)
            assertTrue(s.flyers.count { !it.sliced } <= PouchGame.MAX_ON_SCREEN)
        }
        assertTrue(s.over)
    }
}
