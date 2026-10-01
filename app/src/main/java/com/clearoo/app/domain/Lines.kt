package com.clearoo.app.domain

/** Everything Roo says. Picks are deterministic per seed so text doesn't flicker on recomposition. */
object Lines {
    private fun <T> List<T>.pick(seed: Long): T = this[Math.floorMod(seed, size.toLong()).toInt()]

    fun home(mood: Mood, streak: Int, deleted: Int, goal: Int, seed: Long): String = when (mood) {
        Mood.PROUD -> listOf(
            "You crushed it today! 🏆 Your phone feels lighter already.",
            "Goal smashed! I'm doing a happy hop 🦘",
            "$deleted cleared today. You're a legend ✨",
        ).pick(seed)
        Mood.HAPPY -> listOf(
            "Streak saved! 🔥 Go for $goal?",
            "Nice work! ${(goal - deleted).coerceAtLeast(0)} more and today's goal is done.",
        ).pick(seed)
        Mood.WORRIED -> listOf(
            "Our $streak-day streak is in danger! Just ${StreakRules.STREAK_MIN} deletes to save it 😰",
            "Psst… the day's almost over. Save our streak? 🔥",
        ).pick(seed)
        Mood.SAD -> listOf(
            "We lost our streak… 🥺 Let's start a new one today.",
            "I missed swiping with you 😢",
        ).pick(seed)
        Mood.SLEEPY -> listOf(
            "Zzz… a few swipes before bed? 😴",
            "Late-night clean-up? I'm in 🌙",
        ).pick(seed)
        else -> listOf(
            "Ready for a quick clean-up? $goal swipes, 2 minutes.",
            "I found some photos you forgot about 👀",
            "Let's make some space today! 🧹",
        ).pick(seed)
    }

    fun widget(mood: Mood): String = when (mood) {
        Mood.PROUD -> "Goal done! 🏆"
        Mood.HAPPY -> "Streak safe 🔥"
        Mood.WORRIED -> "Save our streak! 😰"
        Mood.SAD -> "I miss you 🥺"
        Mood.SLEEPY -> "Zzz…"
        else -> "Swipe time? 👀"
    }

    fun notification(mood: Mood, streak: Int, seed: Long): Pair<String, String> = when (mood) {
        Mood.WORRIED -> "🔥 Your $streak-day streak is at risk!" to
            "Roo needs just ${StreakRules.STREAK_MIN} deletes to keep it alive. 2 minutes, tops."
        Mood.SAD -> "Roo misses you 🥺" to "Your gallery keeps growing… swipe through a few photos today?"
        else -> listOf(
            "Roo found some photos for you 👀" to "Swipe left to delete, right to keep. Ready?",
            "Your gallery called 📞" to "It wants to go on a diet. 10 quick swipes?",
            "Time for your daily clean-up ✨" to "Free up some space in under 2 minutes.",
            "Swipe night is here 🦘" to "10 photos are waiting for your verdict.",
        ).pick(seed)
    }

    fun deleteReaction(seed: Long): String =
        listOf("Bye-bye! 👋", "Yeet! 🗑️", "Less is more ✨", "Gone with the wind 💨", "Byeee 🫡").pick(seed)

    fun keepReaction(seed: Long): String =
        listOf("A keeper! 💖", "Aww, cute 🥰", "Good memory ✨", "Saved! 📌").pick(seed)
}
