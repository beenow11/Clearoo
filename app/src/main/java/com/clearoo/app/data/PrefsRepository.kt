package com.clearoo.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.clearoo.app.domain.Album
import com.clearoo.app.domain.AlbumRules
import com.clearoo.app.domain.NO_DAY
import com.clearoo.app.domain.Outfit
import com.clearoo.app.domain.Progress
import com.clearoo.app.domain.StreakRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * Two stores, split by what survives a move to a new phone:
 * - "clearoo": stats, streak and settings. Backed up by Android (see res/xml/backup_rules.xml),
 *   so they come back after a reinstall or on a new phone.
 * - "clearoo_device": MediaStore ids (kept photos, the bin). Those ids only mean something on
 *   this phone, so they are never backed up; restored elsewhere they would hide the wrong photos.
 *
 * Keys are a contract with every installed copy of the app: never rename or reuse one.
 * Add new keys instead, and bump SCHEMA_VERSION if old data ever needs converting.
 */
private val Context.clearooStore: DataStore<Preferences> by preferencesDataStore(
    name = "clearoo",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
private val Context.deviceStore: DataStore<Preferences> by preferencesDataStore(
    name = "clearoo_device",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** A read error (not corruption) should show defaults, not crash. */
private fun DataStore<Preferences>.safeData(): Flow<Preferences> = data.catch { emit(emptyPreferences()) }

/**
 * A store whose saves never throw. When a save fails (most often because the phone's storage
 * is full, which is exactly when people need Clearoo), the change is kept in memory, shown as
 * if it were saved, and written together with the next save that succeeds.
 */
private class SafeStore(private val store: DataStore<Preferences>) {
    private val unsaved = MutableStateFlow<List<(MutablePreferences) -> Unit>>(emptyList())
    private val lock = Mutex()

    val data: Flow<Preferences> = combine(store.data, unsaved) { saved, ops ->
        if (ops.isEmpty()) saved else saved.toMutablePreferences().apply { ops.forEach { it(this) } }
    }

    /** Applies [op] and returns the resulting preferences, saved or not. */
    suspend fun edit(op: (MutablePreferences) -> Unit): Preferences = lock.withLock {
        val queued = unsaved.value
        try {
            store.edit { p ->
                queued.forEach { it(p) }
                op(p)
            }.also { unsaved.value = emptyList() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            unsaved.value = queued + op
            data.first()
        }
    }
}

/** One SafeStore per file for the whole process, so every screen, the widget and the worker agree. */
private object Stores {
    private var main: SafeStore? = null
    private var device: SafeStore? = null

    @Synchronized
    fun main(context: Context): SafeStore = main ?: SafeStore(context.clearooStore).also { main = it }

    @Synchronized
    fun device(context: Context): SafeStore = device ?: SafeStore(context.deviceStore).also { device = it }
}

data class AppSettings(
    val reminderHour: Int = 19,
    val reminderMinute: Int = 30,
    val dailyGoal: Int = StreakRules.DEFAULT_GOAL,
    val permanentDelete: Boolean = false,
    val onboarded: Boolean = false,
    val outfit: Outfit = Outfit.CLASSIC,
)

class PrefsRepository(context: Context) {
    private val store = Stores.main(context.applicationContext)
    private val device = Stores.device(context.applicationContext)

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
        val SCHEMA_VERSION = intPreferencesKey("schema_version")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val PERMANENT_DELETE = booleanPreferencesKey("permanent_delete")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val OUTFIT = stringPreferencesKey("outfit")
    }

    /** Keys in the device-only store. */
    private object DeviceKeys {
        val KEPT_IDS = stringSetPreferencesKey("kept_ids")
        val BIN_IDS = stringSetPreferencesKey("bin_ids")
        val ALBUMS = stringPreferencesKey("albums")
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

    val keptIds: Flow<Set<Long>> = device.data.map { p -> p[DeviceKeys.KEPT_IDS].toIds() }

    /** Items waiting in the bin, so a swipe session survives the app being closed. */
    val binIds: Flow<Set<Long>> = device.data.map { p -> p[DeviceKeys.BIN_IDS].toIds() }

    /** The user's albums, newest first. Device-only: they list MediaStore ids. */
    val albums: Flow<List<Album>> = device.data.map { p -> AlbumRules.decode(p[DeviceKeys.ALBUMS]) }.distinctUntilChanged()

    suspend fun setAlbums(albums: List<Album>) {
        device.edit { p -> p[DeviceKeys.ALBUMS] = AlbumRules.encode(albums) }
    }

    suspend fun setBin(ids: Collection<Long>) {
        device.edit { p -> p[DeviceKeys.BIN_IDS] = ids.map { it.toString() }.toSet() }
    }

    /** Returns (before, after) so callers can tell whether the streak just grew. */
    suspend fun recordDeletion(count: Int, bytes: Long, today: Long): Pair<Progress, Progress> {
        val before = progress.first()
        val after = store.edit { p -> p.write(StreakRules.recordDeletion(p.toProgress(), count, bytes, today)) }.toProgress()
        return before to after
    }

    suspend fun recordReview(delta: Int, today: Long) {
        store.edit { p -> p.write(StreakRules.recordReview(p.toProgress(), delta, today)) }
    }

    suspend fun addKept(id: Long) {
        device.edit { p -> p[DeviceKeys.KEPT_IDS] = p[DeviceKeys.KEPT_IDS].orEmpty() + id.toString() }
    }

    suspend fun removeKept(id: Long) {
        device.edit { p -> p[DeviceKeys.KEPT_IDS] = p[DeviceKeys.KEPT_IDS].orEmpty() - id.toString() }
    }

    suspend fun clearKept() {
        device.edit { p -> p.remove(DeviceKeys.KEPT_IDS) }
    }

    private fun Set<String>?.toIds(): Set<Long> = orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

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
        this[Keys.SCHEMA_VERSION] = SCHEMA_VERSION
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

/** Version of the stored data layout; bump it alongside a migration when old data must be converted. */
private const val SCHEMA_VERSION = 1
