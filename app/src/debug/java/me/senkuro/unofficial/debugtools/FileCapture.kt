package me.senkuro.unofficial.debugtools

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.compose.runtime.mutableStateListOf
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CapturedFile(
    val url: String,
    val mime: String,
    val size: Int,
    val time: Long,
    val status: Int,
    /** Имя для сохранения: host/путь без недопустимых символов. */
    val name: String,
)

/**
 * Перехват статических файлов сайта (js, css, json, карты, шрифты и т.п.) во встроенном браузере.
 * Файл скачивается самим приложением (shouldInterceptRequest) и отдаётся странице как есть,
 * поэтому содержимое доступно для сохранения. Только debug-сборка.
 * Не трогаем: API-хосты, запросы с Authorization, адреса с секретными параметрами, html-страницы, картинки.
 */
object FileCapture {

    val files = mutableStateListOf<CapturedFile>()

    private val contents = ConcurrentHashMap<String, ByteArray>()
    private val seen = ConcurrentHashMap.newKeySet<String>()
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()

    @Volatile
    private var total = 0L

    private val allowedExt = setOf(
        "js", "mjs", "cjs", "css", "json", "map", "webmanifest", "txt", "xml", "svg",
        "wasm", "woff", "woff2", "ttf", "otf", "ico",
    )
    private val secretQuery = Regex("(^|[?&])(code|state|token|access_token|sig|signature|key|jwt|sid|ticket)=", RegexOption.IGNORE_CASE)

    private const val MAX_FILE = 10 * 1024 * 1024
    private const val MAX_TOTAL = 80L * 1024 * 1024

    private fun ext(url: String): String =
        runCatching { URI(url).path.orEmpty().substringAfterLast('/').substringAfterLast('.', "").lowercase() }.getOrDefault("")

    private fun ownStatic(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host.orEmpty()
        uri.scheme == "https" &&
            Regex("senkuro|shiruho|senkognito", RegexOption.IGNORE_CASE).containsMatchIn(host) &&
            !host.startsWith("api.") && !uri.path.orEmpty().contains("/graphql") &&
            ext(url) in allowedExt && !secretQuery.containsMatchIn(uri.rawQuery?.let { "?$it" }.orEmpty())
    }.getOrDefault(false)

