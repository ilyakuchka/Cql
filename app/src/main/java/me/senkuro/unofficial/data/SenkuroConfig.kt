package me.senkuro.unofficial.data

/** Константы подключения к Senkuro (значения взяты из приложенных расширений). */
object SenkuroConfig {
    const val BASE_URL = "https://senkuro.me"
    const val API_URL = "https://api.senkuro.me/graphql"
    const val API_HOST = "api.senkuro.me"

    const val USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1"

    const val APP_ID = "4026531840100"
    const val APP_VERSION = "060626"

    /** Размер страницы выдачи mangaTachiyomiSearch (offset-пагинация). */
    const val SEARCH_PAGE_SIZE = 10
}
