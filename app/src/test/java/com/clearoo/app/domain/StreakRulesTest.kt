package com.clearoo.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakRulesTest {
    private val day = 20_000L

    @Test
    fun `streak starts once the daily minimum is reached`() {
        var p = StreakRules.recordDeletion(Progress(), 3, 300, day)
        assertEquals(0, StreakRules.currentStreak(p, day))
        p = StreakRules.recordDeletion(p, 2, 200, day)
        assertEquals(1, StreakRules.currentStreak(p, day))
        assertEquals(5, p.deletedToday)
        assertEquals(500L, p.freedToday)
        assertTrue(day in p.goalDays)
    }

    @Test
    fun `streak counts once per day`() {
        var p = StreakRules.recordDeletion(Progress(), 5, 0, day)
        p = StreakRules.recordDeletion(p, 10, 0, day)
        assertEquals(1, p.streak)
        assertEquals(15, p.deletedToday)
    }

    @Test
    fun `consecutive days extend the streak`() {
        var p = StreakRules.recordDeletion(Progress(), 5, 0, day)
        p = StreakRules.recordDeletion(p, 5, 0, day + 1)
        p = StreakRules.recordDeletion(p, 6, 0, day + 2)
        assertEquals(3, StreakRules.currentStreak(p, day + 2))
        assertEquals(3, p.bestStreak)
        assertEquals(16, p.totalDeleted)
    }

    @Test
    fun `missing a day breaks the streak but keeps the best`() {
        var p = StreakRules.recordDeletion(Progress(), 5, 0, day)
        p = StreakRules.recordDeletion(p, 5, 0, day + 1)
        assertEquals(2, StreakRules.currentStreak(p, day + 2))
        assertTrue(StreakRules.atRisk(p, day + 2))
        assertEquals(0, StreakRules.currentStreak(p, day + 3))
        p = StreakRules.recordDeletion(p, 5, 0, day + 3)
        assertEquals(1, p.streak)
        assertEquals(2, p.bestStreak)
    }

    @Test
    fun `today counters reset on a new day`() {
        val p = StreakRules.recordDeletion(Progress(), 4, 400, day)
        assertEquals(0, StreakRules.deletedToday(p, day + 1))
        val next = StreakRules.recordDeletion(p, 1, 100, day + 1)
        assertEquals(1, next.deletedToday)
        assertEquals(0, next.streak)
        assertFalse(StreakRules.securedToday(next, day + 1))
    }

    @Test
    fun `reviews never go negative`() {
        val p = StreakRules.recordReview(Progress(), -1, day)
        assertEquals(0, p.reviewedToday)
        assertEquals(0, p.totalReviewed)
    }
}
