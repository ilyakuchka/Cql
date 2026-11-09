package me.senkuro.unofficial.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ---- DTO (запросы собраны по схеме сайта; поля могут отсутствовать — всё с значениями по умолчанию)

@Serializable class ECard(val id: String = "", val slug: String = "", val titles: List<Title>? = null, val rank: JsonElement? = null, val image: Cover? = null)
@Serializable class EPreview(val modifier: JsonElement? = null, val quantity: Int? = null, val card: ECard? = null)
@Serializable class EAuthor(val name: String = "")

@Serializable class EDeck(
    val id: String = "", val slug: String = "", val titles: List<Title>? = null, val quantity: Int? = null,
    val upvotes: Int? = null, val downvotes: Int? = null, val views: Int? = null, val createdAt: String? = null,
    val viewerDidAuthor: Boolean = false, val viewerCanDelete: Boolean = false, val viewerVoteDirection: Boolean? = null,
    val author: EAuthor? = null, val previewCards: List<EPreview>? = null, val cards: List<EPreview>? = null,
)
@Serializable class EDeckEdge(val node: EDeck)
@Serializable class EDeckConn(val edges: List<EDeckEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())
@Serializable class EDecksResponse(val cardDecks: EDeckConn = EDeckConn())
@Serializable class EDeckResponse(val cardDeck: EDeck? = null)
@Serializable class ECreatedDeck(val slug: String = "", val id: String = "")
@Serializable class ECreateDeckPayload(val deck: ECreatedDeck? = null)
@Serializable class ECreateDeckResponse(val createCardDeck: ECreateDeckPayload? = null)

@Serializable class EPack(
    val id: String = "", val slug: String = "", val titles: List<Title>? = null, val cover: Cover? = null,
    val credits: Int? = null, val points: Int? = null, val quantity: Int? = null, val pickCount: Int? = null,
    val viewerFreeOpens: Int? = null, val viewerOpens: Int? = null, val maxRolls: Int? = null, val pickLimit: Int? = null, val endAt: String? = null,
    val dropRates: List<EDrop>? = null, val coverCards: List<ECard>? = null,
)
@Serializable class EDrop(val rank: JsonElement? = null, val chance: JsonElement? = null)
@Serializable class EPackEdge(val node: EPack)
@Serializable class EPackConn(val edges: List<EPackEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())
@Serializable class EPacksResponse(val cardPacks: EPackConn = EPackConn())
@Serializable class EPackResponse(val cardPack: EPack? = null)
@Serializable class EOpen(val id: String = "", val completedAt: String? = null, val resultCards: List<ECard>? = null)
@Serializable class EOpenPayload(val opens: List<EOpen> = emptyList())
@Serializable class EPendingOpen(val id: String = "", val pack: EPack? = null, val resultCards: List<ECard>? = null)
@Serializable class EPendingOpensResponse(val viewerPendingCardPackOpens: List<EPendingOpen> = emptyList())
@Serializable class EOpenResponse(val openCardPack: EOpenPayload? = null)

@Serializable class ESlots(val baseSlots: Int? = null, val subscriptionSlots: Int? = null, val used: Int? = null, val available: Int? = null, val resetAt: String? = null)
@Serializable class ESlotsResponse(val forgeCardsSlots: ESlots? = null)
@Serializable class EForgePayload(val newCards: List<EPreview>? = null)
@Serializable class EForgeResponse(val forgeCards: EForgePayload? = null)

@Serializable class EPending(val id: String = "", val titles: List<Title>? = null, val rank: JsonElement? = null, val image: Cover? = null, val author: EAuthor? = null)
@Serializable class EChar(val titles: List<Title>? = null)
@Serializable class EContest(val id: String = "", val endAt: String? = null, val rank: JsonElement? = null, val character: EChar? = null, val pendingCards: List<EPending>? = null)
@Serializable class EContestResponse(val pendingCardContest: EContest? = null)

@Serializable class ECollectible(
    val id: String = "", val slug: String = "", val type: JsonElement? = null, val titles: List<Title>? = null,
    val price: Int? = null, val basePrice: Int? = null, val rating: JsonElement? = null, val image: Cover? = null,
    val viewerCollectible: EViewerCollectible? = null,
)
@Serializable class EViewerCollectible(val id: String = "", val active: Boolean = false)
@Serializable class ECollectibleEdge(val node: ECollectible)
@Serializable class ECollectibleConn(val edges: List<ECollectibleEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())
@Serializable class ECollectiblesResponse(val collectibles: ECollectibleConn = ECollectibleConn())

