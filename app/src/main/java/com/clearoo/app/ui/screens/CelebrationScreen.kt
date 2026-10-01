package com.clearoo.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clearoo.app.domain.Mood
import com.clearoo.app.mascot.Mascot
import com.clearoo.app.ui.ClearooViewModel
import com.clearoo.app.ui.components.Confetti
import com.clearoo.app.ui.components.GhostButton
import com.clearoo.app.ui.components.GradientButton
import com.clearoo.app.ui.components.SpeechBubble
import com.clearoo.app.ui.components.gradientTint
import com.clearoo.app.ui.theme.BrandBrush
import com.clearoo.app.ui.theme.Flame
import com.clearoo.app.ui.theme.TextHi
import com.clearoo.app.ui.theme.TextLo
import com.clearoo.app.util.Fmt

@Composable
fun CelebrationScreen(vm: ClearooViewModel, onKeepGoing: () -> Unit, onDone: () -> Unit) {
    val result = vm.lastResult
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(result) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val line = when {
                result == null -> "Cleaning up… 🧹"
                result.streakGrew && result.streak > 1 -> "${result.streak}-day streak! You're on fire 🔥"
                result.streakGrew -> "Streak started! See you tomorrow 🔥"
                result.goalReached -> "Daily goal smashed! 🏆"
                else -> "Woohoo! Your phone says thanks ✨"
            }
            SpeechBubble(line)
            Mascot(Mood.PROUD, size = 220.dp)
            Spacer(Modifier.height(12.dp))
            if (result != null) {
                Text(
                    "+${Fmt.bytes(result.bytes)}",
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        }
                        .gradientTint(BrandBrush),
                )
                Text(
                    "freed by clearing ${result.count} ${if (result.count == 1) "item" else "items"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextLo,
                    textAlign = TextAlign.Center,
                )
                if (result.streak > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("🔥 ${result.streak}-day streak", style = MaterialTheme.typography.titleLarge, color = Flame)
                }
            }
            Spacer(Modifier.height(36.dp))
            GradientButton("Keep swiping", onKeepGoing)
            Spacer(Modifier.height(12.dp))
            GhostButton("Done for today", onDone)
            Spacer(Modifier.height(8.dp))
            Text("Roo will check in tomorrow 🦘", style = MaterialTheme.typography.bodySmall, color = TextHi.copy(alpha = 0.6f))
        }
        Confetti(Modifier.fillMaxSize(), key = result ?: Unit)
    }
}
