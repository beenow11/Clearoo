package com.clearoo.app.ui.screens

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.clearoo.app.data.MediaItem
import com.clearoo.app.domain.Mood
import com.clearoo.app.domain.Puzzle
import com.clearoo.app.domain.PuzzleRules
import com.clearoo.app.mascot.Mascot
import com.clearoo.app.ui.ClearooViewModel
import com.clearoo.app.ui.components.Confetti
import com.clearoo.app.ui.components.GhostButton
import com.clearoo.app.ui.components.GradientButton
import com.clearoo.app.ui.components.Pill
import com.clearoo.app.ui.components.SpeechBubble
import com.clearoo.app.ui.components.pressable
import com.clearoo.app.ui.theme.Gold
import com.clearoo.app.ui.theme.Surface1
import com.clearoo.app.ui.theme.Surface2
import com.clearoo.app.ui.theme.TextHi
import com.clearoo.app.ui.theme.TextLo
import com.clearoo.app.ui.theme.Violet
import com.clearoo.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

// ---------------------------------------------------------------- Games hub

/** Where tapping Roo leads: every game in one place. */
@Composable
fun GamesScreen(vm: ClearooViewModel, onBack: () -> Unit, onPouch: () -> Unit, onPuzzle: () -> Unit, onClimb: () -> Unit) {
    val pouchBest by vm.pouchBest.collectAsStateWithLifecycle()
    val puzzleBest by vm.puzzleBest.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        GameTopBar("Play with Roo", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpeechBubble("Pick a game! Every one helps tidy your gallery. 🎮")
            Mascot(Mood.EXCITED, size = 140.dp)
            Spacer(Modifier.height(12.dp))
            GameCard("🔪", "Roo's Pouch", "Slice the photos you don't want", if (pouchBest > 0) "Best: $pouchBest" else null, onPouch)
            Spacer(Modifier.height(12.dp))
            val solved = puzzleBest.keys.sorted()
            GameCard(
                "🧩", "Roo's Puzzle", "Unscramble one of your photos",
                if (solved.isNotEmpty()) "Solved: ${solved.joinToString(" ") { "${it}×$it" }}" else "New",
                onPuzzle,
            )
            Spacer(Modifier.height(12.dp))
            GameCard("🏔️", "Roo's Climb", "${progress.totalDeleted} hops up the mountain", null, onClimb)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GameCard(emoji: String, title: String, subtitle: String, badge: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(24.dp))
            .background(Surface1)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = TextHi)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextLo)
        }
        badge?.let { Pill(it, Violet) }
    }
}

// ---------------------------------------------------------------- Roo's Puzzle

private enum class PuzzlePhase { Pick, Loading, Playing, Solved, Empty }

/**
 * Roo's Puzzle: one of the user's photos is cut into a grid and shuffled. Tap two tiles to swap
 * them. Once it's solved, the user decides whether the photo stays or goes in the bin.
 */
