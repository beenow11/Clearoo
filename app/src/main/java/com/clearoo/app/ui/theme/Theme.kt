package com.clearoo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.clearoo.app.R

val Bg = Color(0xFF130F1E)
val Surface1 = Color(0xFF1E182D)
val Surface2 = Color(0xFF2A2140)
val Outline = Color(0xFF3A3054)
val TextHi = Color(0xFFF7F3FF)
val TextLo = Color(0xFFB4A9CC)
val Coral = Color(0xFFFF5F6D)
val Pink = Color(0xFFFF5F9E)
val Peach = Color(0xFFFFB36B)
val Violet = Color(0xFF8E7CFF)
val KeepGreen = Color(0xFF2EE59D)
val DeleteRed = Color(0xFFFF4D6D)
val Flame = Color(0xFFFF8A3D)
val Gold = Color(0xFFFFD84D)
val Cream = Color(0xFFFFE6CC)

val BrandBrush = Brush.linearGradient(listOf(Coral, Pink, Peach))
val KeepBrush = Brush.linearGradient(listOf(Color(0xFF1FD1A0), KeepGreen))
val DeleteBrush = Brush.linearGradient(listOf(DeleteRed, Coral))

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

private fun style(weight: FontWeight, size: Int, line: Int = size + 6) =
    TextStyle(fontFamily = Nunito, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private val ClearooTypography = Typography(
    displayLarge = style(FontWeight.Black, 56),
    displaySmall = style(FontWeight.Black, 34),
    headlineMedium = style(FontWeight.ExtraBold, 26),
    headlineSmall = style(FontWeight.ExtraBold, 22),
    titleLarge = style(FontWeight.ExtraBold, 20),
    titleMedium = style(FontWeight.Bold, 16),
    titleSmall = style(FontWeight.Bold, 14),
    bodyLarge = style(FontWeight.SemiBold, 16),
    bodyMedium = style(FontWeight.SemiBold, 14),
    bodySmall = style(FontWeight.SemiBold, 12),
    labelLarge = style(FontWeight.ExtraBold, 16),
    labelMedium = style(FontWeight.Bold, 13),
    labelSmall = style(FontWeight.Bold, 11),
)

private val ClearooColors = darkColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    secondary = Violet,
    onSecondary = Color.White,
    tertiary = Peach,
    background = Bg,
    onBackground = TextHi,
    surface = Surface1,
    onSurface = TextHi,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextLo,
    outline = Outline,
    error = DeleteRed,
)

@Composable
fun ClearooTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ClearooColors, typography = ClearooTypography, content = content)
}
