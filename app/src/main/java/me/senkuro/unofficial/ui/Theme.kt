package me.senkuro.unofficial.ui

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Палитра из дизайна Figma: фон #171717, акцент #DA0037, текст #EDEDED. */
object AppColors {
    val Bg = Color(0xFF171717)
    val Accent = Color(0xFFDA0037)
    val Text = Color(0xFFEDEDED)
}

private val DarkColors = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C0017),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = AppColors.Accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF5C0017),
    onSecondaryContainer = Color(0xFFFFD9DF),
    background = AppColors.Bg,
    onBackground = AppColors.Text,
    surface = AppColors.Bg,
    onSurface = AppColors.Text,
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFF333333),
    surfaceContainerLowest = Color(0xFF121212),
    surfaceContainerLow = Color(0xFF1C1C1C),
    surfaceContainer = Color(0xFF202020),
    surfaceContainerHigh = Color(0xFF262626),
    surfaceContainerHighest = Color(0xFF2C2C2C),
    error = Color(0xFFFF5370),
    errorContainer = Color(0xFF5C1020),
    onErrorContainer = Color(0xFFFFD9DF),
)

/**
 * Шрифт заголовков. В дизайне Oswald Bold; шрифты по сети не подключаем,
 * поэтому берём системный узкий жирный (sans-serif-condensed).
 */
val HeadingFont = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

val HeadingStyle = TextStyle(
    fontFamily = HeadingFont,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    letterSpacing = (-0.33).sp,
)

val HeadingSmallStyle = HeadingStyle.copy(fontSize = 18.sp)

/** Дизайн тёмный, поэтому тема всегда тёмная (независимо от системной). */
@Composable
fun UnofficialTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
