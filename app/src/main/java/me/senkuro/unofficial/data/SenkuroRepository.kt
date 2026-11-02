package me.senkuro.unofficial.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put

class SenkuroRepository(private val api: SenkuroApi) {

    private val chaptersCache = HashMap<String, ChaptersResult>()
    private val detailsCache = HashMap<String, MangaDetails>()
    private var labelsCache: List<LabelItem>? = null

    /** Сбрасывает кэши (нужно при смене домена: идентификаторы и главы могут отличаться). */
    fun clearCaches() {
        synchronized(chaptersCache) { chaptersCache.clear() }
        synchronized(detailsCache) { detailsCache.clear() }
        labelsCache = null
    }

    // ---------------------------------------------------------------- Каталог

    suspend fun search(query: String?, filters: CatalogFilters, offset: Int): List<MangaItem> {
        val variables = buildJsonObject {
            if (!query.isNullOrBlank()) put("query", query.trim())
            for (group in FilterDefs.groups.map { it.key } + FilterDefs.GROUP_LABEL) {
                filters.filterJson(group)?.let { put(group, it) }
            }
            put(
                "orderBy",
                buildJsonObject {
                    put("direction", if (filters.ascending) "ASC" else "DESC")
                    put("field", filters.orderField)
                },
            )
            put("offset", offset)
        }
        val response = api.execute(
            "searchTachiyomiManga", "searchTachiyomiManga", variables, SearchResponse.serializer(),
        )
        return response.mangaTachiyomiSearch.mangas.mapNotNull { it.toItem() }
    }

    suspend fun labels(): List<LabelItem> {
        labelsCache?.let { return it }
        val response = api.execute(
            "fetchTachiyomiSearchFilters", "fetchTachiyomiSearchFilters", JsonObject(emptyMap()),
            FiltersResponse.serializer(),
        )
        val labels = response.mangaTachiyomiSearchFilters.labels
            .map { LabelItem(it.slug, it.titles.localized() ?: it.slug, it.rootId) }
            .sortedBy { it.title }
        labelsCache = labels
        return labels
    }

    // ----------------------------------------------------------------- Главная

    suspend fun home(): HomeData = coroutineScope {
        val none = JsonObject(emptyMap())
        val latest = async {
            runCatching {
                api.execute("HomeLatest", "homeLatest", none, MangasResponse.serializer())
                    .mangas.edges.mapNotNull { it.node.toItem() }
            }
        }
        val popular = async {
            runCatching {
                api.execute("HomePopular", "homePopular", none, PopularResponse.serializer())
                    .mangaPopularByPeriod.mapNotNull { it.toItem() }
            }
        }
        val new = async {
            runCatching {
                api.execute("HomeNew", "homeNew", none, MangasResponse.serializer())
                    .mangas.edges.mapNotNull { it.node.toItem() }
            }
        }
        val recommendations = async {
            runCatching {
                api.execute(
                    "HomeRecommendations", "homeRecommendations", none, RecommendationsResponse.serializer(),
                ).mangaRecommendations.mapNotNull { it.toItem() }
            }
        }
        val results = listOf(latest.await(), popular.await(), new.await(), recommendations.await())
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        HomeData(
            latest = results[0].getOrDefault(emptyList()),
            popular = results[1].getOrDefault(emptyList()),
            newTitles = results[2].getOrDefault(emptyList()),
            recommendations = results[3].getOrDefault(emptyList()),
        )
    }

    // ------------------------------------------------------------ Тайтл и главы

    suspend fun details(mangaId: String): MangaDetails {
        synchronized(detailsCache) { detailsCache[mangaId] }?.let { return it }
        val response = api.execute(
            "fetchTachiyomiManga", "fetchTachiyomiManga", mangaVars(mangaId), InfoResponse.serializer(),
        )
        val dto = response.mangaTachiyomiInfo ?: throw ApiException("Тайтл не найден")
        val details = dto.toDetails()
        synchronized(detailsCache) { detailsCache[mangaId] = details }
        return details
    }

    suspend fun chapters(mangaId: String, force: Boolean = false): ChaptersResult {
        if (!force) synchronized(chaptersCache) { chaptersCache[mangaId] }?.let { return it }
        val response = api.execute(
            "fetchTachiyomiChapters", "fetchTachiyomiChapters", mangaVars(mangaId),
            ChaptersResponse.serializer(),
        )
        val dto = response.mangaTachiyomiChapters
        val teams = dto.teams.associate { it.id to it.name }
        val chapters = dto.chapters.map { c ->
            Chapter(
                id = c.id,
                slug = c.slug,
                branchId = c.branchId,
                name = c.name,
                number = c.number,
                volume = c.volume,
                date = (c.updatedAt ?: c.createdAt)?.take(10),
                teams = c.teamIds.mapNotNull { teams[it] },
            )
        }
        val result = ChaptersResult(chapters, dto.message)
        synchronized(chaptersCache) { chaptersCache[mangaId] = result }
        return result
    }

    // ------------------------------------------------------------------ Ридер

