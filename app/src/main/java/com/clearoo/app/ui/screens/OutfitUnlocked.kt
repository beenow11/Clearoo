package com.clearoo.app.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clearoo.app.domain.Mood
import com.clearoo.app.domain.Outfit
import com.clearoo.app.domain.OutfitRules
import com.clearoo.app.mascot.Mascot
import com.clearoo.app.ui.components.Confetti
import com.clearoo.app.ui.components.GhostButton
import com.clearoo.app.ui.components.GradientButton
import com.clearoo.app.ui.components.Pill
import com.clearoo.app.ui.theme.Surface1
import com.clearoo.app.ui.theme.Surface2
import com.clearoo.app.ui.theme.TextHi
import com.clearoo.app.ui.theme.TextLo

/**
 * Shown once for every outfit the user unlocks, wherever it was earned, so a new reward is
 * never missed. [outfits] are the unlocks not yet announced; the newest is shown on Roo.
 */
@Composable
fun OutfitUnlockedDialog(outfits: List<Outfit>, onWear: (Outfit) -> Unit, onLater: () -> Unit) {
    val star = outfits.last()
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xCC130F1E)), contentAlignment = Alignment.Center) {
            Confetti(Modifier.fillMaxSize(), key = outfits)
            Column(
                Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Surface1)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (outfits.size == 1) "New outfit unlocked! 🎁" else "${outfits.size} new outfits unlocked! 🎁",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextHi,
                    textAlign = TextAlign.Center,
                )
                Mascot(Mood.EXCITED, size = 200.dp, outfit = star)
                Text("${star.emoji} ${star.title}", style = MaterialTheme.typography.titleLarge, color = TextHi)
                Text(
                    "Earned for: ${OutfitRules.hint(star).replaceFirstChar { it.lowercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLo,
                    textAlign = TextAlign.Center,
                )
                if (outfits.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        outfits.dropLast(1).forEach { Pill("${it.emoji} ${it.title}", Surface2) }
                    }
                }
                Spacer(Modifier.height(20.dp))
                GradientButton("Wear it now", { onWear(star) })
                Spacer(Modifier.height(8.dp))
                GhostButton("Later", onLater)
            }
        }
    }
}
