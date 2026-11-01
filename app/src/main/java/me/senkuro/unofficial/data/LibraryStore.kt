package me.senkuro.unofficial.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Локальная библиотека: избранное, история чтения и прочитанные главы (SharedPreferences + JSON). */
class LibraryStore(context: Context) {

    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val favoritesSer = ListSerializer(MangaItem.serializer())
    private val historySer = ListSerializer(HistoryEntry.serializer())
    private val readSer = SetSerializer(String.serializer())

    private val _favorites = MutableStateFlow(load("favorites", favoritesSer, emptyList()))
    private val _history = MutableStateFlow(load("history", historySer, emptyList()))
    private val _readIds = MutableStateFlow(load("read", readSer, emptySet()))

    val favorites: StateFlow<List<MangaItem>> = _favorites.asStateFlow()
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()
    val readIds: StateFlow<Set<String>> = _readIds.asStateFlow()

    fun toggleFavorite(item: MangaItem) {
        val current = _favorites.value
        val next = if (current.any { it.id == item.id }) {
            current.filterNot { it.id == item.id }
        } else {
            listOf(item) + current
        }
        _favorites.value = next
        save("favorites", favoritesSer, next)
    }

    fun recordRead(item: MangaItem, chapter: Chapter) {
        val read = _readIds.value + chapter.id
        _readIds.value = read
        save("read", readSer, read)

        val entry = HistoryEntry(item, chapter.id, chapter.title, System.currentTimeMillis())
        val history = (listOf(entry) + _history.value.filterNot { it.manga.id == item.id }).take(MAX_HISTORY)
        _history.value = history
        save("history", historySer, history)
    }

    fun clearHistory() {
        _history.value = emptyList()
        save("history", historySer, emptyList())
    }

    private fun <T> load(key: String, serializer: KSerializer<T>, default: T): T = try {
        prefs.getString(key, null)?.let { json.decodeFromString(serializer, it) } ?: default
    } catch (_: Exception) {
        default
    }

    private fun <T> save(key: String, serializer: KSerializer<T>, value: T) {
        prefs.edit().putString(key, json.encodeToString(serializer, value)).apply()
    }

    private companion object {
        const val MAX_HISTORY = 100
    }
}
