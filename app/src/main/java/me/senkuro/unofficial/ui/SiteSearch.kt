package me.senkuro.unofficial.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.SearchHit

/**
 * Единый поиск по сайту (как на senkuro.me): поле, вкладки типов, «Ты искал» и «Популярные запросы».
 * Заменяет прежний отдельный пункт в настройках и ряд популярных запросов в каталоге.
 */
@Composable
fun SiteSearchScreen(
    onBack: () -> Unit,
    onManga: (String) -> Unit,
    onTeam: (String) -> Unit,
    onGuild: (String) -> Unit,
    onPerson: (String) -> Unit,
    onCharacter: (String) -> Unit,
) {
    val types = listOf("MANGA" to "Манга", "USER" to "Пользователи", "GUILD" to "Гильдии", "TEAM" to "Команды", "PERSON" to "Люди", "CHARACTER" to "Персонажи")
    val prefs = LocalContext.current.getSharedPreferences("search", android.content.Context.MODE_PRIVATE)
    fun loadHistory(): List<String> = try {
        prefs.getString("history", "").orEmpty().split('\n').filter { it.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }
    var history by remember { mutableStateOf(loadHistory()) }
    fun saveHistory(list: List<String>) {
        history = list
        try { prefs.edit().putString("history", list.joinToString("\n")).apply() } catch (_: Exception) {}
    }
    fun saveQuery(q: String) {
        val t = q.trim()
        if (t.length >= 2) saveHistory((listOf(t) + history.filter { !it.equals(t, true) }).take(8))
    }

    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("MANGA") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var popular by remember { mutableStateOf<List<String>>(emptyList()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { focus.requestFocus() } catch (_: Exception) {}
        popular = try {
            AppGraph.repository.popularSearches()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
    LaunchedEffect(query, type) {
        error = null
        if (query.trim().length < 2) { hits = emptyList(); return@LaunchedEffect }
        delay(400)
        loading = true
        try {
            hits = AppGraph.directory.search(query.trim(), type)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.msg(); hits = emptyList()
        } finally {
            loading = false
        }
    }

    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Поиск", Modifier.weight(1f), color = cs.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = onBack,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant),
            ) { Icon(Icons.Filled.Close, "Закрыть", tint = cs.onBackground) }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp).height(48.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceVariant).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Box(Modifier.weight(1f).padding(start = 10.dp)) {
                if (query.isEmpty()) Text("Что вы ищете?", color = cs.onSurfaceVariant, fontSize = 16.sp)
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = TextStyle(color = cs.onBackground, fontSize = 16.sp),
                    cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { saveQuery(query) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) Icon(Icons.Filled.Close, "Очистить", tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp).clickable { query = "" })
        }
        LazyRow(contentPadding = PaddingValues(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(types) { (key, label) ->
                val sel = type == key
                Text(
                    label, color = if (sel) cs.onBackground else cs.onSurfaceVariant, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                        .then(if (sel) Modifier.border(BorderStroke(1.5.dp, cs.primary), RoundedCornerShape(14.dp)) else Modifier)
                        .clickable { type = key }.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
        error?.let { Text(it, color = cs.error, fontSize = 12.sp) }
        if (query.trim().length < 2) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (history.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Ты искал", color = cs.onSurfaceVariant, fontSize = 14.sp)
                            Icon(Icons.Filled.Close, "Очистить историю", tint = cs.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).size(18.dp).clickable { saveHistory(emptyList()) })
                        }
                    }
                    items(history) { q -> QueryChip("↺  $q") { query = q } }
                }
                if (popular.isNotEmpty()) {
                    item { Text("Популярные запросы", color = cs.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
                    items(popular.take(8)) { q -> QueryChip("↗  $q") { query = q } }
                }
            }
        } else if (loading) {
            LoadingBox()
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (hits.isEmpty() && error == null) item { Text("Ничего не найдено", color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp)) }
                items(hits) { h ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            saveQuery(query)
                            when (type) {
                                "MANGA" -> onManga(h.id)
                                "TEAM" -> onTeam(h.slug)
                                "GUILD" -> onGuild(h.slug)
                                "PERSON" -> onPerson(h.slug)
                                "CHARACTER" -> onCharacter(h.slug)
                            }
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(h.image, 44)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(h.title, fontSize = 15.sp, color = cs.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            h.subtitle?.let { Text(it, fontSize = 12.sp, color = cs.onSurfaceVariant) }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun QueryChip(text: String, onClick: () -> Unit) {
    Text(
        text, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
