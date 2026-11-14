package me.senkuro.unofficial.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

enum class Tri { NONE, INCLUDE, EXCLUDE }

/** [popular] > 0 — показывать только первые [popular] значений, остальные — по кнопке «Показать все». */
class FilterGroupDef(val key: String, val title: String, val options: List<Pair<String, String>>, val popular: Int = 0)

/** Статические фильтры (значения и русские названия — из расширения Tachiyomi и страницы каталога сайта). */
object FilterDefs {
    const val GROUP_LABEL = "label"

    /**
     * Статус закладок: API каталога такого фильтра не имеет, поэтому он работает на клиенте — по отметкам
     * закладок аккаунта (AppGraph.marks). Значение — название коллекции закладок.
     */
    const val GROUP_BOOKMARK = "bookmark"

    const val GROUP_SOURCE = "source"
    const val GROUP_COUNTRY = "originCountry"

    /** Группы, которые знает поиск Tachiyomi. «Первоисточник» и «Страна» есть только в запросе сайта `mangas`. */
    val tachiyomiGroups = setOf("type", "format", "status", "translationStatus", "rating")

    /** Группы в порядке страницы каталога сайта (метки и диапазоны глав/года вставляются UI между ними). */
    val groups = listOf(
        FilterGroupDef(
            "type", "Тип тайтла",
            listOf(
                "MANGA" to "Манга", "MANHWA" to "Манхва", "MANHUA" to "Маньхуа",
                "COMICS" to "Комикс", "OEL_MANGA" to "OEL Манга", "RU_MANGA" to "РуМанга",
            ),
        ),
        FilterGroupDef(
            "rating", "Возрастной рейтинг",
            listOf("GENERAL" to "0+", "SENSITIVE" to "12+", "QUESTIONABLE" to "16+", "EXPLICIT" to "18+"),
        ),
        FilterGroupDef(
            "format", "Формат выпуска",
            listOf(
                "DIGEST" to "Сборник", "DOUJINSHI" to "Додзинси", "IN_COLOR" to "В цвете",
                "SINGLE" to "Сингл", "WEB" to "Веб", "WEBTOON" to "Вебтун",
                "YONKOMA" to "Ёнкома", "SHORT" to "Short",
            ),
        ),
        FilterGroupDef(
            "status", "Статус тайтла",
            listOf(
                "ANNOUNCE" to "Анонс", "ONGOING" to "Онгоинг", "FINISHED" to "Выпущено",
                "HIATUS" to "Приостановлено", "CANCELLED" to "Отменено",
            ),
        ),
        FilterGroupDef(
            "translationStatus", "Статус перевода",
            listOf(
                "IN_PROGRESS" to "Переводится", "FINISHED" to "Завершён",
                "FROZEN" to "Заморожен", "ABANDONED" to "Заброшен",
            ),
        ),
        FilterGroupDef(
            GROUP_SOURCE, "Первоисточник",
            listOf(
                "ANIME" to "Аниме", "COMIC" to "Комикс", "DOUJINSHI" to "Додзинси", "GAME" to "Игра",
                "LIGHT_NOVEL" to "Ранобэ", "LIVE_ACTION" to "Лайв-экшн", "MANGA" to "Манга", "NOVEL" to "Новелла",
                "ORIGINAL" to "Оригинальная работа", "OTHER" to "Другое", "PICTURE_BOOK" to "Иллюстрированные книги",
                "VIDEO_GAME" to "Видео-игра", "VISUAL_NOVEL" to "Визуальная новелла", "WEB_NOVEL" to "Веб-новелла",
            ),
            popular = 4,
        ),
        FilterGroupDef(
            GROUP_COUNTRY, "Страна производства",
            listOf(
                "KR" to "Корея", "JP" to "Япония", "CN" to "Китай",
                "US" to "США", "RU" to "Россия", "FR" to "Франция",
            ),
        ),
    )

    /** Корневые группы меток (rootId из fetchTachiyomiSearchFilters). */
    val labelRoots = listOf(
        "TEFCRUw6NQ" to "Темы",
        "TEFCRUw6NA" to "Сеттинг",
        "TEFCRUw6Ng" to "Элементы",
        "TEFCRUw6Mw" to "Черты",
        "TEFCRUw6Nw" to "Демография",
    )

    /** Поля сортировки каталога. Две последние — по датам, для них направление подписывается «от нового к старому». */
    val sortFields = listOf(
        "POPULARITY_SCORE" to "По популярности",
        "SCORE" to "По рейтингу",
        "CHAPTERS" to "По количеству глав",
        "VIEWS" to "По просмотрам",
        "LAST_CHAPTER_AT" to "По дате залива",
        "CREATED_AT" to "По дате создания",
    )

    /** Поля-даты: LAST_CHAPTER_AT — когда у тайтла вышла последняя глава, CREATED_AT — когда тайтл создан на сайте. */
    val dateSortFields = setOf("LAST_CHAPTER_AT", "CREATED_AT")

