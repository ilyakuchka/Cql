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
class ChapterDto(
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
class ReaderPageDto(val image: ReaderImageSizes? = null)

@Serializable
class ReaderChapterDto(val pages: List<ReaderPageDto> = emptyList())

@Serializable
class ReaderResponse(val mangaChapter: ReaderChapterDto? = null)
