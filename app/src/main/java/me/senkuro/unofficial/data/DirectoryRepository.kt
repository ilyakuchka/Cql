package me.senkuro.unofficial.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
class DSearchNode(
    val __typename: String = "",
    val id: String = "",
    val slug: String = "",
    val name: String? = null,
    val level: Int? = null,
    val titles: List<Title>? = null,
    val avatar: Cover? = null,
    val cover: Cover? = null,
)

@Serializable
class DSearchEdge(val node: DSearchNode)

@Serializable
class DSearchConn(val edges: List<DSearchEdge> = emptyList())

@Serializable
class DSearchResponse(val search: DSearchConn = DSearchConn())

@Serializable
class DTeamNode(
    val id: String = "", val slug: String = "", val name: String = "", val activity: JsonElement? = null,
    val verified: Boolean = false, val subscribers: Int? = null, val views: Int? = null,
    val avatar: Cover? = null, val banner: Cover? = null,
    val projects: Int? = null, val chapters: Int? = null, val members: Int? = null,
    val createdAt: String? = null, val viewerIsMember: Boolean = false,
    val about: List<TiptapBlockDto>? = null, val links: List<DLink>? = null,
)

@Serializable
class DLink(val title: String? = null, val url: String? = null)

@Serializable
class DTeamEdge(val node: DTeamNode)