    /**
     * Сортировка по датам в поиске Tachiyomi не поддерживается (у его enum нет CREATED_AT), поэтому для неё каталог
     * идёт через запрос сайта `mangas` (SenkuroRepository.searchSite). Остальные поля — через Tachiyomi-поиск.
     */
    fun usesSiteQuery(field: String): Boolean = field in dateSortFields || field in siteOnlySortFields

    /** Остальные сортировки сайта, которых нет в поиске Tachiyomi. */
    private val siteOnlySortFields = setOf("CHAPTERS", "VIEWS")

    /** Подпись направления сортировки: для дат «новое/старое», для остальных полей «убывание/возрастание». */
    fun directionTitle(field: String, ascending: Boolean): String = when {
        field in dateSortFields -> if (ascending) "От старого к новому" else "От нового к старому"
        else -> if (ascending) "По возрастанию" else "По убыванию"
    }
}

data class CatalogFilters(
    val orderField: String = "POPULARITY_SCORE",
    val ascending: Boolean = false,
    val states: Map<String, Tri> = emptyMap(),
    /** Диапазоны «Количество глав» и «Год релиза» (null — граница не задана). Есть только в запросе сайта. */
    val chaptersMin: Int? = null,
    val chaptersMax: Int? = null,
    val yearMin: Int? = null,
    val yearMax: Int? = null,
) {
    private fun key(group: String, value: String) = "$group:$value"

    fun state(group: String, value: String): Tri = states[key(group, value)] ?: Tri.NONE

    /** Цикл: нет → включить → исключить → нет. */
    fun toggle(group: String, value: String): CatalogFilters {
        val next = when (state(group, value)) {
            Tri.NONE -> Tri.INCLUDE
            Tri.INCLUDE -> Tri.EXCLUDE
            Tri.EXCLUDE -> Tri.NONE
        }
        val map = states.toMutableMap()
        if (next == Tri.NONE) map.remove(key(group, value)) else map[key(group, value)] = next
        return copy(states = map)
    }

    private val hasChaptersRange: Boolean get() = chaptersMin != null || chaptersMax != null
    private val hasYearRange: Boolean get() = yearMin != null || yearMax != null

    val activeCount: Int get() = states.size + (if (hasChaptersRange) 1 else 0) + (if (hasYearRange) 1 else 0)

    fun cleared() = copy(states = emptyMap(), chaptersMin = null, chaptersMax = null, yearMin = null, yearMax = null)

    /**
     * Нужен ли запрос сайта `mangas` вместо поиска Tachiyomi: сортировка по датам/главам/просмотрам либо фильтр,
     * которого в Tachiyomi нет (первоисточник, страна, диапазоны глав и года).
     */
    val usesSiteQuery: Boolean
        get() = FilterDefs.usesSiteQuery(orderField) || hasChaptersRange || hasYearRange ||
            states.keys.any { it.startsWith("${FilterDefs.GROUP_SOURCE}:") || it.startsWith("${FilterDefs.GROUP_COUNTRY}:") }

    /** Выбран ли хоть один фильтр по статусу закладок (его API не знает, применяется на клиенте). */
    val hasBookmarkFilter: Boolean get() = states.keys.any { it.startsWith("${FilterDefs.GROUP_BOOKMARK}:") }

    /**
     * Подходит ли тайтл с отметкой [mark] (название его коллекции закладок, null — не в закладках) под фильтр
     * по статусу закладок: «включить» оставляет только выбранные коллекции, «исключить» скрывает их.
     */
    fun matchesBookmark(mark: String?): Boolean {
        val prefix = "${FilterDefs.GROUP_BOOKMARK}:"
        var hasInclude = false
        var included = false
        for ((k, v) in states) {
            if (!k.startsWith(prefix)) continue
            val name = k.removePrefix(prefix)
            when (v) {
                Tri.INCLUDE -> { hasInclude = true; if (name == mark) included = true }
                Tri.EXCLUDE -> if (name == mark) return false
                Tri.NONE -> {}
            }
        }
        return !hasInclude || included
    }

    /** `{include: [...], exclude: [...]}` либо null, если по группе ничего не выбрано. */
    fun filterJson(group: String): JsonObject? {
        val prefix = "$group:"
        val include = ArrayList<String>()
        val exclude = ArrayList<String>()
        for ((k, v) in states) {
            if (!k.startsWith(prefix)) continue
            val value = k.removePrefix(prefix)
            if (v == Tri.INCLUDE) include += value else if (v == Tri.EXCLUDE) exclude += value
        }
        if (include.isEmpty() && exclude.isEmpty()) return null
        return buildJsonObject {
            put("include", JsonArray(include.map { JsonPrimitive(it) }))
            put("exclude", JsonArray(exclude.map { JsonPrimitive(it) }))
        }
    }
}
