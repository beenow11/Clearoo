package com.clearoo.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.clearoo.app.data.MediaItem
import com.clearoo.app.domain.ClimbRules
import com.clearoo.app.domain.Mood
import com.clearoo.app.domain.PouchGame
import com.clearoo.app.domain.PouchState
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
import com.clearoo.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
private fun TopBar(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextHi)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = TextHi, modifier = Modifier.weight(1f))
        trailing()
    }
}

// ---------------------------------------------------------------- Roo's Climb

/** Hops per dot on the climb path. */
private const val HOPS_PER_DOT = 5
private val DOT_GAP = 64.dp

/** Roo's Climb: a path up through four worlds, one hop for every photo deleted. */
@Composable
fun ClimbScreen(vm: ClearooViewModel, onBack: () -> Unit, onPlay: () -> Unit) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val hops = progress.totalDeleted
    val world = ClimbRules.worldFor(hops)
    val next = ClimbRules.nextMilestone(hops)

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopBar("Roo's Climb", onBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill("${world.emoji} ${world.name}", Surface2)
            Text("$hops hops · next flag ${ClimbRules.reward(next)} at $next", style = MaterialTheme.typography.bodyMedium, color = TextLo)
        }
        Spacer(Modifier.height(8.dp))
        ClimbMap(hops, Modifier.weight(1f).fillMaxWidth())
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            GradientButton("🎮 Play Roo's Pouch", onPlay)
            Spacer(Modifier.height(6.dp))
            Text(
                "Every photo you delete is one hop up the mountain.",
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
        fun xOf(i: Float) = width / 2 + width * 0.26f * sin(i * 0.55f)
        val rooAt = hops.toFloat() / HOPS_PER_DOT

        LaunchedEffect(hops, viewport) {
            val target = with(density) { (yOf(rooAt) - viewport / 2).toPx() }.toInt()
            scroll.scrollTo(target.coerceAtLeast(0))
        }

        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(mapHeight)) {
                Canvas(Modifier.fillMaxSize()) {
                    // Scenery for each world, then a soft blend where two worlds meet.
                    ClimbRules.WORLDS.forEachIndexed { w, info ->
                        val endHop = ClimbRules.WORLDS.getOrNull(w + 1)?.startHop
                        val bottom = yOf(info.startHop.toFloat() / HOPS_PER_DOT - 0.5f).toPx().coerceAtMost(size.height)
                        val upper = if (endHop == null) 0f else yOf(endHop.toFloat() / HOPS_PER_DOT - 0.5f).toPx().coerceAtLeast(0f)
                        if (bottom > upper) drawWorld(w, upper, bottom)
                    }
                    ClimbRules.WORLDS.drop(1).forEach { info ->
                        val y = yOf(info.startHop.toFloat() / HOPS_PER_DOT - 0.5f).toPx()
                        if (y in 0f..size.height) {
                            drawRect(
                                Brush.verticalGradient(listOf(Color.Transparent, Color(0x55000000), Color.Transparent), startY = y - 60.dp.toPx(), endY = y + 60.dp.toPx()),
                                Offset(0f, y - 60.dp.toPx()), Size(size.width, 120.dp.toPx()),
                            )
                        }
                    }
                    // A dirt trail, lit up to where Roo is, with a stepping stone every 5 hops.
                    val all = Path()
                    val done = Path()
                    for (i in 0 until dots) {
                        val p = Offset(xOf(i.toFloat()).toPx(), yOf(i.toFloat()).toPx())
                        if (i == 0) all.moveTo(p.x, p.y) else all.lineTo(p.x, p.y)
                        if (i * HOPS_PER_DOT <= hops) {
                            if (i == 0) done.moveTo(p.x, p.y) else done.lineTo(p.x, p.y)
                        }
                    }
                    drawPath(all, Color(0x55000000), style = Stroke(22.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(all, Color(0x33FFF3E0), style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(done, Brush.verticalGradient(listOf(Gold, Flame, Coral)), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    for (i in 0 until dots) {
                        if (i > 0 && (i * HOPS_PER_DOT) % ClimbRules.MILESTONE_EVERY == 0) continue
                        val c = Offset(xOf(i.toFloat()).toPx(), yOf(i.toFloat()).toPx())
                        val reached = i * HOPS_PER_DOT <= hops
                        drawOval(Color(0x66000000), Offset(c.x - 13.dp.toPx(), c.y - 6.dp.toPx()), Size(26.dp.toPx(), 16.dp.toPx()))
                        drawOval(if (reached) Color(0xFFFFD9A8) else Color(0xFFB9B0C9), Offset(c.x - 12.dp.toPx(), c.y - 8.dp.toPx()), Size(24.dp.toPx(), 14.dp.toPx()))
                    }
                }
                // Flags every 25 hops, with their sticker once reached.
                for (m in ClimbRules.MILESTONE_EVERY..top step ClimbRules.MILESTONE_EVERY) {
                    val i = (m / HOPS_PER_DOT).toFloat()
                    val reached = hops >= m
                    Box(
                        Modifier
                            .offset(xOf(i) - 24.dp, yOf(i) - 24.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (reached) Surface1 else Color(0xCC2A2140))
                            .border(3.dp, if (reached) Gold else Color.White.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (reached) ClimbRules.reward(m) else "🚩", style = MaterialTheme.typography.titleMedium)
                    }
                }
                ClimbRules.WORLDS.filter { it.startHop <= top }.forEach { w ->
                    val i = w.startHop.toFloat() / HOPS_PER_DOT
                    Pill("${w.emoji} ${w.name}", Color(0x99000000), Modifier.offset(12.dp, yOf(i) - 10.dp))
                }
                Mascot(Mood.HAPPY, Modifier.offset(xOf(rooAt) - 40.dp, yOf(rooAt) - 76.dp), size = 80.dp)
            }
        }
    }
}

/** Paints one world's scenery between [top] and [bottom]. Seeded, so it looks the same every time. */
private fun DrawScope.drawWorld(world: Int, top: Float, bottom: Float) {
    val w = size.width
    val h = bottom - top
    val rng = Random(world * 7919 + 17)
    val u = 1.dp.toPx()
    // Items stay near the sides so the trail in the middle stays clear.
    fun sideX(): Float = if (rng.nextBoolean()) rng.nextFloat() * w * 0.2f else w * 0.8f + rng.nextFloat() * w * 0.2f
    fun anyY(): Float = top + rng.nextFloat() * h
    val count = (h / (70 * u)).toInt().coerceAtLeast(4)
    when (world) {
        0 -> { // Meadow: sky to grass, clouds, hills, trees and flowers.
            drawRect(Brush.verticalGradient(listOf(Color(0xFF3E8FB8), Color(0xFF3C8D5A), Color(0xFF2E6B3F)), startY = top, endY = bottom), Offset(0f, top), Size(w, h))
            repeat(count / 3) {
                val c = Offset(rng.nextFloat() * w, anyY())
                drawOval(Color(0x55FFFFFF), Offset(c.x - 34 * u, c.y - 10 * u), Size(68 * u, 22 * u))
                drawOval(Color(0x55FFFFFF), Offset(c.x - 16 * u, c.y - 22 * u), Size(36 * u, 26 * u))
            }
            repeat(count / 4) {
                val c = Offset(rng.nextFloat() * w, anyY())
                drawOval(Color(0x3326A65B), Offset(c.x - 120 * u, c.y - 30 * u), Size(240 * u, 90 * u))
            }
            repeat(count) {
                val x = sideX()
                val y = anyY()
                if (rng.nextFloat() < 0.45f) {
                    drawRect(Color(0xFF6D4C2F), Offset(x - 3 * u, y), Size(6 * u, 22 * u))
                    drawCircle(Color(0xFF2F8F4E), 16 * u, Offset(x, y - 4 * u))
                    drawCircle(Color(0xFF38A55A), 12 * u, Offset(x - 9 * u, y + 2 * u))
                    drawCircle(Color(0xFF38A55A), 12 * u, Offset(x + 9 * u, y + 2 * u))
                } else {
                    val petal = listOf(Color(0xFFFFD84D), Color(0xFFFF8FA8), Color(0xFFFFFFFF), Color(0xFFB79CFF)).random(rng)
                    for (k in 0 until 5) {
                        val a = k * 1.2566f
                        drawCircle(petal, 4 * u, Offset(x + cos(a) * 5 * u, y + sin(a) * 5 * u))
                    }
                    drawCircle(Color(0xFFFF8A3D), 3 * u, Offset(x, y))
                }
            }
        }
        1 -> { // Desert: warm sand, dunes, pyramids, cacti and a big sun.
            drawRect(Brush.verticalGradient(listOf(Color(0xFFD9894A), Color(0xFFC0743A), Color(0xFF9A5A2C)), startY = top, endY = bottom), Offset(0f, top), Size(w, h))
            drawCircle(Color(0x66FFE08A), 60 * u, Offset(w * 0.82f, top + 90 * u))
            repeat(count / 3) {
                val c = Offset(rng.nextFloat() * w, anyY())
                drawOval(Color(0x33FFD9A8), Offset(c.x - 140 * u, c.y - 24 * u), Size(280 * u, 70 * u))
            }
            repeat(count / 4) {
                val x = sideX()
                val y = anyY()
                val p = Path().apply {
                    moveTo(x - 34 * u, y)
                    lineTo(x, y - 40 * u)
                    lineTo(x + 34 * u, y)
                    close()
                }
                drawPath(p, Color(0xFFE2B36B))
                drawPath(Path().apply { moveTo(x, y - 40 * u); lineTo(x + 34 * u, y); lineTo(x + 8 * u, y); close() }, Color(0x33000000))
            }
            repeat(count) {
                val x = sideX()
                val y = anyY()
                val g = Color(0xFF3E8E4E)
                drawRoundRect(g, Offset(x - 6 * u, y - 30 * u), Size(12 * u, 34 * u), CornerRadius(6 * u))
                drawRoundRect(g, Offset(x - 18 * u, y - 20 * u), Size(8 * u, 14 * u), CornerRadius(4 * u))
                drawRoundRect(g, Offset(x - 18 * u, y - 10 * u), Size(14 * u, 6 * u), CornerRadius(3 * u))
                drawRoundRect(g, Offset(x + 10 * u, y - 24 * u), Size(8 * u, 12 * u), CornerRadius(4 * u))
                drawRoundRect(g, Offset(x + 4 * u, y - 16 * u), Size(14 * u, 6 * u), CornerRadius(3 * u))
            }
        }
        2 -> { // Snow peaks: icy sky, snow-capped mountains, pines and falling snow.
            drawRect(Brush.verticalGradient(listOf(Color(0xFF5E7FB5), Color(0xFF7FA3CC), Color(0xFFB8D0E8)), startY = top, endY = bottom), Offset(0f, top), Size(w, h))
            repeat(count / 3) {
                val x = rng.nextFloat() * w
                val y = anyY()
                val s = (50 + rng.nextFloat() * 50) * u
                drawPath(Path().apply { moveTo(x - s, y); lineTo(x, y - s * 1.1f); lineTo(x + s, y); close() }, Color(0xFF6C7FA6))
                drawPath(Path().apply { moveTo(x - s * 0.32f, y - s * 0.75f); lineTo(x, y - s * 1.1f); lineTo(x + s * 0.32f, y - s * 0.75f); close() }, Color.White)
            }
            repeat(count) {
                val x = sideX()
                val y = anyY()
                for (k in 0 until 3) {
                    val wk = (16 - k * 4) * u
                    val yk = y - k * 10 * u
                    drawPath(Path().apply { moveTo(x - wk, yk); lineTo(x, yk - 16 * u); lineTo(x + wk, yk); close() }, Color(0xFF1F5A44))
                }
                drawPath(Path().apply { moveTo(x - 5 * u, y - 30 * u); lineTo(x, y - 36 * u); lineTo(x + 5 * u, y - 30 * u); close() }, Color.White)
            }
            repeat(count * 4) { drawCircle(Color(0xCCFFFFFF), (1.5f + rng.nextFloat() * 2) * u, Offset(rng.nextFloat() * w, anyY())) }
        }
        else -> { // Space: deep sky, stars, ringed planets and a moon.
            drawRect(Brush.verticalGradient(listOf(Color(0xFF07061A), Color(0xFF1A1340), Color(0xFF2B1D5C)), startY = top, endY = bottom), Offset(0f, top), Size(w, h))
            repeat(count * 8) {
                drawCircle(Color.White.copy(alpha = 0.3f + rng.nextFloat() * 0.6f), (0.8f + rng.nextFloat() * 1.6f) * u, Offset(rng.nextFloat() * w, anyY()))
            }
            repeat((count / 3).coerceAtLeast(2)) {
                val c = Offset(sideX(), anyY())
                val r = (14 + rng.nextFloat() * 18) * u
                val col = listOf(Color(0xFFFF8FA8), Color(0xFF8E7CFF), Color(0xFF4FC3F7), Color(0xFFFFB36B)).random(rng)
                drawCircle(col, r, c)
                drawCircle(Color(0x33000000), r, Offset(c.x + r * 0.35f, c.y + r * 0.2f))
                drawOval(Color(0xAAFFFFFF), Offset(c.x - r * 1.7f, c.y - r * 0.25f), Size(r * 3.4f, r * 0.5f), style = Stroke(2 * u))
            }
            val moon = Offset(w * 0.85f, top + 80 * u)
            drawCircle(Color(0xFFFFF3C4), 26 * u, moon)
            drawCircle(Color(0xFF07061A), 22 * u, Offset(moon.x + 12 * u, moon.y - 6 * u))
            repeat(count / 4) {
                val s = Offset(rng.nextFloat() * w, anyY())
                drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Color.White), startX = s.x, endX = s.x + 50 * u), s, Offset(s.x + 50 * u, s.y + 18 * u), 2 * u)
            }
        }
    }
}

