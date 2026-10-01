package com.clearoo.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clearoo.app.domain.Deck
import com.clearoo.app.domain.Mood
import com.clearoo.app.domain.MoodRules
import com.clearoo.app.domain.StreakRules
import com.clearoo.app.mascot.Mascot
import com.clearoo.app.ui.ClearooViewModel
import com.clearoo.app.ui.components.GhostButton
import com.clearoo.app.ui.components.GradientButton
import com.clearoo.app.ui.components.Pill
import com.clearoo.app.ui.components.RoundAction
import com.clearoo.app.ui.components.SpeechBubble
import com.clearoo.app.ui.components.pressable
import com.clearoo.app.ui.theme.BrandBrush
import com.clearoo.app.ui.theme.DeleteBrush
import com.clearoo.app.ui.theme.DeleteRed
import com.clearoo.app.ui.theme.KeepBrush
import com.clearoo.app.ui.theme.KeepGreen
import com.clearoo.app.ui.theme.Surface2
import com.clearoo.app.ui.theme.TextHi
import com.clearoo.app.ui.theme.TextLo
import com.clearoo.app.ui.theme.Violet
import com.clearoo.app.util.Perms
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min

@Composable
fun SwipeScreen(vm: ClearooViewModel, onBack: () -> Unit, onOpenBin: () -> Unit) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(Perms.hasMedia(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasAccess = Perms.hasMedia(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = Perms.hasMedia(context) }
    LaunchedEffect(hasAccess) { if (hasAccess) vm.ensureDeck() }

    val progress by vm.progress.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val goal = settings?.dailyGoal ?: StreakRules.DEFAULT_GOAL
    val today = LocalDate.now().toEpochDay()
    val confirmed = StreakRules.deletedToday(progress, today)
    val count = confirmed + vm.pending.size

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var deckWidth by remember { mutableFloatStateOf(0f) }
    var muted by remember { mutableStateOf(true) }

    val top = vm.deck.firstOrNull()
    val undone = vm.lastUndone
    val topState = remember(top?.id) {
        val enterFrom = if (undone != null && undone.item.id == top?.id) {
            if (undone.keep) deckWidth * 1.6f else -deckWidth * 1.6f
        } else 0f
        CardSwipeState(enterFrom)
    }
    LaunchedEffect(topState) { if (topState.offsetX.value != 0f) topState.settle() }

    fun swipeTop(keep: Boolean) {
        val item = top ?: return
        if (topState.flying || deckWidth == 0f) return
        scope.launch {
            topState.flyOut(keep, deckWidth)
            vm.swipe(item, keep)
        }
    }

    // Roo reacts live: shocked while you drag toward delete, heart eyes toward keep.
    val dragMood by remember(topState, deckWidth) {
        derivedStateOf {
            val p = topState.progress(deckWidth)
            when {
                p < -0.5f -> Mood.SHOCKED
                p > 0.5f -> Mood.LOVE
                else -> null
            }
        }
    }
    var reactionVisible by remember { mutableStateOf(false) }
    val reaction = vm.reaction
    LaunchedEffect(reaction?.id) {
        if (reaction == null) return@LaunchedEffect
        reactionVisible = true
        delay(1300)
        reactionVisible = false
    }
    val baseMood = MoodRules.sessionMood(count, goal)
    val mood = dragMood ?: if (reactionVisible && reaction != null) reaction.mood else baseMood
    val bubble = when {
        dragMood == Mood.SHOCKED -> "Delete it? 😱"
        dragMood == Mood.LOVE -> "Keep it? 💖"
        reactionVisible && reaction != null -> reaction.text
        count >= goal -> "Goal reached! 🏆"
        count >= StreakRules.STREAK_MIN -> "Streak saved! Keep going 🔥"
        else -> "${StreakRules.STREAK_MIN - count} more to keep your streak"
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextHi)
            }
            GoalBar(vm.activeDeck, count, goal, Modifier.weight(1f).padding(horizontal = 12.dp))
            BinButton(vm.pending.size, onOpenBin)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mascot(mood, size = 72.dp)
            Spacer(Modifier.width(4.dp))
            SpeechBubbleLeft(bubble)
        }

        AnimatedVisibility(
            visible = vm.pending.isNotEmpty() && count >= goal,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
        ) {
            Pill(
                "🎯 Goal reached — empty the bin to lock it in",
                Violet,
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp).pressable(onClick = onOpenBin),
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .onSizeChanged { deckWidth = it.width.toFloat() },
            contentAlignment = Alignment.Center,
        ) {
            when {
                !hasAccess -> NoAccess { permissionLauncher.launch(Perms.media) }
                top == null && vm.deckLoading -> DeckMessage(
                    Mood.HOPEFUL,
                    if (vm.activeDeck == Deck.BLURRY) "Squinting at your photos… 🔍" else "Finding photos for you…",
                )
                top == null -> DeckMessage(Mood.PROUD, doneLine(vm.activeDeck)) {
                    GhostButton("Pick another deck", onBack)
                }
                else -> {
                    val visible = vm.deck.take(3)
                    for (i in visible.indices.reversed()) {
                        val item = visible[i]
                        key(item.id) {
                            val cardModifier = if (i == 0) {
                                Modifier
                                    .swipeGestures(topState, deckWidth, scope, haptic) { keep -> vm.swipe(item, keep) }
                                    .graphicsLayer {
                                        translationX = topState.offsetX.value
                                        translationY = topState.offsetY.value
                                        rotationZ = if (deckWidth > 0f) topState.offsetX.value / deckWidth * 18f else 0f
                                        transformOrigin = TransformOrigin(0.5f, 1.1f)
                                    }
                            } else {
                                Modifier.graphicsLayer {
                                    val lift = if (deckWidth > 0f) min(1f, abs(topState.offsetX.value) / (deckWidth * 0.4f)) else 0f
                                    val depth = i - lift
                                    scaleX = 1f - 0.05f * depth
                                    scaleY = 1f - 0.05f * depth
                                    translationY = 16.dp.toPx() * depth
                                }
                            }
                            MediaCard(
                                item = item,
                                isTop = i == 0,
                                muted = muted,
                                onToggleMute = { muted = !muted },
                                progress = { if (i == 0) topState.progress(deckWidth) else 0f },
                                modifier = Modifier.fillMaxSize().then(cardModifier),
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundAction(72.dp, DeleteBrush, onClick = { swipeTop(keep = false) }, enabled = top != null) {
                Icon(Icons.Filled.Close, contentDescription = "Delete", tint = DeleteRed, modifier = Modifier.size(36.dp))
            }
            RoundAction(54.dp, BrandBrush, onClick = vm::undo, enabled = vm.canUndo) {
                Icon(Icons.Filled.Refresh, contentDescription = "Undo", tint = TextHi, modifier = Modifier.size(26.dp))
            }
            RoundAction(72.dp, KeepBrush, onClick = { swipeTop(keep = true) }, enabled = top != null) {
                Icon(Icons.Filled.Favorite, contentDescription = "Keep", tint = KeepGreen, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun GoalBar(deck: Deck, count: Int, goal: Int, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        (count.toFloat() / goal).coerceIn(0f, 1f),
        spring(dampingRatio = 0.7f, stiffness = 200f),
        label = "goal",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${deck.emoji} ${deck.title} · $count/$goal",
            style = MaterialTheme.typography.labelMedium,
            color = TextLo,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Surface2),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(BrandBrush),
            )
        }
    }
}

@Composable
private fun BinButton(count: Int, onClick: () -> Unit) {
    val bump = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (count > 0) {
            bump.snapTo(1.25f)
            bump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
    }
    Row(
        Modifier
            .graphicsLayer {
                scaleX = bump.value
                scaleY = bump.value
            }
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (count > 0) DeleteRed else Surface2)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🗑️ $count", style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
private fun SpeechBubbleLeft(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF2A1B3D))
    }
}

@Composable
private fun DeckMessage(mood: Mood, text: String, action: @Composable () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Mascot(mood, size = 160.dp)
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, color = TextHi, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        action()
    }
}

private fun doneLine(deck: Deck): String = when (deck) {
    Deck.RANDOM, Deck.BIGGEST -> "All caught up! 🎉\nYou've reviewed everything for now."
    Deck.BLURRY -> "No blurry shots found 🔍\nYour photos look sharp!"
    else -> "No more ${deck.title.lowercase()}! 🎉\nTry another deck."
}

@Composable
private fun NoAccess(onRequest: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Surface2)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpeechBubble("I can't see your gallery yet 🙈")
        Mascot(Mood.WORRIED, size = 140.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Clearoo needs access to your photos and videos to show them here. Nothing leaves your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextLo,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        GradientButton("Allow access", onRequest)
        Spacer(Modifier.height(8.dp))
        Text(
            "Already said no? Enable it in Settings › Apps › Clearoo › Permissions.",
            style = MaterialTheme.typography.bodySmall,
            color = TextLo,
            textAlign = TextAlign.Center,
        )
    }
}
