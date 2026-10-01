package com.clearoo.app.ui

import android.app.Application
import android.content.IntentSender
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clearoo.app.data.AppSettings
import com.clearoo.app.data.GallerySummary
import com.clearoo.app.data.MediaItem
import com.clearoo.app.data.MediaRepository
import com.clearoo.app.data.PrefsRepository
import com.clearoo.app.domain.Lines
import com.clearoo.app.domain.Mood
import com.clearoo.app.domain.MoodRules
import com.clearoo.app.domain.Progress
import com.clearoo.app.domain.StreakRules
import com.clearoo.app.notify.Notifications
import com.clearoo.app.notify.ReminderScheduler
import com.clearoo.app.widget.RooWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class SwipeRecord(val item: MediaItem, val keep: Boolean)

/** A short-lived mascot reaction to a swipe. */
data class Reaction(val mood: Mood, val text: String, val id: Long)

data class CleanResult(
    val count: Int,
    val bytes: Long,
    val streak: Int,
    val streakGrew: Boolean,
    val goalReached: Boolean,
)

class ClearooViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = PrefsRepository(app)
    private val media = MediaRepository(app)

    val progress: StateFlow<Progress> =
        prefs.progress.stateIn(viewModelScope, SharingStarted.Eagerly, Progress())
    val settings: StateFlow<AppSettings?> =
        prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** deck[0] is the card on top. */
    val deck = mutableStateListOf<MediaItem>()
    /** Swiped left, waiting for the user to confirm in the bin. */
    val pending = mutableStateListOf<MediaItem>()

    var deckLoading by mutableStateOf(false)
        private set
    var deckExhausted by mutableStateOf(false)
        private set
    var canUndo by mutableStateOf(false)
        private set
    /** The swipe that was just undone, so the card can fly back in from the side it left. */
    var lastUndone by mutableStateOf<SwipeRecord?>(null)
        private set
    var reaction by mutableStateOf<Reaction?>(null)
        private set
    var gallery by mutableStateOf<GallerySummary?>(null)
        private set
    var lastResult by mutableStateOf<CleanResult?>(null)
        private set

    private val history = ArrayDeque<SwipeRecord>()
    private val seen = HashSet<Long>()
    private var swipeCount = 0L

    private fun today() = LocalDate.now().toEpochDay()

    fun refreshGallery() {
        viewModelScope.launch { gallery = runCatching { media.summary() }.getOrNull() }
    }

    fun ensureDeck() {
        if (deck.isEmpty()) loadMore()
    }

    private fun loadMore() {
        if (deckLoading) return
        deckLoading = true
        viewModelScope.launch {
            val exclude = prefs.keptIds.first() + seen + pending.map { it.id }
            val batch = runCatching { media.randomBatch(BATCH_SIZE, exclude) }.getOrDefault(emptyList())
            batch.forEach { seen += it.id }
            deck.addAll(batch)
            deckExhausted = deck.isEmpty()
            deckLoading = false
        }
    }

    fun swipe(item: MediaItem, keep: Boolean) {
        if (deck.firstOrNull()?.id != item.id) return
        deck.removeAt(0)
        history.addLast(SwipeRecord(item, keep))
        if (history.size > MAX_UNDO) history.removeFirst()
        canUndo = true
        lastUndone = null
        swipeCount++
        if (keep) {
            viewModelScope.launch {
                prefs.addKept(item.id)
                prefs.recordReview(1, today())
            }
            reaction = Reaction(Mood.LOVE, Lines.keepReaction(swipeCount), swipeCount)
        } else {
            pending.add(item)
            reaction = Reaction(Mood.EXCITED, Lines.deleteReaction(swipeCount), swipeCount)
        }
        if (deck.size < PREFETCH_AT) loadMore()
    }

    fun undo() {
        val record = history.removeLastOrNull() ?: return
        canUndo = history.isNotEmpty()
        if (record.keep) {
            viewModelScope.launch {
                prefs.removeKept(record.item.id)
                prefs.recordReview(-1, today())
            }
        } else {
            pending.removeAll { it.id == record.item.id }
        }
        deck.add(0, record.item)
        lastUndone = record
        deckExhausted = false
    }

    /** Un-marks an item from the bin; it counts as kept. */
    fun restore(item: MediaItem) {
        pending.removeAll { it.id == item.id }
        history.removeAll { it.item.id == item.id }
        canUndo = history.isNotEmpty()
        viewModelScope.launch {
            prefs.addKept(item.id)
            prefs.recordReview(1, today())
        }
    }

    fun deleteRequest(): IntentSender? {
        if (pending.isEmpty()) return null
        val permanent = settings.value?.permanentDelete == true
        return runCatching { media.deleteRequest(pending.toList(), permanent) }.getOrNull()
    }

    /** Called once the system confirmed the trash/delete request. */
    fun onDeleted() {
        val items = pending.toList()
        if (items.isEmpty()) return
        pending.clear()
        history.clear()
        canUndo = false
        lastResult = null
        viewModelScope.launch {
            val today = today()
            val bytes = items.sumOf { it.sizeBytes }
            val (before, after) = prefs.recordDeletion(items.size, bytes, today)
            val goal = settings.value?.dailyGoal ?: StreakRules.DEFAULT_GOAL
            lastResult = CleanResult(
                count = items.size,
                bytes = bytes,
                streak = StreakRules.currentStreak(after, today),
                streakGrew = !StreakRules.securedToday(before, today) && StreakRules.securedToday(after, today),
                goalReached = StreakRules.deletedToday(before, today) < goal &&
                    StreakRules.deletedToday(after, today) >= goal,
            )
            refreshWidget()
            gallery = runCatching { media.summary() }.getOrNull()
        }
    }

    fun completeOnboarding(hour: Int, minute: Int) {
        viewModelScope.launch {
            prefs.setReminder(hour, minute)
            prefs.setOnboarded()
            ReminderScheduler.schedule(getApplication(), hour, minute, replace = true)
            refreshWidget()
        }
    }

    fun setReminder(hour: Int, minute: Int) {
        viewModelScope.launch {
            prefs.setReminder(hour, minute)
            ReminderScheduler.schedule(getApplication(), hour, minute, replace = true)
        }
    }

    fun setDailyGoal(goal: Int) {
        viewModelScope.launch {
            prefs.setDailyGoal(goal.coerceIn(StreakRules.STREAK_MIN, MAX_GOAL))
            refreshWidget()
        }
    }

    fun setPermanentDelete(enabled: Boolean) {
        viewModelScope.launch { prefs.setPermanentDelete(enabled) }
    }

    fun resetKept() {
        viewModelScope.launch {
            prefs.clearKept()
            seen.clear()
        }
    }

    fun previewReminder() {
        val today = today()
        val p = progress.value
        val goal = settings.value?.dailyGoal ?: StreakRules.DEFAULT_GOAL
        val mood = MoodRules.moodFor(p, today, LocalTime.now().hour, goal)
        Notifications.showReminder(getApplication(), mood, StreakRules.currentStreak(p, today), System.nanoTime())
    }

    private suspend fun refreshWidget() {
        runCatching { RooWidget.refresh(getApplication()) }
    }

    companion object {
        const val MAX_GOAL = 50
        private const val BATCH_SIZE = 30
        private const val PREFETCH_AT = 6
        private const val MAX_UNDO = 30
    }
}
