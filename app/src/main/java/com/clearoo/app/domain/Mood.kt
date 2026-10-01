package com.clearoo.app.domain

enum class Mood { HAPPY, PROUD, HOPEFUL, WORRIED, SAD, SLEEPY, LOVE, SHOCKED, EXCITED }

object MoodRules {
    /** Roo's resting mood, used on the home screen, widget and notifications. */
    fun moodFor(p: Progress, today: Long, hour: Int, goal: Int): Mood {
        val deleted = StreakRules.deletedToday(p, today)
        val streak = StreakRules.currentStreak(p, today)
        return when {
            deleted >= goal -> Mood.PROUD
            deleted >= StreakRules.STREAK_MIN -> Mood.HAPPY
            hour < 7 || hour >= 23 -> Mood.SLEEPY
            streak == 0 && p.bestStreak > 0 -> Mood.SAD
            streak > 0 && hour >= 18 -> Mood.WORRIED
            else -> Mood.HOPEFUL
        }
    }

    /** Mood while swiping, driven by today's count (confirmed + in the bin). */
    fun sessionMood(count: Int, goal: Int): Mood = when {
        count >= goal -> Mood.PROUD
        count >= StreakRules.STREAK_MIN -> Mood.HAPPY
        else -> Mood.HOPEFUL
    }
}
