package me.senkuro.unofficial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.BookmarkEntry
import me.senkuro.unofficial.data.MangaItem

class SiteBookmarksViewModel : ViewModel() {
    var collectionId by mutableStateOf<String?>(null)
        private set
    var items by mutableStateOf<List<BookmarkEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasMore by mutableStateOf(false)
        private set

    private var cursor: String? = null
    private var job: Job? = null

    fun select(id: String?) {
        collectionId = id
        reload()
    }

    fun reload() {
        job?.cancel()
        items = emptyList()
        cursor = null
        hasMore = false
        error = null
        fetch()
    }

    fun loadMore() {
        if (loading || !hasMore) return
        fetch()
    }

    private fun fetch() {
        val userId = AppGraph.account.value?.userId ?: return
        loading = true
        job = viewModelScope.launch {
            try {
                val page = AppGraph.repository.bookmarks(userId, collectionId, cursor)
                items = (items + page.items).distinctBy { it.manga.id }
                cursor = page.next
                hasMore = page.next != null
                loading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: "Ошибка сети"
                loading = false
            }
        }
    }
}

/** Вкладка «Закладки» библиотеки: серверные закладки аккаунта на сайте. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteBookmarksTab(onManga: (MangaItem) -> Unit, onLogin: () -> Unit, vm: SiteBookmarksViewModel = viewModel()) {
    val token by AppGraph.auth.token.collectAsState()
    val account by AppGraph.account.collectAsState()
    val accountError by AppGraph.accountError.collectAsState()
    val marks by AppGraph.marks.collectAsState()

    if (token == null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Войдите, чтобы видеть закладки с сайта")
            Button(onClick = onLogin, modifier = Modifier.padding(top = 12.dp)) { Text("Войти через сайт") }
        }
        return
    }
    val acc = account
    if (acc == null) {
        if (accountError != null) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(accountError.orEmpty())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    Button(onClick = AppGraph::refreshAccount) { Text("Повторить") }
                    TextButton(onClick = AppGraph::logout) { Text("Выйти") }
                }
            }
        } else {
            LoadingBox()
        }
        return
    }

    LaunchedEffect(acc.userId) { vm.reload() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(acc.name, modifier = Modifier.weight(1f))
            TextButton(onClick = AppGraph::logout) { Text("Выйти") }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterChip(selected = vm.collectionId == null, onClick = { vm.select(null) }, label = { Text("Все") }) }
            items(acc.collections, key = { it.id }) { c ->
                FilterChip(selected = vm.collectionId == c.id, onClick = { vm.select(c.id) }, label = { Text(c.name) })
            }
        }
        CollectionTools(acc.collections, vm.collectionId, onChanged = { vm.select(null); AppGraph.refreshAccount() })
        Box(Modifier.fillMaxSize()) {
            val error = vm.error
            when {
                error != null && vm.items.isEmpty() -> ErrorBox(error, vm::reload)
                vm.items.isEmpty() && vm.loading -> LoadingBox()
                vm.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Здесь пока пусто") }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(8.dp, 12.dp, 8.dp, 12.dp + LocalBottomInset.current),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(vm.items) { index, entry ->
                        if (index >= vm.items.size - 4) LaunchedEffect(vm.items.size) { vm.loadMore() }
                        MangaCard(entry.manga, onClick = { onManga(entry.manga) }, label = marks[entry.manga.id], compact = true)
                    }
                }
            }
        }
    }
}


/** Создание, переименование и удаление своих коллекций закладок (системные не удаляются). */
@Composable
private fun CollectionTools(collections: List<me.senkuro.unofficial.data.BookmarkCollection>, selected: String?, onChanged: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var dialog by androidx.compose.runtime.remember { mutableStateOf<String?>(null) } // "new" | "rename" | "delete"
    var name by androidx.compose.runtime.remember { mutableStateOf("") }
    var error by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    val cur = collections.firstOrNull { it.id == selected }
    Row(Modifier.padding(horizontal = 8.dp)) {
        TextButton(onClick = { name = ""; error = null; dialog = "new" }) { Text("＋ Коллекция") }
        if (cur != null && !cur.system) {
            TextButton(onClick = { name = cur.name; error = null; dialog = "rename" }) { Text("Переименовать") }
            TextButton(onClick = { error = null; dialog = "delete" }) { Text("Удалить") }
        }
    }
    val d = dialog ?: return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { dialog = null },
        title = { Text(when (d) { "new" -> "Новая коллекция"; "rename" -> "Переименовать"; else -> "Удалить «${cur?.name}»?" }) },
        text = {
            Column {
                if (d != "delete") androidx.compose.material3.OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Название") })
                error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    try {
                        when (d) {
                            "new" -> AppGraph.repository.createCollection(name.trim())
                            "rename" -> AppGraph.repository.renameCollection(cur!!.id, name.trim())
                            else -> AppGraph.repository.deleteCollection(cur!!.id)
                        }
                        dialog = null
                        onChanged()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "Ошибка"
                    }
                }
            }, enabled = d == "delete" || name.isNotBlank()) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
    )
}
