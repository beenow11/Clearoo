package com.clearoo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clearoo.app.ui.theme.BrandBrush
import com.clearoo.app.ui.theme.Outline
import com.clearoo.app.ui.theme.Surface1
import com.clearoo.app.ui.theme.Surface2
import com.clearoo.app.ui.theme.TextHi
import com.clearoo.app.ui.theme.TextLo

/** Squishes slightly while pressed, like a real button. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "press",
    )
    val haptic = LocalHapticFeedback.current
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = BrandBrush,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .pressable(enabled, onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(RoundedCornerShape(30.dp))
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(27.dp))
            .border(2.dp, Outline, RoundedCornerShape(27.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = TextHi)
    }
}

/** Big round action button used under the card stack. */
@Composable
fun RoundAction(
    size: Dp,
    ring: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .pressable(enabled, onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clip(CircleShape)
            .background(Surface1)
            .border(3.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Paints its content with a gradient (used for the wordmark). */
fun Modifier.gradientTint(brush: Brush): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(brush, blendMode = BlendMode.SrcIn)
    }

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    stroke: Dp = 12.dp,
    brush: Brush = BrandBrush,
    content: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 120f),
        label = "ring",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(Surface2, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            if (animated > 0f) {
                drawArc(
                    brush, -90f, 360f * animated, false, Offset(inset, inset), arcSize,
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** Roo's speech bubble, tail pointing down at the mascot. */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF2A1B3D),
                textAlign = TextAlign.Center,
            )
        }
        Canvas(Modifier.size(width = 22.dp, height = 10.dp)) {
            val tail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            }
            drawPath(tail, Color.White)
        }
    }
}

@Composable
fun StatTile(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Surface1)
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
        Text(value, style = MaterialTheme.typography.titleLarge, color = TextHi, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextLo, maxLines = 1)
    }
}

@Composable
fun Pill(text: String, background: Color, modifier: Modifier = Modifier, color: Color = Color.White) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}
