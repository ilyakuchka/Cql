package me.senkuro.unofficial.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFE85D00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF3A1300),
    secondary = Color(0xFF77574A),
    secondaryContainer = Color(0xFFFFDBCE),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB68B),
    onPrimary = Color(0xFF552000),
    primaryContainer = Color(0xFF7A3000),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFE7BEAD),
    secondaryContainer = Color(0xFF5C4033),
    background = Color(0xFF1F1B18),
    surface = Color(0xFF1F1B18),
)

@Composable
fun UnofficialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
