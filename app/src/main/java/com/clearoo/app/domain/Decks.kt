package com.clearoo.app.domain

/** The bits of a gallery item the deck rules look at. Pure data, no Android types. */
data class MediaMeta(
    val id: Long,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val takenAtMillis: Long,
    val displayName: String,
    val album: String?,
    val relativePath: String?,
    val isFavorite: Boolean = false,
)

/** Smart decks: different ways of choosing which cards to show. */
enum class Deck(val emoji: String, val title: String, val blurb: String) {
    RANDOM("🎲", "Quick mix", "A random handful from your gallery"),
    BIGGEST("🐘", "Biggest", "Largest files first: the most space per swipe"),
    SCREENSHOTS("📸", "Screenshots", "Screenshots and screen recordings"),
    CHATS("💬", "Chat media", "WhatsApp, Telegram and downloads"),
    SIMILAR("👯", "Look-alikes", "Burst shots and near-duplicates"),
    BLURRY("😵", "Blurry", "Shaky, out-of-focus shots"),
    OLD("🕰️", "Old memories", "Photos from 2+ years ago"),
    VIDEOS("🎬", "Videos", "Videos, biggest first"),
}

/** A card chosen for a deck, with an optional reason shown on the card. */
data class Pick(val meta: MediaMeta, val badge: String? = null)

object DeckRules {
    const val SIMILAR_GAP_MS = 4_000L
    private const val OLD_AFTER_MS = 2L * 365 * 24 * 60 * 60 * 1000
    private val SCREENSHOT_HINTS = listOf("screenshot", "screen record", "screenrecord", "screen_record")
    private val CHAT_HINTS = listOf(
        "whatsapp", "telegram", "messenger", "signal", "instagram", "snapchat", "viber", "wechat", "download",
    )

    private fun MediaMeta.haystack() = "${relativePath.orEmpty()} ${album.orEmpty()} $displayName".lowercase()

    fun isScreenshot(m: MediaMeta): Boolean = m.haystack().let { h -> SCREENSHOT_HINTS.any { it in h } }

    fun isChatMedia(m: MediaMeta): Boolean = m.haystack().let { h -> CHAT_HINTS.any { it in h } }

    fun isOld(m: MediaMeta, now: Long): Boolean = m.takenAtMillis > 0 && now - m.takenAtMillis > OLD_AFTER_MS

    /** Photos (not screenshots) eligible for the blur scan. */
    fun isBlurCandidate(m: MediaMeta): Boolean = !m.isVideo && !isScreenshot(m)

    /** Photos taken within a few seconds of each other in the same album, oldest group first. */
    fun similarGroups(items: List<MediaMeta>): List<List<MediaMeta>> {
        val photos = items.filter { !it.isVideo && it.takenAtMillis > 0 && !isScreenshot(it) }
            .sortedBy { it.takenAtMillis }
        val groups = ArrayList<List<MediaMeta>>()
        var current = ArrayList<MediaMeta>()
        for (m in photos) {
            val last = current.lastOrNull()
            if (last != null && (m.takenAtMillis - last.takenAtMillis > SIMILAR_GAP_MS || m.album != last.album)) {
                if (current.size > 1) groups += current
                current = ArrayList()
            }
            current += m
        }
        if (current.size > 1) groups += current
        return groups
    }

    /** Everything that belongs in [deck]; favourites are never offered. Not defined for [Deck.BLURRY]. */
    fun members(deck: Deck, items: List<MediaMeta>, now: Long): List<MediaMeta> {
        val pool = items.filter { !it.isFavorite }
        return when (deck) {
            Deck.RANDOM, Deck.BIGGEST -> pool
            Deck.SCREENSHOTS -> pool.filter(::isScreenshot)
            Deck.CHATS -> pool.filter(::isChatMedia)
            Deck.SIMILAR -> similarGroups(pool).flatten()
            Deck.BLURRY -> pool.filter(::isBlurCandidate)
            Deck.OLD -> pool.filter { isOld(it, now) }
            Deck.VIDEOS -> pool.filter { it.isVideo }
        }
    }

    /** The next [count] cards for [deck], skipping [exclude]. Blurry is scored separately. */
    fun pick(
        deck: Deck,
        items: List<MediaMeta>,
        exclude: Set<Long>,
        count: Int,
        now: Long,
        shuffle: (List<MediaMeta>) -> List<MediaMeta> = { it.shuffled() },
    ): List<Pick> {
        val pool = items.filter { it.id !in exclude && !it.isFavorite }
        return when (deck) {
            Deck.RANDOM, Deck.BLURRY -> shuffle(pool).take(count).map { Pick(it) }
            Deck.BIGGEST -> pool.sortedByDescending { it.sizeBytes }.take(count).map { Pick(it) }
            Deck.VIDEOS -> pool.filter { it.isVideo }.sortedByDescending { it.sizeBytes }.take(count).map { Pick(it) }
            Deck.SCREENSHOTS -> shuffle(pool.filter(::isScreenshot)).take(count).map { Pick(it) }
            Deck.CHATS -> shuffle(pool.filter(::isChatMedia)).take(count).map { m -> Pick(m, "💬 ${m.album ?: "Chat"}") }
            Deck.OLD -> pool.filter { isOld(it, now) }.sortedBy { it.takenAtMillis }.take(count).map { Pick(it) }
            Deck.SIMILAR -> {
                // Whole groups only, so look-alikes always appear back to back.
                val out = ArrayList<Pick>()
                for (group in similarGroups(pool)) {
                    if (out.size >= count) break
                    group.forEachIndexed { i, m -> out += Pick(m, "👯 Look-alike ${i + 1}/${group.size}") }
                }
                out
            }
        }
    }
}

object Blur {
    /** Below this Laplacian variance (on a ~256px thumbnail) a photo counts as blurry. */
    const val THRESHOLD = 90.0

    /** Variance of the Laplacian over a greyscale image: low values mean few sharp edges. */
    fun laplacianVariance(luma: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3) return Double.MAX_VALUE
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val l = 4 * luma[i] - luma[i - 1] - luma[i + 1] - luma[i - width] - luma[i + width]
                sum += l
                sumSq += l.toDouble() * l
                n++
            }
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }
}