@Serializable
class DTeamsConn(val edges: List<DTeamEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class DTeamsResponse(val teams: DTeamsConn = DTeamsConn())

@Serializable
class DTeamResponse(val team: DTeamNode? = null)

@Serializable
class DMemberNode(val roles: List<String>? = null, val user: SUser? = null)

@Serializable
class DMemberEdge(val node: DMemberNode)

@Serializable
class DMembersConn(val edges: List<DMemberEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class DMembersResponse(val teamMembers: DMembersConn = DMembersConn())

@Serializable
class DPersonDto(val id: String = "", val slug: String = "", val name: String = "", val titles: List<Title>? = null, val cover: Cover? = null)

@Serializable
class DPersonResponse(val person: DPersonDto? = null)

@Serializable
class DStaffNode(val roles: List<String>? = null, val person: DPersonDto? = null)

@Serializable
class DStaffEdge(val node: DStaffNode)

@Serializable
class DStaffConn(val edges: List<DStaffEdge> = emptyList())

@Serializable
class DStaffResponse(val mangaStaff: DStaffConn = DStaffConn())

data class SearchHit(val kind: String, val id: String, val slug: String, val title: String, val subtitle: String?, val image: String?)

data class TeamItem(val id: String, val slug: String, val name: String, val avatar: String?, val verified: Boolean, val line: String)

data class TeamProfile(
    val item: TeamItem, val banner: String?, val about: String, val links: List<Pair<String, String>>,
    val projects: Int?, val chapters: Int?, val members: Int?, val views: Int?, val created: String?, val viewerIsMember: Boolean,
)

data class PersonProfile(val id: String, val name: String, val cover: String?)

data class StaffItem(val slug: String, val name: String, val role: String, val cover: String?)

/** Поиск по сайту, команды переводчиков, люди (авторы и художники). */
class DirectoryRepository(private val api: SenkuroApi) {

    private fun List<Title>?.pick(): String? =
        orEmpty().let { l -> l.firstOrNull { it.lang.equals("RU", true) } ?: l.firstOrNull { it.lang.equals("EN", true) } ?: l.firstOrNull() }?.content

    private fun List<TiptapBlockDto>?.plain() =
        orEmpty().joinToString("\n") { b -> b.content.orEmpty().joinToString("") { it.text.orEmpty() } }.trim()

    suspend fun search(query: String, type: String): List<SearchHit> {
        val vars = buildJsonObject { put("query", query); put("type", type) }
        return api.execute("search", "search", vars, DSearchResponse.serializer()).search.edges.map { e ->
            val n = e.node
            SearchHit(
                kind = n.__typename.removePrefix("Search"), id = n.id, slug = n.slug,
                title = n.name?.takeIf { it.isNotBlank() } ?: n.titles.pick() ?: n.slug,
                subtitle = n.level?.let { "ур. $it" } ?: if (n.name != null) n.titles.pick() else null,
                image = n.avatar?.original?.url ?: n.cover?.original?.url,
            )
        }
    }

    private fun DTeamNode.item() = TeamItem(
        id, slug, name, avatar?.original?.url, verified,
        listOfNotNull(subscribers?.let { "подписчиков $it" }, views?.let { "просмотров $it" }).joinToString(" · "),
    )

    suspend fun teams(after: String?, name: String?): Pair<List<TeamItem>, String?> {
        val vars = buildJsonObject {
            if (after != null) put("after", after)
            if (!name.isNullOrBlank()) put("name", name)
        }
        val c = api.execute("fetchTeams", "fetchTeams", vars, DTeamsResponse.serializer()).teams
        return c.edges.map { it.node.item() } to c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }

    suspend fun team(slug: String): TeamProfile? {
        val t = api.execute("fetchTeam", "fetchTeam", buildJsonObject { put("slug", slug) }, DTeamResponse.serializer()).team ?: return null
        runCatching {
            api.execute("viewTeam", "viewTeam", buildJsonObject { put("input", buildJsonObject { put("teamId", t.id) }) }, JsonElement.serializer())
        }
        return TeamProfile(
            t.item(), t.banner?.original?.url, t.about.plain(),
            t.links.orEmpty().mapNotNull { l -> l.url?.let { (l.title ?: it) to it } },
            t.projects, t.chapters, t.members, t.views, t.createdAt?.take(10), t.viewerIsMember,
        )
    }

    suspend fun teamMembers(teamId: String, after: String?): Pair<List<GuildMemberItem>, String?> {
        val vars = buildJsonObject { put("first", 30); put("teamId", teamId); if (after != null) put("after", after) }
        val c = api.execute("fetchTeamMembers", "fetchTeamMembers", vars, DMembersResponse.serializer()).teamMembers
        return c.edges.map { e -> GuildMemberItem(e.node.user?.name.orEmpty(), e.node.user?.avatar?.original?.url, null, e.node.roles?.joinToString(", ")) } to
            c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }

    suspend fun person(slug: String): PersonProfile? =
        api.execute("fetchPerson", "fetchPerson", buildJsonObject { put("slug", slug) }, DPersonResponse.serializer()).person?.let {
            PersonProfile(it.id, it.titles.pick() ?: it.name, it.cover?.original?.url)
        }

    suspend fun staff(mangaId: String): List<StaffItem> =
        api.execute("fetchMangaStaff", "fetchMangaStaff", buildJsonObject { put("mangaId", mangaId) }, DStaffResponse.serializer())
            .mangaStaff.edges.mapNotNull { e ->
                val p = e.node.person ?: return@mapNotNull null
                StaffItem(p.slug, p.titles.pick() ?: p.name, e.node.roles.orEmpty().joinToString(", ") { staffRole(it) }, p.cover?.original?.url)
            }

    suspend fun mangasByPerson(personId: String, after: String?): Pair<List<MangaItem>, String?> {
        val vars = buildJsonObject {
            put("first", 24)
            if (after != null) put("after", after)
            put("staff", buildJsonObject { put("include", JsonArray(listOf(JsonPrimitive(personId)))) })
        }
        val c = api.execute("fetchMangas", "fetchMangas", vars, CMangasResponse.serializer()).mangas
        val items = c.edges.mapNotNull { e ->
            val n = e.node
            val id = n.id ?: return@mapNotNull null
            MangaItem(id, n.slug, n.titles.pick() ?: n.slug, n.cover?.original?.url)
        }
        return items to c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }

    private fun staffRole(r: String) = when (r.uppercase()) {
        "STORY" -> "сюжет"; "ART" -> "рисунок"; "STORY_AND_ART" -> "сюжет и рисунок"
        "ORIGINAL_CREATOR" -> "автор оригинала"; else -> r.lowercase().replace('_', ' ')
    }
}
