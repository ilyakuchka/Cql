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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.data.NotificationItem

class NotificationsViewModel : ViewModel() {
    var read by mutableStateOf(false)
        private set
    var items by mutableStateOf<List<NotificationItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasMore by mutableStateOf(false)
        private set

    private var cursor: String? = null
    private var job: Job? = null

    fun select(read: Boolean) {
        this.read = read
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
        loading = true
        job = viewModelScope.launch {
            try {
                val page = AppGraph.repository.notifications(read, cursor)
                items = (items + page.items).distinctBy { it.id }
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

/** Уведомления аккаунта: вкладки «Новые» и «Прочитанные», подгрузка страниц, нажатие открывает тайтл. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit, onManga: (MangaItem) -> Unit, vm: NotificationsViewModel = viewModel()) {
    LaunchedEffect(Unit) {
        vm.reload()
        AppGraph.refreshUnread()
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Уведомления", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground)
        }
        Row(
            Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = !vm.read, onClick = { vm.select(false) }, label = { Text("Новые") })
            FilterChip(selected = vm.read, onClick = { vm.select(true) }, label = { Text("Прочитанные") })
        }
        Box(Modifier.fillMaxSize()) {
            val error = vm.error
            when {
                error != null && vm.items.isEmpty() -> ErrorBox(error, vm::reload)
                vm.items.isEmpty() && vm.loading -> LoadingBox()
                vm.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Уведомлений нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
                    itemsIndexed(vm.items) { index, item ->
                        if (index >= vm.items.size - 3) LaunchedEffect(vm.items.size) { vm.loadMore() }
                        NotificationRow(item, onClick = { item.manga?.let(onManga) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.8.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: NotificationItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.manga != null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(width = 44.dp, height = 62.dp)
                .clip(RoundedCornerShape(4.dp))
                .let { it },
        ) {
            AsyncImage(
                model = item.manga?.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                item.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.date?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
    }
}
