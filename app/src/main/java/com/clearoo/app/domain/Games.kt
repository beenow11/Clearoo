package com.clearoo.app.domain

import kotlin.random.Random

/** A stretch of Roo's climb. Every photo deleted is one hop up the path. */
data class World(val name: String, val emoji: String, val startHop: Int)

object ClimbRules {
    const val MILESTONE_EVERY = 25

    val WORLDS = listOf(
        World("Meadow", "🌼", 0),
        World("Desert", "🌵", 100),
        World("Snow peaks", "🏔️", 300),
        World("Space", "🚀", 600),
        World("Ocean", "🌊", 1_000),
        World("Volcano", "🌋", 1_600),
        World("Candy land", "🍭", 2_500),
        World("Rainbow sky", "🌈", 3_500),
        World("Roo's Outback home", "🏡", 5_000),
    )

    /** Reaching Roo's home is the top of the mountain. */
    const val SUMMIT = 5_000

    /** Worlds first reached when going from [before] to [after] hops. */
    fun worldsEntered(before: Int, after: Int): List<World> = WORLDS.filter { it.startHop in (before + 1)..after }

    private val REWARDS = listOf("🎈", "🌟", "🍀", "🎉", "🏅", "🌈", "🍉", "🎵")

    fun worldFor(hops: Int): World = WORLDS.last { hops >= it.startHop }

    fun nextWorld(hops: Int): World? = WORLDS.firstOrNull { it.startHop > hops }

    /** The next flag above [hops]; a flag sits every [MILESTONE_EVERY] hops. */
    fun nextMilestone(hops: Int): Int = (hops.coerceAtLeast(0) / MILESTONE_EVERY + 1) * MILESTONE_EVERY

    /** Flags reached when going from [before] to [after] hops, lowest first. */
    fun milestonesPassed(before: Int, after: Int): List<Int> =
        (before / MILESTONE_EVERY + 1..after / MILESTONE_EVERY).map { it * MILESTONE_EVERY }.filter { it > 0 }

    /** The sticker on the flag at [milestone] hops. */
    fun reward(milestone: Int): String = REWARDS[(milestone / MILESTONE_EVERY - 1).mod(REWARDS.size)]
}

/** A photo flying across the Pouch game. World units: [PouchGame.WIDTH] wide, y grows downwards. */
data class Flyer(
    val id: Int,
    val mediaId: Long,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val angle: Float = 0f,
    /** Seconds since it was sliced; null while whole. */
    val slicedFor: Float? = null,
    /** Direction of the cut, so the halves fly apart the right way. */
    val cutAngle: Float = 0f,
) {
    val sliced get() = slicedFor != null
}

data class PouchState(
    val height: Float,
    val queue: List<Long>,
    val flyers: List<Flyer> = emptyList(),
    val time: Float = 0f,
    val untilWave: Float = 0.6f,
    val nextId: Int = 0,
    /** Photos sliced into the pouch, in order. */
    val pouch: List<Long> = emptyList(),
    /** Photos that flew past untouched (skipped, not kept). */
    val skipped: Int = 0,
    val combo: Int = 0,
    val bestCombo: Int = 0,
) {
    val over get() = time >= PouchGame.ROUND_SECONDS || (queue.isEmpty() && flyers.isEmpty() && time > 0f)
    val secondsLeft get() = (PouchGame.ROUND_SECONDS - time).coerceAtLeast(0f)
}

/**
 * Roo's Pouch: the user's photos fly up like fruit. Slice one and it drops into Roo's pouch
 * (the bin); leave it and it falls away. Nothing is deleted here; the pouch is reviewed after.
 */
object PouchGame {
    const val WIDTH = 100f
    const val SIZE = 28f
    const val GRAVITY = 120f
    const val ROUND_SECONDS = 60f
    const val MAX_ON_SCREEN = 3
    /** How long the halves take to drop into the pouch. */
    const val SLICE_ANIM = 0.55f

