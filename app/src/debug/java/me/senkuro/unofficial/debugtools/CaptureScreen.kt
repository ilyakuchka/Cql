@file:OptIn(ExperimentalMaterial3Api::class)

package me.senkuro.unofficial.debugtools

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import me.senkuro.unofficial.data.SenkuroConfig
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Мост JS → Kotlin. Доступен только в debug-сборке. */
internal class CaptureBridge {
    @JavascriptInterface
    fun log(json: String) {
        CaptureLog.ingest(json)
    }
}

private class WebHolder(val view: WebView, val usesDocumentStart: Boolean)

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: Context, onHistory: (Boolean) -> Unit): WebHolder {
    val web = WebView(context)
    web.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(web, true)
    }
    web.addJavascriptInterface(CaptureBridge(), "SenkuroDebug")

    val documentStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    if (documentStart) {
        WebViewCompat.addDocumentStartJavaScript(web, CaptureScript.JS, setOf("*"))
    }

    web.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (!documentStart) view?.evaluateJavascript(CaptureScript.JS, null)
            // Переходы между страницами (редиректы авторизации) тоже попадают в лог.
            url?.let { CaptureLog.navigated(it) }
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            onHistory(view?.canGoBack() == true)
        }
    }
    web.loadUrl(SenkuroConfig.BASE_URL)
    return WebHolder(web, documentStart)
}

@Composable
fun CaptureScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    val holder = remember {
        CaptureLog.loadImplemented(context)
        createWebView(context) { canGoBack = it }
    }

    DisposableEffect(holder) {
        onDispose {
            holder.view.stopLoading()
            holder.view.destroy()
        }
    }

    BackHandler {
        if (tab == 0 && canGoBack) holder.view.goBack() else onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Перехват запросов (debug)", style = MaterialTheme.typography.titleMedium)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Браузер") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Лог (${CaptureLog.visible().size})") })
            FilterChip(
                selected = CaptureLog.showImplemented,
                onClick = { CaptureLog.showImplemented = !CaptureLog.showImplemented },
                label = { Text(if (CaptureLog.showImplemented) "Реализованные: показаны" else "Реализованные скрыты: ${CaptureLog.hiddenCount()}") },
            )
            OutlinedButton(onClick = { copyReport(context) }) { Text("Копировать") }
            OutlinedButton(onClick = { shareReport(context) }) { Text("Поделиться") }
            OutlinedButton(onClick = { CaptureLog.clear() }) { Text("Очистить") }
            // Быстрый переход на домены (не меняет домен приложения, только страницу в браузере).
            // Домены приложения плюс сайт авторизации sso.senkuro.net (нужен только для перехвата входа).
            val quickLinks = SenkuroConfig.DOMAINS.map { it.title to it.baseUrl } +
                ("sso.senkuro.net" to "https://sso.senkuro.net")
            quickLinks.forEach { (title, url) ->
                OutlinedButton(onClick = {
                    tab = 0
                    holder.view.loadUrl(url)
                }) { Text(title) }
            }
        }

        if (tab == 0) {
            AndroidView(factory = { holder.view }, modifier = Modifier.fillMaxSize())
        } else {
            LogList()
        }
    }
}

@Composable
private fun LogList() {
    val calls = CaptureLog.visible()
    val hidden = CaptureLog.hiddenCount()
    if (calls.isEmpty()) {
        Text(
            (if (hidden > 0) "Скрыто уже реализованных запросов: $hidden. Новых пока нет. " else "") + "Пока пусто. Откройте вкладку «Браузер», войдите в аккаунт и пройдитесь по разделам сайта (закладки, история, профиль).",
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (hidden > 0) {
            item {
                Text(
                    "Скрыто уже реализованных запросов: $hidden",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
            }
        }
        items(calls) { call ->
            var open by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    "${call.operation} · ${call.status ?: call.error ?: "—"}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    DateFormat.getTimeInstance().format(Date(call.time)),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (open) {
                    if (call.variables.isNotBlank()) Block("Variables", call.variables)
                    val query = CaptureLog.queryOf(call)
                    Block("Query", if (query.isNotBlank()) query else "(текст не перехвачен, хеш: ${call.hash ?: "—"})")
                    (call.response ?: call.error)?.let { Block("Response", it.take(4000)) }
                } else if (call.variables.isNotBlank()) {
                    Text(
                        call.variables.replace(Regex("\\s+"), " "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Block(title: String, text: String) {
    Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Text(text, style = MaterialTheme.typography.bodySmall)
}

private fun copyReport(context: Context) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("senkuro-graphql", CaptureLog.report()))
        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Не удалось скопировать — используйте «Поделиться»", Toast.LENGTH_LONG).show()
    }
}

private fun shareReport(context: Context) {
    try {
        val dir = File(context.cacheDir, "debug").apply { mkdirs() }
        val file = File(dir, "senkuro-graphql.txt")
        file.writeText(CaptureLog.report())
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.debugfiles", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться логом").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "Не удалось поделиться: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
