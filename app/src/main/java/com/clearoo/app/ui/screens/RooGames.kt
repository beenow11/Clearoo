package com.clearoo.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clearoo.app.domain.ClimbRules
import com.clearoo.app.domain.HopGame
import com.clearoo.app.domain.HopState
import com.clearoo.app.domain.Mood
import com.clearoo.app.mascot.Mascot
import com.clearoo.app.ui.ClearooViewModel
import com.clearoo.app.ui.components.GhostButton
import com.clearoo.app.ui.components.GradientButton
import com.clearoo.app.ui.components.Pill
import com.clearoo.app.ui.components.SpeechBubble
import com.clearoo.app.ui.theme.Coral
import com.clearoo.app.ui.theme.Flame
import com.clearoo.app.ui.theme.Gold
import com.clearoo.app.ui.theme.Surface1
import com.clearoo.app.ui.theme.Surface2
import com.clearoo.app.ui.theme.TextHi
import com.clearoo.app.ui.theme.TextLo
import com.clearoo.app.ui.theme.Violet
import kotlin.math.sin
import kotlin.random.Random

/** Hops per dot on the climb path. */
private const val HOPS_PER_DOT = 5
private val DOT_GAP = 64.dp

/** Background for each world, bottom to top. */
private val WORLD_TINT = listOf(Color(0xFF1C3326), Color(0xFF3A2C1A), Color(0xFF1F2E46), Color(0xFF15112C))

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextHi)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = TextHi)
    }
}

