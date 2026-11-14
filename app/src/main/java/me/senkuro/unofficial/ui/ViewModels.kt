package me.senkuro.unofficial.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.CatalogFilters
import me.senkuro.unofficial.data.Chapter
import me.senkuro.unofficial.data.FilterDefs
import me.senkuro.unofficial.data.ChaptersResult
import me.senkuro.unofficial.data.HomeData
import me.senkuro.unofficial.data.LabelItem
import me.senkuro.unofficial.data.MangaDetails
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.data.TopsTab
import me.senkuro.unofficial.data.SenkuroConfig

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Ready<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

private fun Throwable.readable(): String = message?.takeIf { it.isNotBlank() } ?: "Ошибка сети"

class HomeViewModel : ViewModel() {
    var state by mutableStateOf<UiState<HomeData>>(UiState.Loading)
        private set

    /** Период «Самого читаемого»: DAY (приходит вместе с главной), WEEK, MONTH (грузятся по выбору и кэшируются). */
    var popularPeriod by mutableStateOf("DAY")
        private set
    var popularOther by mutableStateOf<List<MangaItem>?>(null)
        private set
    var popularLoading by mutableStateOf(false)
        private set
    private val popularCache = HashMap<String, List<MangaItem>>()

    /** «Топ пользователей»: вкладки credits | experience. Первая грузится вместе с главной, вторая — при выборе; результаты кэшируются. */
    var topsTab by mutableStateOf("credits")
        private set
    var topsData by mutableStateOf<Map<String, TopsTab>>(emptyMap())
        private set
    var topsFailed by mutableStateOf<Set<String>>(emptySet())
        private set
    private var topsLoading = HashSet<String>()

    fun selectTopsTab(tab: String) {
        topsTab = tab
        loadTops(tab)
    }

    fun loadTops(tab: String, force: Boolean = false) {
        if (!force && (topsData.containsKey(tab) || tab in topsLoading)) return
        topsLoading.add(tab)
        topsFailed = topsFailed - tab
        viewModelScope.launch {
            try {
                val result = AppGraph.offers.tops(tab == "experience")
                topsData = topsData + (tab to result)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                topsFailed = topsFailed + tab
            } finally {
                topsLoading.remove(tab)
            }
        }
    }

    fun selectPopularPeriod(period: String) {
        if (period == popularPeriod && !popularLoading) return
        popularPeriod = period
        if (period == "DAY") { popularOther = null; popularLoading = false; return }
        popularCache[period]?.let { popularOther = it; popularLoading = false; return }
        popularLoading = true
        viewModelScope.launch {
            val list = try {
                AppGraph.repository.popular(period)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (popularPeriod != period) return@launch
            if (list != null) { popularCache[period] = list; popularOther = list }
            else { popularPeriod = "DAY"; popularOther = null }
            popularLoading = false
        }
    }

    init {
        load()
        // Смена домена: загружаем главную заново.
        viewModelScope.launch {
            AppGraph.domain.drop(1).collect { load() }
        }
    }

    fun load() {
        state = UiState.Loading
        popularPeriod = "DAY"; popularOther = null; popularLoading = false; popularCache.clear()
        topsData = emptyMap(); topsFailed = emptySet(); topsLoading.clear()
        loadTops(topsTab) // параллельно с главной, не после неё
        viewModelScope.launch {
            state = try {
                UiState.Ready(AppGraph.repository.home())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.readable())
            }
        }
    }
}

class CatalogViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set
    var filters by mutableStateOf(CatalogFilters())
        private set
    var items by mutableStateOf<List<MangaItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var endReached by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var labels by mutableStateOf<List<LabelItem>>(emptyList())
        private set

    private var job: Job? = null
    private var requestId = 0
    private var nextOffset = 0
    private var nextCursor: String? = null

    init {
        refresh()
        loadLabels()
        // Смена домена: сбрасываем фильтры по меткам (набор меток может отличаться) и грузим каталог заново.
        viewModelScope.launch {
            AppGraph.domain.drop(1).collect {
                labels = emptyList()
                filters = filters.cleared()
                loadLabels()
                refresh()
            }
        }
    }

    private fun loadLabels() {
        viewModelScope.launch {
            try {
                labels = AppGraph.repository.labels()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Метки необязательны: каталог работает и без них.
            }
        }
    }

    fun submit(text: String) {
        query = text
        refresh()
    }

    fun applyFilters(value: CatalogFilters) {
        filters = value
        refresh()
    }

    fun refresh() {
        job?.cancel()
        job = null
        requestId++
        items = emptyList()
        nextOffset = 0
        nextCursor = null
        endReached = false
        error = null
        loadMore()
    }

    fun loadMore() {
        if (endReached || job?.isActive == true) return
        val token = requestId
        loading = true
        error = null
        job = viewModelScope.launch {
            try {
                if (FilterDefs.usesSiteQuery(filters.orderField)) {
                    // Сортировка по датам — запросом сайта (курсорная постраничность, закладки фильтрует сервер).
                    val ids = AppGraph.account.value?.collections.orEmpty().associate { it.name to it.id }
                    val (page, next) = AppGraph.repository.searchSite(query, filters, ids, nextCursor)
                    if (token != requestId) return@launch
                    nextCursor = next
                    if (next == null) endReached = true
                    val known = items.mapTo(HashSet()) { it.id }
                    items = items + page.filter { it.id !in known }
                } else {
                    // Фильтр по статусу закладок в Tachiyomi-поиске работает на клиенте, и страница может целиком
                    // отсеяться: тогда догружаем следующие, пока что-то не появится (но не бесконечно).
                    var added = 0
                    var requests = 0
                    do {
                        val page = AppGraph.repository.search(query, filters, nextOffset)
                        if (token != requestId) return@launch
                        nextOffset += page.size
                        if (page.size < SenkuroConfig.SEARCH_PAGE_SIZE) endReached = true
                        val marks = AppGraph.marks.value
                        val known = items.mapTo(HashSet()) { it.id }
                        val fresh = page.filter { it.id !in known && filters.matchesBookmark(marks[it.id]) }
                        items = items + fresh
                        added += fresh.size
                        requests++
                    } while (added == 0 && !endReached && filters.hasBookmarkFilter && requests < 15)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == requestId) error = e.readable()
            } finally {
                if (token == requestId) loading = false
            }
        }
    }
}

class DetailsData(val details: MangaDetails, val chapters: ChaptersResult, val chaptersError: String?)

class DetailsViewModel : ViewModel() {
    var state by mutableStateOf<UiState<DetailsData>>(UiState.Loading)
        private set

    private var loadedId: String? = null

    fun load(mangaId: String, force: Boolean = false) {
        if (!force && loadedId == mangaId) return
        loadedId = mangaId
        state = UiState.Loading
        viewModelScope.launch {
            state = try {
                coroutineScope {
                    val repo = AppGraph.repository
                    val details = async { repo.details(mangaId) }
                    val chapters = async { runCatching { repo.chapters(mangaId, force = true) } }
                    val result = chapters.await()
                    UiState.Ready(
                        DetailsData(
                            details = details.await(),
                            chapters = result.getOrElse { ChaptersResult(emptyList(), null) },
                            chaptersError = result.exceptionOrNull()?.readable(),
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.readable())
            }
        }
    }
}

class ReaderData(
    val manga: MangaItem?,
    val chapter: Chapter,
    val pages: List<String>,
    val previous: Chapter?,
    val next: Chapter?,
)

class ReaderViewModel : ViewModel() {
    var state by mutableStateOf<UiState<ReaderData>>(UiState.Loading)
        private set

    private var loadedKey: String? = null

    fun load(mangaId: String, chapterId: String, force: Boolean = false) {
        val key = "$mangaId/$chapterId"
        if (!force && loadedKey == key) return
        loadedKey = key
        state = UiState.Loading
        viewModelScope.launch {
            state = try {
                val repo = AppGraph.repository
                val chapters = repo.chapters(mangaId)
                val chapter = chapters.chapters.firstOrNull { it.id == chapterId }
                    ?: throw IllegalStateException("Глава не найдена")
                val branch = chapters.branches.firstOrNull { it.id == chapter.branchId }?.chapters.orEmpty()
                val index = branch.indexOfFirst { it.id == chapterId }
                val pages = repo.pages(mangaId, chapter)
                val manga = runCatching { repo.details(mangaId).item }.getOrNull()
                if (manga != null) AppGraph.library.recordRead(manga, chapter)
                UiState.Ready(
                    ReaderData(
                        manga = manga,
                        chapter = chapter,
                        pages = pages,
                        previous = branch.getOrNull(index - 1),
                        next = branch.getOrNull(index + 1),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                me.senkuro.unofficial.data.ReaderErrorLog.add("Загрузка главы", "manga=$mangaId chapter=$chapterId", e)
                UiState.Error(e.readable())
            }
        }
    }
}
