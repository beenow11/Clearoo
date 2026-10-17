package com.clearoo.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.clearoo.app.domain.AppMeta
import com.clearoo.app.domain.AppRules
import com.clearoo.app.domain.Mood
import com.clearoo.app.mascot.Mascot
import com.clearoo.app.ui.ClearooViewModel
import com.clearoo.app.ui.components.GradientButton
import com.clearoo.app.ui.components.Pill
import com.clearoo.app.ui.components.SpeechBubble
import com.clearoo.app.ui.components.pressable
import com.clearoo.app.ui.theme.DeleteRed
import com.clearoo.app.ui.theme.KeepGreen
import com.clearoo.app.ui.theme.Surface1
import com.clearoo.app.ui.theme.Surface2
import com.clearoo.app.ui.theme.TextHi
import com.clearoo.app.ui.theme.TextLo
import com.clearoo.app.ui.theme.Violet
import com.clearoo.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Apps the user hasn't opened in a while, biggest first, each one tap from Android's uninstall dialog. */
@Composable
fun UnusedAppsScreen(vm: ClearooViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var days by rememberSaveable { mutableIntStateOf(AppRules.THRESHOLD_DAYS.first()) }
    // Comes back here after Usage access is switched on, or after an uninstall.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshApps() }
    val uninstall = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onUninstallReturned()
    }

    fun startFirst(intents: List<Intent>, launch: (Intent) -> Unit) {
        for (intent in intents) {
            try {
                launch(intent)
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextHi)
            }
            Text("Unused apps", style = MaterialTheme.typography.headlineSmall, color = TextHi)
        }

        if (!vm.usageAccess) {
            NeedsUsageAccess { startFirst(vm.usageAccessIntents()) { context.startActivity(it) } }
            return@Column
        }

        val now = System.currentTimeMillis()
        val list = vm.installedApps?.let { AppRules.unused(it, now, days, context.packageName) }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Not opened in", style = MaterialTheme.typography.bodyMedium, color = TextLo)
                        AppRules.THRESHOLD_DAYS.forEach { d ->
                            Pill("$d days", if (d == days) Violet else Surface2, Modifier.pressable { days = d })
                        }
                    }
                    Text(
                        when {
                            list == null -> "Checking your apps…"
                            list.isEmpty() -> ""
                            else -> "${list.size} ${if (list.size == 1) "app" else "apps"} · ${Fmt.bytes(list.sumOf { it.sizeBytes })} you could free"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHi,
                    )
                    if (vm.appsRemoved > 0) {
                        Text(
                            "🎉 Removed ${vm.appsRemoved} ${if (vm.appsRemoved == 1) "app" else "apps"} · ${Fmt.bytes(vm.appsFreed)} freed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KeepGreen,
                        )
                    }
                }
            }
            if (list != null && list.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Mascot(Mood.PROUD, size = 140.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Every app earns its place!\nNothing left unopened for $days days.",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextHi,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            items(list.orEmpty(), key = { it.packageName }) { app ->
                AppRow(vm, app, now) { startFirst(vm.uninstallIntents(app)) { uninstall.launch(it) } }
            }
            item {
                Text(
                    "Android shows its own confirmation before anything is uninstalled. Your app usage never leaves this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLo,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun NeedsUsageAccess(onAllow: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpeechBubble("I can find the apps you've forgotten about! 🕵️")
        Mascot(Mood.HOPEFUL, size = 140.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            "To see when you last opened each app, Android needs you to turn on Usage access for Clearoo. Your app usage never leaves this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextLo,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        GradientButton("Allow usage access", onAllow)
        Spacer(Modifier.height(8.dp))
        Text(
            "Find Clearoo in the list and switch it on, then come back.",
            style = MaterialTheme.typography.bodySmall,
            color = TextLo,
            textAlign = TextAlign.Center,
        )
    }
}

private const val YEAR_MS = 365L * 24 * 60 * 60 * 1000

private fun lastOpened(app: AppMeta, now: Long): String = when {
    app.lastUsedMillis > 0 && app.lastUsedMillis >= app.installedMillis -> "Last opened ${Fmt.age(app.lastUsedMillis).lowercase()}"
    now - app.installedMillis > YEAR_MS -> "Not opened in over a year"
    else -> "Never opened · installed ${Fmt.age(app.installedMillis).lowercase()}"
}

@Composable
private fun AppRow(vm: ClearooViewModel, app: AppMeta, now: Long, onUninstall: () -> Unit) {
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) { vm.appIcon(app.packageName)?.asImageBitmap() }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Surface2)) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, color = TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(lastOpened(app, now), style = MaterialTheme.typography.bodySmall, color = TextLo, maxLines = 1)
            if (app.sizeBytes > 0) Text(Fmt.bytes(app.sizeBytes), style = MaterialTheme.typography.bodySmall, color = TextLo)
        }
        Spacer(Modifier.width(8.dp))
        Pill("Uninstall", DeleteRed, Modifier.pressable(onClick = onUninstall))
    }
}
