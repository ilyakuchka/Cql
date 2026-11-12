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

    /** «Самое читаемое»: period = DAY | WEEK | MONTH (MangaPopularPeriod, как на сайте). */
    suspend fun popular(period: String): List<MangaItem> =
        api.execute(
            "HomePopularPeriod", "homePopularPeriod",
            buildJsonObject { put("period", period) }, PopularResponse.serializer(),
        ).mangaPopularByPeriod.mapNotNull { it.toItem() }

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
        // tachiyomi-ответ бывает неполным (новые главы приходят позже): добираем недостающие запросом сайта.
        val known = dto.chapters.map { it.id }.toHashSet()
        val extra = mutableListOf<ChapterDto>()
        for (branch in dto.chapters.map { it.branchId }.filter { it.isNotBlank() }.distinct()) {
            runCatching {
                var after: String? = null
                var guard = 0
                do {
                    val vars = buildJsonObject {
                        put("branchId", branch)
                        put("orderBy", buildJsonObject { put("field", "NUMBER"); put("direction", "DESC") })
                        if (after != null) put("after", after)
                    }
                    val page = api.execute("fetchMangaChapters", "fetchMangaChapters", vars, SiteChaptersResponse.serializer()).mangaChapters
                    var allKnown = true
                    page.edges.forEach { e ->
                        if (known.add(e.node.id)) { extra.add(e.node.copy(branchId = branch)); allKnown = false }
                    }
                    after = page.pageInfo.endCursor.takeIf { page.pageInfo.hasNextPage && !allKnown }
                } while (after != null && ++guard < 5)
            }
        }
        val merged = if (extra.isEmpty()) dto.chapters else (dto.chapters + extra).sortedWith(
            compareByDescending<ChapterDto> { it.volume.toDoubleOrNull() ?: 0.0 }.thenByDescending { it.number.toDoubleOrNull() ?: 0.0 },
        )
        val chapters = merged.map { c ->
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

    /** Страницы тем же запросом, что использует сайт (по slug главы). */
    suspend fun sitePages(chapter: Chapter): List<String> {
        val vars = buildJsonObject {
            put("slug", chapter.slug)
            put("cdn_quality", "red")
        }
        return api.execute("Reader", "readerChapter", vars, ReaderResponse.serializer())
            .mangaChapter?.pages?.mapNotNull { it.image?.original?.url }?.filter { it.isNotBlank() }.orEmpty()
    }

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
        primary.exceptionOrNull()?.let { ReaderErrorLog.add("Страницы: tachiyomi-запрос", "глава ${chapter.slug} (${chapter.id})", it) }
        if (primary.isSuccess && primary.getOrNull().isNullOrEmpty()) ReaderErrorLog.add("Страницы: tachiyomi-запрос", "пустой список, глава ${chapter.slug}")
        primary.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        val fallback = runCatching { sitePages(chapter) }
        fallback.exceptionOrNull()?.let { ReaderErrorLog.add("Страницы: запрос сайта", "глава ${chapter.slug}", it) }
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
            slug = user.slug,
            name = user.name.ifBlank { user.slug },
            avatar = user.avatar?.original?.url,
            frame = user.frame?.original?.url,
            adsDisabled = viewer.permissions?.canDisableAdvertisement == true,
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

    @Volatile private var extendedNotifications = true

    /** Переключатели «Настройки уведомлений»: включено всё, кроме пар type+channel с frequency = NEVER. */
    suspend fun notificationOff(userId: String): Set<String> =
        api.execute(
            "fetchUserNotificationPreferences", "fetchUserNotificationPreferences",
            buildJsonObject { put("userId", userId) }, NotifPrefsResponse.serializer(),
        ).userNotificationPreferences.filter { it.frequency == "NEVER" }.map { it.type + ":" + it.channel }.toSet()

    /** Включить = удалить настройку, выключить = frequency NEVER (как на сайте). */
    suspend fun setNotificationEnabled(userId: String, type: String, channel: String, enabled: Boolean) {
        val input = buildJsonObject {
            put("userId", userId); put("type", type); put("channel", channel)
            if (!enabled) put("frequency", "NEVER")
        }
        val op = if (enabled) "deleteUserNotificationPreference" else "updateUserNotificationPreference"
        api.execute(op, op, buildJsonObject { put("input", input) }, JsonElement.serializer())
    }

    suspend fun notifications(read: Boolean, after: String?, types: List<String>? = null, first: Int = 10): NotificationsPage {
        val vars = buildJsonObject {
            put("first", first)
            put("isRead", read)
            if (after != null) put("after", after)
            if (types != null) put("types", kotlinx.serialization.json.JsonArray(types.map { JsonPrimitive(it) }))
        }
        // Расширенный запрос (комментарии, оценки). Если сервер его не принял — один раз откатываемся на простой.
        val conn = try {
            api.execute("fetchNotificationFeeds", if (extendedNotifications) "fetchNotificationFeeds" else "fetchNotificationFeedsBasic", vars, NotifResponse.serializer())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!extendedNotifications) throw e
            extendedNotifications = false
            api.execute("fetchNotificationFeeds", "fetchNotificationFeedsBasic", vars, NotifResponse.serializer())
        }.notificationFeeds
        return NotificationsPage(
            items = conn.edges.map { it.node.toItem() },
            next = conn.pageInfo.endCursor.takeIf { conn.pageInfo.hasNextPage },
        )
    }

    /** Все id уведомлений (для «Скрыть все» / «Удалить все»: на сайте это отдельные действия над всем списком). */
    suspend fun allNotificationIds(read: Boolean): List<String> {
        val ids = ArrayList<String>()
        var cursor: String? = null
        repeat(40) {
            val page = notifications(read, cursor, first = 50)
            ids += page.items.map { it.id }
            cursor = page.next ?: return ids
        }
        return ids
    }

    private suspend fun bulk(op: String, ids: List<String>): BulkResultDto? {
        val vars = buildJsonObject {
            put("input", buildJsonObject {
                put("notificationIds", kotlinx.serialization.json.JsonArray(ids.map { JsonPrimitive(it) }))
            })
        }
        val r = api.execute(op, op, vars, BulkResponse.serializer())
        return r.bulkReadNotifications ?: r.bulkDeleteNotifications
    }

    suspend fun markNotificationsRead(ids: List<String>): Boolean = bulk("bulkReadNotifications", ids)?.success == true

    suspend fun deleteNotifications(ids: List<String>): Boolean = bulk("bulkDeleteNotifications", ids)?.success == true

    suspend fun profile(slug: String): Profile? =
        api.execute("fetchUser", "fetchUser", buildJsonObject { put("slug", slug) }, ProfileResponse.serializer()).user?.let {
            Profile(it.id, it.slug, it.name.ifBlank { it.slug }, it.verified, it.level, it.createdAt?.take(10),
                it.lastOnlineAt?.take(10), it.avatar?.original?.url, it.banner?.original?.url)
        }

    /** Статистика, библиотека, друзья, отношения, история. Любая ошибка — null (экран остаётся с базовым профилем). */
    suspend fun profileExtra(slug: String): ProfileExtra? = try {
        val u = api.execute("fetchUserProfileExtra", "fetchUserProfileExtra", buildJsonObject { put("slug", slug) }, ProfileExtraResponse.serializer()).user
        u?.let { toProfileExtra(it) }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun statusLabel(s: String) = when (s.uppercase()) {
        "READING", "WATCHING" -> "Читаю"
        "PLANNED", "PLAN_TO_READ", "PLAN_TO_WATCH" -> "В планах"
        "COMPLETED", "FINISHED" -> "Прочитано"
        "DROPPED" -> "Брошено"
        "ON_HOLD", "POSTPONED", "DEFERRED", "PAUSED" -> "Отложено"
        else -> s.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun toProfileExtra(u: ProfileExtraUserDto): ProfileExtra {
        val st = u.statistics
        fun List<PUserShortDto>.people() = map { PeopleItem(it.slug, it.name.ifBlank { it.slug }, it.avatar?.original?.url, it.frame?.original?.url) }
        val manga = u.bookmarkEntityStatusDistributions.filter { it.entityType.equals("MANGA", true) }
        val numbers = { m: PActivityMetaDto? -> m?.numbers.orEmpty().mapNotNull { it.text() }.take(3).joinToString(", ") }
        return ProfileExtra(
            readChapters = st?.readChapters, watchEpisodes = st?.watchEpisodes, rated = st?.rateProjects, comments = st?.comments,
            friends = st?.friends, subscribers = st?.subscribers, follows = st?.follows,
            rank = st?.rank, betterPercentile = st?.betterPercentile,
            gender = when (u.gender?.uppercase()) { "MALE" -> "Мужской"; "FEMALE" -> "Женский"; null, "" -> null; else -> u.gender },
            birthday = u.birthdayOn,
            friendsList = u.lastFriends.people(), followsList = u.lastFollows.people(),
            waifus = u.waifus.mapNotNull { w ->
                val c = w.character ?: return@mapNotNull null
                WaifuItem(c.slug, c.name, when (w.relation?.uppercase()) { "WAIFU" -> "Вайфу"; "HUSBANDO" -> "Муж"; "LAIFU" -> "Лайфу"; null -> ""; else -> w.relation.orEmpty().lowercase().replaceFirstChar { it.uppercase() } }, c.cover?.original?.url)
            },
            libraryTotal = manga.sumOf { it.count },
            libraryByStatus = manga.sortedByDescending { it.count }.map { statusLabel(it.status) to it.count },
            libraryPreview = u.bookmarkMangaPreview.map { MangaItem(it.id, it.slug, pickTitle(it.titles, it.originalName, it.slug), it.cover?.original?.url) },
            activities = u.lastActivities.map { mapActivity(it) },
        )
    }

    suspend fun profileAppearance(slug: String): ProfileAppearance? = try {
        api.execute("fetchUserAppearance", "fetchUserAppearance", buildJsonObject { put("slug", slug) }, AppearanceResponse.serializer())
            .user?.let {
                ProfileAppearance(
                    it.frame?.original?.url, it.wallpaper?.original?.url, it.status?.original?.url, it.banner?.original?.url, it.avatar?.original?.url,
                    bannerVideo = videoVariant(it.banner, maxWidth = true),
                    wallpaperVideo = videoVariant(it.wallpaper, maxWidth = true),
                    avatarVideo = videoVariant(it.avatar, maxWidth = false),
                )
            }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Видео-вариант анимированной картинки: mp4 (аппаратный декодер) или webm; ширина — как на сайте (для баннера максимальная, но не больше 1280). */
    private fun videoVariant(img: AppearanceImageDto?, maxWidth: Boolean): String? {
        if (img?.animation != true) return null
        val videos = img.variants.filter { v ->
            val f = v.format.orEmpty().lowercase()
            v.url.isNotBlank() && (f.contains("mp4") || f.contains("webm"))
        }
        if (videos.isEmpty()) return null
        val wanted = if (maxWidth) videos.filter { (it.width ?: 0) <= 1280 }.maxOfOrNull { it.width ?: 0 } ?: videos.minOf { it.width ?: 0 }
        else videos.filter { (it.width ?: 0) >= 200 }.minOfOrNull { it.width ?: 0 } ?: videos.maxOf { it.width ?: 0 }
        val sameWidth = videos.filter { (it.width ?: 0) == wanted }
        return (sameWidth.firstOrNull { it.format.orEmpty().lowercase().contains("mp4") } ?: sameWidth.first()).url
    }

    suspend fun shorts(after: String?): Pair<List<ShortItem>, String?> {
        val vars = buildJsonObject { if (after != null) put("after", after) }
        val c = api.execute("fetchShortsMangaChapters", "fetchShortsMangaChapters", vars, ShortsResponse.serializer()).shortsMangaChapters
        return c.edges.map { e ->
            val n = e.node
            ShortItem(n.id, n.slug, n.manga?.toItem(), n.number.text().orEmpty(), n.pages.mapNotNull { it.image?.original?.url })
        }.filter { it.pages.isNotEmpty() } to c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }

    private fun mapActivity(a: PActivityDto): ProfileActivity {
        val m = a.meta
        val numbers = m?.numbers.orEmpty().mapNotNull { it.text() }.take(3).joinToString(", ")
        val subject = a.entity?.titles?.localized() ?: a.entity?.originalName?.content
        val text = when (m?.typename) {
            "ActivityMetaBookmarkStatus" -> "Обновление закладки: " + statusLabel(m.status.orEmpty())
            "ActivityMetaChapters" -> "Прочитаны главы $numbers"
            "ActivityMetaEpisodes" -> "Просмотрены серии $numbers"
            "ActivityMetaBookmark" -> "Добавлено в закладки"
            else -> if (a.entity?.typename == "AchievementLevel") "Новое достижение" else a.type.orEmpty().lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
        return ProfileActivity(text.trim(), subject, a.createdAt)
    }

    /** Полная история действий пользователя, по 10 записей (как на сайте). */
    suspend fun activities(userId: String, after: String?): Pair<List<ProfileActivity>, String?> {
        val vars = buildJsonObject { put("userId", userId); if (after != null) put("after", after) }
        val c = api.execute("fetchActivities", "fetchActivities", vars, ActivitiesResponse.serializer()).activities
        return c.edges.map { mapActivity(it.node) } to c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }

    /** «Топ пользователей»: рейтинг по активности (userRanksInsight). */
    suspend fun ranks(): RanksPage {
        val r = api.execute("fetchUserRanks", "fetchUserRanks", buildJsonObject {}, RanksResponse.serializer())
        return RanksPage(
            myRank = r.viewer?.user?.statistics?.rank,
            entries = r.userRanksInsight.mapNotNull { e ->
                val u = e.user ?: return@mapNotNull null
                RankEntry(e.rank ?: 0, u.slug, u.name.ifBlank { u.slug }, u.avatar?.original?.url, u.frame?.original?.url, u.level, e.betterPercentile, e.readChapters, e.watchEpisodes)
            }.sortedBy { it.rank },
        )
    }

    suspend fun achievements(userId: String): List<AchievementItem> =
        api.execute("fetchUserAchievements", "fetchUserAchievements", buildJsonObject { put("userId", userId) },
            AchievementsResponse.serializer()).userAchievements.map {
            AchievementItem(
                title = it.currentLevel?.titles?.localized() ?: it.achievement?.slug.orEmpty(),
                level = it.level,
                points = it.currentLevel?.points,
                done = it.completedAt != null,
                progress = it.progress.text(),
                cover = it.currentLevel?.cover?.original?.url,
            )
        }

    // ---------------------------------------------------------------- Комментарии и просмотры

    suspend fun viewManga(mangaId: String) {
        api.execute(
            "viewManga", "viewManga",
            buildJsonObject { put("input", buildJsonObject { put("mangaId", mangaId) }) },
            JsonElement.serializer(),
        )
    }

    /** id страниц главы: комментарии на сайте привязаны к странице. */
    suspend fun pageIds(chapterSlug: String): List<String> {
        val vars = buildJsonObject {
            put("slug", chapterSlug)
            put("cdn_quality", "red")
        }
        return api.execute("Reader", "readerChapter", vars, ReaderResponse.serializer())
            .mangaChapter?.pages?.map { it.id }?.filter { it.isNotBlank() }.orEmpty()
    }

    suspend fun comments(subjectId: String, after: String?): CommentsPage {
        val vars = buildJsonObject {
            put("subjectId", subjectId)
            if (after != null) put("after", after)
            put("orderBy", buildJsonObject {
                put("field", "CREATED_AT")
                put("direction", "DESC")
            })
        }
        val conn = api.execute("fetchThreadComments", "fetchThreadComments", vars, CommentsResponse.serializer()).comments
        val items = conn.edges.flatMap { e -> listOf(e.node) + e.node.replies.orEmpty() }
            .filter { it.deletedAt == null }
            .map { c ->
                CommentItem(
                    id = c.id,
                    threadId = c.threadId ?: c.id,
                    depth = c.depth ?: 0,
                    author = c.author?.name.orEmpty(),
                    avatar = c.author?.avatar?.original?.url,
                    authorSlug = c.author?.slug.orEmpty(),
                    frame = c.author?.frame?.original?.url,
                    status = c.author?.status?.original?.url,
                    statusCaption = c.author?.status?.caption,
                    text = c.content.orEmpty().joinToString("\n") { b -> b.content.orEmpty().joinToString("") { it.text.orEmpty() } }.trim(),
                    date = c.createdAt?.take(10),
                    up = c.upvotes ?: 0,
                    down = c.downvotes ?: 0,
                    canEdit = c.viewerCanUpdate,
                    canDelete = c.viewerCanDelete,
                    vote = c.viewerVoteDirection,
                )
            }
        return CommentsPage(items, conn.pageInfo.endCursor.takeIf { conn.pageInfo.hasNextPage })
    }

    suspend fun voteComment(commentId: String, upvote: Boolean) {
        val vars = buildJsonObject { put("input", buildJsonObject { put("commentId", commentId); put("upvote", upvote) }) }
        api.execute("voteComment", "voteComment", vars, JsonElement.serializer())
    }

    suspend fun deleteCommentVote(commentId: String) {
        val vars = buildJsonObject { put("input", buildJsonObject { put("commentId", commentId) }) }
        api.execute("deleteCommentVote", "deleteCommentVote", vars, JsonElement.serializer())
    }

    private fun commentBody(text: String) = kotlinx.serialization.json.JsonArray(
        text.split("\n").map { line ->
            buildJsonObject {
                put("type", "paragraph")
                if (line.isNotEmpty()) put("content", kotlinx.serialization.json.JsonArray(listOf(
                    buildJsonObject { put("type", "text"); put("text", line) },
                )))
            }
        },
    )

    suspend fun addComment(subjectId: String, text: String) {
        val vars = buildJsonObject {
            put("input", buildJsonObject {
                // автор — сам пользователь (с null сайт ставит автора по умолчанию, например команду)
                put("authorId", AppGraph.account.value?.userId?.let { JsonPrimitive(it) } ?: JsonNull)
                put("content", commentBody(text))
                put("subjectId", subjectId)
            })
        }
        api.execute("createComment", "createComment", vars, JsonElement.serializer())
    }

    suspend fun editComment(commentId: String, text: String) {
        val vars = buildJsonObject {
            put("input", buildJsonObject {
                put("commentId", commentId)
                put("content", commentBody(text))
            })
        }
        api.execute("updateComment", "updateComment", vars, JsonElement.serializer())
    }

    suspend fun deleteComment(commentId: String, threadId: String) {
        val vars = buildJsonObject {
            put("input", buildJsonObject {
                put("commentId", commentId)
                put("threadId", threadId)
            })
        }
        api.execute("deleteComment", "deleteComment", vars, JsonElement.serializer())
    }

    // ---------------------------------------------------------------- Оценки, прогресс чтения

    /** Текущая оценка пользователя (1..10) или null. */
    /** Номер главы из закладки (null — закладки нет). */
    suspend fun bookmarkNumber(slug: String): String? =
        api.execute("fetchMangaViewerState", "fetchMangaViewerState", buildJsonObject { put("slug", slug) },
            ViewerStateResponse.serializer()).manga?.viewerBookmark?.number.text()?.takeIf { it.isNotBlank() }

    /**
     * Место, где пользователь остановился (закладка сайта), для уведомления о новой главе [notifChapterId].
     * null — закладки нет, она не позади новой главы или такой главы нет в ветке.
     */
    suspend fun bookmarkTarget(manga: MangaItem, notifChapterId: String, notifNumber: String): Pair<Chapter, Int>? {
        val num = bookmarkNumber(manga.slug) ?: return null
        val saved = num.toDoubleOrNull() ?: return null
        val fresh = notifNumber.toDoubleOrNull() ?: return null
        if (saved >= fresh) return null                 // новая глава уже прочитана — продолжать нечего
        val res = chapters(manga.id)
        val branch = res.branches.firstOrNull { b -> b.chapters.any { it.id == notifChapterId } } ?: res.branches.firstOrNull() ?: return null
        val chapter = branch.chapters.firstOrNull { it.number == num } ?: return null
        return chapter to (bookmarkPage(manga.slug) ?: 1)
    }

    private var progressCache: Triple<String, Double?, Int?>? = null

    /**
     * Прогресс чтения как на сайте (reader.js): страница = MangaChapterPage.number, сохраняется в закладку
     * через updateUserBookmark {subjectId, number, page, manual:false}. Только если закладка уже есть и
     * новая позиция не позади сохранённой; на последней странице последней главы — следующий целый номер и page=null.
     */
    suspend fun saveReadProgress(mangaId: String, slug: String, chapterNumber: String, page: Int, lastOfLast: Boolean) {
        val cur = progressCache?.takeIf { it.first == mangaId } ?: run {
            val b = api.execute("fetchMangaViewerState", "fetchMangaViewerState", buildJsonObject { put("slug", slug) },
                ViewerStateResponse.serializer()).manga?.viewerBookmark
            Triple(mangaId, b?.number.text()?.toDoubleOrNull(), b?.page).also { progressCache = if (b == null) Triple(mangaId, null, null) else it }
        }
        val saved = cur.second ?: return            // закладки нет — сайт тоже ничего не сохраняет
        val num = chapterNumber.toDoubleOrNull() ?: return
        if (num < saved) return
        if (num == saved && cur.third != null && page <= cur.third!!) return
        val ceil = kotlin.math.ceil(num)
        val nextNumber = if (num == ceil) (ceil + 1).toLong().toString() else ceil.toLong().toString()
        val vars = buildJsonObject {
            put("input", buildJsonObject {
                put("subjectId", mangaId)
                put("number", if (lastOfLast) nextNumber else chapterNumber)
                if (lastOfLast) put("page", JsonNull) else put("page", page)
                put("manual", false)
            })
        }
        api.execute("updateUserBookmark", "updateUserBookmark", vars, MutationResponse.serializer())
        progressCache = Triple(mangaId, if (lastOfLast) nextNumber.toDouble() else num, if (lastOfLast) null else page)
    }

    /** Закладка с сайта: номер главы и страница (нумерация страниц с 1, как на сайте). */
    suspend fun bookmarkPage(slug: String): Int? =
        api.execute("fetchMangaViewerState", "fetchMangaViewerState", buildJsonObject { put("slug", slug) },
            ViewerStateResponse.serializer()).manga?.viewerBookmark?.page?.takeIf { it > 0 }

    suspend fun bookmarkChapterSlug(branchId: String, number: String): String? =
        api.execute("fetchMangaBookmarkChapter", "fetchMangaBookmarkChapter",
            buildJsonObject { put("branchId", branchId); put("number", number) },
            BookmarkChapterResponse.serializer()).mangaBookmarkChapter?.slug?.takeIf { it.isNotBlank() }

    suspend fun createCollection(name: String) {
        val vars = buildJsonObject { put("input", buildJsonObject { put("subjectType", "MANGA"); put("name", name) }) }
        api.execute("createUserBookmarkCollection", "createUserBookmarkCollection", vars, JsonElement.serializer())
    }

    suspend fun renameCollection(id: String, name: String) {
        val vars = buildJsonObject { put("input", buildJsonObject { put("collectionId", id); put("name", name) }) }
        api.execute("updateUserBookmarkCollection", "updateUserBookmarkCollection", vars, JsonElement.serializer())
    }

    suspend fun deleteCollection(id: String) {
        val vars = buildJsonObject { put("input", buildJsonObject { put("collectionId", id) }) }
        api.execute("deleteUserBookmarkCollection", "deleteUserBookmarkCollection", vars, JsonElement.serializer())
    }

    suspend fun viewerVote(slug: String): Int? =
        api.execute("fetchMangaViewerState", "fetchMangaViewerState", buildJsonObject { put("slug", slug) },
            ViewerStateResponse.serializer()).manga?.viewerVote.text()?.toIntOrNull()

    suspend fun vote(mangaId: String, score: Int) {
        val vars = buildJsonObject {
            put("input", buildJsonObject {
                put("mangaId", mangaId)
                put("score", score)
            })
        }
        api.execute("voteManga", "voteManga", vars, JsonElement.serializer())
    }

    /** Отмечает главу прочитанной на сайте. */
    suspend fun readChapter(mangaId: String, chapter: Chapter) {
        val vars = buildJsonObject {
            put("input", buildJsonObject {
                put("branchId", chapter.branchId)
                put("chapterId", chapter.id)
                put("mangaId", mangaId)
            })
        }
        api.execute("readMangaChapter", "readMangaChapter", vars, JsonElement.serializer())
    }

    /** type: OPEN_COMMENTS или COMPLETED_READING. */
    suspend fun readerEvent(mangaId: String, chapter: Chapter, type: String) {
        val vars = buildJsonObject {
            put("input", buildJsonObject {
                put("chapterId", chapter.id)
                put("mangaId", mangaId)
                put("type", type)
            })
        }
        api.execute("updateMangaReaderSetting", "updateMangaReaderSetting", vars, JsonElement.serializer())
    }

    /** «Продолжить чтение»: следующие главы по закладкам. */
    suspend fun continueReading(): List<MangaItem> =
        api.execute("fetchMangaBookmarkNextChapters", "fetchMangaBookmarkNextChapters", buildJsonObject {},
            NextChaptersResponse.serializer()).mangaBookmarkNextChapters.mapNotNull { n ->
            n.manga?.toItem()?.copy(status = n.number.text()?.let { "Глава $it" })
        }.distinctBy { it.id }

    // ---------------------------------------------------------------- Связанное, персонажи, поиск

    suspend fun deleteVote(mangaId: String) {
        val vars = buildJsonObject { put("input", buildJsonObject { put("mangaId", mangaId) }) }
        api.execute("deleteMangaVote", "deleteMangaVote", vars, JsonElement.serializer())
    }

    suspend fun relations(mangaId: String): List<RelatedManga> =
        api.execute("fetchMangaRelations", "fetchMangaRelations", mangaVars(mangaId), RelationsResponse.serializer())
            .mangaRelations.edges.mapNotNull { e ->
                e.node.destination?.toItem()?.let { RelatedManga(it, e.node.relation) }
            }

    suspend fun characters(mangaId: String): List<CharacterItem> =
        api.execute("fetchMangaCharacters", "fetchMangaCharacters", mangaVars(mangaId), CharactersResponse.serializer())
            .mangaCharacters.edges.mapNotNull { e ->
                val c = e.node.character ?: return@mapNotNull null
                CharacterItem(c.titles?.localized() ?: c.name, e.node.role, c.cover?.original?.url, c.slug)
            }

    suspend fun popularSearches(): List<String> =
        api.execute("fetchPopularTodaySearchQueries", "fetchPopularTodaySearchQueries", buildJsonObject {},
            PopularQueriesResponse.serializer()).popularTodaySearchQueries

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun plural(n: Int, one: String, few: String, many: String): String {
        val m100 = n % 100; val m10 = n % 10
        return if (m100 in 11..14) many else when (m10) { 1 -> one; in 2..4 -> few; else -> many }
    }

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
        val chapterRefs = payload?.chapters.orEmpty().mapNotNull { c ->
            val cid = c.id ?: return@mapNotNull null
            NotifChapter(cid, c.number.text().orEmpty())
        }.ifEmpty {
            chapterNode?.id?.let { listOf(NotifChapter(it, chapterNode.number.text().orEmpty())) }.orEmpty()
        }
        fun List<TiptapBlockDto>?.plain(): String =
            orEmpty().joinToString("\n") { b -> b.content.orEmpty().joinToString("") { it.text.orEmpty() } }.trim()
        val commentNode = nodes.firstOrNull { it.typename == "Comment" }
        val quote = commentNode?.content.plain().ifBlank { null }
        val replies = payload?.recentComments.orEmpty().map { r ->
            NotifReply(NotifActor(r.author?.name.orEmpty(), r.author?.avatar?.original?.url, r.author?.frame?.original?.url), r.content.plain(), r.createdAt)
        }
        val actors = when (type) {
            "COMMENT_REPLIED" -> replies.map { it.author }
            "COMMENT_UPVOTED" -> payload?.recentUsers.orEmpty().map { NotifActor(it.name, it.avatar?.original?.url, it.frame?.original?.url) }
            else -> emptyList()
        }.distinctBy { it.name }
        val count = eventCount ?: 0
        val names = actors.map { it.name }.filter { it.isNotBlank() }.joinToString(", ")
        val headline = when {
            type == "COMMENT_REPLIED" && actors.isNotEmpty() ->
                if (count > 2) "$count ${plural(count, "ответ", "ответа", "ответов")} на твой комментарий"
                else "$names ответил${if (actors.size > 1) "и" else ""} тебе на комментарий"
            type == "COMMENT_UPVOTED" && actors.isNotEmpty() ->
                if (count > 3) "$count чел. оценили твой комментарий"
                else "$names оценил${if (actors.size > 1) "и" else ""} твой комментарий"
            else -> null
        }
        val postNode = nodes.firstOrNull { it.typename == "Post" }
        val entityTitle = when {
            postNode != null -> "Пост · " + (postNode.title ?: postNode.slug)
            manga != null -> manga.title
            chapterNode != null -> "Глава " + chapterNode.number.text().orEmpty()
            else -> null
        }
        val caption = when (type) {
            "NEW_MANGA_CHAPTER", "NEW_RANOBE_CHAPTER" -> "Вышли новые главы"
            "COMMENT_REPLIED" -> "Ответили на ваш комментарий"
            "COMMENT_UPVOTED" -> "Оценили ваш комментарий"
            "COMMENT_MENTIONED" -> "Вас упомянули в комментарии"
            "POST_COMMENTED" -> "Новый комментарий к посту"
            "NEW_FRIEND_REQUEST" -> "Заявка в друзья"
            "NEW_POST" -> "Новый пост"
            "NEW_ACHIEVEMENT" -> "Новое достижение"
            else -> null
        }

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
            caption = caption ?: subtitle,
            at = lastEventAt,
            chapters = chapterRefs,
            type = type,
            headline = headline,
            actors = actors,
            quote = quote,
            replies = replies,
            entityTitle = entityTitle,
            postSlug = postNode?.slug?.takeIf { it.isNotBlank() },
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