// ---- Domain

/** Карта пользователя с «сырым» модификатором (в кузницу/колоду он уходит без изменений). */
data class OwnedCard(val cardId: String, val slug: String, val title: String, val rank: String?, val image: String?, val quantity: Int, val modifier: JsonElement, val locked: Boolean) {
    val key get() = cardId + "|" + modifier.toString()
}

data class DeckCard(val cardId: String, val slug: String, val title: String, val rank: String?, val image: String?, val quantity: Int, val modifier: String?)
data class DeckItem(val id: String, val slug: String, val title: String, val author: String?, val quantity: Int, val up: Int, val down: Int, val views: Int, val preview: List<String>)
data class DeckDetails(val item: DeckItem, val date: String?, val cards: List<DeckCard>, val mine: Boolean, val canDelete: Boolean, val vote: Boolean?)
data class PackItem(val id: String, val slug: String, val title: String, val cover: String?, val credits: Int?, val points: Int?, val freeOpens: Int)
data class PendingOpen(val id: String, val packId: String, val packTitle: String, val pickLimit: Int, val cards: List<DroppedCard>)
data class PackDetails(val item: PackItem, val pickCount: Int, val maxRolls: Int, val expired: Boolean, val rates: List<Pair<String, String>>, val covers: List<String>)
data class DroppedCard(val slug: String, val title: String, val rank: String?, val image: String?, val modifier: String? = null, val quantity: Int = 1)
data class PackOpenResult(val cards: List<DroppedCard>, val needsChoice: Boolean)
data class ForgeSlots(val available: Int, val used: Int, val total: Int, val resetAt: String?)
data class ContestCard(val id: String, val title: String, val rank: String?, val image: String?, val author: String?)
data class Contest(val id: String, val character: String?, val rank: String?, val endAt: String?, val cards: List<ContestCard>)
data class ShopItem(val id: String, val title: String, val type: String, val price: Int?, val basePrice: Int?, val image: String?, val owned: Boolean, val active: Boolean)

