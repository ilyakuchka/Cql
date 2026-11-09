package me.senkuro.unofficial.debugtools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.SenkuroConfig
import okhttp3.Request

/** Что сайт сейчас шлёт в App-Version и какой clientId у него в конфиге. */
class SiteInfo(val version: String?, val clientId: String?, val notes: List<String>)

object SiteVersion {
    private fun get(url: String): String {
        val req = Request.Builder().url(url).build()
        AppGraph.httpClient.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code} для $url")
            return r.body?.string().orEmpty()
        }
    }

    /** Читает главную страницу сайта, основной скрипт и конфиг сайта и достаёт оттуда версию и clientId. */
    fun fetch(): SiteInfo {
        val notes = mutableListOf<String>()
        val base = SenkuroConfig.BASE_URL
        val html = get("$base/")
        val main = Regex("""/assets/index-[A-Za-z0-9_-]+\.js""").find(html)?.value
        if (main == null) return SiteInfo(null, null, listOf("На главной странице не найден основной скрипт (/assets/index-*.js)"))
        notes += "Основной скрипт: $main"
        val js = get(base + main)
        val version = Regex("""App-Version`\]\s*=\s*`([0-9A-Za-z._-]+)`""").find(js)?.groupValues?.get(1)
            ?: Regex("""["']App-Version["']\s*[\]:=]+\s*[`"']([0-9A-Za-z._-]+)""").find(js)?.groupValues?.get(1)
        if (version == null) notes += "App-Version в основном скрипте не найден (формат кода сайта изменился)"
        var clientId: String? = null
        val cfg = Regex("""website-mode-[A-Za-z0-9_-]+\.js""").find(js)?.value
        if (cfg != null) {
            notes += "Конфиг сайта: $cfg"
            val text = runCatching { get("$base/assets/$cfg") }.getOrNull()
            clientId = text?.let { Regex("""clientId:`(\d+)`""").find(it)?.groupValues?.get(1) }
        } else notes += "Файл website-mode-*.js не найден"
        if (clientId == null) notes += "clientId не найден"
        return SiteInfo(version, clientId, notes)
    }
}

@Composable
fun SiteVersionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<SiteInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf(0) }

    fun load() {
        loading = true; error = null
        scope.launch {
            try { info = withContext(Dispatchers.IO) { SiteVersion.fetch() } } catch (e: Exception) { error = e.message ?: e.javaClass.simpleName } finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text("Версия сайта", style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val i = info
            // applied нужен, чтобы экран перерисовывался после применения/сброса подмены
            val usedVersion = SenkuroConfig.appVersion.also { applied }
            val usedId = SenkuroConfig.appId
            Text("В приложении сейчас", style = MaterialTheme.typography.titleSmall)
            Text("App-Version: $usedVersion" + if (SenkuroConfig.appVersionOverride != null) "  (подмена, в коде ${SenkuroConfig.APP_VERSION})" else "", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Text("App-Id: $usedId" + if (SenkuroConfig.appIdOverride != null) "  (подмена, в коде ${SenkuroConfig.APP_ID})" else "", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Text("На сайте сейчас", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            when {
                loading -> Text("Загружаю…")
                error != null -> Text("Ошибка: $error", color = MaterialTheme.colorScheme.error)
                i != null -> {
                    val vOk = i.version == usedVersion
                    val idOk = i.clientId == usedId
                    Text("App-Version: ${i.version ?: "не найдена"}  " + if (i.version == null) "" else if (vOk) "✓ совпадает" else "✗ отличается", fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        color = if (i.version != null && !vOk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground)
                    Text("clientId (App-Id): ${i.clientId ?: "не найден"}  " + if (i.clientId == null) "" else if (idOk) "✓ совпадает" else "✗ отличается", fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        color = if (i.clientId != null && !idOk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground)
                    SelectionContainer { Text(i.notes.joinToString("\n"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = i.version != null || i.clientId != null, onClick = {
                            i.version?.let { SenkuroConfig.appVersionOverride = it }
                            i.clientId?.let { SenkuroConfig.appIdOverride = it }
                            applied++
                        }) { Text("Применить до перезапуска") }
                        OutlinedButton(onClick = { SenkuroConfig.appVersionOverride = null; SenkuroConfig.appIdOverride = null; applied++ }) { Text("Сбросить") }
                    }
                    OutlinedButton(onClick = {
                        copy(context, "const val APP_ID = \"${i.clientId ?: SenkuroConfig.APP_ID}\"\nconst val APP_VERSION = \"${i.version ?: SenkuroConfig.APP_VERSION}\"")
                    }) { Text("Копировать строки для SenkuroConfig") }
                }
            }
            OutlinedButton(enabled = !loading, onClick = { load() }) { Text("Проверить снова") }
            Text("«Применить» подменяет заголовки только на время работы приложения, чтобы проверить, как отвечает сервер. Чтобы изменить навсегда, обновите константы в SenkuroConfig.kt.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun copy(context: Context, text: String) {
    runCatching { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("config", text)) }
}
