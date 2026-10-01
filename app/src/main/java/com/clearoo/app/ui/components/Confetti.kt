package com.clearoo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.clearoo.app.ui.theme.Coral
import com.clearoo.app.ui.theme.Gold
import com.clearoo.app.ui.theme.KeepGreen
import com.clearoo.app.ui.theme.Peach
import com.clearoo.app.ui.theme.Pink
import com.clearoo.app.ui.theme.Violet
import kotlin.random.Random

private class Piece(
    val x: Float,
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val rotation: Float,
    val color: Color,
    val w: Float,
    val h: Float,
)

private const val DURATION_S = 3.2f

/** A one-shot confetti burst; changing [key] fires it again. */
@Composable
fun Confetti(modifier: Modifier = Modifier, key: Any = Unit) {
    val colors = listOf(Coral, Pink, Peach, Gold, Violet, KeepGreen)
    val pieces = remember(key) {
        List(140) {
            Piece(
                x = 0.5f + (Random.nextFloat() - 0.5f) * 0.3f,
                vx = (Random.nextFloat() - 0.5f) * 1.4f,
                vy = -(0.9f + Random.nextFloat() * 1.1f),
                spin = (Random.nextFloat() - 0.5f) * 900f,
                rotation = Random.nextFloat() * 360f,
                color = colors.random(),
                w = 10f + Random.nextFloat() * 10f,
                h = 6f + Random.nextFloat() * 8f,
            )
        }
    }
    var t by remember(key) { mutableFloatStateOf(0f) }
    LaunchedEffect(key) {
        val start = withFrameNanos { it }
        while (t < DURATION_S) {
            withFrameNanos { now -> t = (now - start) / 1_000_000_000f }
        }
    }
    Canvas(modifier) {
        if (t >= DURATION_S) return@Canvas
        val fade = (1f - (t - DURATION_S + 0.8f).coerceAtLeast(0f) / 0.8f).coerceIn(0f, 1f)
        pieces.forEach { p ->
            val x = (p.x + p.vx * t * 0.5f) * size.width
            val y = size.height * 0.4f + (p.vy * t + 1.1f * t * t) * size.height * 0.4f
            rotate(p.rotation + p.spin * t, Offset(x, y)) {
                drawRect(p.color, Offset(x - p.w / 2, y - p.h / 2), Size(p.w, p.h), alpha = fade)
            }
        }
    }
}
