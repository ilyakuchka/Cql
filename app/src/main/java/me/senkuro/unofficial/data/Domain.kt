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
    val slug: String,
    val name: String,
    val avatar: String?,
    val collections: List<BookmarkCollection>,
    val frame: String? = null,
    /** Премиум на сайте (viewer.permissions.canDisableAdvertisement) — реклама не показывается. */
    val adsDisabled: Boolean = false,
)

data class BookmarkEntry(val manga: MangaItem, val collectionId: String?, val page: Int?, val number: String?)

data class BookmarksPage(val items: List<BookmarkEntry>, val next: String?)

data class NotifChapter(val id: String, val number: String)

data class NotifActor(val name: String, val avatar: String?, val frame: String? = null)

data class NotifReply(val author: NotifActor, val text: String, val at: String?)

data class NotificationItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val date: String?,
    val manga: MangaItem?,
    /** Подпись карточки, как на сайте («Вышли новые главы»). */
    val caption: String = subtitle,
    /** Время события (ISO, lastEventAt) — для «9 часов назад». */
    val at: String? = null,
    val chapters: List<NotifChapter> = emptyList(),
    val type: String? = null,
    /** Заголовок карточек про комментарии («X ответил тебе на комментарий») — вместо названия тайтла. */
    val headline: String? = null,
    val actors: List<NotifActor> = emptyList(),
    /** Текст твоего комментария, на который ответили / который оценили. */
    val quote: String? = null,
    val replies: List<NotifReply> = emptyList(),
    /** Подпись объекта, под которым комментарий («Пост · …», «Глава 5»). */
    val entityTitle: String? = null,
    val postSlug: String? = null,
)

data class NotificationsPage(val items: List<NotificationItem>, val next: String?)

data class Profile(
    val id: String,
    val slug: String,
    val name: String,
    val verified: Boolean,
    val level: Int?,
    val createdAt: String?,
    val lastOnlineAt: String?,
    val avatar: String?,
    val banner: String?,
)

data class AchievementItem(val title: String, val level: Int?, val points: Int?, val done: Boolean, val progress: String?, val cover: String?)

data class CommentItem(
    val id: String,
    val threadId: String,
    val depth: Int,
    val author: String,
    val avatar: String?,
    val text: String,
    val date: String?,
    val up: Int,
    val down: Int,
    val canEdit: Boolean,
    val canDelete: Boolean,
    /** true — вы поставили ▲, false — ▼, null — не голосовали. */
    val vote: Boolean? = null,
)

data class CommentsPage(val items: List<CommentItem>, val next: String?)

data class RelatedManga(val manga: MangaItem, val relation: String?)

data class CharacterItem(val name: String, val role: String?, val cover: String?, val slug: String = "")

data class PeopleItem(val slug: String, val name: String, val avatar: String?, val frame: String? = null)

data class WaifuItem(val slug: String, val name: String, val relation: String, val cover: String?)

data class ProfileActivity(val text: String, val subject: String?, val at: String?)

/** Дополнительные блоки профиля (как на странице пользователя senkuro.me). */
data class ProfileExtra(
    val readChapters: Long?, val watchEpisodes: Long?, val rated: Long?, val comments: Long?,
    val friends: Long?, val subscribers: Long?, val follows: Long?,
    val rank: Long?, val betterPercentile: Double?,
    val gender: String?, val birthday: String?,
    val friendsList: List<PeopleItem>, val followsList: List<PeopleItem>,
    val waifus: List<WaifuItem>,
    val libraryTotal: Long, val libraryByStatus: List<Pair<String, Long>>,
    val libraryPreview: List<MangaItem>,
    val activities: List<ProfileActivity>,
)

/** Оформление профиля: рамка аватара, обои страницы, статус; анимированные картинки (gif/webp) показываются как есть. */
data class ProfileAppearance(
    val frame: String?, val wallpaper: String?, val status: String?, val banner: String?, val avatar: String?,
    /** mp4/webm-варианты анимированных картинок: играются видеоплеером, а не как GIF (как на сайте). */
    val bannerVideo: String? = null, val wallpaperVideo: String? = null, val avatarVideo: String? = null,
)

data class RankEntry(
    val rank: Long, val slug: String, val name: String, val avatar: String?, val frame: String?, val level: Int?,
    val better: Double?, val chapters: Long?, val episodes: Long?,
)

data class RanksPage(val myRank: Long?, val entries: List<RankEntry>)
