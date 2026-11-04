package me.senkuro.unofficial.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
class PTeam(val id: String = "", val slug: String = "", val name: String = "")

@Serializable
class PPost(
    val id: String = "", val slug: String = "", val title: String = "", val views: Int? = null, val comments: Int? = null,
    val createdAt: String? = null, val author: SUser? = null, val team: PTeam? = null, val content: List<TiptapBlockDto>? = null,
)

@Serializable
class PPostEdge(val node: PPost)

@Serializable
class PPostsConn(val edges: List<PPostEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class PPostsResponse(val posts: PPostsConn = PPostsConn())

@Serializable
class PPostResponse(val post: PPost? = null)

@Serializable
class PFeedChapter(val id: String = "", val slug: String = "", val number: JsonElement? = null, val volume: JsonElement? = null, val createdAt: String? = null, val manga: MangaCardDto? = null)

@Serializable
class PFeedEdge(val node: PFeedChapter)

@Serializable
class PFeedConn(val edges: List<PFeedEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class PFeedResponse(val newMangaChapters: PFeedConn = PFeedConn())

data class PostItem(val id: String, val slug: String, val title: String, val author: String, val avatar: String?, val team: String?, val date: String?, val views: Int?, val comments: Int?)

data class PostFull(val item: PostItem, val text: String)

data class FeedItem(val manga: MangaItem, val chapter: String, val date: String?)

/** Посты сайта и лента новых глав. */
class PostsRepository(private val api: SenkuroApi) {
    private fun PPost.item() = PostItem(id, slug, title, author?.name.orEmpty(), author?.avatar?.original?.url, team?.name, createdAt?.take(10), views, comments)

    private fun kotlinx.serialization.json.JsonElement?.str(): String? = (this as? kotlinx.serialization.json.JsonPrimitive)?.content

    suspend fun posts(after: String?): Pair<List<PostItem>, String?> {
        val c = api.execute("fetchPosts", "fetchPosts", buildJsonObject { if (after != null) put("after", after) }, PPostsResponse.serializer()).posts
        return c.edges.map { it.node.item() } to c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }

    suspend fun post(slug: String): PostFull? {
        val p = api.execute("fetchPost", "fetchPost", buildJsonObject { put("slug", slug) }, PPostResponse.serializer()).post ?: return null
        runCatching { api.execute("viewPost", "viewPost", buildJsonObject { put("input", buildJsonObject { put("postId", p.id) }) }, JsonElement.serializer()) }
        val text = p.content.orEmpty().joinToString("\n") { b -> b.content.orEmpty().joinToString("") { it.text.orEmpty() } }.trim()
        return PostFull(p.item(), text)
    }

    suspend fun feed(after: String?): Pair<List<FeedItem>, String?> {
        val c = api.execute("fetchFeed", "fetchFeed", buildJsonObject { if (after != null) put("after", after) }, PFeedResponse.serializer()).newMangaChapters
        val items = c.edges.mapNotNull { e ->
            val m = e.node.manga ?: return@mapNotNull null
            val id = m.id ?: return@mapNotNull null
            val title = m.titles.orEmpty().let { l -> l.firstOrNull { it.lang.equals("RU", true) } ?: l.firstOrNull() }?.content ?: m.originalName?.content ?: m.slug
            FeedItem(
                MangaItem(id, m.slug, title, m.cover?.original?.url),
                listOfNotNull(e.node.volume.str()?.let { "Том $it" }, e.node.number.str()?.let { "Глава $it" }).joinToString(". "),
                e.node.createdAt?.take(10),
            )
        }
        return items to c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }
}
