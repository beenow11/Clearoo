package com.clearoo.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.clearoo.app.data.MediaItem
import com.clearoo.app.domain.Album
import com.clearoo.app.domain.AlbumRules
import com.clearoo.app.domain.Deck
import com.clearoo.app.domain.Mood
import com.clearoo.app.mascot.Mascot
import com.clearoo.app.ui.ClearooViewModel
import com.clearoo.app.ui.components.GhostButton
import com.clearoo.app.ui.components.GradientButton
import com.clearoo.app.ui.components.Pill
import com.clearoo.app.ui.components.SpeechBubble
import com.clearoo.app.ui.components.pressable
import com.clearoo.app.ui.theme.KeepGreen
import com.clearoo.app.ui.theme.Outline
import com.clearoo.app.ui.theme.Surface1
import com.clearoo.app.ui.theme.Surface2
import com.clearoo.app.ui.theme.TextHi
import com.clearoo.app.ui.theme.TextLo
import com.clearoo.app.ui.theme.Violet
import com.clearoo.app.util.Fmt
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** How album decks are named on their tiles and in the swipe screen. */
fun Deck.albumTitle(): String = if (this == Deck.RANDOM) "Everything" else title

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextHi)
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextHi,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** All albums, plus the way to make a new one. */
@Composable
fun AlbumsScreen(vm: ClearooViewModel, onBack: () -> Unit, onNew: () -> Unit, onOpen: (Album) -> Unit) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshAlbums() }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopBar("Albums", onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SpeechBubble(
                        if (albums.isEmpty()) {
                            "Back from a trip? Pick its photos and I'll find the blurry ones and duplicates!"
                        } else {
                            "Which pile shall we tidy? 🗂️"
                        },
                    )
                    Mascot(Mood.HAPPY, size = 120.dp)
                    Spacer(Modifier.height(8.dp))
                    GradientButton("＋ New album", onNew)
                    Spacer(Modifier.height(8.dp))
                }
            }
            items(albums, key = { it.id }) { album ->
                val photos = vm.albumItems[album.id]
                AlbumRow(album, photos) { onOpen(album) }
            }
        }
    }
}

@Composable
private fun AlbumRow(album: Album, photos: List<MediaItem>?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(22.dp))
            .background(Surface1)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Surface2),
            contentAlignment = Alignment.Center,
        ) {
            val cover = photos?.firstOrNull { !it.isBroken }
            if (cover != null) {
                AsyncImage(cover.uri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("🗂️", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(album.name, style = MaterialTheme.typography.titleMedium, color = TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    photos == null -> "…"
                    photos.isEmpty() -> "All cleaned up 🎉"
                    else -> "${photos.size} photos · ${Fmt.bytes(photos.sumOf { it.sizeBytes })}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextLo,
            )
        }
    }
}

/** Only today and earlier: an album is for photos already taken. */
@OptIn(ExperimentalMaterial3Api::class)
private object PastDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.utcDate() <= LocalDate.now()
    override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
}

