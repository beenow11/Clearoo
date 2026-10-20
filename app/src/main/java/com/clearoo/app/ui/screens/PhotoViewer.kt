package com.clearoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.clearoo.app.data.MediaItem
import com.clearoo.app.ui.components.Pill
import com.clearoo.app.util.Fmt

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Full-screen look at a card's photo: pinch or double-tap to zoom, drag to look around.
 * Opened by a long press on the top card; the deck underneath is untouched.
 */
@Composable
fun PhotoViewer(item: MediaItem, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var box by remember { mutableStateOf(IntSize.Zero) }
        var chrome by remember { mutableStateOf(true) }

        // Keeps the zoomed photo from being dragged off the screen.
        fun clamp(o: Offset, s: Float): Offset {
            val maxX = box.width * (s - 1) / 2
            val maxY = box.height * (s - 1) / 2
            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { box = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                        // Zoom around the fingers, not the middle of the screen.
                        val center = Offset(box.width / 2f, box.height / 2f)
                        val focus = centroid - center
                        val moved = offset + pan + (focus - offset) * (1 - next / scale)
                        scale = next
                        offset = if (next == 1f) Offset.Zero else clamp(moved, next)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { chrome = !chrome },
                        onDoubleTap = { tap ->
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val center = Offset(box.width / 2f, box.height / 2f)
                                scale = DOUBLE_TAP_ZOOM
                                offset = clamp((center - tap) * (DOUBLE_TAP_ZOOM - 1), DOUBLE_TAP_ZOOM)
                            }
                        },
                    )
                },
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(item.uri).size(2400).crossfade(true).build(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            if (chrome) {
                Row(
                    Modifier.fillMaxWidth().systemBarsPadding().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().systemBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.takenAtMillis > 0) Pill(Fmt.date(item.takenAtMillis), Color(0x66000000))
                        if (item.sizeBytes > 0) Pill(Fmt.bytes(item.sizeBytes), Color(0x66000000))
                        item.album?.let { Pill(it, Color(0x66000000)) }
                    }
                    Text(
                        if (item.isVideo) "Video preview frame · pinch to zoom" else "Pinch or double-tap to zoom · tap to hide",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF),
                    )
                }
            }
        }
    }
}
