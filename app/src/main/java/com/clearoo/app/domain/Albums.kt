package com.clearoo.app.domain

/** A user-made set of photos to clean up together, e.g. a trip. Ids are MediaStore ids. */
data class Album(val id: Long, val name: String, val ids: Set<Long>)

object AlbumRules {
    const val MAX_NAME = 40
    private val ILLEGAL = Regex("""[\\/:*?"<>|\t\r\n]""")

    /** A name that is also safe as a folder name; empty if nothing usable is left. */
    fun cleanName(raw: String): String = raw.replace(ILLEGAL, " ").replace(Regex(" +"), " ").trim().trim('.').take(MAX_NAME).trim()

    /** Where kept photos go when the user moves them, e.g. "Pictures/London/". */
    fun folderPath(name: String): String = "Pictures/${cleanName(name)}/"

    /** Ids of items taken between [startMillis] and [endMillis], inclusive. */
    fun inRange(items: List<MediaMeta>, startMillis: Long, endMillis: Long): List<MediaMeta> =
        items.filter { it.takenAtMillis in startMillis..endMillis }.sortedBy { it.takenAtMillis }

    /** One album per line: id, name and ids separated by tabs (names never contain tabs). */
    fun encode(albums: List<Album>): String = albums.joinToString("\n") { a ->
        "${a.id}\t${cleanName(a.name)}\t${a.ids.joinToString(",")}"
    }

    /** Skips anything it can't read rather than losing every album. */
    fun decode(text: String?): List<Album> = text.orEmpty().lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 3) return@mapNotNull null
        val id = parts[0].toLongOrNull() ?: return@mapNotNull null
        val name = parts[1].takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val ids = parts[2].split(',').mapNotNull { it.toLongOrNull() }.toSet()
        Album(id, name, ids)
    }.toList()
}

/** Finds near-identical photos with a difference hash (dHash) over a tiny greyscale thumbnail. */
object Duplicates {
    const val HASH_W = 9
    const val HASH_H = 8
    /** Up to this many of the 64 bits may differ and still count as the same picture. */
    const val MAX_DISTANCE = 6

    /** [luma] is a [HASH_W] x [HASH_H] greyscale image; each bit says whether a pixel is brighter than its right neighbour. */
    fun dHash(luma: IntArray): Long {
        require(luma.size == HASH_W * HASH_H)
        var hash = 0L
        var bit = 0
        for (y in 0 until HASH_H) {
            for (x in 0 until HASH_W - 1) {
                val i = y * HASH_W + x
                if (luma[i] > luma[i + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Groups of 2+ ids whose hashes are close, in the order of [hashes]. Each id is in at most one group. */
    fun groups(hashes: List<Pair<Long, Long>>): List<List<Long>> {
        val used = HashSet<Long>()
        val out = ArrayList<List<Long>>()
        for ((i, first) in hashes.withIndex()) {
            if (first.first in used) continue
            val group = arrayListOf(first.first)
            for (j in i + 1 until hashes.size) {
                val other = hashes[j]
                if (other.first !in used && distance(first.second, other.second) <= MAX_DISTANCE) group += other.first
            }
            if (group.size > 1) {
                used += group
                out += group
            }
        }
        return out
    }
}
