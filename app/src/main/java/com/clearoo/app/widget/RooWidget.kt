package com.clearoo.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.clearoo.app.MainActivity
import com.clearoo.app.R
import com.clearoo.app.data.PrefsRepository
import com.clearoo.app.domain.Lines
import com.clearoo.app.domain.MoodRules
import com.clearoo.app.domain.StreakRules
import com.clearoo.app.mascot.MascotPainter
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

/** Home-screen Roo whose face changes with your streak and the time of day. */
class RooWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = PrefsRepository(context)
        val progress = prefs.progress.first()
        val settings = prefs.settings.first()
        val today = LocalDate.now().toEpochDay()
        val mood = MoodRules.moodFor(progress, today, LocalTime.now().hour, settings.dailyGoal)
        val state = WidgetState(
            roo = MascotPainter().render(mood, 240),
            line = Lines.widget(mood),
            streak = StreakRules.currentStreak(progress, today),
            deleted = StreakRules.deletedToday(progress, today),
            goal = settings.dailyGoal,
        )
        provideContent { Content(state) }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val WIDE = DpSize(220.dp, 110.dp)

        suspend fun refresh(context: Context) = RooWidget().updateAll(context)
    }

    private class WidgetState(val roo: Bitmap, val line: String, val streak: Int, val deleted: Int, val goal: Int)

    @Composable
    private fun Content(state: WidgetState) {
        val wide = LocalSize.current.width >= WIDE.width
        val title = TextStyle(color = ColorProvider(Color.White), fontSize = if (wide) 16.sp else 13.sp, fontWeight = FontWeight.Bold)
        val sub = TextStyle(color = ColorProvider(Color(0xFFFFE6CC)), fontSize = if (wide) 13.sp else 11.sp)
        val stats = "🔥 ${state.streak}  ·  ${state.deleted}/${state.goal}"
        Box(
            GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg))
                .cornerRadius(24.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (wide) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(state.roo), "Roo", GlanceModifier.size(88.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    Column {
                        Text(state.line, style = title, maxLines = 2)
                        Spacer(GlanceModifier.height(4.dp))
                        Text(stats, style = sub)
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(ImageProvider(state.roo), "Roo", GlanceModifier.size(64.dp))
                    Text(state.line, style = title, maxLines = 1)
                    Text(stats, style = sub)
                }
            }
        }
    }
}

class RooWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RooWidget()
}
