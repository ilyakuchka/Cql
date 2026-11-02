package me.senkuro.unofficial.data

import kotlinx.serialization.Serializable

@Serializable
data class MangaItem(
    val id: String,
    val slug: String,
    val title: String,
    val cover: String? = null,
    /** Статус и возрастное ограничение (русские названия), если пришли в ответе. */
    val status: String? = null,
    val age: String? = null,
)

data class MangaDetails(
    val item: MangaItem,
    val altNames: List<String>,
    val description: String,
    val type: String?,
    val status: String?,
    val ageRating: String?,
    val formats: List<String>,
    val translationStatus: String?,
    val labels: List<String>,
    val authors: String?,
    val artists: String?,
)

data class Chapter(
    val id: String,
    val slug: String,
    val branchId: String,
    val name: String?,
    val number: String,
    val volume: String,
    val date: String?,
    val teams: List<String>,
) {
    val numberValue: Float get() = number.toFloatOrNull() ?: 0f
    val volumeValue: Float get() = volume.toFloatOrNull() ?: 0f

    val title: String
        get() = buildString {
            if (volume.isNotBlank() && volume != "0") append("Том ").append(volume).append(". ")
            append("Глава ").append(number)
            if (!name.isNullOrBlank()) append(" — ").append(name)
        }
}

data class Branch(val id: String, val label: String, val chapters: List<Chapter>)

data class ChaptersResult(val chapters: List<Chapter>, val message: String?) {
    /** Ветки переводов: самая заполненная — первой. Главы внутри ветки — по возрастанию. */
    val branches: List<Branch> by lazy {
        val comparator = compareBy<Chapter>({ it.volumeValue }, { it.numberValue })
        chapters.groupBy { it.branchId }
            .values
            .sortedByDescending { it.size }
            .mapIndexed { index, list ->
                val teams = list.flatMap { it.teams }.distinct()
                val label = if (teams.isEmpty()) "Ветка ${index + 1}" else teams.take(2).joinToString(", ")
                Branch(list.first().branchId, label, list.sortedWith(comparator))
            }
    }
}

data class HomeData(
    val latest: List<MangaItem>,
    val popular: List<MangaItem>,
    val newTitles: List<MangaItem>,
    val recommendations: List<MangaItem>,
)

data class LabelItem(val slug: String, val title: String, val rootId: String?)

data class BookmarkCollection(val id: String, val name: String, val system: Boolean)

/** Аккаунт на сайте: пользователь и его коллекции закладок (только манга). */
data class Account(
    val userId: String,
    val name: String,
    val avatar: String?,
    val collections: List<BookmarkCollection>,
)

data class BookmarkEntry(val manga: MangaItem, val collectionId: String?, val page: Int?, val number: String?)

data class BookmarksPage(val items: List<BookmarkEntry>, val next: String?)

data class NotificationItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val date: String?,
    val manga: MangaItem?,
)

data class NotificationsPage(val items: List<NotificationItem>, val next: String?)
