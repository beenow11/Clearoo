package com.clearoo.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.clearoo.app.data.PrefsRepository
import com.clearoo.app.notify.Notifications
import com.clearoo.app.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ClearooApplication : Application(), ImageLoaderFactory, Configuration.Provider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        scope.launch {
            val settings = PrefsRepository(this@ClearooApplication).settings.first()
            if (settings.onboarded) {
                ReminderScheduler.schedule(this@ClearooApplication, settings.reminderHour, settings.reminderMinute, replace = false)
            }
        }
    }

    // Reminders are a nice-to-have: if their database can't be opened (e.g. storage full), carry on without them.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setInitializationExceptionHandler { Log.w("Clearoo", "WorkManager unavailable", it) }
            .build()

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()
}