/** "4 – 12 Oct 2026", "28 Sep – 3 Oct 2026", or a prompt while the range is half picked. */
private fun rangeText(start: LocalDate?, end: LocalDate?): String {
    if (start == null) return "Pick the first day"
    val full = DateTimeFormatter.ofPattern("d MMM yyyy")
    if (end == null) return "${start.format(full)} – pick the last day"
    if (start == end) return start.format(full)
    val first = when {
        start.year != end.year -> full
        start.month != end.month -> DateTimeFormatter.ofPattern("d MMM")
        else -> DateTimeFormatter.ofPattern("d")
    }
    return "${start.format(first)} – ${end.format(full)}"
}

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
private fun LocalDate.localStart(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Name the album, choose its dates, then untick anything that doesn't belong. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAlbumScreen(vm: ClearooViewModel, onBack: () -> Unit, onCreated: (Album) -> Unit) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf("") }
    var startDay by rememberSaveable { mutableLongStateOf(LocalDate.now().minusDays(6).toEpochDay()) }
    var endDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var pickingDates by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf<List<MediaItem>?>(null) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var saving by remember { mutableStateOf(false) }
    val start = LocalDate.ofEpochDay(startDay)
    val end = LocalDate.ofEpochDay(endDay)

    LaunchedEffect(startDay, endDay) {
        photos = null
        val found = vm.itemsInRange(start.localStart(), end.plusDays(1).localStart() - 1)
        photos = found
        selected = found.map { it.id }.toSet()
    }

    if (pickingDates) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = start.utcMillis(),
            initialSelectedEndDateMillis = end.utcMillis(),
            selectableDates = PastDates,
        )
        DatePickerDialog(
            onDismissRequest = { pickingDates = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = state.selectedStartDateMillis
                        if (s != null) {
                            startDay = s.utcDate().toEpochDay()
                            endDay = (state.selectedEndDateMillis ?: s).utcDate().toEpochDay()
                        }
                        pickingDates = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDates = false }) { Text("Cancel") } },
        ) {
            // Material's own title and headline are laid out for a full screen and wrap in a dialog.
            DateRangePicker(
                state = state,
                modifier = Modifier.weight(1f),
                title = {
                    Text(
                        "Trip dates",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextLo,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    )
                },
                headline = {
                    Text(
                        rangeText(state.selectedStartDateMillis?.utcDate(), state.selectedEndDateMillis?.utcDate()),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextHi,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                    )
                },
                showModeToggle = false,
            )
        }
    }

    val cleanName = AlbumRules.cleanName(name)
    // imePadding keeps the Create button above the keyboard.
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        TopBar("New album", onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(AlbumRules.MAX_NAME) },
                        label = { Text("Album name") },
                        placeholder = { Text("e.g. London trip") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextHi,
                            unfocusedTextColor = TextHi,
                            focusedBorderColor = Violet,
                            unfocusedBorderColor = Outline,
                            focusedLabelColor = Violet,
                            unfocusedLabelColor = TextLo,
                            cursorColor = Violet,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable { pickingDates = true }
                            .clip(RoundedCornerShape(18.dp))
                            .background(Surface1)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("📅", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${start.format(fmt)} – ${end.format(fmt)}", style = MaterialTheme.typography.titleSmall, color = TextHi)
                            Text("Tap to change the dates", style = MaterialTheme.typography.bodySmall, color = TextLo)
                        }
                    }
                    val list = photos
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                list == null -> "Finding photos…"
                                list.isEmpty() -> "No photos on these dates"
                                else -> "${selected.size} of ${list.size} selected · tap to untick"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextLo,
                            modifier = Modifier.weight(1f),
                        )
                        if (!list.isNullOrEmpty()) {
                            val all = selected.size == list.size
                            Pill(
                                if (all) "Select none" else "Select all",
                                Surface2,
                                Modifier.pressable { selected = if (all) emptySet() else list.map { it.id }.toSet() },
                            )
                        }
                    }
                }
            }
            items(photos.orEmpty(), key = { it.id }) { item ->
                val on = item.id in selected
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface1)
                        .pressable { selected = if (on) selected - item.id else selected + item.id },
                ) {
                    if (item.isBroken) {
                        Text("🩹", modifier = Modifier.align(Alignment.Center))
                    } else {
                        AsyncImage(
                            item.uri,
                            item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (on) 1f else 0.35f },
                        )
                    }
                    if (item.isVideo) Pill("▶", Color(0x88000000), Modifier.align(Alignment.BottomStart).padding(4.dp))
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (on) KeepGreen else Color(0x66000000))
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            GradientButton(
                if (cleanName.isEmpty()) "Name your album first" else "Create album (${selected.size})",
                onClick = {
                    if (saving) return@GradientButton
                    saving = true
                    scope.launch {
                        val keep = photos.orEmpty().map { it.id }.filter { it in selected }
                        onCreated(vm.createAlbum(cleanName, LinkedHashSet(keep)))
                    }
                },
                enabled = cleanName.isNotEmpty() && selected.isNotEmpty() && !saving,
            )
        }
    }
}