    /** Сначала tachiyomi-запрос страниц, при неудаче — запрос ридера сайта по slug главы. */
    suspend fun pages(mangaId: String, chapter: Chapter): List<String> {
        val primary = runCatching {
            val vars = buildJsonObject {
                put("mangaId", mangaId)
                put("chapterId", chapter.id)
            }
            api.execute(
                "fetchTachiyomiChapterPages", "fetchTachiyomiChapterPages", vars,
                PagesResponse.serializer(),
            ).mangaTachiyomiChapterPages.pages.map { it.url }.filter { it.isNotBlank() }
        }
        primary.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        val vars = buildJsonObject {
            put("slug", chapter.slug)
            put("cdn_quality", "red")
        }
        val fallback = runCatching {
            api.execute("Reader", "readerChapter", vars, ReaderResponse.serializer())
                .mangaChapter?.pages?.mapNotNull { it.image?.original?.url }?.filter { it.isNotBlank() }
                .orEmpty()
        }
        fallback.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        throw (primary.exceptionOrNull() ?: fallback.exceptionOrNull() ?: ApiException("В главе нет страниц"))
    }

    // ------------------------------------------------------- Аккаунт и закладки сайта

    /** null — токен не принят (аноним). */
    suspend fun viewer(): Account? {
        val viewer = api.execute("fetchViewer", "fetchViewer", buildJsonObject {}, ViewerResponse.serializer()).viewer
        val user = viewer?.user ?: return null
        return Account(
            userId = user.id,
            name = user.name.ifBlank { user.slug },
            avatar = user.avatar?.original?.url,
            collections = viewer.userBookmarkCollections
                .filter { it.entityType?.equals("MANGA", true) != false }
                .map { c ->
                    BookmarkCollection(
                        id = c.id,
                        name = c.preference?.name?.takeIf { it.isNotBlank() } ?: c.name.ifBlank { c.slug },
                        system = c.system,
                    )
                },
        )
    }

    suspend fun bookmarks(userId: String, collectionId: String?, after: String?): BookmarksPage {
        val vars = buildJsonObject {
            put("userId", userId)
            put("subjectType", "MANGA")
            if (after != null) put("after", after)
            if (collectionId != null) put("collectionId", collectionId)
            put("orderBy", buildJsonObject {
                put("field", "UPDATED_AT")
                put("direction", "DESC")
            })
        }
        val conn = api.execute("fetchUserBookmarks", "fetchUserBookmarks", vars, BookmarksResponse.serializer()).userBookmarks
        val items = conn.edges.mapNotNull { edge ->
            val node = edge.node
            val manga = node.entity?.toItem() ?: return@mapNotNull null
            BookmarkEntry(manga, node.collectionId, node.page, node.numberText())
        }
        return BookmarksPage(items, conn.pageInfo.endCursor.takeIf { conn.pageInfo.hasNextPage })
    }

    /** Коллекция, в которой лежит тайтл, или null. */
    suspend fun bookmarkOf(mangaId: String): String? =
        api.execute(
            "fetchBookmarkable", "fetchBookmarkable",
            buildJsonObject { put("subjectId", mangaId) },
            BookmarkableResponse.serializer(),
        ).bookmarkable?.collectionId

    suspend fun setBookmark(mangaId: String, collectionId: String) {
        val vars = buildJsonObject {
            put("input", buildJsonObject {
                put("collectionId", collectionId)
                put("manual", true)
                put("subjectId", mangaId)
            })
        }
        val r = api.execute("updateUserBookmark", "updateUserBookmark", vars, MutationResponse.serializer())
        if (r.updateUserBookmark?.success != true) throw ApiException("Не удалось сохранить закладку")
    }

    suspend fun removeBookmark(mangaId: String) {
        val vars = buildJsonObject { put("input", buildJsonObject { put("subjectId", mangaId) }) }
        val r = api.execute("deleteUserBookmark", "deleteUserBookmark", vars, MutationResponse.serializer())
        if (r.deleteUserBookmark?.success != true) throw ApiException("Не удалось удалить закладку")
    }

