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
    )

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

/** How many Roo Hop runs are left today: one free run, three more once the streak is secured. */
object PlayRules {
    const val FREE_RUNS = 1
    const val STREAK_RUNS = 3

    fun allowed(securedToday: Boolean): Int = FREE_RUNS + if (securedToday) STREAK_RUNS else 0

    fun runsLeft(securedToday: Boolean, runsDay: Long, runsUsed: Int, today: Long): Int {
        val used = if (runsDay == today) runsUsed else 0
        return (allowed(securedToday) - used).coerceAtLeast(0)
    }
}

/** Something on the track: junk to jump over, or a star to catch. Units match [HopGame]. */
data class Thing(val id: Int, val x: Float, val y: Float, val size: Float, val isStar: Boolean, val kind: Int)

data class HopState(
    val rooY: Float = 0f,
    val rooVy: Float = 0f,
    val things: List<Thing> = emptyList(),
    val speed: Float = HopGame.START_SPEED,
    val distance: Float = 0f,
    val stars: Int = 0,
    val untilSpawn: Float = 1.2f,
    val nextId: Int = 0,
    val over: Boolean = false,
) {
    val onGround get() = rooY <= 0f
    val score get() = (distance / 10f).toInt() + stars * HopGame.STAR_POINTS
}

/**
 * Roo Hop: Roo runs along the ground, junk files slide in from the right, tap to jump.
 * World units: the track is [WIDTH] wide, the ground is y = 0 and up is positive.
 */
object HopGame {
    const val WIDTH = 100f
    const val ROO_X = 16f
    const val ROO_SIZE = 12f
    const val GRAVITY = 320f
    const val JUMP = 128f
    const val START_SPEED = 48f
    const val MAX_SPEED = 110f
    const val ACCEL = 1.6f
    const val STAR_POINTS = 10
    /** Hitboxes are a little smaller than the drawings so near misses feel fair. */
    private const val FORGIVE = 0.7f

    fun jump(s: HopState): HopState = if (s.over || !s.onGround) s else s.copy(rooVy = JUMP)

    fun step(s: HopState, dt: Float, rng: Random): HopState {
        if (s.over) return s
        val t = dt.coerceIn(0f, 0.05f)
        var vy = s.rooVy - GRAVITY * t
        var y = s.rooY + vy * t
        if (y <= 0f) {
            y = 0f
            vy = 0f
        }
        val speed = (s.speed + ACCEL * t).coerceAtMost(MAX_SPEED)
        var nextId = s.nextId
        var untilSpawn = s.untilSpawn - t
        val moved = s.things.map { it.copy(x = it.x - speed * t) }.filter { it.x + it.size > -5f }.toMutableList()
        if (untilSpawn <= 0f) {
            val star = rng.nextFloat() < 0.3f
            moved += if (star) {
                Thing(nextId++, WIDTH + 5f, 18f + rng.nextFloat() * 10f, 7f, true, 0)
            } else {
                Thing(nextId++, WIDTH + 5f, 0f, 8f + rng.nextFloat() * 3f, false, rng.nextInt(4))
            }
            // Gaps shrink as the game speeds up, but always leave room to land and jump again.
            untilSpawn = (1.1f + rng.nextFloat() * 0.9f) * (START_SPEED / speed).coerceAtLeast(0.55f)
        }
        var stars = s.stars
        var over = false
        val kept = moved.filter { th ->
            if (!hits(y, th)) return@filter true
            if (th.isStar) {
                stars++
                false
            } else {
                over = true
                true
            }
        }
        return s.copy(
            rooY = y, rooVy = vy, things = kept, speed = speed, distance = s.distance + speed * t,
            stars = stars, untilSpawn = untilSpawn, nextId = nextId, over = over,
        )
    }

    /** Box overlap between Roo (standing at [rooY]) and [th], with forgiving hitboxes. */
    fun hits(rooY: Float, th: Thing): Boolean {
        val r = ROO_SIZE * FORGIVE / 2
        val o = th.size * FORGIVE / 2
        val rooCx = ROO_X + ROO_SIZE / 2
        val rooCy = rooY + ROO_SIZE / 2
        val thCx = th.x + th.size / 2
        val thCy = th.y + th.size / 2
        return kotlin.math.abs(rooCx - thCx) < r + o && kotlin.math.abs(rooCy - thCy) < r + o
    }
}
