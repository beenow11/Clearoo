package com.clearoo.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.clearoo.app.domain.Mood
import com.clearoo.app.mascot.Mascot
import com.clearoo.app.ui.ClearooViewModel
import com.clearoo.app.ui.components.GradientButton
import com.clearoo.app.ui.components.Pill
import com.clearoo.app.ui.components.pressable
import com.clearoo.app.ui.theme.DeleteBrush
import com.clearoo.app.ui.theme.Surface1
import com.clearoo.app.ui.theme.TextHi
import com.clearoo.app.ui.theme.TextLo
import com.clearoo.app.util.Fmt

@Composable
fun ReviewScreen(vm: ClearooViewModel, onBack: () -> Unit, onDeleted: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val permanent = settings?.permanentDelete == true
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.onDeleted()
            onDeleted()
        }
    }
    val binned = vm.pending
    val bytes = binned.sumOf { it.sizeBytes }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextHi)
            }
            Text("The bin", style = MaterialTheme.typography.headlineSmall, color = TextHi)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mascot(if (binned.isEmpty()) Mood.HOPEFUL else Mood.EXCITED, size = 84.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    if (binned.isEmpty()) "Nothing in the bin" else "${binned.size} items · ${Fmt.bytes(bytes)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextHi,
                )
                Text(
                    if (binned.isEmpty()) "Swipe left on something first!" else "Tap ✕ on anything you want to keep.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLo,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(binned, key = { it.id }) { item ->
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface1),
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (item.isVideo) {
                        Pill("▶", Color(0x88000000), Modifier.align(Alignment.BottomStart).padding(6.dp))
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(28.dp)
                            .pressable { vm.restore(item) }
                            .clip(CircleShape)
                            .background(Color(0xCC000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Keep this", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            GradientButton(
                text = if (permanent) "Delete forever (${binned.size})" else "Move ${binned.size} to trash",
                onClick = { vm.deleteRequest()?.let { launcher.launch(IntentSenderRequest.Builder(it).build()) } },
                brush = DeleteBrush,
                enabled = binned.isNotEmpty(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (permanent) "This can't be undone." else "You can still recover them from your gallery's trash for 30 days.",
                style = MaterialTheme.typography.bodySmall,
                color = TextLo,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
