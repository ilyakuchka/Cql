package me.senkuro.unofficial.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

enum class Tri { NONE, INCLUDE, EXCLUDE }

class FilterGroupDef(val key: String, val title: String, val options: List<Pair<String, String>>)

/** Статические фильтры (значения и русские названия — из расширения Tachiyomi). */
object FilterDefs {
    const val GROUP_LABEL = "label"

    /**
     * Статус закладок: API каталога такого фильтра не имеет, поэтому он работает на клиенте — по отметкам
     * закладок аккаунта (AppGraph.marks). Значение — название коллекции закладок.
     */
    const val GROUP_BOOKMARK = "bookmark"

    val groups = listOf(
        FilterGroupDef(
            "type", "Тип",
            listOf(
                "MANGA" to "Манга", "MANHWA" to "Манхва", "MANHUA" to "Маньхуа",
                "COMICS" to "Комикс", "OEL_MANGA" to "OEL Манга", "RU_MANGA" to "РуМанга",
            ),
        ),
        FilterGroupDef(
            "format", "Формат",
            listOf(
                "DIGEST" to "Сборник", "DOUJINSHI" to "Додзинси", "IN_COLOR" to "В цвете",
                "SINGLE" to "Сингл", "WEB" to "Веб", "WEBTOON" to "Вебтун",
                "YONKOMA" to "Ёнкома", "SHORT" to "Short",
            ),
        ),
        FilterGroupDef(
            "status", "Статус",
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
            "rating", "Возраст",
            listOf("GENERAL" to "0+", "SENSITIVE" to "12+", "QUESTIONABLE" to "16+", "EXPLICIT" to "18+"),
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
        "LAST_CHAPTER_AT" to "По дате залива",
        "CREATED_AT" to "По дате создания",
    )

    /** Поля-даты: LAST_CHAPTER_AT — когда у тайтла вышла последняя глава, CREATED_AT — когда тайтл создан на сайте. */
    val dateSortFields = setOf("LAST_CHAPTER_AT", "CREATED_AT")

    /**
     * Сортировка по датам в поиске Tachiyomi не поддерживается (у его enum нет CREATED_AT), поэтому для неё каталог
     * идёт через запрос сайта `mangas` (SenkuroRepository.searchSite). Остальные поля — через Tachiyomi-поиск.
     */
    fun usesSiteQuery(field: String): Boolean = field in dateSortFields

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

    val activeCount: Int get() = states.size

    fun cleared() = copy(states = emptyMap())

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
