package com.clearoo.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoodRulesTest {
    private val day = 20_000L

    @Test
    fun `fresh user is hopeful during the day`() {
        assertEquals(Mood.HOPEFUL, MoodRules.moodFor(Progress(), day, 12, 10))
    }

    @Test
    fun `goal reached makes Roo proud`() {
        val p = StreakRules.recordDeletion(Progress(), 10, 0, day)
        assertEquals(Mood.PROUD, MoodRules.moodFor(p, day, 21, 10))
    }

    @Test
    fun `streak secured makes Roo happy`() {
        val p = StreakRules.recordDeletion(Progress(), 6, 0, day)
        assertEquals(Mood.HAPPY, MoodRules.moodFor(p, day, 21, 10))
    }

    @Test
    fun `streak at risk in the evening worries Roo`() {
        val p = StreakRules.recordDeletion(Progress(), 5, 0, day)
        assertEquals(Mood.WORRIED, MoodRules.moodFor(p, day + 1, 19, 10))
        assertEquals(Mood.HOPEFUL, MoodRules.moodFor(p, day + 1, 10, 10))
    }

    @Test
    fun `lost streak makes Roo sad`() {
        val p = StreakRules.recordDeletion(Progress(), 5, 0, day)
        assertEquals(Mood.SAD, MoodRules.moodFor(p, day + 3, 12, 10))
    }

    @Test
    fun `late night is sleepy`() {
        assertEquals(Mood.SLEEPY, MoodRules.moodFor(Progress(), day, 23, 10))
    }
}
