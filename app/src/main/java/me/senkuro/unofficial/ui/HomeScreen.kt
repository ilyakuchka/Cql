package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.data.SenkuroConfig
import java.util.Calendar

@Composable
fun HomeScreen(
    onManga: (MangaItem) -> Unit,
    onSearch: () -> Unit,
    onMore: () -> Unit,
    onLogin: () -> Unit,
    onProfile: () -> Unit,
    onNotifications: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    Column(Modifier.fillMaxSize()) {
        HomeHeader(onSearch = onSearch, onRefresh = vm::load, onLogin = onLogin, onProfile = onProfile, onNotifications = onNotifications)
        when (val state = vm.state) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(state.message, vm::load)
            is UiState.Ready -> {
                val account by AppGraph.account.collectAsState()
                var resume by remember { mutableStateOf<List<MangaItem>>(emptyList()) }
                LaunchedEffect(account?.userId) {
                    resume = if (account == null) emptyList() else try {
                        AppGraph.repository.continueReading()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp + LocalBottomInset.current),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val data = state.data
                    cardsSection("Продолжить чтение", resume, onManga, onMore, showMore = false)
                    trendingSection("Популярное за день", data.popular, onManga)
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

/** Шапка как «Banner» в ките: белая полоса с нижней линией #E0E0E0, слева аватар и приветствие, справа иконки. */
@Composable
private fun HomeHeader(onSearch: () -> Unit, onRefresh: () -> Unit, onLogin: () -> Unit, onProfile: () -> Unit, onNotifications: () -> Unit) {
    val unread by AppGraph.unread.collectAsState()
    val account by AppGraph.account.collectAsState()
    val token by AppGraph.auth.token.collectAsState()
    val line = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .drawBehind {
                val y = size.height - 0.4.dp.toPx()
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8.dp.toPx())
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(0.8.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { if (token == null) onLogin() else onProfile() },
            contentAlignment = Alignment.Center,
        ) {
            val avatar = account?.avatar
            if (avatar != null) {
                AsyncImage(
                    model = avatar,
                    contentDescription = "Профиль",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Filled.Person, contentDescription = "Войти", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                account?.name?.takeIf { it.isNotBlank() }?.let { "${greeting()}, $it" } ?: greeting(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
                style = HeadingStyle,
            )
            Text(SenkuroConfig.current.title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        if (token != null) {
            IconButton(onClick = onNotifications) {
                Box {
                    Icon(Icons.Filled.Notifications, contentDescription = "Уведомления", tint = MaterialTheme.colorScheme.onBackground)
                    if (unread > 0) {
                        Text(
                            text = if (unread > 99) "99+" else unread.toString(),
                            color = androidx.compose.ui.graphics.Color.Black,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(start = 10.dp)
                                .clip(CircleShape)
                                .background(AppColors.Accent)
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
        IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Поиск", tint = MaterialTheme.colorScheme.onBackground) }
        IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Обновить", tint = MaterialTheme.colorScheme.onBackground) }
    }
}

@Composable
private fun SectionHeader(title: String, onMore: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground)
        if (onMore != null) {
            Text(
                text = "Ещё",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onMore)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** «Trending Series»: пронумерованные строки по три в колонке, колонки листаются вбок. */
private fun LazyListScope.trendingSection(title: String, list: List<MangaItem>, onManga: (MangaItem) -> Unit) {
    if (list.isEmpty()) return
    item(key = "trending-$title") {
        Column {
            SectionHeader(title, onMore = null)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(17.dp),
            ) {
                val columns = list.chunked(3)
                items(columns.size) { column ->
                    Column(Modifier.width(246.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        columns[column].forEachIndexed { row, manga ->
                            TrendingRow(rank = column * 3 + row + 1, item = manga, onClick = { onManga(manga) })
                        }
                    }
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
    showMore: Boolean = true,
) {
    if (list.isEmpty()) return
    item(key = "section-$title") {
        Column {
            SectionHeader(title, onMore = if (showMore) onMore else null)
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