    private fun BookmarkNodeDto.numberText(): String? =
        (number as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    // ---------------------------------------------------------------- Уведомления

    suspend fun unreadNotifications(): Int =
        (api.execute(
            "fetchViewerUnreadNotifications", "fetchViewerUnreadNotifications",
            buildJsonObject {}, UnreadResponse.serializer(),
        ).viewerUnreadNotifications as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0

    suspend fun notifications(read: Boolean, after: String?): NotificationsPage {
        val vars = buildJsonObject {
            put("first", 10)
            put("isRead", read)
            if (after != null) put("after", after)
        }
        val conn = api.execute("fetchNotificationFeeds", "fetchNotificationFeeds", vars, NotifResponse.serializer())
            .notificationFeeds
        return NotificationsPage(
            items = conn.edges.map { it.node.toItem() },
            next = conn.pageInfo.endCursor.takeIf { conn.pageInfo.hasNextPage },
        )
    }

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun NotifFeedDto.toItem(): NotificationItem {
        val nodes = listOfNotNull(notifiable, context)
        val mangaNode = nodes.firstOrNull { it.typename == "Manga" }
        val manga = mangaNode?.let { n ->
            n.id?.let { MangaItem(it, n.slug, pickTitle(n.titles, n.originalName, n.slug), n.cover?.original?.url) }
        }
        val chapterNode = nodes.firstOrNull { it.typename == "MangaChapter" }
        val user = nodes.firstOrNull { it.typename == "User" }?.name
        val post = nodes.firstOrNull { it.typename == "Post" }?.title
        val numbers = payload?.chapters.orEmpty().mapNotNull { it.number.text() }
        val label = type.orEmpty().lowercase().replace('_', ' ')

        val subtitle = when {
            numbers.isNotEmpty() -> "Новые главы: " + numbers.take(4).joinToString(", ") + if (numbers.size > 4) "…" else ""
            chapterNode != null -> "Глава " + chapterNode.number.text().orEmpty()
            else -> label + if ((eventCount ?: 0) > 1) " ×$eventCount" else ""
        }
        return NotificationItem(
            id = id,
            title = manga?.title ?: post ?: user ?: label.ifBlank { "Уведомление" },
            subtitle = subtitle,
            date = lastEventAt?.take(10),
            manga = manga,
        )
    }

    // ---------------------------------------------------------------- Маппинг

    private fun mangaVars(mangaId: String) = buildJsonObject { put("mangaId", mangaId) }

    private fun List<Title>.localized(): String? =
        firstOrNull { it.lang.equals("RU", true) }?.content
            ?: firstOrNull { it.lang.equals("EN", true) }?.content
            ?: firstOrNull()?.content

    private fun pickTitle(titles: List<Title>?, original: Title?, slug: String): String =
        titles?.localized() ?: original?.content ?: slug

    private fun MangaCardDto.toItem(): MangaItem? {
        val id = id ?: return null
        return MangaItem(
            id = id,
            slug = slug,
            title = pickTitle(titles, originalName, slug),
            cover = cover?.original?.url,
            status = statusName(status),
            age = ageName(rating),
        )
    }

    private fun MangaInfoDto.toDetails(): MangaDetails {
        val authorRoles = setOf("STORY", "STORY_AND_ART", "ORIGINAL_CREATOR")
        val artistRoles = setOf("ART", "STORY_AND_ART")
        fun names(roles: Set<String>) = mainStaff.orEmpty()
            .filter { s -> s.roles.any { it in roles } }
            .joinToString { it.person.name }
            .ifBlank { null }

        return MangaDetails(
            item = MangaItem(
                id = id,
                slug = slug,
                title = pickTitle(titles, originalName, slug),
                cover = cover?.original?.url,
                status = statusName(status),
                age = ageName(rating),
            ),
            altNames = alternativeNames.orEmpty().map { it.content }.filter { it.isNotBlank() }.distinct(),
            description = localizations.orEmpty()
                .firstOrNull { it.lang.equals("RU", true) }?.description.orEmpty()
                .ifBlank { localizations.orEmpty().firstNotNullOfOrNull { it.description }.orEmpty() },
            type = typeName(type),
            status = statusName(status),
            ageRating = ageName(rating),
            formats = formats.orEmpty().mapNotNull { formatName(it) },
            translationStatus = translationName(translationStatus),
            labels = labels.orEmpty().mapNotNull { it.titles.localized()?.ifBlank { null } },
            authors = names(authorRoles),
            artists = names(artistRoles),
        )
    }

    private fun typeName(v: String?) = when (v) {
        "MANGA" -> "Манга"; "MANHWA" -> "Манхва"; "MANHUA" -> "Маньхуа"; "COMICS" -> "Комикс"
        "OEL_MANGA" -> "OEL Манга"; "RU_MANGA" -> "РуМанга"; else -> null
    }

    private fun statusName(v: String?) = when (v) {
        "ANNOUNCE" -> "Анонс"; "ONGOING" -> "Онгоинг"; "FINISHED" -> "Выпущено"
        "HIATUS" -> "Приостановлено"; "CANCELLED" -> "Отменено"; else -> null
    }

    private fun translationName(v: String?) = when (v) {
        "IN_PROGRESS" -> "Переводится"; "FINISHED" -> "Завершён"
        "FROZEN" -> "Заморожен"; "ABANDONED" -> "Заброшен"; else -> null
    }

    private fun ageName(v: String?) = when (v) {
        "GENERAL" -> "0+"; "SENSITIVE" -> "12+"; "QUESTIONABLE" -> "16+"; "EXPLICIT" -> "18+"; else -> null
    }

    private fun formatName(v: String?) = when (v) {
        "DIGEST" -> "Сборник"; "DOUJINSHI" -> "Додзинси"; "IN_COLOR" -> "В цвете"; "SINGLE" -> "Сингл"
        "WEB" -> "Веб"; "WEBTOON" -> "Вебтун"; "YONKOMA" -> "Ёнкома"; "SHORT" -> "Short"; else -> null
    }
}
