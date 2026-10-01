package com.clearoo.app.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clearoo.app.domain.Mood
import com.clearoo.app.domain.StreakRules
import com.clearoo.app.mascot.Mascot
import com.clearoo.app.ui.ClearooViewModel
import com.clearoo.app.ui.components.GhostButton
import com.clearoo.app.ui.components.GradientButton
import com.clearoo.app.ui.components.SpeechBubble
import com.clearoo.app.ui.components.gradientTint
import com.clearoo.app.ui.components.pressable
import com.clearoo.app.ui.theme.BrandBrush
import com.clearoo.app.ui.theme.Coral
import com.clearoo.app.ui.theme.DeleteRed
import com.clearoo.app.ui.theme.KeepGreen
import com.clearoo.app.ui.theme.Surface1
import com.clearoo.app.ui.theme.Surface2
import com.clearoo.app.ui.theme.TextHi
import com.clearoo.app.ui.theme.TextLo
import com.clearoo.app.util.Fmt
import com.clearoo.app.util.Perms

private const val STEPS = 4

@Composable
fun OnboardingScreen(vm: ClearooViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var hour by rememberSaveable { mutableIntStateOf(19) }
    var minute by rememberSaveable { mutableIntStateOf(30) }

    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        step = 3
    }
    fun finish() {
        vm.completeOnboarding(hour, minute)
        onDone()
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        finish()
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Dots(step)
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(250)) { -it / 3 } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { current ->
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (current) {
                    0 -> Intro()
                    1 -> HowItWorks()
                    2 -> Page(Mood.HOPEFUL, "Can I peek at your gallery? 👀", "Let Roo see your photos",
                        "Clearoo only reads your photos and videos to show them as cards. Nothing is uploaded — ever.")
                    else -> Reminder(hour, minute) {
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                hour = h
                                minute = m
                            },
                            hour,
                            minute,
                            DateFormat.is24HourFormat(context),
                        ).show()
                    }
                }
            }
        }

        when (step) {
            0 -> GradientButton("Hi Roo! 👋", { step = 1 })
            1 -> GradientButton("Got it", { step = 2 })
            2 -> {
                GradientButton("Allow access", {
                    if (Perms.hasMedia(context)) step = 3 else mediaLauncher.launch(Perms.media)
                })
            }
            else -> {
                GradientButton("Remind me at ${Fmt.time(hour, minute)}", {
                    if (Build.VERSION.SDK_INT >= 33 && !Perms.hasNotifications(context)) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        finish()
                    }
                })
                Spacer(Modifier.height(10.dp))
                GhostButton("Skip for now", { finish() })
            }
        }
    }
}

@Composable
private fun Dots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        repeat(STEPS) { i ->
            Box(
                Modifier
                    .height(8.dp)
                    .width(if (i == step) 24.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (i <= step) Coral else Surface2),
            )
        }
    }
}

@Composable
private fun Intro() {
    SpeechBubble("G'day! I'm Roo 🦘")
    Mascot(Mood.EXCITED, size = 220.dp)
    Spacer(Modifier.height(20.dp))
    Text("Clearoo", style = MaterialTheme.typography.displaySmall, modifier = Modifier.gradientTint(BrandBrush))
    Spacer(Modifier.height(8.dp))
    Text(
        "Your gallery is full of forgotten photos. Let's clear it together — a few swipes a day.",
        style = MaterialTheme.typography.bodyLarge,
        color = TextLo,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun HowItWorks() {
    Mascot(Mood.HAPPY, size = 140.dp)
    Spacer(Modifier.height(16.dp))
    Text("How it works", style = MaterialTheme.typography.headlineMedium, color = TextHi)
    Spacer(Modifier.height(16.dp))
    Rule("👈", "Swipe left to delete", DeleteRed)
    Rule("👉", "Swipe right to keep", KeepGreen)
    Rule("🔥", "Delete ${StreakRules.STREAK_MIN} a day to keep your streak", Coral)
    Rule("♻️", "Deleted items sit in the trash for 30 days, just in case", TextLo)
}

@Composable
private fun Rule(emoji: String, text: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (accent == TextLo) TextHi else accent)
    }
}

@Composable
private fun Page(mood: Mood, bubble: String, title: String, body: String) {
    SpeechBubble(bubble)
    Mascot(mood, size = 200.dp)
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, color = TextHi, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = TextLo, textAlign = TextAlign.Center)
}

@Composable
private fun Reminder(hour: Int, minute: Int, onPickTime: () -> Unit) {
    SpeechBubble("I'll nudge you once a day 🔔")
    Mascot(Mood.LOVE, size = 190.dp)
    Spacer(Modifier.height(20.dp))
    Text("When should Roo remind you?", style = MaterialTheme.typography.headlineMedium, color = TextHi, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Box(
        Modifier
            .pressable(onClick = onPickTime)
            .clip(RoundedCornerShape(22.dp))
            .background(Surface1)
            .padding(horizontal = 28.dp, vertical = 14.dp),
    ) {
        Text(Fmt.time(hour, minute), style = MaterialTheme.typography.displaySmall, color = TextHi)
    }
    Spacer(Modifier.height(8.dp))
    Text("Tap to change · Evenings work best", style = MaterialTheme.typography.bodySmall, color = TextLo)
}
