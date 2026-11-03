package me.senkuro.unofficial.data

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Журнал ошибок ридера (в памяти, до закрытия приложения): загрузка страниц, запросы, переключение на метод сайта. */
object ReaderErrorLog {
    val entries = mutableStateListOf<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun add(where: String, details: String, error: Throwable? = null) {
        val err = error?.let { "\n  ${it.javaClass.simpleName}: ${it.message.orEmpty().take(500)}" }.orEmpty()
        val line = "[${fmt.format(Date())}] $where\n  $details$err"
        runCatching {
            entries.add(line)
            while (entries.size > 200) entries.removeAt(0)
        }
    }

    fun text(context: String = ""): String =
        (if (context.isNotBlank()) listOf(context) else emptyList<String>()).plus(entries).joinToString("\n\n")

    fun clear() = entries.clear()
}
