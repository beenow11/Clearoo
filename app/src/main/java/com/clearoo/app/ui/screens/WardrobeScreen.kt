package com.clearoo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clearoo.app.domain.Mood
import com.clearoo.app.domain.Outfit
import com.clearoo.app.domain.OutfitRules
import com.clearoo.app.domain.Progress
import com.clearoo.app.mascot.Mascot
import com.clearoo.app.ui.ClearooViewModel
import com.clearoo.app.ui.components.Pill
import com.clearoo.app.ui.components.SpeechBubble
import com.clearoo.app.ui.components.pressable
import com.clearoo.app.ui.theme.BrandBrush
import com.clearoo.app.ui.theme.KeepGreen
import com.clearoo.app.ui.theme.Surface1
import com.clearoo.app.ui.theme.Surface2
import com.clearoo.app.ui.theme.TextHi
import com.clearoo.app.ui.theme.TextLo
import com.clearoo.app.ui.theme.Violet

/** Roo's wardrobe: outfits unlock with streaks and clean-up milestones. */
@Composable
fun WardrobeScreen(vm: ClearooViewModel, onBack: () -> Unit) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val wearing = settings?.outfit ?: Outfit.CLASSIC
    val unlockedCount = Outfit.entries.count { OutfitRules.isUnlocked(it, progress) }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextHi)
            }
            Text("Roo's wardrobe", style = MaterialTheme.typography.headlineSmall, color = TextHi)
            Spacer(Modifier.weight(1f))
            Pill("$unlockedCount/${Outfit.entries.size}", Surface2)
        }

        SpeechBubble(if (wearing == Outfit.CLASSIC) "What should I wear today? 👀" else "How do I look? ${wearing.emoji}")
        Mascot(Mood.HAPPY, size = 180.dp)
        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Outfit.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { outfit ->
                        OutfitTile(outfit, progress, outfit == wearing, Modifier.weight(1f)) { vm.wear(outfit) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OutfitTile(outfit: Outfit, progress: Progress, wearing: Boolean, modifier: Modifier, onWear: () -> Unit) {
    val unlocked = OutfitRules.isUnlocked(outfit, progress)
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .pressable(enabled = unlocked && !wearing, onClick = onWear)
            .clip(shape)
            .background(Surface1)
            .then(if (wearing) Modifier.border(2.dp, BrandBrush, shape) else Modifier)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Locked outfits are shown as a faded teaser so there's something to aim for.
            Mascot(
                Mood.HOPEFUL,
                Modifier.graphicsLayer { alpha = if (unlocked) 1f else 0.3f },
                size = 110.dp,
                outfit = outfit,
            )
            if (!unlocked) Text("🔒", style = MaterialTheme.typography.headlineMedium)
        }
        Text("${outfit.emoji} ${outfit.title}", style = MaterialTheme.typography.titleMedium, color = TextHi, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        when {
            wearing -> Pill("Wearing ✓", KeepGreen.copy(alpha = 0.2f), color = KeepGreen)
            unlocked -> Pill("Tap to wear", Violet)
            else -> {
                Text(
                    OutfitRules.hint(outfit),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLo,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Surface2),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(OutfitRules.progress(outfit, progress))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(BrandBrush),
                    )
                }
            }
        }
    }
}
