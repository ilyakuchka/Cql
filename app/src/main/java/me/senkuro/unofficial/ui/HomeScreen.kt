@file:OptIn(ExperimentalMaterial3Api::class)

package me.senkuro.unofficial.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.data.SenkuroConfig

@Composable
fun HomeScreen(onManga: (MangaItem) -> Unit, vm: HomeViewModel = viewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Senkuro") },
                actions = {
                    DomainSwitcher()
                    IconButton(onClick = vm::load) { Icon(Icons.Filled.Refresh, contentDescription = "Обновить") }
                },
            )
        },
    ) { padding ->
        when (val state = vm.state) {
            is UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(state.message, vm::load, Modifier.padding(padding))
            is UiState.Ready -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val data = state.data
                section("Свежие обновления", data.latest, onManga)
                section("Популярное за день", data.popular, onManga)
                section("Новые тайтлы", data.newTitles, onManga)
                section("Рекомендации", data.recommendations, onManga)
            }
        }
    }
}

/** Переключатель домена (senkuro.me / sso.senkuro.net): выбор сохраняется, данные перезагружаются. */
@Composable
private fun DomainSwitcher() {
    val current by AppGraph.domain.collectAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(current.title) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SenkuroConfig.DOMAINS.forEach { domain ->
                DropdownMenuItem(
                    text = { Text(if (domain.id == current.id) "✓ ${domain.title}" else domain.title) },
                    onClick = {
                        open = false
                        AppGraph.setDomain(domain)
                    },
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    list: List<MangaItem>,
    onManga: (MangaItem) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "section-$title") {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { manga ->
                    MangaCard(manga, onClick = { onManga(manga) }, modifier = Modifier.width(120.dp))
                }
            }
        }
    }
}
