package me.senkuro.unofficial.debugtools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Заглушка для release-сборки: отладочных инструментов здесь нет.
 * Настоящая реализация лежит в src/debug и в release-APK не попадает.
 */
object DebugTools {
    @Composable
    fun Screen(onBack: () -> Unit) {
        LaunchedEffect(Unit) { onBack() }
    }

    @Composable
    fun JournalScreen(onBack: () -> Unit) {
        LaunchedEffect(Unit) { onBack() }
    }

    fun installCrashHandler(context: android.content.Context) {}

    @Composable
    fun CrashDialog() {}
}