/** Roo's Climb: a path up through four worlds, one hop for every photo deleted. */
@Composable
fun ClimbScreen(vm: ClearooViewModel, onBack: () -> Unit, onPlay: () -> Unit) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    vm.game.collectAsStateWithLifecycle().value // re-read runs when a run is used
    val hops = progress.totalDeleted
    val world = ClimbRules.worldFor(hops)
    val next = ClimbRules.nextMilestone(hops)
    val runs = vm.runsLeft()

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopBar("Roo's Climb", onBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill("${world.emoji} ${world.name}", Surface2)
            Text(
                "$hops hops · next flag ${ClimbRules.reward(next)} at $next",
                style = MaterialTheme.typography.bodyMedium,
                color = TextLo,
            )
        }
        Spacer(Modifier.height(8.dp))
        ClimbMap(hops, Modifier.weight(1f).fillMaxWidth())
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            GradientButton(
                if (runs > 0) "🎮 Play Roo Hop · $runs ${if (runs == 1) "run" else "runs"} left" else "🎮 Roo Hop: no runs left today",
                onPlay,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Every photo you delete is one hop up. Secure today's streak for 3 more Roo Hop runs.",
                style = MaterialTheme.typography.bodySmall,
                color = TextLo,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ClimbMap(hops: Int, modifier: Modifier) {
    // Show the path to just past the next world (or 100 hops above Roo in the last world).
    val top = (ClimbRules.nextWorld(hops)?.startHop ?: (hops + 100)).let { (it / ClimbRules.MILESTONE_EVERY + 1) * ClimbRules.MILESTONE_EVERY }
    val dots = top / HOPS_PER_DOT + 1
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val mapHeight = DOT_GAP * (dots + 1)

    BoxWithConstraints(modifier) {
        val width = maxWidth
        val viewport = maxHeight
        fun yOf(i: Float) = mapHeight - DOT_GAP * (i + 0.5f)
        fun xOf(i: Float) = width / 2 + width * 0.28f * sin(i * 0.55f)
        val rooAt = hops.toFloat() / HOPS_PER_DOT

        LaunchedEffect(hops, viewport) {
            val target = with(density) { (yOf(rooAt) - viewport / 2).toPx() }.toInt()
            scroll.scrollTo(target.coerceIn(0, scroll.maxValue.takeIf { it > 0 } ?: target.coerceAtLeast(0)))
        }

        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(mapHeight)) {
                Canvas(Modifier.fillMaxSize()) {
                    val px = { d: androidx.compose.ui.unit.Dp -> d.toPx() }
                    // World bands.
                    ClimbRules.WORLDS.forEachIndexed { w, info ->
                        val endHop = ClimbRules.WORLDS.getOrNull(w + 1)?.startHop ?: Int.MAX_VALUE
                        val from = px(yOf(info.startHop.toFloat() / HOPS_PER_DOT - 0.5f)).coerceAtMost(size.height)
                        val to = if (endHop == Int.MAX_VALUE) 0f else px(yOf(endHop.toFloat() / HOPS_PER_DOT - 0.5f)).coerceAtLeast(0f)
                        if (from > to) drawRect(WORLD_TINT[w], Offset(0f, to), Size(size.width, from - to))
                    }
                    // The path, lit up to where Roo is.
                    val all = Path()
                    val done = Path()
                    for (i in 0 until dots) {
                        val p = Offset(px(xOf(i.toFloat())), px(yOf(i.toFloat())))
                        if (i == 0) all.moveTo(p.x, p.y) else all.lineTo(p.x, p.y)
                        if (i * HOPS_PER_DOT <= hops) {
                            if (i == 0) done.moveTo(p.x, p.y) else done.lineTo(p.x, p.y)
                        }
                    }
                    drawPath(all, Color.White.copy(alpha = 0.14f), style = Stroke(px(10.dp), cap = StrokeCap.Round))
                    drawPath(done, Brush.verticalGradient(listOf(Coral, Flame)), style = Stroke(px(10.dp), cap = StrokeCap.Round))
                    for (i in 0 until dots) {
                        if (i > 0 && (i * HOPS_PER_DOT) % ClimbRules.MILESTONE_EVERY == 0) continue
                        val reached = i * HOPS_PER_DOT <= hops
                        drawCircle(
                            if (reached) Flame else Color.White.copy(alpha = 0.25f),
                            radius = px(7.dp),
                            center = Offset(px(xOf(i.toFloat())), px(yOf(i.toFloat()))),
                        )
                    }
                }
                // Flags every 25 hops, with their sticker.
                for (m in ClimbRules.MILESTONE_EVERY..top step ClimbRules.MILESTONE_EVERY) {
                    val i = (m / HOPS_PER_DOT).toFloat()
                    val reached = hops >= m
                    Box(
                        Modifier
                            .offset(xOf(i) - 22.dp, yOf(i) - 22.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (reached) Surface1 else Surface2)
                            .border(3.dp, if (reached) Gold else Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (reached) ClimbRules.reward(m) else "🚩", style = MaterialTheme.typography.titleMedium)
                    }
                }
                // World names at the foot of each band.
                ClimbRules.WORLDS.filter { it.startHop <= top }.forEach { w ->
                    val i = w.startHop.toFloat() / HOPS_PER_DOT
                    Pill(
                        "${w.emoji} ${w.name}",
                        Color(0x66000000),
                        Modifier.offset(12.dp, yOf(i) - 8.dp),
                    )
                }
                Mascot(
                    Mood.HAPPY,
                    Modifier.offset(xOf(rooAt) - 40.dp, yOf(rooAt) - 74.dp),
                    size = 80.dp,
                )
            }
        }
    }
}

private val JUNK = listOf("🗑️", "📸", "😵", "🧾")

