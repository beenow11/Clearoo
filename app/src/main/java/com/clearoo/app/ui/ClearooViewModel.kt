package com.clearoo.app.ui

import android.app.Application
import android.content.IntentSender
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clearoo.app.data.AlbumScan
import com.clearoo.app.data.AppSettings
import com.clearoo.app.data.GallerySummary
import com.clearoo.app.data.MediaItem
import com.clearoo.app.data.MediaRepository
import com.clearoo.app.data.MoveResult
import com.clearoo.app.data.PrefsRepository
import com.clearoo.app.domain.Album
import com.clearoo.app.domain.AlbumRules
import com.clearoo.app.domain.Deck
import com.clearoo.app.domain.Lines
import com.clearoo.app.domain.Mood
import com.clearoo.app.domain.MoodRules
import com.clearoo.app.domain.Outfit
import com.clearoo.app.domain.OutfitRules
import com.clearoo.app.domain.Progress
import com.clearoo.app.domain.StreakRules
import com.clearoo.app.notify.Notifications
import com.clearoo.app.util.Device
import com.clearoo.app.util.Perms
import com.clearoo.app.util.StorageInfo
import com.clearoo.app.notify.ReminderScheduler
import com.clearoo.app.widget.RooWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    val unlocked: List<Outfit> = emptyList(),
)

class ClearooViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = PrefsRepository(app)
    private val media = MediaRepository(app)

    val progress: StateFlow<Progress> =
        prefs.progress.stateIn(viewModelScope, SharingStarted.Eagerly, Progress())
    val settings: StateFlow<AppSettings?> =
        prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val albums: StateFlow<List<Album>> =
        prefs.albums.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
    var deckSummaries by mutableStateOf<Map<Deck, GallerySummary>>(emptyMap())
        private set
    /** Which smart deck the cards come from. */
    var activeDeck by mutableStateOf(Deck.RANDOM)
        private set
    /** The album the deck is limited to; null for the whole gallery. */
    var deckAlbum by mutableStateOf<Album?>(null)
        private set

    /** Photos still in each album, by album id. */
    var albumItems by mutableStateOf<Map<Long, List<MediaItem>>>(emptyMap())
        private set
    /** Scan of the album that is open: blurry photos and duplicates. */
    var albumScan by mutableStateOf<AlbumScan?>(null)
        private set
    /** (done, total) while an album is being scanned. */
    var scanProgress by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var moveResult by mutableStateOf<MoveResult?>(null)
        private set
    private var scanJob: Job? = null
    var lastResult by mutableStateOf<CleanResult?>(null)
        private set
    /** Free space on the phone; null until checked. */
    var storage by mutableStateOf<StorageInfo?>(Device.storage())
        private set
    /** Skip the trash for this bin, so the space comes back right away. Offered when the phone is full. */
    var freeSpaceNow by mutableStateOf(false)
    /** Videos only play when tapped, so old phones don't run out of memory. */
    val tapToPlay = Device.isLowMemory(app)

    private val history = ArrayDeque<SwipeRecord>()
    /** Swiped (kept or binned) this session, so reloads never repeat a card. */
    private val swiped = HashSet<Long>()
    private var swipeCount = 0L
    private var loadJob: Job? = null
    private var binRestored = false
    /** Bumped on every deck switch so a slow load for the old deck is dropped. */
    private var generation = 0

    private fun today() = LocalDate.now().toEpochDay()

    init {
        restoreBin()
    }

    /** Brings back the bin from the last session; retried once media permission exists. */
    private fun restoreBin() {
        if (binRestored) return
        viewModelScope.launch {
            val ids = prefs.binIds.first()
            if (ids.isEmpty()) {
                binRestored = true
                return@launch
            }
            // Without media permission nothing can be looked up yet; try again later.
            if (!Perms.hasMedia(getApplication())) return@launch
            val items = runCatching { media.itemsByIds(ids) }.getOrNull() ?: return@launch
            binRestored = true
            val known = pending.map { it.id }.toSet()
            pending.addAll(items.filter { it.id !in known })
            saveBin()
        }
    }

    private fun saveBin() {
        val ids = pending.map { it.id }.toSet()
        viewModelScope.launch {
            // Until the old bin is restored, keep its ids rather than overwrite them.
            prefs.setBin(if (binRestored) ids else ids + prefs.binIds.first())
        }
    }

    fun refreshGallery() {
        media.invalidate()
        restoreBin()
        storage = Device.storage()
        viewModelScope.launch {
            gallery = runCatching { media.summary() }.getOrNull()
            deckSummaries = runCatching { media.deckSummaries(System.currentTimeMillis()) }.getOrDefault(emptyMap())
        }
    }

    fun ensureDeck() {
        restoreBin()
        if (deck.isEmpty()) loadMore()
    }

    /** Switches to [mode], within [album] if given; the bin and undo history carry over. */
    fun startDeck(mode: Deck, album: Album? = null) {
        if (mode == activeDeck && album?.id == deckAlbum?.id && deck.isNotEmpty()) return
        loadJob?.cancel()
        generation++
        deckLoading = false
        activeDeck = mode
        deckAlbum = album
        deck.clear()
        lastUndone = null
        deckExhausted = false
        loadMore()
    }

    private fun loadMore() {
        if (deckLoading) return
        deckLoading = true
        val mode = activeDeck
        val album = deckAlbum
        val scan = albumScan
        val gen = generation
        loadJob = viewModelScope.launch {
            val shown = swiped + pending.map { it.id } + deck.map { it.id }
            val batch = runCatching {
                if (album != null) {
                    // An album shows all its photos again, even ones kept elsewhere before.
                    media.albumBatch(mode, album.ids, scan, shown, BATCH_SIZE)
                } else {
                    media.batch(mode, prefs.keptIds.first() + shown, BATCH_SIZE, System.currentTimeMillis())
                }
            }.getOrDefault(emptyList())
            if (gen != generation) return@launch
            deck.addAll(batch)
            deckExhausted = deck.isEmpty()
            deckLoading = false
        }
    }

    fun swipe(item: MediaItem, keep: Boolean) {
        if (deck.firstOrNull()?.id != item.id) return
        deck.removeAt(0)
        swiped += item.id
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
            saveBin()
            reaction = Reaction(Mood.EXCITED, Lines.deleteReaction(swipeCount), swipeCount)
        }
        if (deck.size < PREFETCH_AT) loadMore()
    }

    fun undo() {
        val record = history.removeLastOrNull() ?: return
        swiped -= record.item.id
        canUndo = history.isNotEmpty()
        if (record.keep) {
            viewModelScope.launch {
                prefs.removeKept(record.item.id)
                prefs.recordReview(-1, today())
            }
        } else {
            pending.removeAll { it.id == record.item.id }
            saveBin()
        }
        deck.add(0, record.item)
        lastUndone = record
        deckExhausted = false
    }

    /** A card's file couldn't be opened: show it as broken and list it in the Broken deck. */
    fun markBroken(item: MediaItem) {
        if (item.isBroken) return
        media.markBroken(item.id)
        val broken = item.copy(meta = item.meta.copy(isBroken = true))
        deck.indexOfFirst { it.id == item.id }.takeIf { it >= 0 }?.let { deck[it] = broken }
        pending.indexOfFirst { it.id == item.id }.takeIf { it >= 0 }?.let { pending[it] = broken }
    }

    /** Puts every card left in the deck in the bin; used for the Broken deck. */
    fun binAll() {
        val items = deck.toList()
        if (items.isEmpty()) return
        deck.clear()
        for (item in items) {
            swiped += item.id
            history.addLast(SwipeRecord(item, keep = false))
            if (history.size > MAX_UNDO) history.removeFirst()
        }
        pending.addAll(items.filter { item -> pending.none { it.id == item.id } })
        saveBin()
        canUndo = true
        lastUndone = null
        swipeCount += items.size
        reaction = Reaction(Mood.EXCITED, Lines.deleteReaction(swipeCount), swipeCount)
        loadMore()
    }

    /** Un-marks an item from the bin; it counts as kept. */
    fun restore(item: MediaItem) {
        pending.removeAll { it.id == item.id }
        saveBin()
        history.removeAll { it.item.id == item.id }
        canUndo = history.isNotEmpty()
        viewModelScope.launch {
            prefs.addKept(item.id)
            prefs.recordReview(1, today())
        }
    }

    fun deleteRequest(): IntentSender? {
        if (pending.isEmpty()) return null
        val permanent = settings.value?.permanentDelete == true || freeSpaceNow
        return runCatching { media.deleteRequest(pending.toList(), permanent) }.getOrNull()
    }

    /** Called once the system confirmed the trash/delete request. */
    fun onDeleted() {
        val items = pending.toList()
        if (items.isEmpty()) return
        pending.clear()
        saveBin()
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
                unlocked = OutfitRules.newlyUnlocked(before, after),
            )
            refreshWidget()
            media.invalidate()
            refreshGallery()
        }
    }

    /** Reloads every album's photos and forgets ones that were deleted. */
    fun refreshAlbums() {
        viewModelScope.launch {
            val list = prefs.albums.first()
            val present = runCatching { media.itemsByIds(list.flatMap { it.ids }.toSet()) }.getOrNull() ?: return@launch
            val byId = present.associateBy { it.id }
            albumItems = list.associate { a -> a.id to a.ids.mapNotNull { byId[it] }.sortedBy { it.takenAtMillis } }
            val pruned = list.map { a -> a.copy(ids = a.ids.filterTo(LinkedHashSet()) { it in byId }) }
            if (pruned != list) prefs.setAlbums(pruned)
        }
    }

    suspend fun itemsInRange(startMillis: Long, endMillis: Long): List<MediaItem> =
        runCatching { media.itemsInRange(startMillis, endMillis) }.getOrDefault(emptyList())

    /** Saves a new album and returns it. */
    suspend fun createAlbum(name: String, ids: Set<Long>): Album {
        val album = Album(System.currentTimeMillis(), AlbumRules.cleanName(name), ids)
        prefs.setAlbums(listOf(album) + albums.value)
        // Wait until the album list includes it, so the album screen can find it straight away.
        withTimeoutOrNull(2_000) { albums.first { list -> list.any { it.id == album.id } } }
        refreshAlbums()
        return album
    }

    /** Forgets the album; its photos stay where they are. */
    fun removeAlbum(album: Album) {
        viewModelScope.launch {
            prefs.setAlbums(albums.value.filter { it.id != album.id })
            refreshAlbums()
        }
    }

    /** Opens [album]: scans its photos for blur and duplicates. */
    fun openAlbum(album: Album) {
        scanJob?.cancel()
        albumScan = null
        moveResult = null
        scanProgress = 0 to album.ids.size
        refreshAlbums()
        scanJob = viewModelScope.launch {
            albumScan = runCatching {
                media.scanAlbum(album.ids) { done, total -> scanProgress = done to total }
            }.getOrNull()
            scanProgress = null
        }
    }

    fun albumById(id: Long): Album? = albums.value.firstOrNull { it.id == id }

    /** The album's photos that aren't in its folder yet. */
    fun notInFolder(album: Album): List<MediaItem> {
        val folder = AlbumRules.folderPath(album.name).trimEnd('/')
        return albumItems[album.id].orEmpty().filter { it.meta.relativePath?.trimEnd('/') != folder }
    }

    /** Asks Android for permission to move the album's photos. */
    fun moveRequest(album: Album): IntentSender? {
        val items = notInFolder(album)
        if (items.isEmpty()) return null
        return runCatching { media.writeRequest(items) }.getOrNull()
    }

    /** Moves the album's photos into a folder named after it, once Android allowed it. */
    fun moveAlbum(album: Album) {
        val items = notInFolder(album)
        viewModelScope.launch {
            moveResult = media.moveTo(items, AlbumRules.folderPath(album.name))
            refreshAlbums()
            refreshGallery()
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

    /** Only unlocked outfits can be worn. */
    fun wear(outfit: Outfit) {
        if (!OutfitRules.isUnlocked(outfit, progress.value)) return
        viewModelScope.launch {
            prefs.setOutfit(outfit)
            refreshWidget()
        }
    }

    fun setPermanentDelete(enabled: Boolean) {
        viewModelScope.launch { prefs.setPermanentDelete(enabled) }
    }

    fun resetKept() {
        viewModelScope.launch {
            prefs.clearKept()
            swiped.clear()
        }
    }

    fun previewReminder() {
        val today = today()
        val p = progress.value
        val goal = settings.value?.dailyGoal ?: StreakRules.DEFAULT_GOAL
        val mood = MoodRules.moodFor(p, today, LocalTime.now().hour, goal)
        val outfit = settings.value?.outfit ?: Outfit.CLASSIC
        Notifications.showReminder(getApplication(), mood, StreakRules.currentStreak(p, today), System.nanoTime(), outfit)
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
