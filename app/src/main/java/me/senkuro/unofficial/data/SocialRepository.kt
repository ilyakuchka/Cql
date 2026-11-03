package me.senkuro.unofficial.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ---- DTO

@Serializable
class SUser(val id: String = "", val name: String = "", val avatar: Cover? = null)

@Serializable
class SMessageDto(
    val id: String = "",
    val createdAt: String? = null,
    val content: List<TiptapBlockDto>? = null,
    val viewerDidAuthor: Boolean = false,
    val viewerCanDelete: Boolean = false,
    val user: SUser? = null,
)

@Serializable
class SParticipant(val unreadCount: Int? = null)

@Serializable
class SConversationDto(
    val id: String = "",
    val name: String? = null,
    val avatar: Cover? = null,
    val participantUser: SUser? = null,
    val lastMessage: SMessageDto? = null,
    val viewerParticipant: SParticipant? = null,
)

@Serializable
class SConversationEdge(val node: SConversationDto)

@Serializable
class SConversationsConn(val edges: List<SConversationEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class SConversationsResponse(val conversations: SConversationsConn = SConversationsConn())

@Serializable
class SMessageEdge(val cursor: String? = null, val node: SMessageDto)

@Serializable
class SMessagesConn(val edges: List<SMessageEdge> = emptyList(), val pageInfo: SMessagesPageInfo = SMessagesPageInfo())

@Serializable
class SMessagesPageInfo(val hasNextPage: Boolean = false, val hasPreviousPage: Boolean = false)

@Serializable
class SMessagesResponse(val conversationMessages: SMessagesConn = SMessagesConn())

@Serializable
class SGuildDto(
    val id: String = "",
    val slug: String = "",
    val name: String = "",
    val tag: String? = null,
    val level: Int? = null,
    val experience: JsonElement? = null,
    val nextLevelExperience: JsonElement? = null,
    val points: JsonElement? = null,
    val rank: Int? = null,
    val score: JsonElement? = null,
    val members: Int? = null,
    val maxMembers: Int? = null,
    val inviteOnly: Boolean = false,
    val avatar: Cover? = null,
    val banner: Cover? = null,
    val description: List<TiptapBlockDto>? = null,
    val viewerIsMember: Boolean = false,
)

@Serializable
class SGuildEdge(val node: SGuildDto)

@Serializable
class SGuildsConn(val edges: List<SGuildEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class SGuildsResponse(val guilds: SGuildsConn = SGuildsConn())

@Serializable
class SGuildResponse(val guild: SGuildDto? = null)

@Serializable
class SGuildMemberDto(val level: Int? = null, val points: JsonElement? = null, val role: String? = null, val user: SUser? = null)

@Serializable
class SGuildMemberEdge(val node: SGuildMemberDto)

@Serializable
class SGuildMembersConn(val edges: List<SGuildMemberEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class SGuildMembersResponse(val guildMembers: SGuildMembersConn = SGuildMembersConn())

@Serializable
class SCardDto(val titles: List<Title>? = null, val rank: JsonElement? = null, val image: Cover? = null)

@Serializable
class SUserCardDto(val quantity: Int? = null, val modifier: JsonElement? = null, val locked: Boolean = false, val card: SCardDto? = null)

@Serializable
class SUserCardEdge(val node: SUserCardDto)

@Serializable
class SUserCardsConn(val edges: List<SUserCardEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class SUserCardsResponse(val userCards: SUserCardsConn = SUserCardsConn())

// ---- Domain

data class ConversationItem(val id: String, val title: String, val avatar: String?, val last: String, val date: String?, val unread: Int)

data class MessageItem(val id: String, val cursor: String?, val author: String, val avatar: String?, val text: String, val date: String?, val mine: Boolean, val canDelete: Boolean)

data class GuildItem(val id: String, val slug: String, val name: String, val level: Int?, val avatar: String?, val rank: Int?, val score: String?)

data class GuildProfile(
    val item: GuildItem,
    val tag: String?,
    val banner: String?,
    val description: String,
    val members: Int?,
    val maxMembers: Int?,
    val points: String?,
    val inviteOnly: Boolean,
    val viewerIsMember: Boolean,
)

data class GuildMemberItem(val name: String, val avatar: String?, val level: Int?, val role: String?)

data class UserCardItem(val title: String, val rank: String?, val image: String?, val quantity: Int, val modifier: String?, val locked: Boolean)

/** Переписка, гильдии и карточки (запросы восстановлены по js сайта). */
class SocialRepository(private val api: SenkuroApi) {

    private fun List<TiptapBlockDto>?.plain(): String =
        orEmpty().joinToString("\n") { b -> b.content.orEmpty().joinToString("") { it.text.orEmpty() } }.trim()

    private fun JsonElement?.s(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    /** rank/modifier на сервере могут быть строкой или объектом: берём читаемое поле. */
    private fun JsonElement?.label(): String? = when (this) {
        null, is JsonNull -> null
        is JsonPrimitive -> content
        is kotlinx.serialization.json.JsonObject -> listOf("name", "title", "slug", "value", "type").firstNotNullOfOrNull { (get(it) as? JsonPrimitive)?.content }
        else -> null
    }

    private fun List<Title>?.pick(): String? =
        orEmpty().let { l -> l.firstOrNull { it.lang.equals("RU", true) } ?: l.firstOrNull { it.lang.equals("EN", true) } ?: l.firstOrNull() }?.content

    // ---- переписка

    suspend fun conversations(after: String?): Pair<List<ConversationItem>, String?> {
        val vars = buildJsonObject { if (after != null) put("after", after) }
        val c = api.execute("fetchConversations", "fetchConversations", vars, SConversationsResponse.serializer()).conversations
        val items = c.edges.map { e ->
            val n = e.node
            ConversationItem(
                id = n.id,
                title = n.name?.takeIf { it.isNotBlank() } ?: n.participantUser?.name ?: "Диалог",
                avatar = n.avatar?.original?.url ?: n.participantUser?.avatar?.original?.url,
                last = n.lastMessage?.let { m -> (m.user?.name?.let { "$it: " }.orEmpty()) + m.content.plain() }.orEmpty(),
                date = n.lastMessage?.createdAt?.take(10),
                unread = n.viewerParticipant?.unreadCount ?: 0,
            )
        }
        return items to c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }

    /** Последние сообщения; before — курсор для подгрузки более старых. Возвращает в хронологическом порядке. */
    suspend fun messages(conversationId: String, before: String?): Pair<List<MessageItem>, Boolean> {
        val vars = buildJsonObject {
            put("conversationId", conversationId)
            put("last", 30)
            if (before != null) put("before", before)
        }
        val c = api.execute("fetchConversationMessages", "fetchConversationMessages", vars, SMessagesResponse.serializer()).conversationMessages
        val list = c.edges.map { e ->
            val m = e.node
            MessageItem(m.id, e.cursor, m.user?.name.orEmpty(), m.user?.avatar?.original?.url, m.content.plain(),
                m.createdAt?.replace('T', ' ')?.take(16), m.viewerDidAuthor, m.viewerCanDelete)
        }
        // Сайт получает сообщения от новых к старым; отдаём по возрастанию времени.
        val chronological = if (list.size > 1 && (list.first().date.orEmpty() > list.last().date.orEmpty())) list.reversed() else list
        return chronological to c.pageInfo.hasPreviousPage
    }

    private fun body(text: String) = JsonArray(
        text.split("\n").map { line ->
            buildJsonObject {
                put("type", "paragraph")
                if (line.isNotEmpty()) put("content", JsonArray(listOf(buildJsonObject { put("type", "text"); put("text", line) })))
            }
        },
    )

    suspend fun send(conversationId: String, text: String) {
        val vars = buildJsonObject {
            put("input", buildJsonObject {
                put("toId", conversationId)
                put("content", body(text))
            })
        }
        api.execute("createConversationMessage", "createConversationMessage", vars, JsonElement.serializer())
    }

    suspend fun markRead(conversationId: String, userId: String, messageId: String) {
        val vars = buildJsonObject {
            put("input", buildJsonObject {
                put("conversationId", conversationId)
                put("messageId", messageId)
                put("userId", userId)
            })
        }
        api.execute("readConversationMessage", "readConversationMessage", vars, JsonElement.serializer())
    }

    suspend fun deleteMessage(conversationId: String, messageId: String) {
        val vars = buildJsonObject {
            put("input", buildJsonObject {
                put("conversationId", conversationId)
                put("messageIds", JsonArray(listOf(JsonPrimitive(messageId))))
            })
        }
        api.execute("bulkDeleteConversationMessages", "bulkDeleteConversationMessages", vars, JsonElement.serializer())
    }

    // ---- гильдии

    private fun SGuildDto.item() = GuildItem(id, slug, name, level, avatar?.original?.url, rank, score.s())

    suspend fun guilds(after: String?): Pair<List<GuildItem>, String?> {
        val vars = buildJsonObject {
            if (after != null) put("after", after)
            put("orderBy", buildJsonObject { put("field", "RANK"); put("direction", "ASC") })
        }
        val c = api.execute("fetchGuilds", "fetchGuilds", vars, SGuildsResponse.serializer()).guilds
        return c.edges.map { it.node.item() } to c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }

    suspend fun guild(slug: String): GuildProfile? =
        api.execute("fetchGuild", "fetchGuild", buildJsonObject { put("slug", slug) }, SGuildResponse.serializer()).guild?.let { g ->
            GuildProfile(g.item(), g.tag, g.banner?.original?.url, g.description.plain(), g.members, g.maxMembers,
                g.points.s(), g.inviteOnly, g.viewerIsMember)
        }

    suspend fun guildMembers(guildId: String, after: String?): Pair<List<GuildMemberItem>, String?> {
        val vars = buildJsonObject {
            put("first", 30)
            put("guildId", guildId)
            if (after != null) put("after", after)
        }
        val c = api.execute("fetchGuildMembers", "fetchGuildMembers", vars, SGuildMembersResponse.serializer()).guildMembers
        return c.edges.map { e ->
            GuildMemberItem(e.node.user?.name.orEmpty(), e.node.user?.avatar?.original?.url, e.node.level, e.node.role)
        } to c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }

    // ---- карточки

    suspend fun userCards(userId: String, after: String?): Pair<List<UserCardItem>, String?> {
        val vars = buildJsonObject {
            put("userId", userId)
            if (after != null) put("after", after)
        }
        val c = api.execute("fetchUserCards", "fetchUserCards", vars, SUserCardsResponse.serializer()).userCards
        return c.edges.mapNotNull { e ->
            val card = e.node.card ?: return@mapNotNull null
            UserCardItem(card.titles.pick() ?: "Карточка", card.rank.label(), card.image?.original?.url,
                e.node.quantity ?: 1, e.node.modifier.label(), e.node.locked)
        } to c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }
}
