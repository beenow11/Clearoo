package com.clearoo.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumRulesTest {
    @Test
    fun `names are safe folder names`() {
        assertEquals("London 2026", AlbumRules.cleanName("  London/2026 "))
        assertEquals("a b", AlbumRules.cleanName("a\tb\n"))
        assertEquals("", AlbumRules.cleanName(" ... "))
        assertEquals("Pictures/Goa trip/", AlbumRules.folderPath("Goa: trip"))
        assertEquals(AlbumRules.MAX_NAME, AlbumRules.cleanName("x".repeat(99)).length)
    }

    @Test
    fun `albums survive a save and load`() {
        val albums = listOf(Album(1, "London", setOf(5, 6, 7)), Album(2, "Goa", emptySet()))
        assertEquals(albums, AlbumRules.decode(AlbumRules.encode(albums)))
        assertEquals(emptyList<Album>(), AlbumRules.decode(null))
        assertEquals(listOf(Album(3, "Ok", setOf(1))), AlbumRules.decode("junk\n3\tOk\t1\nx\ty\tz"))
    }

    @Test
    fun `range picks photos between two times in order`() {
        fun m(id: Long, t: Long) = MediaMeta(id, false, 1, t, "$id.jpg", null, null)
        val picked = AlbumRules.inRange(listOf(m(1, 50), m(2, 10), m(3, 200), m(4, 20)), 10, 50)
        assertEquals(listOf(2L, 4L, 1L), picked.map { it.id })
    }

    @Test
    fun `near-identical hashes group together`() {
        val gradient = IntArray(72) { i -> (i % 9) * 20 }
        val reversed = IntArray(72) { i -> 200 - (i % 9) * 20 }
        val a = Duplicates.dHash(gradient)
        val b = a xor 0b101L          // two bits off: the same shot
        val c = Duplicates.dHash(reversed)
        assertTrue(Duplicates.distance(a, c) > Duplicates.MAX_DISTANCE)
        assertEquals(listOf(listOf(1L, 3L)), Duplicates.groups(listOf(1L to a, 2L to c, 3L to b)))
    }
}
