package com.clearoo.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PuzzleTest {
    @Test
    fun `a new board uses every piece once and none starts in place`() {
        for (size in PuzzleRules.SIZES) {
            val p = PuzzleRules.shuffled(size, Random(size))
            assertEquals((0 until size * size).toList(), p.tiles.sorted())
            assertEquals(0, p.inPlace)
            assertFalse(p.solved)
        }
    }

    @Test
    fun `swapping into place solves it and counts moves`() {
        var p = Puzzle(2, listOf(1, 0, 3, 2))
        assertEquals(2, PuzzleRules.minSwaps(p))
        p = PuzzleRules.swap(p, 0, 1)
        p = PuzzleRules.swap(p, 2, 3)
        assertTrue(p.solved)
        assertEquals(2, p.moves)
        assertEquals(p, PuzzleRules.swap(p, 1, 1))
    }

    @Test
    fun `solving by putting each piece home takes the minimum`() {
        var p = PuzzleRules.shuffled(4, Random(9))
        val min = PuzzleRules.minSwaps(p)
        for (pos in p.tiles.indices) {
            val at = p.tiles.indexOf(pos)
            if (at != pos) p = PuzzleRules.swap(p, pos, at)
        }
        assertTrue(p.solved)
        assertEquals(min, p.moves)
        assertEquals(3, PuzzleRules.stars(p.moves, min))
        assertEquals(1, PuzzleRules.stars(min * 3, min))
    }

    @Test
    fun `new worlds and the summit`() {
        assertEquals("Ocean", ClimbRules.worldFor(1_000).name)
        assertEquals("Roo's Outback home", ClimbRules.worldFor(9_999).name)
        assertEquals(listOf("Volcano", "Candy land"), ClimbRules.worldsEntered(1_500, 2_500).map { it.name })
        assertEquals(emptyList<World>(), ClimbRules.worldsEntered(1_600, 1_700))
    }

    @Test
    fun `unlocked outfits are announced once`() {
        val p = Progress(bestStreak = 3, totalDeleted = 150)
        assertEquals(listOf(Outfit.PARTY_HAT, Outfit.SCARF), OutfitRules.unseen(p, emptySet()))
        assertEquals(listOf(Outfit.SCARF), OutfitRules.unseen(p, setOf("PARTY_HAT")))
    }
}
