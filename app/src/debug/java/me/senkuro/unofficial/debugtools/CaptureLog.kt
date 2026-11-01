package me.senkuro.unofficial.debugtools

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap

class CapturedCall(
    val time: Long,
    val url: String,
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

/** Журнал перехваченных GraphQL-вызовов. Секретные значения (пароли, токены, cookie) вырезаются при записи. */
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

    fun clear() = calls.clear()

    /** Вызывается из потока WebView; запись переносится в главный поток. */
    fun ingest(raw: String) {
        val parsed = runCatching { parse(raw) }.getOrNull().orEmpty()
        if (parsed.isEmpty()) return
        main.post { parsed.forEach { calls.add(0, it) } }
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

        val body = obj["body"].asText()
        val response = obj["response"].asText()
        val status = (obj["status"] as? JsonPrimitive)?.intOrNull
        val error = obj["error"].asText()
        val note = obj["note"].asText()

        val bodyJson = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
        val responseJson = response?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }

        val requests: List<JsonObject?> = when (bodyJson) {
            is JsonArray -> bodyJson.map { it as? JsonObject }
            is JsonObject -> listOf(bodyJson)
            else -> listOf(null)
        }
        val responses: List<JsonElement?> = when {
            bodyJson is JsonArray && responseJson is JsonArray -> responseJson.toList()
            else -> listOf(responseJson)
        }

        val now = System.currentTimeMillis()
        return requests.mapIndexed { index, request ->
            val responseItem = responses.getOrNull(index) ?: responses.firstOrNull()
            val variables = request?.get("variables")?.let { redact(it) }
            CapturedCall(
                time = now,
                url = obj["url"].asText().orEmpty(),
                operation = request?.get("operationName").asText()?.ifBlank { null }
                    ?: guessOperation(request?.get("query").asText())
                    ?: "(без имени)",
                query = request?.get("query").asText().orEmpty(),
                hash = hashOf(request),
                variables = variables?.let { pretty.encodeToString(JsonElement.serializer(), it) }.orEmpty(),
                variablesJson = variables,
                extensions = request?.get("extensions")?.let { pretty.encodeToString(JsonElement.serializer(), redact(it)) }.orEmpty(),
                status = status,
                response = responseItem?.let { redact(it).toString() } ?: response,
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
        val snapshot = calls.toList().asReversed()
        val groups = snapshot.groupBy { it.operation to (it.hash ?: it.query) }
        val text = buildString {
            appendLine("# Senkuro: перехваченные GraphQL-операции")
            appendLine("Всего вызовов: ${snapshot.size}, уникальных операций: ${groups.size}")
            appendLine("Пароли, токены, cookie и email вырезаны (<redacted>).")
            appendLine()
            groups.entries.forEachIndexed { index, (key, list) ->
                val first = list.first()
                appendLine("## ${index + 1}. ${key.first} (вызовов: ${list.size}, статус: ${first.status ?: "—"})")
                if (first.url.isNotBlank()) appendLine("URL: ${first.url}")
                first.hash?.let { appendLine("sha256: $it") }

                val query = queryOf(first)
                appendLine("### Query")
                appendLine(if (query.isNotBlank()) query.trim() else "(текст запроса не перехвачен, есть только хеш)")

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

                val sample = list.firstOrNull { it.response != null }
                if (sample?.response != null) {
                    appendLine("### Response (пример)")
                    val r = sample.response
                    appendLine(if (r.length > maxResponse) r.take(maxResponse) + "\n…(обрезано)" else r)
                } else {
                    val why = list.firstNotNullOfOrNull { it.error ?: it.note }
                    appendLine("### Response")
                    appendLine("(ответ не получен${why?.let { ": $it" }.orEmpty()})")
                }
                appendLine()
            }
        }
        return if (text.length > MAX_REPORT) text.take(MAX_REPORT) + "\n…(отчёт обрезан)" else text
    }

    private const val MAX_VALUES_PER_PATH = 60
    private const val MAX_REPORT = 400_000
}