    fun start(height: Float, queue: List<Long>) = PouchState(height = height, queue = queue)

    fun step(s: PouchState, dt: Float, rng: kotlin.random.Random): PouchState {
        if (s.over) return s
        val t = dt.coerceIn(0f, 0.05f)
        val time = s.time + t
        var skipped = s.skipped
        val moved = s.flyers.mapNotNull { f ->
            val sliced = f.slicedFor
            if (sliced != null) {
                val left = sliced + t
                if (left >= SLICE_ANIM) null else f.copy(slicedFor = left)
            } else {
                val n = f.copy(x = f.x + f.vx * t, y = f.y + f.vy * t, vy = f.vy + GRAVITY * t, angle = f.angle + f.spin * t)
                // Fell back below the screen untouched: skipped, it may come back another day.
                if (n.vy > 0 && n.y > s.height + SIZE) {
                    skipped++
                    null
                } else n
            }
        }.toMutableList()
        var queue = s.queue
        var nextId = s.nextId
        var untilWave = s.untilWave - t
        if (untilWave <= 0f && time < ROUND_SECONDS - 1.5f) {
            val whole = moved.count { !it.sliced }
            // Starts with one photo at a time and gently adds a second as the round goes on.
            val wave = if (time > 20f && rng.nextFloat() < 0.45f) 2 else 1
            repeat(minOf(wave, MAX_ON_SCREEN - whole, queue.size)) {
                moved += launch(nextId++, queue.first(), s.height, rng)
                queue = queue.drop(1)
            }
            untilWave = 1.7f - 0.6f * (time / ROUND_SECONDS) + rng.nextFloat() * 0.5f
        }
        return s.copy(flyers = moved, time = time, untilWave = untilWave, queue = queue, nextId = nextId, skipped = skipped)
    }

    /** Throws a photo up from below the screen so it peaks in the top half and lands near the middle. */
    fun launch(id: Int, mediaId: Long, height: Float, rng: kotlin.random.Random): Flyer {
        val x0 = 12f + rng.nextFloat() * (WIDTH - 24f - SIZE)
        val rise = height * (0.55f + rng.nextFloat() * 0.2f)
        val vy = -kotlin.math.sqrt(2 * GRAVITY * rise)
        val air = 2 * -vy / GRAVITY
        val target = 20f + rng.nextFloat() * (WIDTH - 40f - SIZE)
        return Flyer(
            id = id, mediaId = mediaId, x = x0, y = height + 4f, vx = (target - x0) / air, vy = vy,
            spin = (rng.nextFloat() - 0.5f) * 90f,
        )
    }

    /**
     * A finger moved from (x0, y0) to (x1, y1). Every whole photo the line crosses is sliced into
     * the pouch. [newSwipe] starts a fresh combo count.
     */
    fun slice(s: PouchState, x0: Float, y0: Float, x1: Float, y1: Float, newSwipe: Boolean): PouchState {
        if (s.over) return s
        var combo = if (newSwipe) 0 else s.combo
        val pouch = s.pouch.toMutableList()
        val cut = kotlin.math.atan2(y1 - y0, x1 - x0)
        val flyers = s.flyers.map { f ->
            if (f.sliced || !crosses(x0, y0, x1, y1, f.x + SIZE / 2, f.y + SIZE / 2, SIZE * 0.5f)) return@map f
            combo++
            pouch += f.mediaId
            f.copy(slicedFor = 0f, cutAngle = cut)
        }
        return s.copy(flyers = flyers, pouch = pouch, combo = combo, bestCombo = maxOf(s.bestCombo, combo))
    }

    /** Whether the segment from (ax, ay) to (bx, by) passes within [r] of (cx, cy). */
    fun crosses(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, r: Float): Boolean {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((cx - ax) * dx + (cy - ay) * dy) / len2).coerceIn(0f, 1f)
        val px = ax + t * dx - cx
        val py = ay + t * dy - cy
        return px * px + py * py <= r * r
    }
}
