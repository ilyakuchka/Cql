package me.senkuro.unofficial.data

import kotlinx.serialization.Serializable

@Serializable
class Title(val lang: String = "", val content: String = "")

@Serializable
class ImageSize(val url: String = "")

@Serializable
class Cover(val original: ImageSize? = null)

@Serializable
class Label(
    val id: String = "",
    val rootId: String? = null,
    val slug: String = "",
    val titles: List<Title> = emptyList(),
)

@Serializable
class Person(val name: String = "")

@Serializable
class Staff(val roles: List<String> = emptyList(), val person: Person = Person())

@Serializable
class Localization(val lang: String = "", val description: String? = null)

@Serializable
class MangaCardDto(
    val id: String? = null,
    val slug: String = "",
    val titles: List<Title>? = null,
    val originalName: Title? = null,
    val alternativeNames: List<Title>? = null,
    val cover: Cover? = null,
    val status: String? = null,
    val rating: String? = null,
)

@Serializable
class MangaInfoDto(
    val id: String = "",
    val slug: String = "",
    val titles: List<Title>? = null,
    val originalName: Title? = null,
    val alternativeNames: List<Title>? = null,
    val localizations: List<Localization>? = null,
    val type: String? = null,
    val status: String? = null,
    val rating: String? = null,
    val formats: List<String>? = null,
    val labels: List<Label>? = null,
    val translationStatus: String? = null,
    val cover: Cover? = null,
    val mainStaff: List<Staff>? = null,
)

