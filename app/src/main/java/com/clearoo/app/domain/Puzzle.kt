package com.clearoo.app.domain

import kotlin.random.Random

/**
 * Roo's Puzzle: a photo cut into a size × size grid and shuffled. Tap two tiles to swap them.
 * [tiles] holds, for each position on the board, which piece of the photo sits there.
 */
data class Puzzle(val size: Int, val tiles: List<Int>, val moves: Int = 0) {
    val solved get() = tiles.withIndex().all { (pos, piece) -> pos == piece }
    /** Pieces already in their right place. */
    val inPlace get() = tiles.withIndex().count { (pos, piece) -> pos == piece }
}

object PuzzleRules {
    val SIZES = listOf(2, 3, 4, 5, 6)

    /** A shuffled board with no piece left in its place, so every tile needs a move. */
    fun shuffled(size: Int, rng: Random): Puzzle {
        require(size in 2..8)
        val n = size * size
        // A random cyclic shift of a shuffled cycle: a derangement, so no tile starts solved.
        val order = (0 until n).shuffled(rng)
        val tiles = IntArray(n)
        for (i in 0 until n) tiles[order[i]] = order[(i + 1) % n]
        return Puzzle(size, tiles.toList())
    }

    /** Swaps the pieces at two board positions; one move. */
    fun swap(p: Puzzle, a: Int, b: Int): Puzzle {
        if (a == b || a !in p.tiles.indices || b !in p.tiles.indices) return p
        val t = p.tiles.toMutableList()
        t[a] = t[b].also { t[b] = t[a] }
        return p.copy(tiles = t, moves = p.moves + 1)
    }

    /** Fewest swaps that can solve [p]: pieces out of place minus the number of cycles. */
    fun minSwaps(p: Puzzle): Int {
        val seen = BooleanArray(p.tiles.size)
        var swaps = 0
        for (i in p.tiles.indices) {
            if (seen[i] || p.tiles[i] == i) continue
            var j = i
            var len = 0
            while (!seen[j]) {
                seen[j] = true
                j = p.tiles[j]
                len++
            }
            swaps += len - 1
        }
        return swaps
    }

    /** Stars for a solve: 3 for a near-perfect run, fewer the more extra swaps it took. */
    fun stars(moves: Int, minimum: Int): Int = when {
        moves <= minimum + minimum / 4 -> 3
        moves <= minimum * 2 -> 2
        else -> 1
    }
}
