package com.clearoo.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutfitRulesTest {
    @Test
    fun `classic is always unlocked`() {
        assertTrue(OutfitRules.isUnlocked(Outfit.CLASSIC, Progress()))
    }

    @Test
    fun `streak outfits follow the best streak so they never re-lock`() {
        val p = Progress(streak = 0, bestStreak = 7)
        assertTrue(OutfitRules.isUnlocked(Outfit.PARTY_HAT, p))
        assertTrue(OutfitRules.isUnlocked(Outfit.SUNGLASSES, p))
        assertFalse(OutfitRules.isUnlocked(Outfit.HEADPHONES, p))
        assertEquals(0.5f, OutfitRules.progress(Outfit.HEADPHONES, p), 0.001f)
    }

    @Test
    fun `deleting and freeing unlock their outfits`() {
        val p = Progress(totalDeleted = 120, totalFreed = 2L shl 30)
        assertTrue(OutfitRules.isUnlocked(Outfit.SCARF, p))
        assertTrue(OutfitRules.isUnlocked(Outfit.WIZARD_HAT, p))
        assertFalse(OutfitRules.isUnlocked(Outfit.CAPE, p))
        assertEquals("Free 1 GB", OutfitRules.hint(Outfit.WIZARD_HAT))
    }

    @Test
    fun `reports outfits unlocked by a clean-up`() {
        val before = Progress(totalDeleted = 95, bestStreak = 2)
        val after = StreakRules.recordDeletion(before.copy(lastGoalDay = 9, streak = 2, day = 10, deletedToday = 0), 6, 0, 10)
        assertEquals(listOf(Outfit.PARTY_HAT, Outfit.SCARF), OutfitRules.newlyUnlocked(before, after))
    }
}
