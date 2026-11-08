package me.senkuro.unofficial.debugtools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import java.io.File

/** Отладочные инструменты (только debug-сборка): перехват GraphQL-запросов сайта во встроенном браузере. */
object DebugTools {
    @Composable
    fun Screen(onBack: () -> Unit) = CaptureScreen(onBack)

    @Composable
    fun JournalScreen(onBack: () -> Unit) = ApiJournalScreen(onBack)

    private fun crashFile(context: Context) = File(context.filesDir, "last-crash.txt")

    /** Сохраняет стек последнего падения в файл; при следующем запуске его покажет [CrashDialog]. */
    fun installCrashHandler(context: Context) {
        // Объекты со snapshot-состоянием создаём заранее, вне композиции: иначе фоновый поток WebView
        // (shouldInterceptRequest) мог создать их позже снимка композиции, и чтение падало с IllegalStateException.
        ApiJournal.install()
        FileCapture.files.size
        CaptureLog.calls.size
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { crashFile(app).writeText("thread=${thread.name}\n" + error.stackTraceToString()) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Окно с текстом последнего падения (debug): можно скопировать и закрыть. */
    @Composable
    fun CrashDialog() {
        val context = LocalContext.current
        var text by remember { mutableStateOf(crashFile(context).takeIf { it.exists() }?.readText()) }
        val crash = text ?: return
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Прошлый запуск упал") },
            text = { Text(crash.take(6000), fontSize = 11.sp, modifier = androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("crash", crash))
                        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Копировать") }
            },
            dismissButton = {
                TextButton(onClick = { crashFile(context).delete(); text = null }) { Text("Закрыть") }
            },
        )
    }
}
