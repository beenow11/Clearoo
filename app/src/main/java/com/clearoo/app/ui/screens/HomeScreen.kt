package com.clearoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clearoo.app.domain.Deck
import com.clearoo.app.domain.MoodRules
import com.clearoo.app.domain.Lines
import com.clearoo.app.domain.StreakRules
import com.clearoo.app.mascot.Mascot
import com.clearoo.app.ui.ClearooViewModel
import com.clearoo.app.data.GallerySummary
import com.clearoo.app.ui.components.GradientButton
import com.clearoo.app.ui.components.Pill
import com.clearoo.app.ui.components.pressable
import com.clearoo.app.ui.components.ProgressRing
import com.clearoo.app.ui.components.SpeechBubble
import com.clearoo.app.ui.components.StatTile
import com.clearoo.app.ui.components.gradientTint
import com.clearoo.app.ui.theme.BrandBrush
import com.clearoo.app.ui.theme.Flame
import com.clearoo.app.ui.theme.Outline
import com.clearoo.app.ui.theme.Surface1
import com.clearoo.app.ui.theme.Surface2
import com.clearoo.app.ui.theme.TextHi
import com.clearoo.app.ui.theme.TextLo
import com.clearoo.app.ui.theme.Violet
import androidx.compose.ui.graphics.graphicsLayer
import com.clearoo.app.util.Fmt
import com.clearoo.app.util.Perms
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.time.format.TextStyle as DayStyle

@Composable
fun HomeScreen(vm: ClearooViewModel, onStart: (Deck) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val progress by vm.progress.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val goal = settings?.dailyGoal ?: StreakRules.DEFAULT_GOAL
    val today = LocalDate.now().toEpochDay()
    val mood = MoodRules.moodFor(progress, today, LocalTime.now().hour, goal)
    val streak = StreakRules.currentStreak(progress, today)
    val deleted = StreakRules.deletedToday(progress, today)
    val line = remember(mood, streak, deleted, goal) { Lines.home(mood, streak, deleted, goal, today) }

    LaunchedEffect(Unit) { if (Perms.hasMedia(context)) vm.refreshGallery() }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Clearoo", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.gradientTint(BrandBrush))
            Spacer(Modifier.weight(1f))
            StreakBadge(streak)
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextLo)
            }
        }

        Spacer(Modifier.height(12.dp))
        SpeechBubble(line, Modifier.padding(horizontal = 12.dp))
        Mascot(mood, size = 190.dp)
        Spacer(Modifier.height(8.dp))
        WeekStrip(progress.goalDays, today)
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Surface1)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(deleted.toFloat() / goal, size = 104.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$deleted", style = MaterialTheme.typography.headlineMedium, color = TextHi)
                    Text("of $goal", style = MaterialTheme.typography.bodySmall, color = TextLo)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Today's clean-up", style = MaterialTheme.typography.titleLarge, color = TextHi)
                Text(
                    when {
                        deleted >= goal -> "Goal smashed! 🏆"
                        deleted >= StreakRules.STREAK_MIN -> "Streak secured 🔥 ${goal - deleted} to the goal"
                        else -> "${StreakRules.STREAK_MIN - deleted} more deletes keep your streak"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLo,
                )
                Text(
                    "${Fmt.bytes(StreakRules.freedToday(progress, today))} freed today",
                    style = MaterialTheme.typography.bodySmall,
                    color = Flame,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        GradientButton(if (deleted >= goal) "Keep swiping 🔥" else "Start swiping", { onStart(Deck.RANDOM) })
        Spacer(Modifier.height(24.dp))
        SmartDecks(vm.deckSummaries, onStart)
        Spacer(Modifier.height(24.dp))

        val (freed, unit) = Fmt.bytesParts(progress.totalFreed)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("💾", freed, "$unit freed", Modifier.weight(1f))
            StatTile("🗑️", "${progress.totalDeleted}", "deleted", Modifier.weight(1f))
            StatTile("🏆", "${progress.bestStreak}", "best streak", Modifier.weight(1f))
        }

        vm.gallery?.let {
            Spacer(Modifier.height(14.dp))
            Text(
                "Your gallery: ${String.format(Locale.getDefault(), "%,d", it.count)} items · ${Fmt.bytes(it.bytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextLo,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StreakBadge(streak: Int) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (streak > 0) Flame.copy(alpha = 0.18f) else Surface2)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (streak > 0) "🔥" else "🩶", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(4.dp))
        Text("$streak", style = MaterialTheme.typography.titleMedium, color = if (streak > 0) Flame else TextLo)
    }
}

/** Duolingo-style last-7-days row: a flame for every day the streak was kept. */
@Composable
private fun WeekStrip(goalDays: Set<Long>, today: Long) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (offset in 6 downTo 0) {
            val day = today - offset
            val met = day in goalDays
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (offset == 0) TextHi else TextLo,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (met) Flame.copy(alpha = 0.22f) else Surface1)
                        .then(if (offset == 0) Modifier.border(2.dp, Outline, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (met) Text("🔥", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** Two-column grid of smart decks. Roo recommends the one that frees the most space. */
@Composable
private fun SmartDecks(summaries: Map<Deck, GallerySummary>, onStart: (Deck) -> Unit) {
    val decks = Deck.entries.filter { it != Deck.RANDOM }
    val rooPick = summaries
        .filterKeys { it != Deck.RANDOM && it != Deck.BIGGEST }
        .filterValues { it.count > 0 }
        .maxByOrNull { it.value.bytes }?.key
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Smart decks", style = MaterialTheme.typography.titleLarge, color = TextHi)
        decks.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { deck ->
                    DeckTile(deck, summaries[deck], summaries.isNotEmpty(), deck == rooPick, Modifier.weight(1f)) {
                        onStart(deck)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DeckTile(
    deck: Deck,
    summary: GallerySummary?,
    loaded: Boolean,
    recommended: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val empty = summary != null && summary.count == 0
    val subtitle = when {
        deck == Deck.BLURRY -> "Roo scans for you"
        !loaded -> "…"
        summary == null || empty -> "All clear 🎉"
        else -> "${String.format(Locale.getDefault(), "%,d", summary.count)} · ${Fmt.bytes(summary.bytes)}"
    }
    Column(
        modifier
            .pressable(enabled = !empty, onClick = onClick)
            .graphicsLayer { alpha = if (empty) 0.5f else 1f }
            .clip(RoundedCornerShape(22.dp))
            .background(Surface1)
            .then(if (recommended) Modifier.border(2.dp, Violet, RoundedCornerShape(22.dp)) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(deck.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            if (recommended) Pill("Roo's pick", Violet)
        }
        Spacer(Modifier.height(4.dp))
        Text(deck.title, style = MaterialTheme.typography.titleMedium, color = TextHi, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextLo, maxLines = 1)
    }
}
