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
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // Запросы самого service worker (precache) тоже попадают во вкладку «Файлы».
    runCatching {
        android.webkit.ServiceWorkerController.getInstance().setServiceWorkerClient(
            object : android.webkit.ServiceWorkerClient() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
                    FileCapture.intercept(request)
            },
        )
    }

    web.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (!documentStart) view?.evaluateJavascript(CaptureScript.JS, null)
            // Переходы между страницами (редиректы авторизации) тоже попадают в лог.
            url?.let { CaptureLog.navigated(it) }
        }

        // Статические файлы сайта (js, css, json…) скачиваем сами и сохраняем для вкладки «Файлы».
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
            request?.let { FileCapture.intercept(it) }

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

    // HTML страницы «как её видит пользователь»: текущий DOM после работы скриптов (SPA), а не исходник ответа сервера.
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var htmlToSave by remember { mutableStateOf<String?>(null) }
    val saveHtml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        val html = htmlToSave
        htmlToSave = null
        if (uri != null && html != null) scope.launch(Dispatchers.IO) {
            val message = try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray()) }
                "Сохранено: ${html.length / 1024} КБ"
            } catch (e: Exception) {
                "Не удалось сохранить: ${e.message}"
            }
            withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
        }
    }
    fun withPageHtml(block: (String) -> Unit) {
        holder.view.evaluateJavascript("'<!DOCTYPE html>\\n' + document.documentElement.outerHTML") { raw ->
            val html = try {
                org.json.JSONTokener(raw).nextValue() as? String
            } catch (e: Exception) {
                null
            }
            if (html == null) Toast.makeText(context, "Не удалось получить HTML страницы", Toast.LENGTH_LONG).show() else block(html)
        }
    }
    fun pageFileName(): String {
        val host = runCatching { android.net.Uri.parse(holder.view.url).host }.getOrNull().orEmpty().ifBlank { "page" }
        return "$host.html"
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
            FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Файлы (${FileCapture.files.size})") })
            FilterChip(
                selected = CaptureLog.showImplemented,
                onClick = { CaptureLog.showImplemented = !CaptureLog.showImplemented },
                label = { Text(if (CaptureLog.showImplemented) "Реализованные: показаны" else "Реализованные скрыты: ${CaptureLog.hiddenCount()}") },
            )
            if (tab == 0) {
                OutlinedButton(onClick = {
                    withPageHtml { html ->
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("page-html", html))
                            Toast.makeText(context, "HTML скопирован: ${html.length / 1024} КБ", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Не удалось скопировать (слишком большой?) — используйте «Сохранить HTML»", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Копировать HTML") }
                OutlinedButton(onClick = {
                    withPageHtml { html ->
                        htmlToSave = html
                        saveHtml.launch(pageFileName())
                    }
                }) { Text("Сохранить HTML") }
            }
            OutlinedButton(onClick = { copyReport(context) }) { Text("Копировать") }
            OutlinedButton(onClick = { shareReport(context) }) { Text("Поделиться") }
            OutlinedButton(onClick = { if (tab == 2) FileCapture.clear() else CaptureLog.clear() }) { Text("Очистить") }
            // Быстрый переход на домены (не меняет домен приложения, только страницу в браузере).
            // Домены приложения плюс сайты авторизации (SSO) этих доменов (нужны только для перехвата входа).
            val quickLinks = SenkuroConfig.DOMAINS.map { it.title to it.baseUrl } +
                SenkuroConfig.DOMAINS.map { it.ssoHost to it.ssoUrl }
            quickLinks.forEach { (title, url) ->
                OutlinedButton(onClick = {
                    tab = 0
                    holder.view.loadUrl(url)
                }) { Text(title) }
            }
        }

        if (tab == 0) {
            AndroidView(factory = { holder.view }, modifier = Modifier.fillMaxSize())
        } else if (tab == 1) {
            LogList()
        } else {
            FilesTab()
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

private fun formatSize(n: Int): String = when {
    n >= 1024 * 1024 -> "%.1f МБ".format(n / 1048576.0)
    n >= 1024 -> "${n / 1024} КБ"
    else -> "$n Б"
}

/** Вкладка «Файлы»: перехваченные js/css/json и т.п., сохранение по одному, ZIP-архивом и найденный в js GraphQL. */
@Composable
private fun FilesTab() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var found by remember { mutableStateOf<List<GqlScanner.Found>?>(null) }
    var pending by remember { mutableStateOf<CapturedFile?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    fun write(uri: android.net.Uri?, block: (java.io.OutputStream) -> Unit) {
        if (uri == null) return
        scope.launch(Dispatchers.IO) {
            val message = try {
                context.contentResolver.openOutputStream(uri)?.use(block)
                "Сохранено"
            } catch (e: Exception) {
                "Не удалось сохранить: ${e.message}"
            }
            withContext(Dispatchers.Main) { toast(message) }
        }
    }

    fun gqlText(): String? = found?.let { GqlScanner.render(it, CaptureLog.implementedNames().takeIf { _ -> !CaptureLog.showImplemented }.orEmpty()) }

    val saveOne = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val file = pending
        if (file != null) write(uri) { out -> FileCapture.bytesOf(file)?.let(out::write) }
        pending = null
    }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val extra = gqlText()?.let { mapOf("_graphql-from-js.txt" to it) }.orEmpty()
        write(uri) { out -> FileCapture.writeZip(out, extra) }
    }
    val saveSchema = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val message = try {
                val (bytes, source) = FileCapture.schemaForced(SenkuroConfig.API_URL)
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                "Схема сохранена ($source): ${bytes.size / 1024} КБ"
            } catch (e: Exception) {
                "Не удалось: ${e.message}"
            }
            withContext(Dispatchers.Main) { toast(message) }
        }
    }
    val saveGql = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = gqlText()
        if (text != null) write(uri) { out -> out.write(text.toByteArray()) }
    }

    var progress by remember { mutableStateOf<String?>(null) }

    fun downloadManifest() {
        scope.launch(Dispatchers.IO) {
            try {
                val swUrl = FileCapture.swFile()?.url ?: (SenkuroConfig.BASE_URL.trimEnd('/') + "/sw.js")
                if (FileCapture.swFile() == null) FileCapture.fetchDirect(swUrl)
                val sw = FileCapture.swFile()?.let { FileCapture.textOf(it) }
                if (sw == null) {
                    withContext(Dispatchers.Main) { toast("sw.js недоступен") }
                    return@launch
                }
                val urls = FileCapture.manifestUrls(sw, swUrl)
                var done = 0
                val gate = kotlinx.coroutines.sync.Semaphore(6)
                kotlinx.coroutines.coroutineScope {
                    urls.map { u ->
                        async {
                            gate.acquire()
                            try {
                                FileCapture.fetchDirect(u)
                            } finally {
                                gate.release()
                                done++
                                if (done % 10 == 0 || done == urls.size) withContext(Dispatchers.Main) { progress = "$done / ${urls.size}" }
                            }
                        }
                    }.awaitAll()
                }
                withContext(Dispatchers.Main) { progress = null; toast("Готово: загружено js по манифесту ${urls.size}") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { progress = null; toast("Ошибка: ${e.message}") }
            }
        }
    }

    fun downloadEverything() {
        scope.launch(Dispatchers.IO) {
            try {
                var last = 0L
                val n = FileCapture.crawlAll { done, total ->
                    val now = System.currentTimeMillis()
                    if (now - last > 300) { last = now; scope.launch(Dispatchers.Main) { progress = "$done / $total" } }
                }
                withContext(Dispatchers.Main) { progress = null; toast("Готово: загружено новых файлов $n") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { progress = null; toast("Ошибка: ${e.message}") }
            }
        }
    }

    val files = FileCapture.files.toList()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "Файлов: ${files.size}, ${"%.1f".format(FileCapture.totalBytes() / 1048576.0)} МБ. " +
                        "Откройте вкладку «Браузер» и походите по сайту: js, css, json и прочие статические файлы " +
                        "появятся здесь. Файлы сохраняются как есть: json может содержать данные вашего аккаунта.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(enabled = progress == null, onClick = { downloadManifest() }) {
                        Text(progress?.let { "Загрузка $it" } ?: "Загрузить js по манифесту sw.js")
                    }
                    OutlinedButton(enabled = progress == null, onClick = { downloadEverything() }) {
                        Text("Подгрузить все файлы")
                    }
                    OutlinedButton(enabled = files.isNotEmpty(), onClick = {
                        found = GqlScanner.scan(files)
                        toast("Найдено документов: ${found?.size ?: 0}")
                    }) { Text("Найти GraphQL в js") }
                    OutlinedButton(enabled = found != null, onClick = { saveGql.launch("graphql-from-js.txt") }) {
                        Text("Сохранить GraphQL (${found?.size ?: 0})")
                    }
                    OutlinedButton(onClick = { saveSchema.launch("senkuro-schema.json") }) { Text("Схема GraphQL (__schema)") }
                    OutlinedButton(enabled = files.isNotEmpty(), onClick = { saveZip.launch("senkuro-files.zip") }) {
                        Text("Скачать всё (ZIP)")
                    }
                    OutlinedButton(onClick = { FileCapture.clear(); found = null }) { Text("Очистить") }
                }
            }
            HorizontalDivider()
        }
        items(files) { f ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(f.name.substringAfterLast('/'), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${f.mime} · ${formatSize(f.size)}", style = MaterialTheme.typography.labelSmall)
                    Text(f.url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = {
                    pending = f
                    saveOne.launch(f.name.substringAfterLast('/'))
                }) { Text("Скачать") }
            }
            HorizontalDivider()
        }
    }
}
