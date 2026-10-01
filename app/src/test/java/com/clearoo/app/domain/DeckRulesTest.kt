package com.clearoo.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckRulesTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun meta(
        id: Long,
        size: Long = 1_000,
        taken: Long = now - day,
        name: String = "IMG_$id.jpg",
        album: String? = "Camera",
        path: String? = "DCIM/Camera/",
        video: Boolean = false,
        favorite: Boolean = false,
    ) = MediaMeta(id, video, size, taken, name, album, path, favorite)

    @Test
    fun `detects screenshots and chat media`() {
        assertTrue(DeckRules.isScreenshot(meta(1, name = "Screenshot_2024.png", path = "Pictures/Screenshots/")))
        assertTrue(DeckRules.isChatMedia(meta(2, album = "WhatsApp Images", path = "Android/media/com.whatsapp/")))
        assertTrue(DeckRules.isChatMedia(meta(3, album = "Download", path = "Download/")))
        assertFalse(DeckRules.isScreenshot(meta(4)))
        assertFalse(DeckRules.isChatMedia(meta(4)))
    }

    @Test
    fun `old means more than two years`() {
        assertTrue(DeckRules.isOld(meta(1, taken = now - 800 * day), now))
        assertFalse(DeckRules.isOld(meta(2, taken = now - 300 * day), now))
        assertFalse(DeckRules.isOld(meta(3, taken = 0), now))
    }

    @Test
    fun `groups photos taken seconds apart in the same album`() {
        val t = now - day
        val items = listOf(
            meta(1, taken = t), meta(2, taken = t + 1_000), meta(3, taken = t + 3_000),
            meta(4, taken = t + 60_000),
            meta(5, taken = t + 120_000), meta(6, taken = t + 121_000, album = "Other"),
            meta(7, taken = t + 200_000), meta(8, taken = t + 202_000),
        )
        val groups = DeckRules.similarGroups(items)
        assertEquals(listOf(listOf(1L, 2L, 3L), listOf(7L, 8L)), groups.map { g -> g.map { it.id } })
    }

    @Test
    fun `biggest deck orders by size and skips excluded and favourites`() {
        val items = listOf(meta(1, size = 10), meta(2, size = 30), meta(3, size = 20), meta(4, size = 99, favorite = true))
        val picks = DeckRules.pick(Deck.BIGGEST, items, exclude = setOf(2L), count = 5, now = now)
        assertEquals(listOf(3L, 1L), picks.map { it.meta.id })
    }

    @Test
    fun `look-alike deck keeps groups together with badges`() {
        val t = now - day
        val items = listOf(meta(1, taken = t), meta(2, taken = t + 500), meta(3, taken = t + 900_000), meta(4, taken = t + 901_000))
        val picks = DeckRules.pick(Deck.SIMILAR, items, emptySet(), count = 1, now = now)
        assertEquals(listOf(1L, 2L), picks.map { it.meta.id })
        assertEquals("👯 Look-alike 2/2", picks[1].badge)
    }

    @Test
    fun `blur score is low for flat images and high for sharp ones`() {
        val w = 32
        val flat = IntArray(w * w) { 128 }
        val checker = IntArray(w * w) { i -> if ((i % w + i / w) % 2 == 0) 0 else 255 }
        assertTrue(Blur.laplacianVariance(flat, w, w) < Blur.THRESHOLD)
        assertTrue(Blur.laplacianVariance(checker, w, w) > Blur.THRESHOLD)
    }
}
