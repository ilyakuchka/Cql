package me.senkuro.unofficial.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.ProfileActivity
import me.senkuro.unofficial.data.RankEntry
import me.senkuro.unofficial.data.RanksPage

private fun num(n: Long?): String = n?.let { "%,d".format(java.util.Locale("ru"), it).replace(' ', ' ') } ?: "—"

private fun rankHint(rank: Long?): String = when {
    rank == null || rank <= 0 || rank > 50 -> "Пока за пределами списка. Ворвись в топ-50"
    rank == 1L -> "Ты на вершине. Все позади — удержи позицию"
    rank <= 3 -> "Почти лидер. Ещё немного — и ты первый"
    rank <= 10 -> "Уже среди лучших. Можно подняться выше"
    rank <= 30 -> "Хороший темп. Ещё немного — и ты в десятке"
    else -> "Уже среди лучших. Можно подняться выше"
}

class RanksViewModel : ViewModel() {
    var page by mutableStateOf<RanksPage?>(null)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    fun load() {
        viewModelScope.launch {
            loading = true; error = null
            try { page = AppGraph.repository.ranks() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Не удалось загрузить" }
            loading = false
        }
    }
}

/** «Топ пользователей» (/ranks на сайте): рейтинг по активности. */
@Composable
fun RanksScreen(onBack: () -> Unit, onUser: (String) -> Unit, vm: RanksViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = MaterialTheme.colorScheme.onBackground) }
            Column {
                Text("Топ пользователей", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground)
                Text("Лучшие пользователи по активности", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val page = vm.page
        when {
            page == null && vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            page == null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(vm.error ?: "Нет данных", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { vm.load() }) { Text("Повторить") }
            }
            else -> LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (page.myRank != null) item(key = "me") {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Ты на ${page.myRank} месте", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.streamerBlur())
                        Text(rankHint(page.myRank), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                itemsIndexed(page.entries, key = { _, e -> e.slug }) { _, e -> RankRow(e, onUser) }
            }
        }
    }
}

@Composable
private fun RankRow(e: RankEntry, onUser: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onUser(e.slug) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(e.rank.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
        val mine = isMe(slug = e.slug)
        AvatarWithFrame(e.avatar, e.frame, 44.dp, Modifier.streamerBlur(mine, 14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(e.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f, fill = false).streamerBlur(mine))
                e.level?.let { Text("Ур. $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.streamerBlur(mine, 6.dp)) }
            }
            e.better?.let { Text("Лучше ${"%.1f".format(java.util.Locale.US, it)}% пользователей", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Прочитал(а) ${num(e.chapters)} гл. · Посмотрел(а) ${num(e.episodes)} серий", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

class HistoryViewModel : ViewModel() {
    val items = mutableStateListOf<ProfileActivity>()
    private var cursor: String? = null
    var hasMore by mutableStateOf(true)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var userId: String? = null

    fun start(id: String) {
        if (userId == id) return
        userId = id; items.clear(); cursor = null; hasMore = true
        loadMore()
    }

    fun loadMore() {
        val id = userId ?: return
        if (loading || !hasMore) return
        viewModelScope.launch {
            loading = true; error = null
            try {
                val (list, next) = AppGraph.repository.activities(id, cursor)
                items.addAll(list); cursor = next; hasMore = next != null
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Не удалось загрузить" }
            loading = false
        }
    }
}

/** «История действий» пользователя (users/{slug}/history). */
@Composable
fun HistoryScreen(userId: String, onBack: () -> Unit, vm: HistoryViewModel = viewModel()) {
    LaunchedEffect(userId) { vm.start(userId) }
    val state = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0; last >= vm.items.size - 3 } }
    LaunchedEffect(nearEnd, vm.items.size) { if (nearEnd) vm.loadMore() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = MaterialTheme.colorScheme.onBackground) }
            Text("История действий", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground)
        }
        LazyColumn(state = state, contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(vm.items) { _, a ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(a.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                    a.subject?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
                    profileRelativeTime(a.at)?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (vm.loading) item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() } }
            vm.error?.let { err -> item { Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
        }
    }
}
