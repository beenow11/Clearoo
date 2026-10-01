package com.clearoo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clearoo.app.ui.ClearooRoot
import com.clearoo.app.ui.ClearooViewModel
import com.clearoo.app.ui.theme.ClearooTheme

class MainActivity : ComponentActivity() {
    /** Set when opened from the daily notification: jump straight into swiping. */
    private val openSwipe = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openSwipe.value = intent?.getBooleanExtra(EXTRA_OPEN_SWIPE, false) == true
        setContent {
            ClearooTheme {
                val vm: ClearooViewModel = viewModel()
                ClearooRoot(vm, openSwipe = openSwipe.value, onSwipeOpened = { openSwipe.value = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_SWIPE, false)) openSwipe.value = true
    }

    companion object {
        const val EXTRA_OPEN_SWIPE = "open_swipe"
    }
}
