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
class UserDto(val id: String = "", val slug: String = "", val name: String = "", val avatar: Cover? = null, val frame: Cover? = null)

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
class ViewerPermissionsDto(val canDisableAdvertisement: Boolean = false)

@Serializable
class ViewerDto(
    val user: UserDto? = null,
    val userBookmarkCollections: List<CollectionDto> = emptyList(),
    val permissions: ViewerPermissionsDto? = null,
)

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
    /** Текст комментария (для notifiable типа Comment). */
    val content: List<TiptapBlockDto>? = null,
)

@Serializable
class NotifChapterRef(val id: String? = null, val number: kotlinx.serialization.json.JsonElement? = null)

@Serializable
class NotifReplyDto(
    val id: String = "",
    val createdAt: String? = null,
    val content: List<TiptapBlockDto>? = null,
    val author: CommentAuthorDto? = null,
)

@Serializable
class NotifPayloadDto(
    val chapters: List<NotifChapterRef>? = null,
    val recentComments: List<NotifReplyDto>? = null,
    val recentUsers: List<CommentAuthorDto>? = null,
)

@Serializable
class NotifPrefDto(val type: String = "", val channel: String = "", val frequency: String? = null)

@Serializable
class NotifPrefsResponse(val userNotificationPreferences: List<NotifPrefDto> = emptyList())

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
class CommentAuthorDto(val id: String = "", val name: String = "", val avatar: Cover? = null, val frame: Cover? = null)

@Serializable
class CommentDto(
    val id: String = "",
    val threadId: String? = null,
    val parentId: String? = null,
    val depth: Int? = null,
    val content: List<TiptapBlockDto>? = null,
    val upvotes: Int? = null,
    val downvotes: Int? = null,
    val viewerVoteDirection: Boolean? = null,
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
class ViewerBookmarkDto(val number: kotlinx.serialization.json.JsonElement? = null, val page: Int? = null)

@Serializable
class ViewerVoteManga(val viewerVote: kotlinx.serialization.json.JsonElement? = null, val viewerBookmark: ViewerBookmarkDto? = null)

@Serializable
class BookmarkChapterDto(val id: String = "", val slug: String = "")

@Serializable
class BookmarkChapterResponse(val mangaBookmarkChapter: BookmarkChapterDto? = null)

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
class CharacterDto(val id: String = "", val slug: String = "", val name: String = "", val titles: List<Title>? = null, val cover: Cover? = null)

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

@Serializable
class PUserShortDto(val id: String = "", val slug: String = "", val name: String = "", val avatar: Cover? = null, val frame: Cover? = null)

@Serializable
class PStatsDto(
    val readChapters: Long? = null, val watchEpisodes: Long? = null, val comments: Long? = null,
    val friends: Long? = null, val subscribers: Long? = null, val follows: Long? = null,
    val achievements: Long? = null, val rank: Long? = null,
    val betterPercentile: Double? = null, val rateProjects: Long? = null,
)

@Serializable
class PCharacterDto(val id: String = "", val slug: String = "", val name: String = "", val cover: Cover? = null)

@Serializable
class PWaifuDto(val id: String = "", val relation: String? = null, val character: PCharacterDto? = null)

@Serializable
class PStatusCountDto(val entityType: String = "", val status: String = "", val count: Long = 0)

@Serializable
class PMangaPreviewDto(
    val id: String = "", val slug: String = "", val type: String? = null,
    val originalName: Title? = null, val titles: List<Title> = emptyList(), val cover: Cover? = null,
)

@Serializable
class PActivityMetaDto(
    @kotlinx.serialization.SerialName("__typename") val typename: String? = null,
    val status: String? = null,
    val numbers: List<kotlinx.serialization.json.JsonElement>? = null,
)

@Serializable
class PActivityEntityDto(
    @kotlinx.serialization.SerialName("__typename") val typename: String? = null,
    val id: String? = null, val slug: String? = null,
    val titles: List<Title> = emptyList(), val originalName: Title? = null,
)

@Serializable
class PActivityDto(
    val id: String = "", val type: String? = null, val createdAt: String? = null,
    val meta: PActivityMetaDto? = null, val entity: PActivityEntityDto? = null,
)

@Serializable
class ProfileExtraUserDto(
    val experience: Long? = null,
    val gender: String? = null,
    val birthdayOn: String? = null,
    val statistics: PStatsDto? = null,
    val lastFriends: List<PUserShortDto> = emptyList(),
    val lastFollows: List<PUserShortDto> = emptyList(),
    val waifus: List<PWaifuDto> = emptyList(),
    val bookmarkEntityStatusDistributions: List<PStatusCountDto> = emptyList(),
    val bookmarkMangaPreview: List<PMangaPreviewDto> = emptyList(),
    val lastActivities: List<PActivityDto> = emptyList(),
)

@Serializable
class ProfileExtraResponse(val user: ProfileExtraUserDto? = null)

@Serializable
class VariantDto(val url: String = "", val format: String? = null, val width: Int? = null, val height: Int? = null)

@Serializable
class AppearanceImageDto(
    val id: String? = null, val animation: Boolean? = null, val original: ImageSize? = null,
    val variants: List<VariantDto> = emptyList(),
)

@Serializable
class AppearanceUserDto(
    val avatar: AppearanceImageDto? = null,
    val frame: AppearanceImageDto? = null,
    val banner: AppearanceImageDto? = null,
    val wallpaper: AppearanceImageDto? = null,
    val status: AppearanceImageDto? = null,
)

@Serializable
class AppearanceResponse(val user: AppearanceUserDto? = null)

@Serializable
class RankUserDto(
    val id: String = "", val slug: String = "", val name: String = "", val level: Int? = null,
    val avatar: Cover? = null, val frame: Cover? = null,
)

@Serializable
class RankEntryDto(
    val watchEpisodes: Long? = null, val readChapters: Long? = null,
    val betterPercentile: Double? = null, val rank: Long? = null, val user: RankUserDto? = null,
)

@Serializable
class RankViewerStatsDto(val rank: Long? = null)

@Serializable
class RankViewerUserDto(val statistics: RankViewerStatsDto? = null)

@Serializable
class RankViewerDto(val user: RankViewerUserDto? = null)

@Serializable
class RanksResponse(val viewer: RankViewerDto? = null, val userRanksInsight: List<RankEntryDto> = emptyList())

@Serializable
class ActivityEdgeDto(val node: PActivityDto)

@Serializable
class ActivityConnDto(val edges: List<ActivityEdgeDto> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class ActivitiesResponse(val activities: ActivityConnDto = ActivityConnDto())

@Serializable
class ShortChapterDto(
    val id: String = "", val slug: String = "",
    val number: kotlinx.serialization.json.JsonElement? = null,
    val name: String? = null,
    val pages: List<ReaderPageDto> = emptyList(),
    val manga: MangaCardDto? = null,
)

@Serializable
class ShortEdgeDto(val node: ShortChapterDto)

@Serializable
class ShortConnDto(val edges: List<ShortEdgeDto> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())

@Serializable
class ShortsResponse(val shortsMangaChapters: ShortConnDto = ShortConnDto())
