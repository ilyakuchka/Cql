package me.senkuro.unofficial.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** Палитра из UI-кита «Webtoon App UI» (Figma): светлые страницы, тёмная страница тайтла, зелёный акцент. */
object AppColors {
    // Светлые страницы (главная, поиск, библиотека)
    val Bg = Color.White
    val Text = Color(0xFF3C3C3C)
    val Secondary = Color(0xFF8C8C8C)
    val Border = Color(0xFFE0E0E0)
    val Chip = Color(0xFFF8F8F8)
    val Accent = Color(0xFF00DC64)

    // Тёмные страницы (тайтл, главы)
    val DarkBg = Color(0xFF161616)
    val DarkItem = Color(0xF52C2C2C)
    val DarkBorder = Color(0xFF464545)
    val DarkSecondary = Color(0xFFBCBCBC)
}

private val LightColors = lightColorScheme(
    primary = AppColors.Accent,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFFD6FBE4),
    onPrimaryContainer = Color(0xFF00391A),
    secondary = AppColors.Text,
    onSecondary = Color.White,
    secondaryContainer = AppColors.Chip,
    onSecondaryContainer = AppColors.Text,
    background = AppColors.Bg,
    onBackground = AppColors.Text,
    surface = AppColors.Bg,
    onSurface = AppColors.Text,
    surfaceVariant = AppColors.Chip,
    onSurfaceVariant = AppColors.Secondary,
    outline = AppColors.Border,
    outlineVariant = AppColors.Border,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = AppColors.Chip,
    surfaceContainer = AppColors.Chip,
    surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainerHighest = Color(0xFFEAEAEA),
)

private val DarkColors = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF00522A),
    onPrimaryContainer = Color(0xFFB8F5D0),
    secondary = Color.White,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2C2C2C),
    onSecondaryContainer = Color.White,
    background = AppColors.DarkBg,
    onBackground = Color.White,
    surface = AppColors.DarkBg,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = AppColors.DarkSecondary,
    outline = AppColors.DarkBorder,
    outlineVariant = AppColors.DarkBorder,
    surfaceContainerLowest = Color(0xFF101010),
    surfaceContainerLow = Color(0xFF1C1C1C),
    surfaceContainer = Color(0xFF202020),
    surfaceContainerHigh = Color(0xFF262626),
    surfaceContainerHighest = Color(0xFF2C2C2C),
)

/** Заголовок раздела: Arial Bold 15 в ките (у нас системный шрифт). */
val HeadingStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)

val HeadingSmallStyle = HeadingStyle.copy(fontSize = 13.sp)

/**
 * Тема приложения. Страницы светлые, страница тайтла и ридер тёмные (`dark = true`);
 * от режима зависит и цвет значков системных панелей.
 */
@Composable
fun UnofficialTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            findActivity(view.context)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
