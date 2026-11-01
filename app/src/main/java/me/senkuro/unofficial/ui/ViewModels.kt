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
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.CatalogFilters
import me.senkuro.unofficial.data.Chapter
import me.senkuro.unofficial.data.ChaptersResult
import me.senkuro.unofficial.data.HomeData
import me.senkuro.unofficial.data.LabelItem
import me.senkuro.unofficial.data.MangaDetails
import me.senkuro.unofficial.data.MangaItem
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

    init {
        load()
    }

    fun load() {
        state = UiState.Loading
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

    init {
        refresh()
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
                val page = AppGraph.repository.search(query, filters, nextOffset)
                if (token != requestId) return@launch
                nextOffset += page.size
                val known = items.mapTo(HashSet()) { it.id }
                items = items + page.filter { it.id !in known }
                if (page.size < SenkuroConfig.SEARCH_PAGE_SIZE) endReached = true
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
                    val chapters = async { runCatching { repo.chapters(mangaId, force) } }
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
                UiState.Error(e.readable())
            }
        }
    }
}
