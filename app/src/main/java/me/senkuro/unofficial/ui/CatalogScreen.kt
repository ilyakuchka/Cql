@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package me.senkuro.unofficial.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    var text by rememberSaveable { mutableStateOf(vm.query) }
    var showFilters by remember { mutableStateOf(false) }
    val focus: FocusManager = LocalFocusManager.current
    val gridState = rememberLazyGridState()

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
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Поиск по каталогу") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focus.clearFocus()
                        vm.submit(text)
                    }),
                )
                val active = vm.filters.activeCount
                OutlinedButton(onClick = { showFilters = true }) {
                    Text(if (active > 0) "Фильтры ($active)" else "Фильтры")
                }
            }

            Box(Modifier.weight(1f)) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(110.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(vm.items, key = { it.id }) { manga ->
                        MangaCard(manga, onClick = { onManga(manga) })
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
                            label = { Text(if (filters.ascending) "По возрастанию" else "По убыванию") },
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, name) in options) {
                val state = stateOf(value)
                FilterChip(
                    selected = state != Tri.NONE,
                    onClick = { onToggle(value) },
                    label = { Text(if (state == Tri.EXCLUDE) "− $name" else name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (state == Tri.EXCLUDE) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                    ),
                )
            }
        }
    }
}
