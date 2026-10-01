package com.clearoo.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.clearoo.app.domain.NO_DAY
import com.clearoo.app.domain.Outfit
import com.clearoo.app.domain.Progress
import com.clearoo.app.domain.StreakRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.clearooStore: DataStore<Preferences> by preferencesDataStore(name = "clearoo")

data class AppSettings(
    val reminderHour: Int = 19,
    val reminderMinute: Int = 30,
    val dailyGoal: Int = StreakRules.DEFAULT_GOAL,
    val permanentDelete: Boolean = false,
    val onboarded: Boolean = false,
    val outfit: Outfit = Outfit.CLASSIC,
)

class PrefsRepository(context: Context) {
    private val store = context.applicationContext.clearooStore

    private object Keys {
        val STREAK = intPreferencesKey("streak")
        val BEST_STREAK = intPreferencesKey("best_streak")
        val LAST_GOAL_DAY = longPreferencesKey("last_goal_day")
        val DAY = longPreferencesKey("day")
        val DELETED_TODAY = intPreferencesKey("deleted_today")
        val FREED_TODAY = longPreferencesKey("freed_today")
        val REVIEWED_TODAY = intPreferencesKey("reviewed_today")
        val TOTAL_DELETED = intPreferencesKey("total_deleted")
        val TOTAL_FREED = longPreferencesKey("total_freed")
        val TOTAL_REVIEWED = intPreferencesKey("total_reviewed")
        val GOAL_DAYS = stringSetPreferencesKey("goal_days")
        val KEPT_IDS = stringSetPreferencesKey("kept_ids")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val PERMANENT_DELETE = booleanPreferencesKey("permanent_delete")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val OUTFIT = stringPreferencesKey("outfit")
    }

    val progress: Flow<Progress> = store.data.map { it.toProgress() }.distinctUntilChanged()

    val settings: Flow<AppSettings> = store.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            reminderHour = p[Keys.REMINDER_HOUR] ?: defaults.reminderHour,
            reminderMinute = p[Keys.REMINDER_MINUTE] ?: defaults.reminderMinute,
            dailyGoal = p[Keys.DAILY_GOAL] ?: defaults.dailyGoal,
            permanentDelete = p[Keys.PERMANENT_DELETE] ?: defaults.permanentDelete,
            onboarded = p[Keys.ONBOARDED] ?: defaults.onboarded,
            outfit = p[Keys.OUTFIT]?.let { name -> Outfit.entries.firstOrNull { it.name == name } } ?: defaults.outfit,
        )
    }.distinctUntilChanged()

    val keptIds: Flow<Set<Long>> = store.data.map { p ->
        p[Keys.KEPT_IDS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    /** Returns (before, after) so callers can tell whether the streak just grew. */
    suspend fun recordDeletion(count: Int, bytes: Long, today: Long): Pair<Progress, Progress> {
        lateinit var result: Pair<Progress, Progress>
        store.edit { p ->
            val before = p.toProgress()
            val after = StreakRules.recordDeletion(before, count, bytes, today)
            p.write(after)
            result = before to after
        }
        return result
    }

    suspend fun recordReview(delta: Int, today: Long) {
        store.edit { p -> p.write(StreakRules.recordReview(p.toProgress(), delta, today)) }
    }

    suspend fun addKept(id: Long) {
        store.edit { p -> p[Keys.KEPT_IDS] = p[Keys.KEPT_IDS].orEmpty() + id.toString() }
    }

    suspend fun removeKept(id: Long) {
        store.edit { p -> p[Keys.KEPT_IDS] = p[Keys.KEPT_IDS].orEmpty() - id.toString() }
    }

    suspend fun clearKept() {
        store.edit { p -> p.remove(Keys.KEPT_IDS) }
    }

    suspend fun setReminder(hour: Int, minute: Int) {
        store.edit { p ->
            p[Keys.REMINDER_HOUR] = hour
            p[Keys.REMINDER_MINUTE] = minute
        }
    }

    suspend fun setDailyGoal(goal: Int) {
        store.edit { p -> p[Keys.DAILY_GOAL] = goal }
    }

    suspend fun setPermanentDelete(enabled: Boolean) {
        store.edit { p -> p[Keys.PERMANENT_DELETE] = enabled }
    }

    suspend fun setOutfit(outfit: Outfit) {
        store.edit { p -> p[Keys.OUTFIT] = outfit.name }
    }

    suspend fun setOnboarded() {
        store.edit { p -> p[Keys.ONBOARDED] = true }
    }

    private fun Preferences.toProgress() = Progress(
        streak = this[Keys.STREAK] ?: 0,
        bestStreak = this[Keys.BEST_STREAK] ?: 0,
        lastGoalDay = this[Keys.LAST_GOAL_DAY] ?: NO_DAY,
        day = this[Keys.DAY] ?: NO_DAY,
        deletedToday = this[Keys.DELETED_TODAY] ?: 0,
        freedToday = this[Keys.FREED_TODAY] ?: 0,
        reviewedToday = this[Keys.REVIEWED_TODAY] ?: 0,
        totalDeleted = this[Keys.TOTAL_DELETED] ?: 0,
        totalFreed = this[Keys.TOTAL_FREED] ?: 0,
        totalReviewed = this[Keys.TOTAL_REVIEWED] ?: 0,
        goalDays = this[Keys.GOAL_DAYS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
    )

    private fun MutablePreferences.write(p: Progress) {
        this[Keys.STREAK] = p.streak
        this[Keys.BEST_STREAK] = p.bestStreak
        this[Keys.LAST_GOAL_DAY] = p.lastGoalDay
        this[Keys.DAY] = p.day
        this[Keys.DELETED_TODAY] = p.deletedToday
        this[Keys.FREED_TODAY] = p.freedToday
        this[Keys.REVIEWED_TODAY] = p.reviewedToday
        this[Keys.TOTAL_DELETED] = p.totalDeleted
        this[Keys.TOTAL_FREED] = p.totalFreed
        this[Keys.TOTAL_REVIEWED] = p.totalReviewed
        this[Keys.GOAL_DAYS] = p.goalDays.map { it.toString() }.toSet()
    }
}
