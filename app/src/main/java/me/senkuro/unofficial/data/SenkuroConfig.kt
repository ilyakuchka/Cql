package me.senkuro.unofficial.data

import java.net.URI

/** Домен Senkuro: адрес сайта и адрес GraphQL API. */
class Domain(
    val id: String,
    val title: String,
    val baseUrl: String,
    val apiUrl: String,
) {
    val apiHost: String get() = URI(apiUrl).host.orEmpty()
}

/** Константы подключения к Senkuro (значения взяты из приложенных расширений). */
object SenkuroConfig {

    /** Основной домен (API api.senkuro.me, вход через sso.senkuro.net). Переключатель доменов в интерфейсе пока убран. */
    val DOMAINS = listOf(
        Domain(
            id = "me",
            title = "senkuro.me",
            baseUrl = "https://senkuro.me",
            apiUrl = "https://api.senkuro.me/graphql",
        ),
    )

    /** Выбранный сейчас домен (меняется через AppGraph.setDomain). */
    @Volatile
    var current: Domain = DOMAINS.first()

    val BASE_URL: String get() = current.baseUrl
    val API_URL: String get() = current.apiUrl

    fun byId(id: String?): Domain = DOMAINS.firstOrNull { it.id == id } ?: DOMAINS.first()

    /** Хост API любого из доменов (заголовки API ставим только на такие запросы). */
    fun isApiHost(host: String): Boolean = DOMAINS.any { it.apiHost == host }

    const val USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1"

    const val APP_ID = "4026531840100"
    // Версия, которую сайт шлёт в заголовке App-Version (смотрите в debug: «Версия сайта»).
    const val APP_VERSION = "301026"

    /** Подмена на время работы приложения (только для проверки в debug-инструменте «Версия сайта»). */
    @Volatile var appIdOverride: String? = null
    @Volatile var appVersionOverride: String? = null
    val appId: String get() = appIdOverride ?: APP_ID
    val appVersion: String get() = appVersionOverride ?: APP_VERSION

    /** Размер страницы выдачи mangaTachiyomiSearch (offset-пагинация). */
    const val SEARCH_PAGE_SIZE = 10

    /** Размер страницы каталога «как на сайте» (запрос `mangas`, сортировка по датам). */
    const val SITE_PAGE_SIZE = 20
}
