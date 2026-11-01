@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package me.senkuro.unofficial.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.Chapter
import me.senkuro.unofficial.data.MangaDetails

@Composable
fun DetailsScreen(
    mangaId: String,
    onBack: () -> Unit,
    onRead: (Chapter) -> Unit,
    vm: DetailsViewModel = viewModel(key = "details-$mangaId"),
) {
    LaunchedEffect(mangaId) { vm.load(mangaId) }
    val library = AppGraph.library
    val favorites by library.favorites.collectAsState()
    val readIds by library.readIds.collectAsState()
    val ready = vm.state as? UiState.Ready

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ready?.data?.details?.item?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    val item = ready?.data?.details?.item
                    if (item != null) {
                        val favorite = favorites.any { it.id == item.id }
                        IconButton(onClick = { library.toggleFavorite(item) }) {
                            Icon(
                                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "В избранное",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val state = vm.state) {
            is UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(state.message, { vm.load(mangaId, force = true) }, Modifier.padding(padding))
            is UiState.Ready -> DetailsContent(
                data = state.data,
                readIds = readIds,
                onRead = onRead,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DetailsContent(
    data: DetailsData,
    readIds: Set<String>,
    onRead: (Chapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val details = data.details
    val branches = data.chapters.branches
    var branchIndex by rememberSaveable { mutableStateOf(0) }
    val branch = branches.getOrNull(branchIndex) ?: branches.firstOrNull()
    val chaptersDescending = remember(branch) { branch?.chapters.orEmpty().asReversed() }
    val continueFrom = remember(branch, readIds) {
        val list = branch?.chapters.orEmpty()
        list.firstOrNull { it.id !in readIds } ?: list.firstOrNull()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "header") { Header(details) }

        item(key = "read") {
            Button(
                onClick = { continueFrom?.let(onRead) },
                enabled = continueFrom != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    when {
                        continueFrom == null -> "Нет глав"
                        continueFrom.id in readIds -> "Читать заново"
                        branch?.chapters?.any { it.id in readIds } == true -> "Продолжить"
                        else -> "Начать читать"
                    },
                )
            }
        }

        if (details.description.isNotBlank()) {
            item(key = "description") { Description(details.description) }
        }

        if (details.labels.isNotEmpty()) {
            item(key = "labels") {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    details.labels.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                }
            }
        }

        item(key = "chapters-title") {
            Column {
                Text(
                    text = "Главы" + if (branch != null) " (${branch.chapters.size})" else "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
                if (branches.size > 1) {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        branches.forEachIndexed { index, b ->
                            FilterChip(
                                selected = index == branchIndex,
                                onClick = { branchIndex = index },
                                label = { Text(b.label) },
                            )
                        }
                    }
                }
                data.chapters.message?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                }
                data.chaptersError?.let {
                    Text("Не удалось загрузить главы: $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                }
            }
        }

        items(chaptersDescending, key = { it.id }) { chapter ->
            ChapterRow(chapter, read = chapter.id in readIds, onClick = { onRead(chapter) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun Header(details: MangaDetails) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(2f / 3f),
        ) {
            AsyncImage(
                model = details.item.cover,
                contentDescription = details.item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(details.item.title, style = MaterialTheme.typography.titleLarge)
            if (details.altNames.isNotEmpty()) {
                Text(
                    details.altNames.joinToString(" / "),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val meta = listOfNotNull(details.type, details.status, details.ageRating).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyMedium)
            details.translationStatus?.let { Text("Перевод: $it", style = MaterialTheme.typography.bodyMedium) }
            details.authors?.let { Text("Автор: $it", style = MaterialTheme.typography.bodySmall) }
            details.artists?.let { Text("Художник: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun Description(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Свернуть" else "Развернуть")
        }
    }
}

@Composable
private fun ChapterRow(chapter: Chapter, read: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (read) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(chapter.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(chapter.teams.takeIf { it.isNotEmpty() }?.joinToString(", "), chapter.date)
                .joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}
