package com.clearoo.app.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clearoo.app.domain.StreakRules
import com.clearoo.app.ui.ClearooViewModel
import com.clearoo.app.ui.components.pressable
import com.clearoo.app.ui.theme.Coral
import com.clearoo.app.ui.theme.KeepGreen
import com.clearoo.app.ui.theme.Surface1
import com.clearoo.app.ui.theme.Surface2
import com.clearoo.app.ui.theme.TextHi
import com.clearoo.app.ui.theme.TextLo
import com.clearoo.app.util.Fmt
import com.clearoo.app.util.Perms

@Composable
fun SettingsScreen(vm: ClearooViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    var notificationsOn by remember { mutableStateOf(Perms.hasNotifications(context)) }
    var canSkipPopup by remember { mutableStateOf(canManageMedia(context)) }
    var keptCleared by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsOn = Perms.hasNotifications(context)
        canSkipPopup = canManageMedia(context)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOn = it
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextHi)
            }
            Text("Settings", style = MaterialTheme.typography.headlineSmall, color = TextHi)
        }

        Section("Daily reminder") {
            SettingRow(
                title = "Reminder time",
                subtitle = "Roo nudges you every day at this time",
                value = Fmt.time(s.reminderHour, s.reminderMinute),
            ) {
                TimePickerDialog(
                    context,
                    { _, h, m -> vm.setReminder(h, m) },
                    s.reminderHour,
                    s.reminderMinute,
                    DateFormat.is24HourFormat(context),
                ).show()
            }
            if (!notificationsOn && Build.VERSION.SDK_INT >= 33) {
                SettingRow("Notifications are off", "Tap to let Roo remind you", "Allow") {
                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            SettingRow("Preview reminder", "See what tonight's nudge looks like", "Send") { vm.previewReminder() }
        }

        Section("Goal") {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Daily goal", style = MaterialTheme.typography.titleMedium, color = TextHi)
                    Text(
                        "${StreakRules.STREAK_MIN} deletes keep the streak alive",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLo,
                    )
                }
                Stepper("−", enabled = s.dailyGoal > StreakRules.STREAK_MIN) { vm.setDailyGoal(s.dailyGoal - 5) }
                Text(
                    "${s.dailyGoal}",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextHi,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                Stepper("+", enabled = s.dailyGoal < ClearooViewModel.MAX_GOAL) { vm.setDailyGoal(s.dailyGoal + 5) }
            }
        }

        Section("Deleting") {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Skip the trash", style = MaterialTheme.typography.titleMedium, color = TextHi)
                    Text(
                        if (s.permanentDelete) "Deleted items are gone for good" else "Items go to trash, recoverable for 30 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLo,
                    )
                }
                Switch(
                    checked = s.permanentDelete,
                    onCheckedChange = vm::setPermanentDelete,
                    colors = SwitchDefaults.colors(checkedTrackColor = Coral),
                )
            }
            if (Build.VERSION.SDK_INT >= 31) {
                SettingRow(
                    title = "Skip the confirmation popup",
                    subtitle = if (canSkipPopup) "Allowed — clearing the bin is one tap" else "Let Clearoo manage media so Android doesn't ask every time",
                    value = if (canSkipPopup) "On ✓" else "Enable",
                ) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${context.packageName}")),
                    )
                }
            }
            SettingRow(
                title = "Show kept photos again",
                subtitle = "Photos you swiped right are skipped. Reset to review them again.",
                value = if (keptCleared) "Done ✓" else "Reset",
            ) {
                vm.resetKept()
                keptCleared = true
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Clearoo works entirely on your phone. Your photos never leave your device.",
            style = MaterialTheme.typography.bodySmall,
            color = TextLo,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun canManageMedia(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(context)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = TextLo,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Surface1),
        content = content,
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextHi)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextLo)
        }
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, color = if (value.endsWith("✓")) KeepGreen else Coral)
    }
}

@Composable
private fun Stepper(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .size(40.dp)
            .pressable(enabled, onClick)
            .clip(CircleShape)
            .background(if (enabled) Surface2 else Surface1),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, color = if (enabled) TextHi else TextLo)
    }
}
