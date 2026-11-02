package me.senkuro.unofficial.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Локальные отметки прочитанных глав (для «Продолжить»). Избранное и история заменены закладками сайта. */
class LibraryStore(context: Context) {

    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val readSer = SetSerializer(String.serializer())

    private val _readIds = MutableStateFlow(load())
    val readIds: StateFlow<Set<String>> = _readIds.asStateFlow()

    fun recordRead(@Suppress("UNUSED_PARAMETER") item: MangaItem, chapter: Chapter) {
        val read = _readIds.value + chapter.id
        _readIds.value = read
        prefs.edit().putString("read", json.encodeToString(readSer, read)).apply()
    }

    private fun load(): Set<String> = try {
        prefs.getString("read", null)?.let { json.decodeFromString(readSer, it) } ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }
}