// ---------------------------------------------------------------- Roo's Pouch

private enum class Phase { Loading, Intro, Playing, Over }

/**
 * Roo's Pouch: the user's own photos fly up like fruit. Slice one and it drops into Roo's pouch,
 * which is the bin; leave it and it falls away. Nothing is deleted until the pouch is reviewed.
 */
@Composable
fun PouchScreen(vm: ClearooViewModel, onBack: () -> Unit, onReview: () -> Unit) {
    val context = LocalContext.current
    val best by vm.pouchBest.collectAsStateWithLifecycle()
    var phase by remember { mutableStateOf(Phase.Loading) }
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val thumbs = remember { mutableStateMapOf<Long, ImageBitmap>() }
    var state by remember { mutableStateOf<PouchState?>(null) }
    var bestBefore by remember { mutableStateOf(0) }
    var comboFlash by remember { mutableStateOf(0 to 0f) } // (combo, seconds left on screen)
    var lastSliceAt by remember { mutableFloatStateOf(-10f) }
    val rng = remember { Random(System.nanoTime()) }

    // Pick the photos and load small previews; the round can start once the first few are ready.
    LaunchedEffect(Unit) {
        val picked = vm.pouchPhotos()
        items = picked
        if (picked.isEmpty()) {
            phase = Phase.Intro
            return@LaunchedEffect
        }
        for ((n, item) in picked.withIndex()) {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val req = ImageRequest.Builder(context).data(item.uri).size(240).allowHardware(false).build()
                    (context.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap()?.asImageBitmap()
                }.getOrNull()
            }
            if (bmp != null) thumbs[item.id] = bmp
            if (n == 5 && phase == Phase.Loading) phase = Phase.Intro
        }
        if (phase == Phase.Loading) phase = Phase.Intro
    }

    LaunchedEffect(phase) {
        if (phase != Phase.Playing) return@LaunchedEffect
        var last = 0L
        while (phase == Phase.Playing) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1e9f
                last = now
                state = state?.let { PouchGame.step(it, dt, rng) }
                if (comboFlash.second > 0f) comboFlash = comboFlash.first to (comboFlash.second - dt)
            }
            val s = state ?: break
            if (s.over) {
                val byId = items.associateBy { it.id }
                vm.pouchToBin(s.pouch.mapNotNull { byId[it] })
                phase = Phase.Over
            }
        }
    }

    val byId = remember(items) { items.associateBy { it.id } }
    val s = state
    val slicedBytes = s?.pouch?.sumOf { byId[it]?.sizeBytes ?: 0L } ?: 0L

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopBar("Roo's Pouch", onBack) {
            if (phase == Phase.Playing && s != null) {
                Pill("⏱ ${s.secondsLeft.toInt()}s", Surface2, Modifier.padding(end = 12.dp))
            }
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1B1433), Color(0xFF2B1D4F), Color(0xFF3B2560)))),
        ) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
            val scale = widthPx / PouchGame.WIDTH
            val worldHeight = heightPx / scale
            val trail = remember { mutableStateListOf<Offset>() }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(phase) {
                        if (phase != Phase.Playing) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var prev = down.position
                            var first = true
                            trail.clear()
                            trail.add(prev)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val p = change.position
                                val before = state
                                if (before != null) {
                                    val after = PouchGame.slice(before, prev.x / scale, prev.y / scale, p.x / scale, p.y / scale, first)
                                    if (after.pouch.size > before.pouch.size) {
                                        lastSliceAt = after.time
                                        if (after.combo >= 2) comboFlash = after.combo to 0.9f
                                    }
                                    state = after
                                }
                                first = false
                                prev = p
                                trail.add(p)
                                if (trail.size > 12) trail.removeAt(0)
                                change.consume()
                                if (!change.pressed) break
                            }
                            trail.clear()
                        }
                    },
            ) {
                val st = state ?: return@Canvas
                val pouch = Offset(size.width / 2, size.height - 70.dp.toPx())
                for (f in st.flyers) {
                    val side = PouchGame.SIZE * scale
                    val img = thumbs[f.mediaId]
                    val center = Offset((f.x + PouchGame.SIZE / 2) * scale, (f.y + PouchGame.SIZE / 2) * scale)
                    val t = f.slicedFor
                    if (t == null) {
                        drawPhoto(img, center, side, f.angle, 1f)
                    } else {
                        // The two halves split along the cut and drop into Roo's pouch.
                        val k = (t / PouchGame.SLICE_ANIM).coerceIn(0f, 1f)
                        val ease = k * k
                        val c = Offset(center.x + (pouch.x - center.x) * ease, center.y + (pouch.y - center.y) * ease)
                        val apart = side * 0.35f * k
                        val nx = -sin(f.cutAngle)
                        val ny = cos(f.cutAngle)
                        val shrink = 1f - 0.75f * ease
                        drawHalf(img, Offset(c.x + nx * apart, c.y + ny * apart), side * shrink, f.cutAngle, upper = false, alpha = 1f - 0.4f * k)
                        drawHalf(img, Offset(c.x - nx * apart, c.y - ny * apart), side * shrink, f.cutAngle, upper = true, alpha = 1f - 0.4f * k)
                    }
                }
                // The blade's trail.
                for (i in 1 until trail.size) {
                    val a = i.toFloat() / trail.size
                    drawLine(Color.White.copy(alpha = a), trail[i - 1], trail[i], strokeWidth = (2 + 6 * a) * 1.dp.toPx(), cap = StrokeCap.Round)
                }
            }

            // Roo holds the pouch open at the bottom.
            val excited = (s?.time ?: 0f) - lastSliceAt < 0.5f
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Mascot(if (excited) Mood.EXCITED else Mood.HAPPY, size = 110.dp)
                Pill("👝 ${s?.pouch?.size ?: 0} · ${Fmt.bytes(slicedBytes)}", Color(0x99000000))
            }
            if (comboFlash.second > 0f) {
                Text(
                    "Combo ×${comboFlash.first}! 🔥",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Gold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            when (phase) {
                Phase.Loading -> Overlay {
                    Mascot(Mood.HOPEFUL, size = 120.dp)
                    Text("Roo is picking photos…", style = MaterialTheme.typography.titleMedium, color = TextHi)
                }
                Phase.Intro -> Overlay {
                    if (items.isEmpty()) {
                        SpeechBubble("No photos to play with right now. Your gallery is spotless! ✨")
                        Mascot(Mood.PROUD, size = 120.dp)
                        Spacer(Modifier.height(16.dp))
                        GhostButton("Back", onBack)
                    } else {
                        SpeechBubble("Swipe to slice the photos you don't want. They drop into my pouch! Leave the good ones. 💖")
                        Mascot(Mood.EXCITED, size = 120.dp)
                        Spacer(Modifier.height(16.dp))
                        GradientButton("Start slicing", {
                            bestBefore = best
                            state = PouchGame.start(worldHeight, items.map { it.id })
                            lastSliceAt = -10f
                            phase = Phase.Playing
                        })
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Nothing is deleted in the game. You review the pouch after.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLo,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Phase.Over -> Overlay {
                    val count = s?.pouch?.size ?: 0
                    Text(
                        if (count > bestBefore && count > 0) "New best! 🏆" else if (count > 0) "Round over!" else "Nothing sliced",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextHi,
                    )
                    Text(
                        "Roo's pouch: $count ${if (count == 1) "photo" else "photos"} · ${Fmt.bytes(slicedBytes)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Gold,
                        textAlign = TextAlign.Center,
                    )
                    if ((s?.bestCombo ?: 0) >= 2) {
                        Text("Best combo ×${s?.bestCombo}", style = MaterialTheme.typography.bodyMedium, color = TextLo)
                    }
                    Spacer(Modifier.height(16.dp))
                    if (count > 0) {
                        GradientButton("Review & move to trash", onReview)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "They're waiting in the bin. Untick anything you want to keep.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLo,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Text("Your photos are safe. Want another go?", style = MaterialTheme.typography.bodyMedium, color = TextLo)
                        Spacer(Modifier.height(8.dp))
                    }
                    GhostButton("Done", onBack)
                }
                Phase.Playing -> Unit
            }
        }
    }
}

@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xCC130F1E)).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** A photo tile: rounded, white-edged, turned by [angleDeg]. */
private fun DrawScope.drawPhoto(img: ImageBitmap?, center: Offset, side: Float, angleDeg: Float, alpha: Float) {
    rotate(angleDeg, center) {
        val rect = Rect(center.x - side / 2, center.y - side / 2, center.x + side / 2, center.y + side / 2)
        val r = side * 0.14f
        drawRoundRect(Color.White.copy(alpha = alpha), rect.topLeft - Offset(3f, 3f), Size(side + 6f, side + 6f), CornerRadius(r + 3f))
        clipPath(Path().apply { addRoundRect(RoundRect(rect, CornerRadius(r))) }) {
            if (img != null) {
                // Centre-crop the preview to a square.
                val m = minOf(img.width, img.height)
                drawImage(
                    img,
                    srcOffset = IntOffset((img.width - m) / 2, (img.height - m) / 2),
                    srcSize = IntSize(m, m),
                    dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
                    dstSize = IntSize(side.toInt(), side.toInt()),
                    alpha = alpha,
                )
            } else {
                drawRect(Surface2, rect.topLeft, rect.size)
            }
        }
    }
}

/** One half of a sliced photo, cut along [cutAngle] (radians). */
private fun DrawScope.drawHalf(img: ImageBitmap?, center: Offset, side: Float, cutAngle: Float, upper: Boolean, alpha: Float) {
    val deg = Math.toDegrees(cutAngle.toDouble()).toFloat()
    rotate(deg, center) {
        val half = Rect(center.x - side, if (upper) center.y - side else center.y, center.x + side, if (upper) center.y else center.y + side)
        clipPath(Path().apply { addRect(half) }) {
            rotate(-deg, center) { drawPhoto(img, center, side, 0f, alpha) }
        }
    }
}