@Composable
fun PuzzleScreen(vm: ClearooViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bests by vm.puzzleBest.collectAsStateWithLifecycle()
    var phase by remember { mutableStateOf(PuzzlePhase.Pick) }
    var size by remember { mutableIntStateOf(3) }
    var item by remember { mutableStateOf<MediaItem?>(null) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var puzzle by remember { mutableStateOf<Puzzle?>(null) }
    var minimum by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var seconds by remember { mutableIntStateOf(0) }
    var peek by remember { mutableStateOf(false) }
    var bestBefore by remember { mutableStateOf<Int?>(null) }
    var verdict by remember { mutableStateOf<String?>(null) }

    fun start() {
        phase = PuzzlePhase.Loading
        verdict = null
        scope.launch {
            val photo = vm.puzzlePhoto()
            val bmp = photo?.let {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val req = ImageRequest.Builder(context).data(it.uri).size(1200).allowHardware(false).build()
                        (context.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap()?.asImageBitmap()
                    }.getOrNull()
                }
            }
            if (photo == null || bmp == null) {
                phase = PuzzlePhase.Empty
                return@launch
            }
            val p = PuzzleRules.shuffled(size, Random(System.nanoTime()))
            item = photo
            image = bmp
            puzzle = p
            minimum = PuzzleRules.minSwaps(p)
            selected = null
            seconds = 0
            bestBefore = bests[size]
            phase = PuzzlePhase.Playing
        }
    }

    LaunchedEffect(phase) {
        while (phase == PuzzlePhase.Playing) {
            delay(1000)
            if (phase == PuzzlePhase.Playing) seconds++
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        GameTopBar("Roo's Puzzle", onBack) {
            val p = puzzle
            if (phase == PuzzlePhase.Playing && p != null) {
                Pill("⏱ ${clock(seconds)} · ${p.moves} moves", Surface2, Modifier.padding(end = 12.dp))
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (phase) {
                PuzzlePhase.Pick -> {
                    SpeechBubble("I'll cut one of your photos into pieces and mix them up. Tap two pieces to swap them! 🧩")
                    Mascot(Mood.EXCITED, size = 140.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("How many pieces?", style = MaterialTheme.typography.titleMedium, color = TextHi)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PuzzleRules.SIZES.forEach { n ->
                            val on = n == size
                            Box(
                                Modifier
                                    .pressable(onClick = { size = n })
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (on) Violet else Surface1)
                                    .then(if (on) Modifier.border(2.dp, Color.White, RoundedCornerShape(16.dp)) else Modifier)
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                            ) { Text("${n}×$n", style = MaterialTheme.typography.titleMedium, color = TextHi) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${size * size} pieces · " + (bests[size]?.let { "your best ${clock(it)}" } ?: "not solved yet"),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLo,
                    )
                    Spacer(Modifier.height(20.dp))
                    GradientButton("Start puzzle", { start() })
                }
                PuzzlePhase.Loading -> {
                    Spacer(Modifier.height(80.dp))
                    Mascot(Mood.HOPEFUL, size = 140.dp)
                    Text("Roo is cutting up a photo…", style = MaterialTheme.typography.titleMedium, color = TextHi)
                }
                PuzzlePhase.Empty -> {
                    Spacer(Modifier.height(40.dp))
                    SpeechBubble("I couldn't find a photo to puzzle with right now. ✨")
                    Mascot(Mood.PROUD, size = 140.dp)
                    Spacer(Modifier.height(16.dp))
                    GhostButton("Back", onBack)
                }
                PuzzlePhase.Playing, PuzzlePhase.Solved -> {
                    val p = puzzle ?: return@Column
                    val img = image ?: return@Column
                    Board(
                        puzzle = p,
                        image = img,
                        selected = selected,
                        peek = peek || phase == PuzzlePhase.Solved,
                        onTap = { pos ->
                            if (phase != PuzzlePhase.Playing) return@Board
                            val first = selected
                            if (first == null) {
                                selected = pos
                            } else {
                                val next = PuzzleRules.swap(p, first, pos)
                                puzzle = next
                                selected = null
                                if (next.solved) {
                                    vm.recordPuzzle(size, seconds)
                                    phase = PuzzlePhase.Solved
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    if (phase == PuzzlePhase.Playing) {
                        Text(
                            "${p.inPlace} of ${p.tiles.size} pieces in place",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextLo,
                        )
                        Spacer(Modifier.height(12.dp))
                        // Hold to see the finished photo; let go to carry on.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(Surface1)
                                .pointerInput(Unit) {
                                    detectTapGestures(onPress = {
                                        peek = true
                                        tryAwaitRelease()
                                        peek = false
                                    })
                                }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("👀 Hold to peek", style = MaterialTheme.typography.titleMedium, color = TextHi) }
                        Spacer(Modifier.height(8.dp))
                        GhostButton("New puzzle", { phase = PuzzlePhase.Pick })
                    } else {
                        val stars = PuzzleRules.stars(p.moves, minimum)
                        val before = bestBefore
                        Text("Solved! " + "⭐".repeat(stars), style = MaterialTheme.typography.headlineSmall, color = TextHi)
                        Text(
                            "${clock(seconds)} · ${p.moves} moves" + if (before == null || seconds < before) " · New best! 🏆" else " · best ${clock(before)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Gold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        val photo = item
                        val said = verdict
                        if (said != null) {
                            Text(said, style = MaterialTheme.typography.bodyMedium, color = TextLo, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            GradientButton("Play again", { start() })
                            Spacer(Modifier.height(8.dp))
                            GhostButton("Change size", { phase = PuzzlePhase.Pick })
                        } else if (photo != null) {
                            Text(
                                "Keep this photo? " + listOfNotNull(
                                    photo.takenAtMillis.takeIf { it > 0 }?.let { Fmt.date(it) },
                                    photo.sizeBytes.takeIf { it > 0 }?.let { Fmt.bytes(it) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextLo,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            GradientButton("Keep it 💖", {
                                vm.puzzleVerdict(photo, keep = true)
                                verdict = "Kept! Roo won't ask about it again."
                            })
                            Spacer(Modifier.height(8.dp))
                            GhostButton("Put it in the bin 🗑", {
                                vm.puzzleVerdict(photo, keep = false)
                                verdict = "In the bin. Review it from Home before it's deleted."
                            })
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    if (phase == PuzzlePhase.Solved) Confetti(Modifier.fillMaxSize(), key = puzzle ?: Unit)
}

/** The square board. Each piece slides to its new spot when swapped. */
@Composable
private fun Board(puzzle: Puzzle, image: ImageBitmap, selected: Int?, peek: Boolean, onTap: (Int) -> Unit) {
    val n = puzzle.size
    // The photo's middle square is what gets cut up.
    val side = minOf(image.width, image.height)
    val left = (image.width - side) / 2
    val top = (image.height - side) / 2
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1),
    ) {
        val cellPx = with(LocalDensity.current) { maxWidth.toPx() } / n
        val cellDp = maxWidth / n
        if (peek) {
            Canvas(Modifier.fillMaxSize()) {
                drawImage(image, IntOffset(left, top), IntSize(side, side), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            }
            return@BoxWithConstraints
        }
        // Where each piece sits now; iterating by piece keeps its animation state across swaps.
        val posOf = IntArray(puzzle.tiles.size).also { arr -> puzzle.tiles.forEachIndexed { pos, piece -> arr[piece] = pos } }
        for (piece in puzzle.tiles.indices) {
            val pos = posOf[piece]
            val target = IntOffset(((pos % n) * cellPx).toInt(), ((pos / n) * cellPx).toInt())
            val at by animateIntOffsetAsState(target, spring(dampingRatio = 0.75f, stiffness = 500f), label = "piece")
            val chosen = pos == selected
            val gap = if (n >= 5) 1.dp else 2.dp
            Canvas(
                Modifier
                    .offset { at }
                    .size(cellDp)
                    .zIndex(if (chosen) 1f else 0f)
                    .padding(gap)
                    .then(if (chosen) Modifier.border(3.dp, Gold, RoundedCornerShape(6.dp)) else Modifier)
                    .semantics { contentDescription = "Piece ${piece + 1} at ${pos + 1}" }
                    .pointerInput(pos) { detectTapGestures { onTap(pos) } },
            ) {
                val src = side / n
                drawImage(
                    image,
                    srcOffset = IntOffset(left + (piece % n) * src, top + (piece / n) * src),
                    srcSize = IntSize(src, src),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    alpha = if (chosen) 0.7f else 1f,
                )
            }
        }
    }
}

private fun clock(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)
