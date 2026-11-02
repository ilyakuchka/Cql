@file:OptIn(ExperimentalMaterial3Api::class)

package me.senkuro.unofficial.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.HistoryEntry
import me.senkuro.unofficial.data.MangaItem
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryScreen(onManga: (MangaItem) -> Unit, onDebug: (() -> Unit)? = null) {
    val library = AppGraph.library
    val favorites by library.favorites.collectAsState()
    val history by library.history.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (onDebug != null) {
                OutlinedButton(
                    onClick = onDebug,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) { Text("Отладка: перехват запросов сайта") }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Избранное") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("История") })
            }
            if (tab == 0) {
                if (favorites.isEmpty()) {
                    EmptyHint("В избранном пока пусто")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(110.dp),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 12.dp,
                            end = 12.dp,
                            bottom = 12.dp + LocalBottomInset.current,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(favorites, key = { it.id }) { manga ->
                            MangaCard(manga, onClick = { onManga(manga) })
                        }
                    }
                }
            } else {
                if (history.isEmpty()) {
                    EmptyHint("История чтения пуста")
                } else {
                    Column {
                        TextButton(
                            onClick = library::clearHistory,
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("Очистить") }
                        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomInset.current)) {
                            items(history, key = { it.manga.id }) { entry ->
                                HistoryRow(entry, onClick = { onManga(entry.manga) })
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .width(56.dp)
                .aspectRatio(2f / 3f),
        ) {
            AsyncImage(
                model = entry.manga.cover,
                contentDescription = entry.manga.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(entry.manga.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(entry.chapterTitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.at)),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
