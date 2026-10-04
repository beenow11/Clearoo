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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clearoo.app.domain.Outfit
import com.clearoo.app.mascot.LocalOutfit
import com.clearoo.app.ui.screens.AlbumScreen
import com.clearoo.app.ui.screens.AlbumsScreen
import com.clearoo.app.ui.screens.CelebrationScreen
import com.clearoo.app.ui.screens.NewAlbumScreen
import com.clearoo.app.ui.screens.HomeScreen
import com.clearoo.app.ui.screens.OnboardingScreen
import com.clearoo.app.ui.screens.ReviewScreen
import com.clearoo.app.ui.screens.SettingsScreen
import com.clearoo.app.ui.screens.SwipeScreen
import com.clearoo.app.ui.screens.WardrobeScreen
import com.clearoo.app.ui.theme.Bg

enum class Screen { Loading, Onboarding, Home, Swipe, Review, Celebrate, Settings, Wardrobe, Albums, NewAlbum, Album }

@Composable
fun ClearooRoot(vm: ClearooViewModel, openSwipe: Boolean, onSwipeOpened: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Loading) }
    var albumId by rememberSaveable { mutableLongStateOf(0L) }
    // Leaving the cards goes back to where they were started from.
    fun swipeParent() = if (vm.deckAlbum != null) Screen.Album else Screen.Home

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

    BackHandler(enabled = screen !in setOf(Screen.Loading, Screen.Onboarding, Screen.Home)) {
        screen = when (screen) {
            Screen.Review -> Screen.Swipe
            Screen.Swipe -> swipeParent()
            Screen.NewAlbum, Screen.Album -> Screen.Albums
            else -> Screen.Home
        }
    }

    CompositionLocalProvider(LocalOutfit provides (settings?.outfit ?: Outfit.CLASSIC)) {
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
                    onWardrobe = { screen = Screen.Wardrobe },
                    onAlbums = { screen = Screen.Albums },
                )
                Screen.Swipe -> SwipeScreen(
                    vm,
                    onBack = { screen = swipeParent() },
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
                    onWardrobe = { screen = Screen.Wardrobe },
                )
                Screen.Settings -> SettingsScreen(vm, onBack = { screen = Screen.Home })
                Screen.Wardrobe -> WardrobeScreen(vm, onBack = { screen = Screen.Home })
                Screen.Albums -> AlbumsScreen(
                    vm,
                    onBack = { screen = Screen.Home },
                    onNew = { screen = Screen.NewAlbum },
                    onOpen = { album ->
                        albumId = album.id
                        screen = Screen.Album
                    },
                )
                Screen.NewAlbum -> NewAlbumScreen(
                    vm,
                    onBack = { screen = Screen.Albums },
                    onCreated = { album ->
                        albumId = album.id
                        screen = Screen.Album
                    },
                )
                Screen.Album -> AlbumScreen(
                    vm,
                    albumId,
                    onBack = { screen = Screen.Albums },
                    onStart = { deck, album ->
                        vm.startDeck(deck, album)
                        screen = Screen.Swipe
                    },
                )
            }
        }
    }
}
