package com.clearoo.app.ui.screens

import android.graphics.Matrix
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.clearoo.app.data.MediaItem
import com.clearoo.app.ui.components.Pill
import com.clearoo.app.ui.theme.DeleteRed
import com.clearoo.app.ui.theme.KeepGreen
import com.clearoo.app.ui.theme.Surface1
import com.clearoo.app.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Fraction of the card width a drag must pass to count as a swipe. */
private const val SWIPE_THRESHOLD = 0.32f

/** Position of the top card. One instance per card, so animations never leak between cards. */
class CardSwipeState(initialX: Float = 0f) {
    val offsetX = Animatable(initialX)
    val offsetY = Animatable(0f)
    var flying = false
        private set

    /** -1 (full delete) … 0 … 1 (full keep). */
    fun progress(width: Float): Float =
        if (width <= 0f) 0f else (offsetX.value / (width * SWIPE_THRESHOLD)).coerceIn(-1f, 1f)

    suspend fun dragBy(dx: Float, dy: Float) {
        offsetX.snapTo(offsetX.value + dx)
        offsetY.snapTo(offsetY.value + dy)
    }

    suspend fun settle() = coroutineScope {
        val spec = spring<Float>(dampingRatio = 0.55f, stiffness = 320f)
        launch { offsetX.animateTo(0f, spec) }
        launch { offsetY.animateTo(0f, spec) }
    }

    suspend fun flyOut(keep: Boolean, width: Float) = coroutineScope {
        flying = true
        val spec = tween<Float>(260, easing = FastOutLinearInEasing)
        launch { offsetX.animateTo(if (keep) width * 1.6f else -width * 1.6f, spec) }
        launch { offsetY.animateTo(offsetY.value + width * 0.15f, spec) }
    }
}

fun Modifier.swipeGestures(
    state: CardSwipeState,
    width: Float,
    scope: CoroutineScope,
    haptic: HapticFeedback,
    onSwiped: (keep: Boolean) -> Unit,
): Modifier = pointerInput(state, width) {
    val tracker = VelocityTracker()
    val flingVelocity = 900.dp.toPx()
    var pastThreshold = false
    detectDragGestures(
        onDragStart = {
            tracker.resetTracking()
            pastThreshold = false
        },
        onDrag = { change, amount ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            val x = state.offsetX.value + amount.x
            val past = abs(x) > width * SWIPE_THRESHOLD
            if (past && !pastThreshold) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            pastThreshold = past
            scope.launch { state.dragBy(amount.x, amount.y) }
        },
        onDragEnd = {
            val vx = tracker.calculateVelocity().x
            val x = state.offsetX.value
            val keep = when {
                abs(x) > width * SWIPE_THRESHOLD -> x > 0
                abs(vx) > flingVelocity && vx * x > 0 -> vx > 0
                else -> null
            }
            scope.launch {
                if (keep == null) {
                    state.settle()
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.flyOut(keep, width)
                    onSwiped(keep)
                }
            }
        },
        onDragCancel = { scope.launch { state.settle() } },
    )
}

@Composable
fun MediaCard(
    item: MediaItem,
    isTop: Boolean,
    muted: Boolean,
    onToggleMute: () -> Unit,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    var fit by remember(item.id) { mutableStateOf(false) }
    Box(
        modifier
            .shadow(18.dp, shape)
            .clip(shape)
            .background(Surface1)
            .pointerInput(item.id) {
                detectTapGestures(onTap = { if (item.isVideo) onToggleMute() else fit = !fit })
            },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(item.uri).crossfade(true).build(),
            contentDescription = item.displayName,
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo && isTop) VideoLayer(item, muted, Modifier.fillMaxSize())

        // Colour wash that grows as you drag.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (-progress()).coerceIn(0f, 1f) * 0.35f }
                .background(DeleteRed),
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress().coerceIn(0f, 1f) * 0.3f }
                .background(KeepGreen),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(Fmt.age(item.takenAtMillis), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.takenAtMillis > 0) Pill(Fmt.date(item.takenAtMillis), Color(0x33FFFFFF))
                Pill(Fmt.bytes(item.sizeBytes), Color(0x33FFFFFF))
                item.album?.let { Pill(it, Color(0x33FFFFFF)) }
            }
        }

        item.badge?.let {
            Pill(it, Color(0x99000000), Modifier.align(Alignment.TopStart).padding(16.dp))
        }

        if (item.isVideo) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill("▶ ${Fmt.duration(item.durationMs)}", Color(0x66000000))
                if (isTop) Pill(if (muted) "🔇" else "🔊", Color(0x66000000))
            }
        }

        Stamp(
            "KEEP", KeepGreen, -14f,
            Modifier.align(Alignment.TopStart).padding(top = 64.dp, start = 24.dp),
        ) { progress().coerceIn(0f, 1f) }
        Stamp(
            "DELETE", DeleteRed, 14f,
            Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 24.dp),
        ) { (-progress()).coerceIn(0f, 1f) }
    }
}

@Composable
private fun Stamp(text: String, color: Color, rotation: Float, modifier: Modifier, amount: () -> Float) {
    Box(
        modifier
            .graphicsLayer {
                val a = amount()
                alpha = a
                rotationZ = rotation
                scaleX = 0.7f + 0.3f * a
                scaleY = 0.7f + 0.3f * a
            }
            .border(4.dp, color, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.displaySmall, color = color)
    }
}

private data class VideoFit(val size: IntSize)

/** Muted looping preview on the top card, drawn into a TextureView so it rotates with the card. */
@Composable
private fun VideoLayer(item: MediaItem, muted: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    var rendered by remember(item.id) { mutableStateOf(false) }
    var videoSize by remember(item.id) { mutableStateOf(IntSize.Zero) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                rendered = true
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = IntSize((size.width * size.pixelWidthHeightRatio).toInt(), size.height)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { player.play() }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> (v as TextureView).centerCrop() }
                player.setVideoTextureView(this)
            }
        },
        update = { view ->
            view.tag = VideoFit(videoSize)
            view.centerCrop()
        },
        modifier = modifier.graphicsLayer { alpha = if (rendered) 1f else 0f },
    )
}

/** TextureView stretches video to fill; undo that and crop to keep the aspect ratio. */
private fun TextureView.centerCrop() {
    val video = (tag as? VideoFit)?.size ?: return
    if (width == 0 || height == 0 || video.width == 0 || video.height == 0) return
    val vw = width.toFloat()
    val vh = height.toFloat()
    val scale = maxOf(vw / video.width, vh / video.height)
    val matrix = Matrix()
    matrix.setScale(video.width * scale / vw, video.height * scale / vh, vw / 2, vh / 2)
    setTransform(matrix)
}
