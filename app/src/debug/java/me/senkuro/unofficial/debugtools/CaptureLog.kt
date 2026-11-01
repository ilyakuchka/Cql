package me.senkuro.unofficial.debugtools

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CapturedCall(
    val time: Long,
    val operation: String,
    val query: String,
    val variables: String,
    val extensions: String,
    val status: Int?,
    val response: String?,
    val error: String?,
)

/** Журнал перехваченных GraphQL-вызовов. Секретные значения (пароли, токены, cookie) вырезаются при записи. */
object CaptureLog {

    val calls = mutableStateListOf<CapturedCall>()

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
        val calls = runCatching { parse(raw) }.getOrNull().orEmpty()
        if (calls.isEmpty()) return
        main.post { calls.forEach { this.calls.add(0, it) } }
    }

    private fun parse(raw: String): List<CapturedCall> {
        val obj = json.parseToJsonElement(raw).jsonObject
        val body = obj["body"].asText()
        val response = obj["response"].asText()
        val status = (obj["status"] as? JsonPrimitive)?.intOrNull
        val error = obj["error"].asText()

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
            CapturedCall(
                time = now,
                operation = request?.get("operationName").asText()?.ifBlank { null }
                    ?: guessOperation(request?.get("query").asText())
                    ?: "(без имени)",
                query = request?.get("query").asText().orEmpty(),
                variables = request?.get("variables")?.let { pretty.encodeToString(JsonElement.serializer(), redact(it)) }.orEmpty(),
                extensions = request?.get("extensions")?.let { pretty.encodeToString(JsonElement.serializer(), redact(it)) }.orEmpty(),
                status = status,
                response = responseItem?.let { redact(it).toString() } ?: response,
                error = error,
            )
        }
    }

    private fun JsonElement?.asText(): String? =
        (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

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

    /** Текстовый отчёт: уникальные операции (по имени и тексту запроса) с запросом, переменными и ответом. */
    fun report(maxResponse: Int = 6000): String {
        val snapshot = calls.toList().asReversed()
        val groups = snapshot.groupBy { it.operation to it.query }
        return buildString {
            appendLine("# Senkuro: перехваченные GraphQL-операции")
            appendLine("Всего вызовов: ${snapshot.size}, уникальных операций: ${groups.size}")
            appendLine("Пароли, токены и cookie вырезаны (<redacted>).")
            appendLine()
            groups.entries.forEachIndexed { index, (key, list) ->
                val first = list.first()
                appendLine("## ${index + 1}. ${key.first} (вызовов: ${list.size}, статус: ${first.status ?: "—"})")
                if (first.query.isNotBlank()) {
                    appendLine("### Query")
                    appendLine(first.query.trim())
                }
                if (first.extensions.isNotBlank()) {
                    appendLine("### Extensions")
                    appendLine(first.extensions)
                }
                if (first.variables.isNotBlank()) {
                    appendLine("### Variables")
                    appendLine(first.variables)
                }
                val response = first.response ?: first.error
                if (response != null) {
                    appendLine("### Response")
                    appendLine(if (response.length > maxResponse) response.take(maxResponse) + "\n…(обрезано)" else response)
                }
                appendLine()
            }
        }
    }
}
