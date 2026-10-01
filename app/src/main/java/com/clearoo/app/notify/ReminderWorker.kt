package com.clearoo.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clearoo.app.data.PrefsRepository
import com.clearoo.app.domain.MoodRules
import com.clearoo.app.domain.StreakRules
import com.clearoo.app.widget.RooWidget
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

/** Daily nudge. Stays quiet if today's streak is already secured. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = PrefsRepository(applicationContext)
        val progress = prefs.progress.first()
        val settings = prefs.settings.first()
        val today = LocalDate.now().toEpochDay()
        if (!StreakRules.securedToday(progress, today)) {
            val mood = MoodRules.moodFor(progress, today, LocalTime.now().hour, settings.dailyGoal)
            Notifications.showReminder(applicationContext, mood, StreakRules.currentStreak(progress, today), today, settings.outfit)
        }
        runCatching { RooWidget.refresh(applicationContext) }
        return Result.success()
    }
}
