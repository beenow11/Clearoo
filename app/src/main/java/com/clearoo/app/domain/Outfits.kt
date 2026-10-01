package com.clearoo.app.domain

/** What an outfit's unlock is measured against. Streaks use the best streak, so a lost streak never re-locks anything. */
enum class Goal { NONE, BEST_STREAK, TOTAL_DELETED, TOTAL_FREED }

/** Roo's wardrobe. Order is the order shown in the wardrobe. */
enum class Outfit(val emoji: String, val title: String, val goal: Goal, val target: Long) {
    CLASSIC("🦘", "Classic Roo", Goal.NONE, 0),
    PARTY_HAT("🎉", "Party hat", Goal.BEST_STREAK, 3),
    SCARF("🧣", "Cosy scarf", Goal.TOTAL_DELETED, 100),
    SUNGLASSES("😎", "Shades", Goal.BEST_STREAK, 7),
    WIZARD_HAT("🧙", "Wizard hat", Goal.TOTAL_FREED, 1L shl 30),
    HEADPHONES("🎧", "Headphones", Goal.BEST_STREAK, 14),
    CAPE("🦸", "Hero cape", Goal.TOTAL_DELETED, 500),
    CROWN("👑", "Crown", Goal.BEST_STREAK, 30),
}

object OutfitRules {
    fun current(outfit: Outfit, p: Progress): Long = when (outfit.goal) {
        Goal.NONE -> 0
        Goal.BEST_STREAK -> p.bestStreak.toLong()
        Goal.TOTAL_DELETED -> p.totalDeleted.toLong()
        Goal.TOTAL_FREED -> p.totalFreed
    }

    fun isUnlocked(outfit: Outfit, p: Progress): Boolean = current(outfit, p) >= outfit.target

    /** 0…1 progress toward unlocking. */
    fun progress(outfit: Outfit, p: Progress): Float =
        if (outfit.target == 0L) 1f else (current(outfit, p).toFloat() / outfit.target).coerceIn(0f, 1f)

    fun hint(outfit: Outfit): String = when (outfit.goal) {
        Goal.NONE -> "Always yours"
        Goal.BEST_STREAK -> "Reach a ${outfit.target}-day streak"
        Goal.TOTAL_DELETED -> "Delete ${outfit.target} items"
        Goal.TOTAL_FREED -> "Free ${outfit.target shr 30} GB"
    }

    fun newlyUnlocked(before: Progress, after: Progress): List<Outfit> =
        Outfit.entries.filter { !isUnlocked(it, before) && isUnlocked(it, after) }
}