/** Roo Hop: tap to jump over junk files and catch stars. */
@Composable
fun RooHopScreen(vm: ClearooViewModel, onBack: () -> Unit, onClean: () -> Unit) {
    val stats by vm.game.collectAsStateWithLifecycle()
    var state by remember { mutableStateOf(HopState()) }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var bestBefore by remember { mutableIntStateOf(0) }
    val rng = remember { Random(System.nanoTime()) }

    fun start() {
        if (!vm.startRun()) return
        bestBefore = stats.best
        state = HopState()
        finished = false
        running = true
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (running) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1e9f
                last = now
                state = HopGame.step(state, dt, rng)
            }
            if (state.over) {
                running = false
                finished = true
                vm.finishRun(state.score)
            }
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopBar("Roo Hop", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Score ${state.score}", style = MaterialTheme.typography.titleLarge, color = TextHi)
            Spacer(Modifier.weight(1f))
            Text("⭐ ${state.stars}   🏆 ${maxOf(stats.best, if (finished) state.score else 0)}", style = MaterialTheme.typography.titleMedium, color = TextLo)
        }
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A2150), Color(0xFF3B2A5C), Color(0xFF5A3A6E))))
                .pointerInput(running) {
                    detectTapGestures { if (running) state = HopGame.jump(state) }
                },
        ) {
            val scale = maxWidth / HopGame.WIDTH
            val ground = maxHeight * 0.72f
            Canvas(Modifier.fillMaxSize()) {
                val s = scale.toPx()
                val g = ground.toPx()
                // Hills scroll slower than the track for a little depth.
                val shift = (state.distance * s * 0.3f) % size.width
                for (k in -1..2) {
                    drawCircle(Color(0x332EE59D), radius = size.width * 0.35f, center = Offset(k * size.width * 0.7f - shift, g + size.width * 0.22f))
                }
                drawRect(Color(0xFF241838), Offset(0f, g), Size(size.width, size.height - g))
                drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, g), Offset(size.width, g), strokeWidth = 3.dp.toPx())
                for (th in state.things) {
                    val left = th.x * s
                    val topY = g - (th.y + th.size) * s
                    val side = th.size * s
                    if (!th.isStar) {
                        drawRoundRect(Color(0x55FF4D6D), Offset(left, topY), Size(side, side), CornerRadius(side * 0.25f))
                    }
                    val emoji = if (th.isStar) "⭐" else JUNK[th.kind % JUNK.size]
                    val text = measurer.measure(emoji, TextStyle(fontSize = (side * 0.7f / density / fontScale).sp))
                    drawText(text, topLeft = Offset(left + (side - text.size.width) / 2, topY + (side - text.size.height) / 2))
                }
            }
            val roo = HopGame.ROO_SIZE * 1.4f
            Mascot(
                if (state.over) Mood.SHOCKED else Mood.EXCITED,
                Modifier.offset(scale * HopGame.ROO_X - scale * (roo - HopGame.ROO_SIZE) / 2, ground - scale * (state.rooY + roo)),
                size = scale * roo,
            )

            if (!running) {
                Overlay(
                    vm = vm,
                    finished = finished,
                    score = state.score,
                    newBest = finished && state.score > bestBefore,
                    onStart = ::start,
                    onBack = onBack,
                    onClean = onClean,
                )
            }
        }
    }
}

@Composable
private fun Overlay(
    vm: ClearooViewModel,
    finished: Boolean,
    score: Int,
    newBest: Boolean,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onClean: () -> Unit,
) {
    vm.game.collectAsStateWithLifecycle().value // re-read runs after a run is used
    val runs = vm.runsLeft()
    Column(
        Modifier.fillMaxSize().background(Color(0xAA130F1E)).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (finished) {
            Text(if (newBest) "New best! 🏆" else "Ouch, junk! 😵", style = MaterialTheme.typography.headlineMedium, color = TextHi)
            Text("Score $score", style = MaterialTheme.typography.displaySmall, color = Gold)
        } else {
            SpeechBubble("Tap to jump over the junk files. Catch ⭐ for +10!")
            Mascot(Mood.EXCITED, size = 120.dp)
        }
        Spacer(Modifier.height(20.dp))
        if (runs > 0) {
            GradientButton(if (finished) "Play again · $runs left" else "Tap to start · $runs ${if (runs == 1) "run" else "runs"} left", onStart)
        } else {
            Text(
                "No runs left today. Delete 5 photos to secure your streak and Roo gets 3 more runs!",
                style = MaterialTheme.typography.bodyLarge,
                color = TextHi,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            GradientButton("Go clean 🧹", onClean)
        }
        Spacer(Modifier.height(10.dp))
        GhostButton("Back to the climb", onBack)
        if (!finished) {
            Spacer(Modifier.height(8.dp))
            Pill("Runs refill every day", Violet.copy(alpha = 0.4f))
        }
    }
}
