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
class CTask(val titles: List<Title>? = null, val threshold: Int? = null, val experience: Int? = null, val points: Int? = null, val enabled: Boolean = true, val active: Boolean = true)

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
)

data class CharacterProfile(val id: String, val name: String, val cover: String?, val gender: String?)

data class TaskItem(val title: String, val progress: Int, val threshold: Int?, val reward: String, val done: Boolean)

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
        )
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
            TaskItem(
                task.titles.pick() ?: "Задание", t.progress ?: 0, task.threshold,
                listOfNotNull(task.experience?.takeIf { it > 0 }?.let { "$it опыта" }, task.points?.takeIf { it > 0 }?.let { "$it очков" }).joinToString(", "),
                t.completedAt != null,
            )
        }
    }
}
