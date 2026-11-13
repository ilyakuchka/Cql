@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package me.senkuro.unofficial.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.senkuro.unofficial.data.CatalogFilters
import me.senkuro.unofficial.data.FilterDefs
import me.senkuro.unofficial.data.LabelItem
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.data.Tri

@Composable
fun CatalogScreen(onManga: (MangaItem) -> Unit, vm: CatalogViewModel = viewModel()) {
    val marks by me.senkuro.unofficial.data.AppGraph.marks.collectAsState()
    var text by rememberSaveable { mutableStateOf(vm.query) }
    var showFilters by remember { mutableStateOf(false) }
    val focus: FocusManager = LocalFocusManager.current
    val gridState = rememberLazyGridState()

    val pendingLabel by me.senkuro.unofficial.data.AppGraph.pendingCatalogLabel.collectAsState()
    LaunchedEffect(pendingLabel, vm.labels) {
        val name = pendingLabel ?: return@LaunchedEffect
        if (vm.labels.isEmpty()) return@LaunchedEffect
        val hit = vm.labels.firstOrNull { it.title.equals(name, ignoreCase = true) }
        me.senkuro.unofficial.data.AppGraph.pendingCatalogLabel.value = null
        if (hit != null) {
            text = ""
            vm.submit("")
            vm.applyFilters(CatalogFilters().toggle(FilterDefs.GROUP_LABEL, hit.slug))
        }
    }

    LaunchedEffect(vm.items.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && last >= vm.items.size - 6) vm.loadMore()
            }
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    textStyle = TextStyle(fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    placeholder = { Text("Поиск по каталогу", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focus.clearFocus()
                        vm.submit(text)
                    }),
                )
                val active = vm.filters.activeCount
                TextButton(onClick = { showFilters = true }) {
                    Text(if (active > 0) "Фильтры ($active)" else "Фильтры", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }

            Box(Modifier.weight(1f)) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        top = 12.dp,
                        end = 8.dp,
                        bottom = 12.dp + LocalBottomInset.current,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(vm.items, key = { it.id }) { manga ->
                        MangaCard(manga, onClick = { onManga(manga) }, label = marks[manga.id], compact = true)
                    }
                    if (vm.loading && vm.items.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    if (vm.error != null && vm.items.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Button(onClick = vm::loadMore) { Text("Повторить загрузку") }
                            }
                        }
                    }
                }
                if (vm.items.isEmpty()) {
                    when {
                        vm.loading -> LoadingBox()
                        vm.error != null -> ErrorBox(vm.error.orEmpty(), vm::refresh)
                        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Ничего не найдено")
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FiltersSheet(
            initial = vm.filters,
            labels = vm.labels,
            onApply = {
                showFilters = false
                vm.applyFilters(it)
            },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun FiltersSheet(
    initial: CatalogFilters,
    labels: List<LabelItem>,
    onApply: (CatalogFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var filters by remember { mutableStateOf(initial) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Сортировка", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((field, title) in FilterDefs.sortFields) {
                            FilterChip(
                                selected = filters.orderField == field,
                                onClick = { filters = filters.copy(orderField = field) },
                                label = { Text(title) },
                            )
                        }
                        FilterChip(
                            selected = filters.ascending,
                            onClick = { filters = filters.copy(ascending = !filters.ascending) },
                            label = { Text(FilterDefs.directionTitle(filters.orderField, filters.ascending)) },
                        )
                    }
                }
            }
            for (group in FilterDefs.groups) {
                item(key = group.key) {
                    FilterGroup(
                        title = group.title,
                        options = group.options,
                        stateOf = { filters.state(group.key, it) },
                        onToggle = { filters = filters.toggle(group.key, it) },
                    )
                }
            }
            for ((rootId, title) in FilterDefs.labelRoots) {
                val options = labels.filter { it.rootId == rootId }.map { it.slug to it.title }
                if (options.isEmpty()) continue
                item(key = "label-$rootId") {
                    FilterGroup(
                        title = title,
                        options = options,
                        stateOf = { filters.state(FilterDefs.GROUP_LABEL, it) },
                        onToggle = { filters = filters.toggle(FilterDefs.GROUP_LABEL, it) },
                        collapsible = rootId in setOf("TEFCRUw6NQ", "TEFCRUw6NA", "TEFCRUw6Ng", "TEFCRUw6Mw", "TEFCRUw6Nw"),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = { filters = filters.cleared() }) { Text("Сбросить") }
            Button(onClick = { onApply(filters) }, modifier = Modifier.weight(1f)) { Text("Применить") }
        }
    }
}

@Composable
private fun FilterGroup(
    title: String,
    options: List<Pair<String, String>>,
    stateOf: (String) -> Tri,
    onToggle: (String) -> Unit,
    collapsible: Boolean = false,
) {
    var showAll by remember { mutableStateOf(false) }
    // Свёрнутый вид: первые 5 тегов + все выбранные (чтобы выбор был виден).
    val shown = if (!collapsible) options else {
        val first = options.take(5)
        first + options.drop(5).filter { stateOf(it.first) != Tri.NONE }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, name) in shown) TagChip(name, stateOf(value)) { onToggle(value) }
        }
        if (collapsible && options.size > 5) {
            TextButton(onClick = { showAll = true }) { Text("Показать все (${options.size})") }
        }
    }
    if (showAll) AllTagsDialog(title, options, stateOf, onToggle) { showAll = false }
}

@Composable
private fun TagChip(name: String, state: Tri, onClick: () -> Unit) {
    FilterChip(
        selected = state != Tri.NONE,
        onClick = onClick,
        label = { Text(if (state == Tri.EXCLUDE) "− $name" else name) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = if (state == Tri.EXCLUDE) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}

/** Отдельное окно со всеми тегами: ленивый список (рисуются только видимые строки) + поиск. */
@Composable
private fun AllTagsDialog(
    title: String,
    options: List<Pair<String, String>>,
    stateOf: (String) -> Tri,
    onToggle: (String) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    val list = remember(options, query) {
        val q = query.trim()
        if (q.isEmpty()) options else options.filter { it.second.contains(q, ignoreCase = true) }
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Готово") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Поиск") },
                )
                Text("Нажатие: включить → исключить → снять", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
                    items(list.size, key = { list[it].first }) { i ->
                        val (value, name) = list[i]
                        val st = stateOf(value)
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(value) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, modifier = Modifier.weight(1f), fontSize = 15.sp)
                            when (st) {
                                Tri.INCLUDE -> Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                                Tri.EXCLUDE -> Text("−", color = MaterialTheme.colorScheme.error, fontSize = 18.sp)
                                Tri.NONE -> {}
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
