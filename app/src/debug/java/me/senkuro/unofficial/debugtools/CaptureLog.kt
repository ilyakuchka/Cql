package me.senkuro.unofficial.debugtools

import android.os.Handler
import android.os.Looper
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

class CapturedCall(
    val time: Long,
    /** Адрес запроса: значения секретных параметров вырезаны, фрагмент (#...) скрыт. */
    val url: String,
    /** true — GraphQL, false — прочий HTTP-запрос (вход, OAuth) или переход по странице. */
    val graphql: Boolean,
    val operation: String,
    val query: String,
    /** sha256 из extensions.persistedQuery (если сайт шлёт persisted query). */
    val hash: String?,
    val variables: String,
    val variablesJson: JsonElement?,
    val extensions: String,
    val status: Int?,
    val response: String?,
    val error: String?,
    val note: String?,
)

/** Журнал перехваченных вызовов. Секретные значения (пароли, токены, code/state, cookie) вырезаются при записи. */
object CaptureLog {

    val calls = mutableStateListOf<CapturedCall>()

    /** Тексты запросов по sha256 (ловятся при вычислении хеша на странице). */
    private val queryByHash = ConcurrentHashMap<String, String>()

    private val main = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true }
    private val pretty = Json { prettyPrint = true }

    private val secretKey = Regex(
        "^(password|passwd|pass|secret|authorization|cookie|email|phone|.*token.*|.*refresh.*|.*session.*)$",
        RegexOption.IGNORE_CASE,
    )

    /** Ключи, значения которых скрываем в URL и в form-urlencoded телах (OAuth-параметры и т.п.). */
    private val urlSecretKey = Regex(
        "^(code|state|ticket|sig|signature|key|jwt|sid|nonce|.*secret.*|.*password.*)$",
        RegexOption.IGNORE_CASE,
    )

    private fun isSecret(key: String) = secretKey.matches(key) || urlSecretKey.matches(key)

    fun clear() = calls.clear()

    /** Имена GraphQL-операций, которые уже реализованы в приложении (читаются из assets/graphql). */
    @Volatile
    private var implemented: Set<String> = emptySet()

    /** true — показывать и уже реализованные запросы; по умолчанию они скрыты. */
    var showImplemented by mutableStateOf(false)

    fun loadImplemented(context: Context) {
        val names = HashSet<String>()
        val regex = Regex("(?:query|mutation|subscription)\\s+(\\w+)")
        runCatching {
            context.assets.list("graphql").orEmpty().forEach { file ->
                val text = context.assets.open("graphql/$file").bufferedReader().use { it.readText() }
                regex.findAll(text).forEach { names.add(it.groupValues[1]) }
            }
        }
        implemented = names
    }

    fun isImplemented(call: CapturedCall): Boolean = call.graphql && call.operation in implemented

    /** Вызовы для показа: без реализованных, пока не включён переключатель. */
    fun visible(): List<CapturedCall> =
        if (showImplemented) calls.toList() else calls.filterNot(::isImplemented)

    fun hiddenCount(): Int = if (showImplemented) 0 else calls.count(::isImplemented)

    /** Вызывается из потока WebView; запись переносится в главный поток. */
    fun ingest(raw: String) {
        val parsed = runCatching { parse(raw) }.getOrNull().orEmpty()
        if (parsed.isEmpty()) return
        main.post { parsed.forEach { calls.add(0, it) } }
    }

    /** Переход браузера на страницу (например, редиректы авторизации). */
    fun navigated(url: String) {
        val call = CapturedCall(
            time = System.currentTimeMillis(),
            url = safeUrl(url),
            graphql = false,
            operation = "PAGE ${path(url)}",
            query = "",
            hash = null,
            variables = "",
            variablesJson = null,
            extensions = "",
            status = null,
            response = null,
            error = null,
            note = null,
        )
        main.post { calls.add(0, call) }
    }

    /** Текст запроса: из тела вызова либо найденный по хешу. */
    fun queryOf(call: CapturedCall): String =
        call.query.ifBlank { call.hash?.let { queryByHash[it] }.orEmpty() }

    private fun parse(raw: String): List<CapturedCall> {
        val obj = json.parseToJsonElement(raw).jsonObject

        if (obj["kind"].asText() == "hash") {
            val hash = obj["hash"].asText()
            val query = obj["query"].asText()
            if (hash != null && query != null) queryByHash[hash] = query
            return emptyList()
        }

        val rawUrl = obj["url"].asText().orEmpty()
        val method = obj["method"].asText()?.uppercase() ?: "POST"
        val isGql = rawUrl.contains("/graphql")
        val body = obj["body"].asText()
        val response = obj["response"].asText()
        val status = (obj["status"] as? JsonPrimitive)?.intOrNull
        val error = obj["error"].asText()
        val note = obj["note"].asText()

        val bodyElement: JsonElement? = body?.let { b ->
            runCatching { json.parseToJsonElement(b) }.getOrNull()?.takeIf { it is JsonObject || it is JsonArray }
                ?: parseForm(b)
                ?: JsonPrimitive("<тело не JSON: ${b.length} симв., скрыто>")
        }
        val responseJson = response?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }

        val items: List<JsonElement?> = when (bodyElement) {
            is JsonArray -> bodyElement.toList()
            null -> listOf(null)
            else -> listOf(bodyElement)
        }
        val responses: List<JsonElement?> = when {
            bodyElement is JsonArray && responseJson is JsonArray -> responseJson.toList()
            else -> listOf(responseJson)
        }

        val url = safeUrl(rawUrl)
        val now = System.currentTimeMillis()
        return items.mapIndexed { index, item ->
            val request = item as? JsonObject
            val gqlRequest = isGql && request != null &&
                (request.containsKey("query") || request.containsKey("operationName") || request.containsKey("extensions"))
            val variables: JsonElement? = if (gqlRequest) request!!["variables"]?.let { redact(it) } else item?.let { redact(it) }
            val responseItem = responses.getOrNull(index) ?: responses.firstOrNull()

            CapturedCall(
                time = now,
                url = url,
                graphql = isGql,
                operation = when {
                    gqlRequest -> request!!["operationName"].asText()?.ifBlank { null }
                        ?: guessOperation(request["query"].asText())
                        ?: "(без имени)"
                    isGql -> "(без имени)"
                    else -> "$method ${path(rawUrl)}"
                },
                query = if (gqlRequest) request!!["query"].asText().orEmpty() else "",
                hash = if (gqlRequest) hashOf(request) else null,
                variables = variables?.let { pretty.encodeToString(JsonElement.serializer(), it) }.orEmpty(),
                variablesJson = variables,
                extensions = if (gqlRequest) {
                    request!!["extensions"]?.let { pretty.encodeToString(JsonElement.serializer(), redact(it)) }.orEmpty()
                } else {
                    ""
                },
                status = status,
                response = responseItem?.let { redact(it).toString() }
                    ?: response?.let { "<ответ не JSON: ${it.length} симв., скрыт>" },
                error = error,
                note = note,
            )
        }
    }

    private fun hashOf(request: JsonObject?): String? = runCatching {
        request?.get("extensions")?.jsonObject?.get("persistedQuery")?.jsonObject?.get("sha256Hash").asText()
    }.getOrNull()

    private fun JsonElement?.asText(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun guessOperation(query: String?): String? =
        query?.let { Regex("(query|mutation|subscription)\\s+(\\w+)").find(it)?.groupValues?.get(2) }

    private fun path(url: String): String = runCatching {
        val u = URI(url.substringBefore('#'))
        u.host.orEmpty() + u.path.orEmpty()
    }.getOrDefault(url.substringBefore('?').substringBefore('#'))

    private fun decode(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    /** Адрес без секретных значений: параметры из isSecret вырезаются, фрагмент скрывается. */
    private fun safeUrl(url: String): String {
        val hasFragment = url.contains('#')
        val noFragment = url.substringBefore('#')
        val base = noFragment.substringBefore('?')
        val query = noFragment.substringAfter('?', "")
        val params = if (query.isEmpty()) "" else "?" + query.split('&').joinToString("&") { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            "$key=" + if (isSecret(decode(key))) "<redacted>" else value
        }
        return base + params + if (hasFragment) "#<скрыто>" else ""
    }

    /** Тело вида a=b&c=d (form-urlencoded) → объект; секретные значения вырезаются. */
    private fun parseForm(text: String): JsonElement? {
        if (!text.contains('=') || text.any { it == ' ' || it == '\n' || it == '{' }) return null
        val map = LinkedHashMap<String, JsonElement>()
        text.split('&').forEach { pair ->
            val key = decode(pair.substringBefore('='))
            val value = decode(pair.substringAfter('=', ""))
            map[key] = JsonPrimitive(if (isSecret(key)) "<redacted>" else value)
        }
        return JsonObject(map)
    }

    private fun redact(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.mapValues { (key, value) ->
                if (secretKey.matches(key) && value is JsonPrimitive) JsonPrimitive("<redacted>") else redact(value)
            },
        )
        is JsonArray -> JsonArray(element.map { redact(it) })
        else -> element
    }

    /** Собирает все значения скалярных переменных по путям (status.include, orderField, ...). */
    private fun collect(element: JsonElement?, path: String, out: MutableMap<String, MutableSet<String>>) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                collect(value, if (path.isEmpty()) key else "$path.$key", out)
            }
            is JsonArray -> element.forEach { collect(it, "$path[]", out) }
            is JsonPrimitive -> {
                val values = out.getOrPut(path) { LinkedHashSet() }
                if (values.size < MAX_VALUES_PER_PATH) values.add(element.contentOrNull ?: "null")
            }
            null -> Unit
        }
    }

    /**
     * Текстовый отчёт по операциям (группировка по имени и запросу/хешу): текст запроса, ВСЕ значения переменных
     * по всем вызовам, уникальные наборы переменных и пример ответа.
     */
    fun report(maxResponse: Int = 6000, maxVariants: Int = 15): String {
        val snapshot = visible().asReversed()
        val hidden = if (showImplemented) emptyList() else calls.filter(::isImplemented)
        val groups = snapshot.groupBy { Triple(it.operation, it.hash ?: it.query, if (it.graphql) "" else it.url) }
        val text = buildString {
            appendLine("# Senkuro: перехваченные вызовы")
            appendLine("Всего вызовов: ${snapshot.size}, уникальных операций: ${groups.size}")
            appendLine("Пароли, токены, code/state, cookie и email вырезаны (<redacted>).")
            if (hidden.isNotEmpty()) {
                val byName = hidden.groupingBy { it.operation }.eachCount()
                appendLine("Скрыто уже реализованных запросов: ${hidden.size} (${byName.entries.joinToString(", ") { "${it.key} ×${it.value}" }})")
            }
            appendLine()
            groups.entries.forEachIndexed { index, (key, list) ->
                val first = list.first()
                appendLine("## ${index + 1}. ${key.first} (вызовов: ${list.size}, статус: ${first.status ?: "—"})")
                if (first.url.isNotBlank()) appendLine("URL: ${first.url}")
                first.hash?.let { appendLine("sha256: $it") }

                if (first.graphql) {
                    val query = queryOf(first)
                    appendLine("### Query")
                    appendLine(if (query.isNotBlank()) query.trim() else "(текст запроса не перехвачен, есть только хеш)")
                }

                val values = LinkedHashMap<String, MutableSet<String>>()
                list.forEach { collect(it.variablesJson, "", values) }
                if (values.isNotEmpty()) {
                    appendLine("### Значения переменных (по всем вызовам)")
                    values.forEach { (path, set) -> appendLine("$path: ${set.joinToString(", ")}") }
                }

                val variants = list.mapNotNull { it.variablesJson?.toString() }.distinct()
                if (variants.size > 1) {
                    appendLine("### Наборы переменных (уникальных: ${variants.size}, показано ${minOf(variants.size, maxVariants)})")
                    variants.take(maxVariants).forEach { appendLine(it) }
                } else if (variants.size == 1) {
                    appendLine("### Variables")
                    appendLine(first.variables)
                }

                if (first.graphql || first.status != null || first.error != null) {
                    val sample = list.firstOrNull { it.response != null }
                    appendLine("### Response" + if (sample?.response != null) " (пример)" else "")
                    if (sample?.response != null) {
                        val r = sample.response
                        appendLine(if (r.length > maxResponse) r.take(maxResponse) + "\n…(обрезано)" else r)
                    } else {
                        val why = list.firstNotNullOfOrNull { it.error ?: it.note }
                        appendLine("(ответ не получен${why?.let { ": $it" }.orEmpty()})")
                    }
                }
                appendLine()
            }
        }
        return if (text.length > MAX_REPORT) text.take(MAX_REPORT) + "\n…(отчёт обрезан)" else text
    }

    private const val MAX_VALUES_PER_PATH = 60
    private const val MAX_REPORT = 400_000
}