    /** Вызывается из shouldInterceptRequest (фоновый поток). null — пусть WebView грузит сам. */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString().substringBefore('#')
        if (!request.method.equals("GET", true) || !ownStatic(url)) return null
        if (request.requestHeaders.keys.any { it.equals("Authorization", true) }) return null
        if (total >= MAX_TOTAL && !seen.contains(url)) return null
        return try {
            val builder = Request.Builder().url(url)
            request.requestHeaders.forEach { (k, v) ->
                // Accept-Encoding не пробрасываем: OkHttp сам запросит gzip и распакует ответ.
                if (!k.equals("Accept-Encoding", true) && !k.equals("Range", true)) builder.header(k, v)
            }
            CookieManager.getInstance().getCookie(url)?.let { builder.header("Cookie", it) }
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body ?: return null
                if (body.contentLength() > MAX_FILE) return null
                val bytes = body.bytes()
                if (bytes.size > MAX_FILE) return null
                val contentType = response.header("Content-Type").orEmpty()
                val mime = contentType.substringBefore(';').trim().ifBlank { "application/octet-stream" }
                val charset = Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE).find(contentType)?.groupValues?.get(1)
                record(url, mime, bytes, response.code)
                val skip = setOf("content-encoding", "content-length", "transfer-encoding", "content-type", "set-cookie")
                val headers = response.headers.names()
                    .filterNot { it.lowercase() in skip }
                    .associateWith { response.header(it).orEmpty() }
                WebResourceResponse(
                    mime, charset, response.code,
                    response.message.ifBlank { "OK" }, headers, ByteArrayInputStream(bytes),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun record(url: String, mime: String, bytes: ByteArray, status: Int) {
        if (status !in 200..299 || !seen.add(url)) return
        contents[url] = bytes
        total += bytes.size
        val file = CapturedFile(url, mime, bytes.size, System.currentTimeMillis(), status, nameOf(url))
        main.post { files.add(0, file) }
    }

    private fun nameOf(url: String): String {
        val uri = URI(url)
        val path = uri.path.orEmpty().trim('/').ifBlank { "index" }
        val q = uri.rawQuery?.takeIf { it.isNotBlank() }?.let { "_" + Integer.toHexString(it.hashCode()) }.orEmpty()
        val base = (uri.host.orEmpty() + "/" + path).replace(Regex("[^A-Za-z0-9._/-]"), "_")
        val dot = base.lastIndexOf('.')
        return if (q.isEmpty()) base else if (dot > base.lastIndexOf('/')) base.substring(0, dot) + q + base.substring(dot) else base + q
    }

    fun bytesOf(file: CapturedFile): ByteArray? = contents[file.url]

    fun textOf(file: CapturedFile): String? = contents[file.url]?.toString(Charsets.UTF_8)

    fun clear() {
        files.clear()
        contents.clear()
        seen.clear()
        total = 0
    }

    fun totalBytes(): Long = total

    /** Все файлы одним ZIP; extra — дополнительные текстовые файлы (например, найденные GraphQL-запросы). */
    fun writeZip(out: OutputStream, extra: Map<String, String> = emptyMap()) {
        ZipOutputStream(out).use { zip ->
            val used = HashSet<String>()
            files.toList().forEach { f ->
                val bytes = contents[f.url] ?: return@forEach
                var name = f.name
                var i = 1
                while (!used.add(name)) name = f.name + "_" + i++
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            extra.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("_urls.txt"))
            zip.write(files.toList().joinToString("\n") { "${it.status} ${it.mime} ${it.size} ${it.url}" }.toByteArray())
            zip.closeEntry()
        }
    }
}

/** Достаёт GraphQL-документы (query/mutation/subscription/fragment) из текстов js-бандлов. */
object GqlScanner {
    class Found(val kind: String, val name: String, val text: String, val source: String)

    private val head = Regex("""\b(query|mutation|subscription|fragment)\s+([A-Za-z_]\w*)\s*(\([^()]*\))?\s*(on\s+\w+\s*)?\{""")

    fun scan(files: List<CapturedFile>): List<Found> {
        val result = LinkedHashMap<String, Found>()
        files.filter { it.name.endsWith(".js") || it.name.endsWith(".mjs") || it.name.endsWith(".json") || it.name.endsWith(".map") }
            .forEach { f ->
                val text = FileCapture.textOf(f) ?: return@forEach
                head.findAll(text).forEach { m ->
                    val open = m.range.last
                    var depth = 0
                    var end = -1
                    var i = open
                    val limit = minOf(text.length, open + 60_000)
                    while (i < limit) {
                        when (text[i]) {
                            '{' -> depth++
                            '}' -> { depth--; if (depth == 0) { end = i; break } }
                        }
                        i++
                    }
                    if (end < 0) return@forEach
                    val raw = text.substring(m.range.first, end + 1)
                    val clean = raw.replace("\\n", "\n").replace("\\\"", "\"").replace(Regex("\\$\\{[^}]*}"), "").trim()
                    // Отсеиваем случайные совпадения: в документе должны быть поля.
                    if (clean.count { it == '\n' } < 1 && clean.length < 40) return@forEach
                    val key = m.groupValues[1] + " " + m.groupValues[2]
                    val old = result[key]
                    if (old == null || clean.length > old.text.length) result[key] = Found(m.groupValues[1], m.groupValues[2], clean, f.name)
                }
            }
        return result.values.toList()
    }

    fun render(found: List<Found>, hideNames: Set<String>): String = buildString {
        val shown = found.filter { it.kind == "fragment" || it.name !in hideNames }
        appendLine("# GraphQL, найденный в js сайта: ${found.size} всего, показано ${shown.size}")
        appendLine("# (реализованные в приложении операции скрыты; фрагменты показаны все)")
        appendLine()
        shown.forEach {
            appendLine("## ${it.kind} ${it.name}   [${it.source}]")
            appendLine(it.text)
            appendLine()
        }
    }
}
