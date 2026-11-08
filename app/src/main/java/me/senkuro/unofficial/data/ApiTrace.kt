package me.senkuro.unofficial.data

/** Один выполненный GraphQL-запрос (для журнала запросов в debug-сборке). */
class ApiCall(
    val time: Long,
    val operation: String,
    val query: String,
    val variables: String,
    val httpCode: Int,
    val durationMs: Long,
    val response: String,
    val error: String?,
)

/** Точка подключения журнала: в release-сборке [sink] не задан, и ничего не записывается. */
object ApiTrace {
    @Volatile var sink: ((ApiCall) -> Unit)? = null

    private val secret = Regex("login|auth|password|token|sign|register|recover", RegexOption.IGNORE_CASE)

    /** Переменные запросов авторизации в журнал не попадают. */
    fun safeVariables(operation: String, variables: String) = if (secret.containsMatchIn(operation)) "<скрыто>" else variables
}
