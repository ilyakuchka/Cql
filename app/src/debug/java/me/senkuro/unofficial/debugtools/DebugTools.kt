package me.senkuro.unofficial.debugtools

import androidx.compose.runtime.Composable

/** Отладочные инструменты (только debug-сборка): перехват GraphQL-запросов сайта во встроенном браузере. */
object DebugTools {
    @Composable
    fun Screen(onBack: () -> Unit) = CaptureScreen(onBack)
}