/** Колоды, паки, кузница, турнир карт, магазин. Входные поля мутаций частично угаданы по соглашениям сайта. */
class EcosystemRepository(private val api: SenkuroApi) {
    private fun JsonElement?.s(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun List<Title>?.pick(): String? =
        orEmpty().let { l -> l.firstOrNull { it.lang.equals("RU", true) } ?: l.firstOrNull { it.lang.equals("EN", true) } ?: l.firstOrNull() }?.content

    private fun ECard.drop(modifier: JsonElement? = null, qty: Int = 1) =
        DroppedCard(slug, titles.pick() ?: "Карточка", rank.s(), image?.original?.url, modifier.s()?.takeIf { it != "0" }, qty)

    private suspend fun mutate(name: String, input: JsonObject) {
        api.execute(name, name, buildJsonObject { put("input", input) }, JsonElement.serializer())
    }

    // ---- мои карты (для выбора в колоду и кузницу)

    suspend fun ownedCards(userId: String, after: String?): Pair<List<OwnedCard>, String?> {
        val vars = buildJsonObject { put("userId", userId); if (after != null) put("after", after) }
        val raw = api.execute("fetchUserCards", "fetchUserCards", vars, EOwnedResponse.serializer()).userCards
        return raw.edges.mapNotNull { e ->
            val c = e.node.card ?: return@mapNotNull null
            if (c.id.isBlank()) return@mapNotNull null
            OwnedCard(c.id, c.slug, c.titles.pick() ?: "Карточка", c.rank.s(), c.image?.original?.url, e.node.quantity ?: 1, e.node.modifier ?: JsonPrimitive(0), e.node.locked)
        } to raw.pageInfo.endCursor.takeIf { raw.pageInfo.hasNextPage }
    }

    // ---- колоды

    private fun EDeck.item() = DeckItem(id, slug, titles.pick() ?: "Колода", author?.name, quantity ?: cards?.size ?: 0, upvotes ?: 0, downvotes ?: 0, views ?: 0,
        previewCards.orEmpty().mapNotNull { it.card?.image?.original?.url }.take(4))

    suspend fun decks(after: String?): Pair<List<DeckItem>, String?> {
        val c = api.execute("fetchCardDecks", "fetchCardDecks", buildJsonObject { if (after != null) put("after", after) }, EDecksResponse.serializer()).cardDecks
        return c.edges.map { it.node.item() } to c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }

    suspend fun deck(slug: String): DeckDetails? {
        val d = api.execute("fetchCardDeck", "fetchCardDeck", buildJsonObject { put("slug", slug) }, EDeckResponse.serializer()).cardDeck ?: return null
        val cards = d.cards.orEmpty().mapNotNull { p ->
            val c = p.card ?: return@mapNotNull null
            DeckCard(c.id, c.slug, c.titles.pick() ?: "Карточка", c.rank.s(), c.image?.original?.url, p.quantity ?: 1, p.modifier.s()?.takeIf { it != "0" })
        }
        return DeckDetails(d.item(), d.createdAt?.take(10), cards, d.viewerDidAuthor, d.viewerCanDelete, d.viewerVoteDirection)
    }

    suspend fun createDeck(title: String, cards: List<OwnedCard>, picked: Map<String, Int>): String? {
        val input = buildJsonObject {
            put("titles", JsonArray(listOf(buildJsonObject { put("lang", "RU"); put("content", title) })))
            put("explicit", false)
            put("cards", JsonArray(cards.filter { (picked[it.key] ?: 0) > 0 }.map {
                buildJsonObject { put("cardId", it.cardId); put("modifier", it.modifier); put("quantity", picked.getValue(it.key)) }
            }))
        }
        return api.execute("createCardDeck", "createCardDeck", buildJsonObject { put("input", input) }, ECreateDeckResponse.serializer()).createCardDeck?.deck?.slug
    }

    suspend fun voteDeck(deckId: String, up: Boolean?) {
        if (up == null) mutate("deleteCardDeckVote", buildJsonObject { put("deckId", deckId) })
        else mutate("voteCardDeck", buildJsonObject { put("deckId", deckId); put("upvote", up) })
    }

    suspend fun deleteDeck(deckId: String) = mutate("deleteCardDeck", buildJsonObject { put("deckId", deckId) })

    // ---- паки

    private fun EPack.item() = PackItem(id, slug, titles.pick() ?: "Пак", cover?.original?.url ?: coverCards?.firstNotNullOfOrNull { it.image?.original?.url }, credits, points, viewerFreeOpens ?: 0)

    suspend fun packs(after: String?): Pair<List<PackItem>, String?> {
        val c = api.execute("fetchCardPacks", "fetchCardPacks", buildJsonObject { if (after != null) put("after", after) }, EPacksResponse.serializer()).cardPacks
        return c.edges.map { it.node.item() } to c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }

    suspend fun pack(slug: String): PackDetails? {
        val p = api.execute("fetchCardPack", "fetchCardPack", buildJsonObject { put("slug", slug) }, EPackResponse.serializer()).cardPack ?: return null
        return PackDetails(p.item(), p.pickCount ?: 1, p.maxRolls ?: 1, isExpired(p.endAt),
            p.dropRates.orEmpty().map { (it.rank.s() ?: "?") to (it.chance.s()?.toDoubleOrNull()?.let { v -> (if (v <= 1.0) v * 100 else v).let { x -> "%.2f%%".format(x) } } ?: "") },
            p.coverCards.orEmpty().mapNotNull { it.image?.original?.url })
    }

    private fun isExpired(endAt: String?): Boolean = runCatching {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        endAt != null && f.parse(endAt.take(19))!!.time < System.currentTimeMillis()
    }.getOrDefault(false)

    /** Открытия, ожидающие выбора карт (пока они есть, сервер не даёт открывать новые паки). */
    suspend fun pendingOpens(): List<PendingOpen> =
        api.execute("fetchPendingPackOpens", "fetchPendingPackOpens", buildJsonObject {}, EPendingOpensResponse.serializer()).viewerPendingCardPackOpens.map { o ->
            PendingOpen(o.id, o.pack?.id.orEmpty(), o.pack?.titles.pick() ?: "Пак", o.pack?.pickLimit ?: o.pack?.pickCount ?: 1, o.resultCards.orEmpty().map { it.drop() })
        }

    /** indexes — позиции выбранных карт в списке выпавших (так делает сайт). */
    suspend fun chooseCards(open: PendingOpen, indexes: List<Int>) {
        val input = buildJsonObject {
            put("opens", JsonArray(listOf(buildJsonObject {
                put("openId", open.id); put("packId", open.packId); put("indexes", JsonArray(indexes.map { JsonPrimitive(it) }))
            })))
        }
        mutate("bulkChooseCardPackOpen", input)
    }

    /** currency: POINTS или CREDITS, rolls — число открытий (как на сайте). */
    suspend fun openPack(packId: String, userId: String, currency: String, rolls: Int): PackOpenResult {
        val input = buildJsonObject { put("packId", packId); put("userId", userId); put("currency", currency); put("rolls", rolls) }
        val opens = api.execute("openCardPack", "openCardPack", buildJsonObject { put("input", input) }, EOpenResponse.serializer()).openCardPack?.opens.orEmpty()
        return PackOpenResult(opens.flatMap { it.resultCards.orEmpty().map { c -> c.drop() } }, opens.any { it.completedAt == null })
    }

    // ---- кузница

    suspend fun forgeSlots(mode: String): ForgeSlots? =
        api.execute("fetchForgeCardsSlots", "fetchForgeCardsSlots", buildJsonObject { put("mode", mode) }, ESlotsResponse.serializer()).forgeCardsSlots?.let {
            ForgeSlots(it.available ?: 0, it.used ?: 0, (it.baseSlots ?: 0) + (it.subscriptionSlots ?: 0), it.resetAt)
        }

    /** mode: UPGRADE (переплавка), SPLIT (раскол), MERGE (объединение). */
    suspend fun forge(mode: String, cards: List<OwnedCard>, picked: Map<String, Int>): List<DroppedCard> {
        val input = buildJsonObject {
            put("mode", mode)
            put("cards", JsonArray(cards.filter { (picked[it.key] ?: 0) > 0 }.map {
                buildJsonObject { put("cardId", it.cardId); put("modifier", it.modifier); put("quantity", picked.getValue(it.key)) }
            }))
        }
        val r = api.execute("forgeCards", "forgeCards", buildJsonObject { put("input", input) }, EForgeResponse.serializer()).forgeCards
        return r?.newCards.orEmpty().mapNotNull { p -> p.card?.drop(p.modifier, p.quantity ?: 1) }
    }

    // ---- турнир карт

    suspend fun contest(): Contest? {
        val c = api.execute("fetchPendingCardContest", "fetchPendingCardContest", buildJsonObject {}, EContestResponse.serializer()).pendingCardContest ?: return null
        return Contest(c.id, c.character?.titles.pick(), c.rank.s(), c.endAt, c.pendingCards.orEmpty().map {
            ContestCard(it.id, it.titles.pick() ?: "Карточка", it.rank.s(), it.image?.original?.url, it.author?.name)
        })
    }

    suspend fun voteContest(contestId: String, pendingCardId: String) =
        mutate("votePendingCard", buildJsonObject { put("contestId", contestId); put("pendingCardId", pendingCardId) })

    // ---- магазин

    suspend fun shop(type: String, after: String?): Pair<List<ShopItem>, String?> {
        val vars = buildJsonObject { put("type", type); if (after != null) put("after", after) }
        val c = api.execute("fetchCollectibles", "fetchCollectibles", vars, ECollectiblesResponse.serializer()).collectibles
        return c.edges.map { e ->
            val n = e.node
            ShopItem(n.id, n.titles.pick() ?: "Товар", type, n.price, n.basePrice, n.image?.original?.url, n.viewerCollectible != null, n.viewerCollectible?.active == true)
        } to c.pageInfo.endCursor.takeIf { c.pageInfo.hasNextPage }
    }

    suspend fun equip(collectibleId: String, type: String, userId: String) =
        mutate("equipUserCollectible", buildJsonObject { put("collectibleId", collectibleId); put("collectibleType", type); put("userId", userId) })

    /** Покупка: поля ввода не подтверждены (на сайте мутация вызывается из чанка, которого нет в архиве). */
    suspend fun buy(collectibleId: String, userId: String) =
        mutate("createCollectibleOrder", buildJsonObject { put("collectibleId", collectibleId); put("userId", userId) })
}

@Serializable class EOwnedNode(val quantity: Int? = null, val modifier: JsonElement? = null, val locked: Boolean = false, val card: ECard? = null)
@Serializable class EOwnedEdge(val node: EOwnedNode)
@Serializable class EOwnedConn(val edges: List<EOwnedEdge> = emptyList(), val pageInfo: PageInfoDto = PageInfoDto())
@Serializable class EOwnedResponse(val userCards: EOwnedConn = EOwnedConn())
