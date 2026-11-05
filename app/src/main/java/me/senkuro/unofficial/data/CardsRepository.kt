package me.senkuro.unofficial.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
class CCardNode(val id: String = "", val slug: String = "", val titles: List<Title>? = null, val rank: JsonElement? = null, val image: Cover? = null)

@Serializable
class CCardEdge(val node: CCardNode)

@Serializable
class CCardsConn(val edges: List<CCardEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class CCardsResponse(val cards: CCardsConn = CCardsConn())

@Serializable
class CTitled(val id: String = "", val slug: String = "", val titles: List<Title>? = null, val __typename: String? = null)

@Serializable
class CAuthor(val name: String = "")

@Serializable
class CViewerCard(val modifier: JsonElement? = null, val quantity: Int? = null, val shard: JsonElement? = null)

@Serializable
class CCardFull(
    val id: String = "",
    val slug: String = "",
    val titles: List<Title>? = null,
    val rank: JsonElement? = null,
    val image: Cover? = null,
    val owners: Int? = null,
    val wishes: Int? = null,
    val upvotes: Int? = null,
    val views: Int? = null,
    val createdAt: String? = null,
    val description: List<TiptapBlockDto>? = null,
    val author: CAuthor? = null,
    val character: CTitled? = null,
    val entity: CTitled? = null,
    val viewerCards: List<CViewerCard>? = null,
    val viewerVoteDirection: Boolean? = null,
)

@Serializable
class CCardResponse(val card: CCardFull? = null)

@Serializable
class CCharacterFull(val id: String = "", val slug: String = "", val name: String = "", val titles: List<Title>? = null, val gender: JsonElement? = null, val cover: Cover? = null)

@Serializable
class CCharacterResponse(val character: CCharacterFull? = null)

@Serializable
class CMangasResponse(val mangas: Connection2 = Connection2())

@Serializable
class Connection2(val edges: List<Edge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class CTask(val titles: List<Title>? = null, val type: JsonElement? = null, val endAt: String? = null, val threshold: Int? = null, val experience: Int? = null, val points: Int? = null, val enabled: Boolean = true, val active: Boolean = true)

@Serializable
class CUserTask(val progress: Int? = null, val completedAt: String? = null, val task: CTask? = null)

@Serializable
class CTasksResponse(val userTasks: List<CUserTask> = emptyList())

data class CardEntry(val slug: String, val title: String, val rank: String?, val image: String?)

data class CardDetails(
    val entry: CardEntry,
    val owners: Int?, val wishes: Int?, val upvotes: Int?, val views: Int?,
    val date: String?, val description: String, val author: String?,
    val characterTitle: String?, val characterSlug: String?,
    val entityTitle: String?, val entityId: String?, val entityIsManga: Boolean,
    val mine: String?,
    val id: String = "",
    val vote: Boolean? = null,
)

data class CharacterProfile(val id: String, val name: String, val cover: String?, val gender: String?)

data class TaskItem(val title: String, val progress: Int, val threshold: Int?, val exp: Int, val points: Int, val done: Boolean, val period: String, val endAtMillis: Long?)

/** Каталог карточек, карточка, персонаж и задания (запросы перехвачены на сайте). */
class CardsRepository(private val api: SenkuroApi) {

    private fun JsonElement?.label(): String? = when (this) {
        null, is JsonNull -> null
        is JsonPrimitive -> content
        is JsonObject -> listOf("name", "title", "slug", "value", "type").firstNotNullOfOrNull { (get(it) as? JsonPrimitive)?.content }
        else -> null
    }

    private fun List<Title>?.pick(): String? =
        orEmpty().let { l -> l.firstOrNull { it.lang.equals("RU", true) } ?: l.firstOrNull { it.lang.equals("EN", true) } ?: l.firstOrNull() }?.content

    private fun List<TiptapBlockDto>?.plain() =
        orEmpty().joinToString("\n") { b -> b.content.orEmpty().joinToString("") { it.text.orEmpty() } }.trim()

    suspend fun catalog(after: String?, characterId: String? = null): Pair<List<CardEntry>, String?> {
        val vars = buildJsonObject {
            if (after != null) put("after", after)
            put("orderField", "RANK")
            put("orderDirection", "DESC")
            if (characterId != null) put("character", buildJsonObject { put("include", JsonArray(listOf(JsonPrimitive(characterId)))) })
        }
        val c = api.execute("fetchCards", "fetchCards", vars, CCardsResponse.serializer()).cards
        return c.edges.map { CardEntry(it.node.slug, it.node.titles.pick() ?: "Карточка", it.node.rank.label(), it.node.image?.original?.url) } to
            c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }

    suspend fun card(slug: String): CardDetails? {
        val c = api.execute("fetchCard", "fetchCard", buildJsonObject { put("slug", slug) }, CCardResponse.serializer()).card ?: return null
        runCatching {
            api.execute("viewCard", "viewCard", buildJsonObject { put("input", buildJsonObject { put("cardId", c.id) }) }, JsonElement.serializer())
        }
        val mine = c.viewerCards.orEmpty().joinToString(", ") { v ->
            listOfNotNull(v.modifier.label(), v.quantity?.let { "×$it" }).joinToString(" ")
        }.ifBlank { null }
        return CardDetails(
            CardEntry(c.slug, c.titles.pick() ?: "Карточка", c.rank.label(), c.image?.original?.url),
            c.owners, c.wishes, c.upvotes, c.views, c.createdAt?.take(10), c.description.plain(), c.author?.name,
            c.character?.titles.pick(), c.character?.slug?.takeIf { it.isNotBlank() },
            c.entity?.titles.pick(), c.entity?.id?.takeIf { it.isNotBlank() }, c.entity?.__typename == "Manga", mine,
            id = c.id, vote = c.viewerVoteDirection,
        )
    }

    suspend fun voteCard(cardId: String, upvote: Boolean?) {
        if (upvote == null) {
            api.execute("deleteCardVote", "deleteCardVote", buildJsonObject { put("input", buildJsonObject { put("cardId", cardId) }) }, JsonElement.serializer())
        } else {
            api.execute("voteCard", "voteCard", buildJsonObject { put("input", buildJsonObject { put("cardId", cardId); put("upvote", upvote) }) }, JsonElement.serializer())
        }
    }

    suspend fun character(slug: String): CharacterProfile? =
        api.execute("fetchCharacter", "fetchCharacter", buildJsonObject { put("slug", slug) }, CCharacterResponse.serializer()).character?.let {
            CharacterProfile(it.id, it.titles.pick() ?: it.name, it.cover?.original?.url, it.gender.label())
        }

    suspend fun mangasOf(characterId: String, after: String?): Pair<List<MangaItem>, String?> {
        val vars = buildJsonObject {
            put("first", 24)
            if (after != null) put("after", after)
            put("character", buildJsonObject { put("include", JsonArray(listOf(JsonPrimitive(characterId)))) })
        }
        val c = api.execute("fetchMangas", "fetchMangas", vars, CMangasResponse.serializer()).mangas
        val items = c.edges.mapNotNull { e ->
            val n = e.node
            val id = n.id ?: return@mapNotNull null
            MangaItem(id, n.slug, n.titles.pick() ?: n.originalName?.content ?: n.slug, n.cover?.original?.url)
        }
        return items to c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }

    suspend fun tasks(userId: String, onlyIncompleted: Boolean): List<TaskItem> {
        val vars = buildJsonObject {
            put("first", 50)
            put("userId", userId)
            put("onlyIncompleted", onlyIncompleted)
        }
        return api.execute("fetchUserTasks", "fetchUserTasks", vars, CTasksResponse.serializer()).userTasks.mapNotNull { t ->
            val task = t.task ?: return@mapNotNull null
            val period = when (task.type.label()?.uppercase()) {
                "DAILY" -> "за день"; "WEEKLY" -> "за неделю"; "MONTHLY" -> "за месяц"; else -> ""
            }
            val end = task.endAt?.let { runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(it.take(19))?.time
            }.getOrNull() }
            TaskItem(task.titles.pick() ?: "Задание", t.progress ?: 0, task.threshold, task.experience ?: 0, task.points ?: 0, t.completedAt != null, period, end)
        }
    }
}

// ---- обмены и топы

@Serializable
class OPreview(val modifier: JsonElement? = null, val quantity: Int? = null, val shard: JsonElement? = null, val card: CCardNode? = null)

@Serializable
class OUser(val id: String = "", val name: String = "", val avatar: Cover? = null, val level: Int? = null)

@Serializable
class OOffer(
    val id: String = "",
    val status: JsonElement? = null,
    val noTrade: Boolean = false,
    val createdAt: String? = null,
    val user: OUser? = null,
    val offer: JsonElement? = null,
    val request: JsonElement? = null,
    val viewerDidAuthor: Boolean = false,
    val viewerCanAccept: Boolean = false,
    val viewerCanClose: Boolean = false,
)

@Serializable
class OOfferEdge(val node: OOffer)

@Serializable
class OOffersConn(val edges: List<OOfferEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class OOffersResponse(val cardOffers: OOffersConn = OOffersConn())

@Serializable
class OTopEntry(val experience: Int? = null, val user: OUser? = null)

@Serializable
class OTopsResponse(
    val experimentalSpotlightExperiences: List<OTopEntry>? = null,
    val experimentalSpotlightCredits: List<OTopEntry>? = null,
)

data class PreviewItem(val title: String, val image: String?, val slug: String, val note: String)

data class OfferItem(
    val id: String, val status: String?, val user: String, val avatar: String?, val date: String?,
    val offer: PreviewItem?, val request: PreviewItem?, val canAccept: Boolean, val canClose: Boolean, val mine: Boolean,
)

data class TopUser(val id: String, val name: String, val avatar: String?, val level: Int?, val experience: Int?, val hasExp: Boolean, val hasCredits: Boolean)

class OffersRepository(private val api: SenkuroApi) {
    internal val adApi get() = api

    private fun JsonElement?.s(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private val lenient = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    /** Сервер отдаёт то объект, то массив превью — берём первый элемент. */
    private fun JsonElement?.item(): PreviewItem? {
        val el = (if (this is kotlinx.serialization.json.JsonArray) firstOrNull() else this) ?: return null
        if (el is JsonNull) return null
        return runCatching { lenient.decodeFromJsonElement(OPreview.serializer(), el) }.getOrNull().item()
    }

    private fun OPreview?.item(): PreviewItem? {
        val c = this?.card ?: return null
        val title = c.titles.orEmpty().let { l -> l.firstOrNull { it.lang.equals("RU", true) } ?: l.firstOrNull() }?.content ?: "Карточка"
        val note = listOfNotNull(c.rank.s(), modifier.s(), quantity?.takeIf { it > 1 }?.let { "×$it" }).joinToString(" · ")
        return PreviewItem(title, c.image?.original?.url, c.slug, note)
    }

    /** mineUserId != null — только обмены этого пользователя; иначе — все открытые. */
    suspend fun offers(after: String?, mineUserId: String?): Pair<List<OfferItem>, String?> {
        val vars = buildJsonObject {
            if (after != null) put("after", after)
            if (mineUserId != null) put("userId", mineUserId) else put("status", "PENDING")
        }
        val c = api.execute("fetchCardOffers", "fetchCardOffers", vars, OOffersResponse.serializer()).cardOffers
        return c.edges.map { e ->
            val n = e.node
            OfferItem(n.id, n.status.s(), n.user?.name.orEmpty(), n.user?.avatar?.original?.url, n.createdAt?.take(10),
                n.offer.item(), n.request.item().takeIf { !n.noTrade }, n.viewerCanAccept, n.viewerCanClose, n.viewerDidAuthor)
        } to c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }

    private suspend fun act(name: String, offerId: String) {
        api.execute(name, name, buildJsonObject { put("input", buildJsonObject { put("offerId", offerId) }) }, JsonElement.serializer())
    }

    suspend fun accept(offerId: String) = act("acceptCardOffer", offerId)
    suspend fun close(offerId: String) = act("closeCardOffer", offerId)

    suspend fun tops(): List<TopUser> {
        val vars = buildJsonObject { put("includeExperiences", true); put("includeCredits", true) }
        val r = api.execute("fetchMainUserTops", "fetchMainUserTops", vars, OTopsResponse.serializer())
        val byId = LinkedHashMap<String, TopUser>()
        r.experimentalSpotlightExperiences.orEmpty().forEach { e ->
            val u = e.user ?: return@forEach
            byId[u.id] = TopUser(u.id, u.name, u.avatar?.original?.url, u.level, e.experience, hasExp = true, hasCredits = false)
        }
        r.experimentalSpotlightCredits.orEmpty().forEach { e ->
            val u = e.user ?: return@forEach
            val old = byId[u.id]
            byId[u.id] = old?.copy(hasCredits = true) ?: TopUser(u.id, u.name, u.avatar?.original?.url, u.level, null, hasExp = false, hasCredits = true)
        }
        return byId.values.toList()
    }
}

// ---- реклама (баннер на главной, как на сайте)

@Serializable
class AdMediaDto(val __typename: String? = null, val url: String? = null)

@Serializable
class AdDto(val id: String = "", val link: String? = null, val media: AdMediaDto? = null)

@Serializable
class AdResponse(val ad: AdDto? = null)

data class AdBanner(val id: String, val link: String?, val image: String)

suspend fun OffersRepository.mainAd(): AdBanner? {
    val a = adApi.execute("fetchMainAd", "fetchMainAd", buildJsonObject {}, AdResponse.serializer()).ad ?: return null
    val url = a.media?.url?.takeIf { a.media?.__typename == "AdMediaImage" && it.isNotBlank() } ?: return null
    return AdBanner(a.id, a.link, url)
}

suspend fun OffersRepository.viewAd(id: String) {
    adApi.execute("viewAd", "viewAd", buildJsonObject { put("input", buildJsonObject { put("adId", id) }) }, JsonElement.serializer())
}
