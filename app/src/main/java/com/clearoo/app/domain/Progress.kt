package com.clearoo.app.domain

import kotlin.math.max

/** Days are stored as epoch days (LocalDate.toEpochDay) so the rules stay pure and testable. */
const val NO_DAY = Long.MIN_VALUE

data class Progress(
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastGoalDay: Long = NO_DAY,
    val day: Long = NO_DAY,
    val deletedToday: Int = 0,
    val freedToday: Long = 0,
    val reviewedToday: Int = 0,
    val totalDeleted: Int = 0,
    val totalFreed: Long = 0,
    val totalReviewed: Int = 0,
    /** Recent days on which the streak minimum was reached (kept for the week strip). */
    val goalDays: Set<Long> = emptySet(),
)

object StreakRules {
    /** Deletions needed in a day to keep the streak alive. */
    const val STREAK_MIN = 5
    const val DEFAULT_GOAL = 10
    private const val HISTORY_DAYS = 60

    fun forDay(p: Progress, today: Long): Progress =
        if (p.day == today) p else p.copy(day = today, deletedToday = 0, freedToday = 0, reviewedToday = 0)

    fun deletedToday(p: Progress, today: Long): Int = if (p.day == today) p.deletedToday else 0

    fun freedToday(p: Progress, today: Long): Long = if (p.day == today) p.freedToday else 0

    /** The streak still counts if it was extended today or yesterday. */
    fun currentStreak(p: Progress, today: Long): Int =
        if (p.lastGoalDay == today || p.lastGoalDay == today - 1) p.streak else 0

    fun securedToday(p: Progress, today: Long): Boolean = p.lastGoalDay == today

    fun atRisk(p: Progress, today: Long): Boolean = currentStreak(p, today) > 0 && !securedToday(p, today)

    fun recordDeletion(p: Progress, count: Int, bytes: Long, today: Long): Progress {
        val base = forDay(p, today)
        val deleted = base.deletedToday + count
        val next = base.copy(
            deletedToday = deleted,
            freedToday = base.freedToday + bytes,
            reviewedToday = base.reviewedToday + count,
            totalDeleted = base.totalDeleted + count,
            totalFreed = base.totalFreed + bytes,
            totalReviewed = base.totalReviewed + count,
        )
        if (deleted < STREAK_MIN || base.lastGoalDay == today) return next
        val streak = if (base.lastGoalDay == today - 1) base.streak + 1 else 1
        return next.copy(
            streak = streak,
            bestStreak = max(base.bestStreak, streak),
            lastGoalDay = today,
            goalDays = (base.goalDays + today).filter { it > today - HISTORY_DAYS }.toSet(),
        )
    }

    fun recordReview(p: Progress, delta: Int, today: Long): Progress {
        val base = forDay(p, today)
        return base.copy(
            reviewedToday = max(0, base.reviewedToday + delta),
            totalReviewed = max(0, base.totalReviewed + delta),
        )
    }
}
