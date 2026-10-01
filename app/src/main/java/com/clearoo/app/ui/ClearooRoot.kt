package com.clearoo.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clearoo.app.ui.screens.CelebrationScreen
import com.clearoo.app.ui.screens.HomeScreen
import com.clearoo.app.ui.screens.OnboardingScreen
import com.clearoo.app.ui.screens.ReviewScreen
import com.clearoo.app.ui.screens.SettingsScreen
import com.clearoo.app.ui.screens.SwipeScreen
import com.clearoo.app.ui.theme.Bg

enum class Screen { Loading, Onboarding, Home, Swipe, Review, Celebrate, Settings }

@Composable
fun ClearooRoot(vm: ClearooViewModel, openSwipe: Boolean, onSwipeOpened: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Loading) }

    LaunchedEffect(settings) {
        val s = settings ?: return@LaunchedEffect
        if (screen == Screen.Loading) screen = if (s.onboarded) Screen.Home else Screen.Onboarding
    }
    LaunchedEffect(openSwipe, screen) {
        if (openSwipe && screen == Screen.Home) {
            screen = Screen.Swipe
            onSwipeOpened()
        }
    }

    BackHandler(enabled = screen in setOf(Screen.Swipe, Screen.Review, Screen.Celebrate, Screen.Settings)) {
        screen = if (screen == Screen.Review) Screen.Swipe else Screen.Home
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.96f)) togetherWith fadeOut(tween(180))
        },
        modifier = Modifier.fillMaxSize().background(Bg),
        label = "screens",
    ) { current ->
        when (current) {
            Screen.Loading -> Box(Modifier.fillMaxSize().background(Bg))
            Screen.Onboarding -> OnboardingScreen(vm, onDone = { screen = Screen.Home })
            Screen.Home -> HomeScreen(
                vm,
                onStart = { deck ->
                    vm.startDeck(deck)
                    screen = Screen.Swipe
                },
                onSettings = { screen = Screen.Settings },
            )
            Screen.Swipe -> SwipeScreen(
                vm,
                onBack = { screen = Screen.Home },
                onOpenBin = { screen = Screen.Review },
            )
            Screen.Review -> ReviewScreen(
                vm,
                onBack = { screen = Screen.Swipe },
                onDeleted = { screen = Screen.Celebrate },
            )
            Screen.Celebrate -> CelebrationScreen(
                vm,
                onKeepGoing = { screen = Screen.Swipe },
                onDone = { screen = Screen.Home },
            )
            Screen.Settings -> SettingsScreen(vm, onBack = { screen = Screen.Home })
        }
    }
}
