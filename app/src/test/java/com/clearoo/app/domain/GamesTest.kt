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
    fun `one free run a day, three more once the streak is secured`() {
        assertEquals(1, PlayRules.runsLeft(securedToday = false, runsDay = 5, runsUsed = 0, today = 5))
        assertEquals(0, PlayRules.runsLeft(false, 5, 1, 5))
        assertEquals(3, PlayRules.runsLeft(true, 5, 1, 5))
        // Yesterday's runs don't count today.
        assertEquals(4, PlayRules.runsLeft(true, 4, 4, 5))
    }

    @Test
    fun `jumping lifts Roo and gravity brings him back`() {
        var s = HopGame.jump(HopState())
        assertTrue(s.rooVy > 0)
        // No double jumps in the air.
        s = HopGame.step(s, 0.05f, Random(1))
        assertEquals(s, HopGame.jump(s))
        repeat(40) { s = HopGame.step(s.copy(things = emptyList(), untilSpawn = 99f), 0.05f, Random(1)) }
        assertTrue(s.onGround)
    }

    @Test
    fun `hitting junk ends the run, catching a star scores`() {
        val junk = Thing(0, HopGame.ROO_X, 0f, 10f, false, 0)
        assertTrue(HopGame.step(HopState(things = listOf(junk), untilSpawn = 99f), 0.01f, Random(1)).over)
        val star = Thing(1, HopGame.ROO_X, 2f, 7f, true, 0)
        val after = HopGame.step(HopState(things = listOf(star), untilSpawn = 99f), 0.01f, Random(1))
        assertFalse(after.over)
        assertEquals(1, after.stars)
        assertTrue(after.things.isEmpty())
        // Junk passes under a Roo high in the air.
        assertFalse(HopGame.hits(rooY = 30f, th = junk))
    }

    @Test
    fun `a jump clears the tallest junk`() {
        // Apex = JUMP² / (2·GRAVITY) must be well above the biggest junk (11 units).
        assertTrue(HopGame.JUMP * HopGame.JUMP / (2 * HopGame.GRAVITY) > 20f)
    }

    @Test
    fun `a long run keeps spawning and speeds up`() {
        var s = HopState()
        val rng = Random(42)
        repeat(600) {
            s = HopGame.step(HopGame.jump(s), 0.016f, rng)
            if (s.over) s = s.copy(over = false, things = emptyList())
        }
        assertTrue(s.speed > HopGame.START_SPEED)
        assertTrue(s.nextId > 3)
    }
}
