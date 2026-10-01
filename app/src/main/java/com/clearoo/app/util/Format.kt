package com.clearoo.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object Fmt {
    fun bytes(b: Long): String = bytesParts(b).let { (n, unit) -> "$n $unit" }

    /** "173.5" to "MB": lets tight layouts put the unit on its own line. */
    fun bytesParts(b: Long): Pair<String, String> {
        val kb = 1024.0
        return when {
            b < kb -> "$b" to "B"
            b < kb * kb -> String.format(Locale.getDefault(), "%.0f", b / kb) to "KB"
            b < kb * kb * kb -> String.format(Locale.getDefault(), "%.1f", b / (kb * kb)) to "MB"
            else -> String.format(Locale.getDefault(), "%.2f", b / (kb * kb * kb)) to "GB"
        }
    }

    fun age(millis: Long): String {
        if (millis <= 0) return "Unknown date"
        val then = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        val days = ChronoUnit.DAYS.between(then, LocalDate.now())
        return when {
            days <= 0 -> "Today"
            days == 1L -> "Yesterday"
            days < 7 -> "$days days ago"
            days < 30 -> plural(days / 7, "week")
            days < 365 -> plural(days / 30, "month")
            else -> plural(days / 365, "year")
        }
    }

    fun date(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy"))

    fun duration(ms: Long): String {
        val total = ms / 1000
        return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
    }

    fun time(hour: Int, minute: Int): String =
        LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    private fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit ago" else "$n ${unit}s ago"
}