/** One album: Roo's scan results, its decks, and the optional move into a gallery folder. */
@Composable
fun AlbumScreen(vm: ClearooViewModel, albumId: Long, onBack: () -> Unit, onStart: (Deck, Album) -> Unit) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val album = albums.firstOrNull { it.id == albumId }
    if (album == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    LaunchedEffect(albumId) { vm.openAlbum(album) }

    val photos = vm.albumItems[album.id]
    val present = photos.orEmpty().map { it.id }.toSet()
    val scan = vm.albumScan
    val progress = vm.scanProgress
    val blurry = scan?.blurry.orEmpty().count { it in present }
    val dupes = scan?.duplicates.orEmpty().sumOf { g -> g.count { it in present }.takeIf { it > 1 } ?: 0 }
    var confirmRemove by remember { mutableStateOf(false) }
    val folder = AlbumRules.folderPath(album.name).trimEnd('/')
    val moveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.moveAlbum(album)
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove “${album.name}”?") },
            text = { Text("Only the album goes. Its photos stay on your phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    vm.removeAlbum(album)
                    onBack()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopBar(album.name, onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mascot(if (progress != null) Mood.HOPEFUL else Mood.HAPPY, size = 84.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            when {
                                photos == null -> "…"
                                photos.isEmpty() -> "All cleaned up 🎉"
                                else -> "${photos.size} photos · ${Fmt.bytes(photos.sumOf { it.sizeBytes })}"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = TextHi,
                        )
                        Text(
                            when {
                                progress != null -> "Checking photos… ${progress.first}/${progress.second}"
                                blurry + dupes == 0 -> "No blurry shots or duplicates found ✨"
                                else -> "Found $blurry blurry and $dupes duplicates"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextLo,
                        )
                    }
                }
            }
            item {
                val scanning = progress != null || scan == null
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AlbumDeckTile(Deck.BLURRY, if (scanning) "Checking…" else "$blurry photos", !scanning && blurry > 0) {
                        onStart(Deck.BLURRY, album)
                    }
                    AlbumDeckTile(Deck.DUPLICATES, if (scanning) "Checking…" else "$dupes photos", !scanning && dupes > 0) {
                        onStart(Deck.DUPLICATES, album)
                    }
                    AlbumDeckTile(Deck.RANDOM, "${present.size} photos, in date order", present.isNotEmpty()) {
                        onStart(Deck.RANDOM, album)
                    }
                }
            }
            if (present.isNotEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Surface1)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("📁 Make it a gallery album", style = MaterialTheme.typography.titleMedium, color = TextHi)
                        // Only photos not already in the folder are moved, so the button never redoes work.
                        val toMove = vm.notInFolder(album).size
                        val inFolder = present.size - toMove
                        Text(
                            when {
                                toMove == 0 -> "✅ All ${present.size} photos are in $folder. Look for “${album.name}” in your gallery app."
                                inFolder > 0 -> "$inFolder photos are already in $folder. $toMove more can be moved there."
                                else -> "Optional. Moves these ${present.size} photos into $folder, so “${album.name}” shows up in your gallery app."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (toMove == 0) KeepGreen else TextLo,
                        )
                        vm.moveResult?.takeIf { it.failed > 0 }?.let { r ->
                            Text(
                                "${r.failed} couldn't be moved. They may belong to another app; try again later.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextLo,
                            )
                        }
                        if (toMove > 0) {
                            GhostButton(if (toMove == 1) "Move 1 photo" else "Move $toMove photos", {
                                vm.moveRequest(album)?.let { moveLauncher.launch(IntentSenderRequest.Builder(it).build()) }
                            })
                        }
                    }
                }
            }
            item {
                Text(
                    "Remove album",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextLo,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().pressable { confirmRemove = true }.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun AlbumDeckTile(deck: Deck, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .clip(RoundedCornerShape(22.dp))
            .background(Surface1)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (deck == Deck.RANDOM) "🃏" else deck.emoji, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(deck.albumTitle(), style = MaterialTheme.typography.titleMedium, color = TextHi)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextLo)
        }
        if (enabled) Text("Swipe ›", style = MaterialTheme.typography.labelLarge, color = Violet)
    }
}
