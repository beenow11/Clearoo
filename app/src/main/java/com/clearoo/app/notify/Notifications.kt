package com.clearoo.app.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.clearoo.app.MainActivity
import com.clearoo.app.R
import com.clearoo.app.domain.Lines
import com.clearoo.app.domain.Mood
import com.clearoo.app.domain.Outfit
import com.clearoo.app.mascot.MascotPainter
import com.clearoo.app.util.Perms

object Notifications {
    private const val CHANNEL_ID = "daily_reminder"
    private const val REMINDER_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_reminder),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_reminder_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun showReminder(context: Context, mood: Mood, streak: Int, seed: Long, outfit: Outfit) {
        if (!Perms.hasNotifications(context)) return
        val (title, text) = Lines.notification(mood, streak, seed)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_SWIPE, true)
        }
        val contentIntent = PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_roo)
            .setLargeIcon(MascotPainter().render(mood, 192, outfit))
            .setColor(0xFFFF5F6D.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(REMINDER_ID, notification)
    }
}
