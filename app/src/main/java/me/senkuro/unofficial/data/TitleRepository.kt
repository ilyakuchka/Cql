package me.senkuro.unofficial.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ---- DTO запроса fetchMangaPage (поля как на сайте; всё с значениями по умолчанию)

@Serializable class TLink(val url: String = "", val type: String? = null, val service: String? = null)
@Serializable class TLogo(val original: ImageSize? = null)
@Serializable class TStudio(val id: String = "", val slug: String = "", val name: String = "", val titles: List<Title>? = null, val logotype: TLogo? = null)
@Serializable class TRange(val start: JsonElement? = null, val end: JsonElement? = null)
@Serializable class TVolume(val volume: JsonElement? = null, val ranges: List<TRange>? = null)
@Serializable class TTeam(val id: String = "", val slug: String = "", val name: String = "", val verified: Boolean = false, val avatar: TLogo? = null)
@Serializable class TTeamActivity(val volumes: List<TVolume>? = null, val team: TTeam? = null)
@Serializable class TBranch(val id: String = "", val lang: String? = null, val chapters: Int? = null, val translitionStatus: String? = null, val primaryBranch: Boolean = false, val teamActivities: List<TTeamActivity>? = null)
@Serializable class TCollectionCount(val collectionId: String = "", val count: Int = 0)
@Serializable class TScoreCount(val score: Int = 0, val count: Int = 0)
@Serializable class TRecommendation(
    val id: String = "", val slug: String = "", val originalName: Title? = null, val titles: List<Title>? = null,
    val status: String? = null, val rating: String? = null, val score: JsonElement? = null, val cover: Cover? = null,
)
@Serializable class TManga(
    val id: String = "", val slug: String = "", val type: String? = null, val status: String? = null,
    val formats: List<String>? = null, val source: String? = null, val translitionStatus: String? = null,
    val views: JsonElement? = null, val score: JsonElement? = null, val totalChaptersViews: JsonElement? = null,
    val originCountry: String? = null, val releasedOn: String? = null, val completedOn: String? = null,
    val rating: String? = null, val chapters: Int? = null, val isLicensed: Boolean = false, val commentsEnabled: Boolean = true,
    val links: List<TLink>? = null, val publishers: List<TStudio>? = null, val branches: List<TBranch>? = null,
    val userBookmarkCollectionDistributions: List<TCollectionCount>? = null,
    val scoreDistributions: List<TScoreCount>? = null,
    val recommendations: List<TRecommendation>? = null,
    val viewerVote: JsonElement? = null,
)
@Serializable class TMangaResponse(val manga: TManga? = null)

// ---- Домен

data class TitleLink(val url: String, val label: String)
data class TitleTeam(val slug: String, val name: String, val avatar: String?, val verified: Boolean, val volumes: List<String>)
data class TitlePublisher(val slug: String, val name: String, val logo: String?)
data class TitleListCount(val collectionId: String, val count: Int)

/** Данные вкладок «О тайтле» и «Статистика», которых нет в кратком tachiyomi-ответе. */
data class TitlePage(
    val mangaId: String,
    val score: Double?,
    val votes: Int,
    val views: String?,
    val year: String?,
    val country: String?,
    val source: String?,
    val chapters: Int?,
    val formats: List<String>,
    val links: List<TitleLink>,
    val publishers: List<TitlePublisher>,
    val teams: List<TitleTeam>,
    val scores: List<TitleListCount>,
    val lists: List<TitleListCount>,
    val recommendations: List<MangaItem>,
    val commentsEnabled: Boolean,
    val viewerVote: Int?,
)

class TitleRepository(private val api: SenkuroApi) {

    private fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private fun List<Title>?.ru(): String? =
        orEmpty().let { l -> l.firstOrNull { it.lang.equals("RU", true) } ?: l.firstOrNull { it.lang.equals("EN", true) } ?: l.firstOrNull() }?.content

    private fun compact(n: Double): String = when {
        n >= 1_000_000 -> "%.1fM".format(java.util.Locale.US, n / 1_000_000)
        n >= 1_000 -> "%.1fK".format(java.util.Locale.US, n / 1_000)
        else -> n.toLong().toString()
    }

    suspend fun page(slug: String): TitlePage? {
        val m = api.execute("fetchMangaPage", "fetchMangaPage", buildJsonObject { put("slug", slug) }, TMangaResponse.serializer()).manga ?: return null
        val teams = LinkedHashMap<String, TitleTeam>()
        m.branches.orEmpty().forEach { b ->
            b.teamActivities.orEmpty().forEach { a ->
                val t = a.team ?: return@forEach
                val vols = a.volumes.orEmpty().map { v ->
                    "Том ${v.volume.str() ?: "?"}: " + v.ranges.orEmpty().joinToString(", ") { r ->
                        val s = r.start.str() ?: "?"; val e = r.end.str() ?: s
                        if (s == e) s else "$s–$e"
                    }
                }
                val old = teams[t.id]
                teams[t.id] = TitleTeam(t.slug, t.name, t.avatar?.original?.url, t.verified, (old?.volumes.orEmpty() + vols).distinct())
            }
        }
        val rec = m.recommendations.orEmpty().map { r ->
            MangaItem(r.id, r.slug, r.titles.ru() ?: r.originalName?.content ?: r.slug, r.cover?.original?.url)
        }
        return TitlePage(
            mangaId = m.id,
            score = m.score.num(),
            votes = m.scoreDistributions.orEmpty().sumOf { it.count },
            views = m.views.num()?.let(::compact),
            year = m.releasedOn?.take(4),
            country = m.originCountry,
            source = m.source,
            chapters = m.chapters,
            formats = m.formats.orEmpty(),
            links = m.links.orEmpty().filter { it.url.isNotBlank() }.map { TitleLink(it.url, it.service?.takeIf { s -> s.isNotBlank() } ?: it.type ?: it.url) },
            publishers = m.publishers.orEmpty().map { TitlePublisher(it.slug, it.name.ifBlank { it.titles.ru().orEmpty() }, it.logotype?.original?.url) },
            teams = teams.values.toList(),
            scores = m.scoreDistributions.orEmpty().map { TitleListCount(it.score.toString(), it.count) }.sortedByDescending { it.collectionId.toInt() },
            lists = m.userBookmarkCollectionDistributions.orEmpty().map { TitleListCount(it.collectionId, it.count) }.sortedByDescending { it.count },
            recommendations = rec,
            commentsEnabled = m.commentsEnabled,
            viewerVote = m.viewerVote.num()?.toInt(),
        )
    }
}
