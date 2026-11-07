package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.data.ShortItem

class ShortsViewModel : ViewModel() {
    val items = mutableStateListOf<ShortItem>()
    private var cursor: String? = null
    private var hasMore = true
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun loadMore() {
        if (loading || !hasMore) return
        viewModelScope.launch {
            loading = true; error = null
            try {
                val (list, next) = AppGraph.repository.shorts(cursor)
                items.addAll(list.filter { n -> items.none { it.id == n.id } }); cursor = next; hasMore = next != null
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Не удалось загрузить" }
            loading = false
        }
    }
}

/** Shorts (/shorts на сайте): вертикальная лента глав-комиксов, внутри главы страницы листаются вбок. */
@Composable
fun ShortsScreen(onBack: () -> Unit, onManga: (MangaItem) -> Unit, vm: ShortsViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.loadMore() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (vm.items.isEmpty()) {
            if (vm.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(vm.error ?: "Пока нет Shorts", color = Color.White)
                OutlinedButton(onClick = { vm.loadMore() }) { Text("Повторить") }
            }
        } else {
            val pager = rememberPagerState { vm.items.size }
            LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { if (it >= vm.items.size - 2) vm.loadMore() } }
            VerticalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { index ->
                ShortCard(vm.items[index], onManga)
            }
        }
        IconButton(onClick = onBack, Modifier.statusBarsPadding()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White)
        }
    }
}

@Composable
private fun ShortCard(item: ShortItem, onManga: (MangaItem) -> Unit) {
    val pages = rememberPagerState { item.pages.size }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pages, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { i ->
            AsyncImage(item.pages[i], null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Text(
            "${pages.currentPage + 1}/${item.pages.size}", color = Color.White, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
                .clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(14.dp)
                .then(if (item.manga != null) Modifier.clickable { onManga(item.manga) } else Modifier),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.manga?.title.orEmpty(), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (item.number.isNotBlank()) "${item.number} глава" else "", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            }
        }
    }
}
