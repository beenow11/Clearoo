package com.clearoo.app.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clearoo.app.domain.Mood
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Roo, alive: idles with a gentle hop, blinks, and squishes whenever the mood changes. */
@Composable
fun Mascot(mood: Mood, modifier: Modifier = Modifier, size: Dp = 160.dp) {
    val painter = remember { MascotPainter() }

    val idle = rememberInfiniteTransition(label = "idle")
    val hopSpeed = when (mood) {
        Mood.EXCITED, Mood.PROUD -> 520
        Mood.SAD, Mood.SLEEPY -> 2200
        else -> 1300
    }
    val bob by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(hopSpeed, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val hop = when (mood) {
        Mood.EXCITED, Mood.PROUD -> 0.09f
        Mood.SAD, Mood.SLEEPY -> 0.015f
        else -> 0.035f
    }

    var blink by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2200, 5200))
            animate(0f, 1f, animationSpec = tween(90)) { v, _ -> blink = v }
            animate(1f, 0f, animationSpec = tween(120)) { v, _ -> blink = v }
        }
    }

    val pop = remember { Animatable(1f) }
    LaunchedEffect(mood) {
        pop.snapTo(0.82f)
        pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 420f))
    }

    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationY = -bob * hop * this.size.height
                scaleX = pop.value * (1f + 0.02f * (1f - bob))
                scaleY = pop.value * (1f - 0.02f * (1f - bob))
            },
    ) {
        drawIntoCanvas { painter.draw(it.nativeCanvas, this.size.minDimension, mood, blink) }
    }
}
