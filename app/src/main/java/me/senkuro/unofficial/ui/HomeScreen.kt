package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.data.SenkuroConfig
import java.util.Calendar

@Composable
fun HomeScreen(
    onManga: (MangaItem) -> Unit,
    onSearch: () -> Unit,
    onMore: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    Column(Modifier.fillMaxSize()) {
        HomeHeader(onSearch = onSearch, onRefresh = vm::load)
        when (val state = vm.state) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(state.message, vm::load)
            is UiState.Ready -> {
                val bottom = LocalBottomInset.current +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottom),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val data = state.data
                    bannerSection("Популярное за день", data.popular, onManga)
                    cardsSection("Свежие обновления", data.latest, onManga, onMore)
                    cardsSection("Новые тайтлы", data.newTitles, onManga, onMore)
                    cardsSection("Рекомендации", data.recommendations, onManga, onMore)
                }
            }
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Доброе утро"
    in 12..17 -> "Добрый день"
    in 18..22 -> "Добрый вечер"
    else -> "Доброй ночи"
}

/** Шапка как в дизайне: круглый красный аватар, приветствие, имя красным (у нас — текущий домен) и иконки справа. */
@Composable
private fun HomeHeader(onSearch: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(greeting(), color = AppColors.Text.copy(alpha = 0.75f), fontSize = 12.sp)
            DomainSwitcher()
        }
        IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Поиск") }
        IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Обновить") }
    }
}

/** Переключатель домена (senkuro.me / senkuro.com): выбор сохраняется, данные перезагружаются. */
@Composable
private fun DomainSwitcher() {
    val current by AppGraph.domain.collectAsState()
    var open by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current.title, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Сменить домен", tint = accent)
        }
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

@Composable
private fun SectionHeader(title: String, onMore: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = HeadingStyle, color = AppColors.Text)
        if (onMore != null) {
            Text(
                text = "Ещё",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onMore)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

private fun LazyListScope.bannerSection(title: String, list: List<MangaItem>, onManga: (MangaItem) -> Unit) {
    if (list.isEmpty()) return
    item(key = "banner-$title") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title, onMore = null)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(list, key = { it.id }) { manga ->
                    BannerCard(manga, onClick = { onManga(manga) })
                }
            }
        }
    }
}

private fun LazyListScope.cardsSection(
    title: String,
    list: List<MangaItem>,
    onManga: (MangaItem) -> Unit,
    onMore: () -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "section-$title") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title, onMore = onMore)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(list, key = { it.id }) { manga ->
                    MangaCard(manga, onClick = { onManga(manga) }, modifier = Modifier.width(128.dp))
                }
            }
        }
    }
}