@Serializable
data class ChapterDto(
    val id: String = "",
    val slug: String = "",
    val branchId: String = "",
    val name: String? = null,
    val teamIds: List<String> = emptyList(),
    val number: String = "",
    val volume: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
class TeamDto(val id: String = "", val slug: String = "", val name: String = "")

@Serializable
class ChaptersDto(
    val message: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
    val teams: List<TeamDto> = emptyList(),
)

@Serializable
class PageDto(val url: String = "")

@Serializable
class PagesDto(val pages: List<PageDto> = emptyList())

@Serializable
class FiltersDto(val labels: List<Label> = emptyList())

// ---- обёртки ответов (data) ----

@Serializable
class SearchResponse(val mangaTachiyomiSearch: SearchPageDto = SearchPageDto())

@Serializable
class SearchPageDto(val mangas: List<MangaCardDto> = emptyList())

@Serializable
class InfoResponse(val mangaTachiyomiInfo: MangaInfoDto? = null)

@Serializable
class ChaptersResponse(val mangaTachiyomiChapters: ChaptersDto = ChaptersDto())

@Serializable
class PagesResponse(val mangaTachiyomiChapterPages: PagesDto = PagesDto())

@Serializable
class FiltersResponse(val mangaTachiyomiSearchFilters: FiltersDto = FiltersDto())

@Serializable
class Edge(val node: MangaCardDto)

@Serializable
class Connection(val edges: List<Edge> = emptyList())

@Serializable
class MangasResponse(val mangas: Connection = Connection())

@Serializable
class PopularResponse(val mangaPopularByPeriod: List<MangaCardDto> = emptyList())

@Serializable
class RecommendationsResponse(val mangaRecommendations: List<MangaCardDto> = emptyList())

@Serializable
class ReaderImage(val url: String = "")

@Serializable
class ReaderImageSizes(val original: ReaderImage? = null)

@Serializable
class ReaderPageDto(val id: String = "", val image: ReaderImageSizes? = null)

@Serializable
class ReaderChapterDto(val pages: List<ReaderPageDto> = emptyList())

@Serializable
class ReaderResponse(val mangaChapter: ReaderChapterDto? = null)

// ---------------------------------------------------------------- Аккаунт и закладки

@Serializable
class UserDto(val id: String = "", val slug: String = "", val name: String = "", val avatar: Cover? = null)

@Serializable
class CollectionPreferenceDto(val name: String? = null)

@Serializable
class CollectionDto(
    val id: String = "",
    val slug: String = "",
    val name: String = "",
    val entityType: String? = null,
    val system: Boolean = false,
    val preference: CollectionPreferenceDto? = null,
)

@Serializable
class ViewerDto(val user: UserDto? = null, val userBookmarkCollections: List<CollectionDto> = emptyList())

@Serializable
class ViewerResponse(val viewer: ViewerDto? = null)

@Serializable
class BookmarkNodeDto(
    val entityId: String? = null,
    val collectionId: String? = null,
    val page: Int? = null,
    val number: kotlinx.serialization.json.JsonElement? = null,
    val entity: MangaCardDto? = null,
)

@Serializable
class BookmarkEdge(val node: BookmarkNodeDto)

@Serializable
class PageInfoDto(val hasNextPage: Boolean = false, val endCursor: String? = null)

@Serializable
class BookmarksConn(val edges: List<BookmarkEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class BookmarksResponse(val userBookmarks: BookmarksConn = BookmarksConn())

@Serializable
class SuccessDto(val success: Boolean = false)

@Serializable
class MutationResponse(val updateUserBookmark: SuccessDto? = null, val deleteUserBookmark: SuccessDto? = null)

// ---------------------------------------------------------------- Уведомления

@Serializable
class UnreadResponse(val viewerUnreadNotifications: kotlinx.serialization.json.JsonElement? = null)

@Serializable
class NotifNodeDto(
    @kotlinx.serialization.SerialName("__typename") val typename: String? = null,
    val id: String? = null,
    val name: String? = null,
    val title: String? = null,
    val slug: String = "",
    val originalName: Title? = null,
    val titles: List<Title>? = null,
    val cover: Cover? = null,
    val number: kotlinx.serialization.json.JsonElement? = null,
    val volume: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
class NotifChapterRef(val id: String? = null, val number: kotlinx.serialization.json.JsonElement? = null)

@Serializable
class NotifPayloadDto(val chapters: List<NotifChapterRef>? = null)

@Serializable
class NotifFeedDto(
    val id: String = "",
    val type: String? = null,
    val lastEventAt: String? = null,
    val eventCount: Int? = null,
    val notifiable: NotifNodeDto? = null,
    val context: NotifNodeDto? = null,
    val payload: NotifPayloadDto? = null,
)

@Serializable
class NotifEdge(val node: NotifFeedDto)

@Serializable
class NotifConn(val edges: List<NotifEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class NotifResponse(val notificationFeeds: NotifConn = NotifConn())

@Serializable
class BulkResultDto(val success: Boolean = false, val notificationsAffected: Int? = null)

@Serializable
class BulkResponse(val bulkReadNotifications: BulkResultDto? = null, val bulkDeleteNotifications: BulkResultDto? = null)

@Serializable
class ProfileUserDto(
    val id: String = "",
    val slug: String = "",
    val name: String = "",
    val verified: Boolean = false,
    val level: Int? = null,
    val createdAt: String? = null,
    val lastOnlineAt: String? = null,
    val avatar: Cover? = null,
    val banner: Cover? = null,
)

@Serializable
class ProfileResponse(val user: ProfileUserDto? = null)

@Serializable
class AchievementRefDto(val id: String = "", val slug: String = "")

@Serializable
class AchievementLevelDto(
    val titles: List<Title> = emptyList(),
    val threshold: kotlinx.serialization.json.JsonElement? = null,
    val points: Int? = null,
    val cover: Cover? = null,
)

@Serializable
class UserAchievementDto(
    val progress: kotlinx.serialization.json.JsonElement? = null,
    val level: Int? = null,
    val completedAt: String? = null,
    val achievement: AchievementRefDto? = null,
    val currentLevel: AchievementLevelDto? = null,
)

@Serializable
class AchievementsResponse(val userAchievements: List<UserAchievementDto> = emptyList())

@Serializable
class TiptapChildDto(val text: String? = null)

@Serializable
class TiptapBlockDto(val content: List<TiptapChildDto>? = null)

@Serializable
class CommentAuthorDto(val id: String = "", val name: String = "", val avatar: Cover? = null)

@Serializable
class CommentDto(
    val id: String = "",
    val threadId: String? = null,
    val parentId: String? = null,
    val depth: Int? = null,
    val content: List<TiptapBlockDto>? = null,
    val upvotes: Int? = null,
    val downvotes: Int? = null,
    val createdAt: String? = null,
    val deletedAt: String? = null,
    val viewerDidAuthor: Boolean = false,
    val viewerCanUpdate: Boolean = false,
    val viewerCanDelete: Boolean = false,
    val author: CommentAuthorDto? = null,
    val replies: List<CommentDto>? = null,
)

@Serializable
class CommentEdge(val node: CommentDto)

@Serializable
class CommentsConn(val edges: List<CommentEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class CommentsResponse(val comments: CommentsConn = CommentsConn())

@Serializable
class ViewerVoteManga(val viewerVote: kotlinx.serialization.json.JsonElement? = null)

@Serializable
class ViewerStateResponse(val manga: ViewerVoteManga? = null)

@Serializable
class NextChapterDto(
    val number: kotlinx.serialization.json.JsonElement? = null,
    val manga: MangaCardDto? = null,
)

@Serializable
class NextChaptersResponse(val mangaBookmarkNextChapters: List<NextChapterDto> = emptyList())

@Serializable
class RelationNodeDto(val relation: String? = null, val destination: MangaCardDto? = null)

@Serializable
class RelationEdge(val node: RelationNodeDto)

@Serializable
class RelationsConn(val edges: List<RelationEdge> = emptyList())

@Serializable
class RelationsResponse(val mangaRelations: RelationsConn = RelationsConn())

@Serializable
class CharacterDto(val id: String = "", val name: String = "", val titles: List<Title>? = null, val cover: Cover? = null)

@Serializable
class CharacterNodeDto(val role: String? = null, val character: CharacterDto? = null)

@Serializable
class CharacterEdge(val node: CharacterNodeDto)

@Serializable
class CharactersConn(val edges: List<CharacterEdge> = emptyList())

@Serializable
class CharactersResponse(val mangaCharacters: CharactersConn = CharactersConn())

@Serializable
class PopularQueriesResponse(val popularTodaySearchQueries: List<String> = emptyList())

@Serializable
class SiteChapterEdge(val node: ChapterDto)

@Serializable
class SiteChaptersConn(val edges: List<SiteChapterEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class SiteChaptersResponse(val mangaChapters: SiteChaptersConn = SiteChaptersConn())
