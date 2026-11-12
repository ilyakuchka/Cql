package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.Chapter
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.data.NotifChapter
import me.senkuro.unofficial.data.NotificationItem

/** Типы уведомлений по разделам фильтра (как в [namespace] на сайте). «Все» — без фильтра. */
private val NAMESPACES: List<Triple<String, String, List<String>?>> = listOf(
    Triple("all", "Все", null),
    Triple("update", "Обновления", listOf("NEW_MANGA_CHAPTER", "NEW_RANOBE_CHAPTER", "TRANSLATOR_CHANGED")),
    Triple(
        "social", "Пользователи",
        listOf(
            "COMMENT_MENTIONED", "COMMENT_REPLIED", "COMMENT_UPVOTED", "POST_COMMENTED", "TRANSLATOR_CHANGED",
            "NEW_FRIEND_REQUEST", "NEW_TEAM_INVITE", "NEW_GUILD_INVITE", "NEW_FRIEND_WAIFU",
        ),
    ),
    Triple(
        "important", "Система",
        listOf(
            "SENKURO_BLESSING", "NEW_POST", "ACCOUNT_LOGIN", "PASSWORD_CHANGED", "NEW_ACHIEVEMENT",
            "TRANSLATE_REQUEST_ACCEPTED", "TRANSLATE_REQUEST_DECLINED", "SUBMISSION_ACCEPTED", "SUBMISSION_DECLINED",
            "CREATE_TEAM_ACCEPTED", "CREATE_TEAM_DECLINED", "CREATE_PROJECT_ACCEPTED", "CREATE_PROJECT_DECLINED",
        ),
    ),
)

class NotificationsViewModel : ViewModel() {
    /** true — вкладка «Скрытые» (isRead = true), false — «Новые». */
    var read by mutableStateOf(false)
        private set
    var namespace by mutableStateOf("all")
        private set
    var items by mutableStateOf<List<NotificationItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var cursor: String? = null
    private var job: Job? = null

    fun select(read: Boolean) {
        this.read = read
        reload()
    }

    fun selectNamespace(ns: String) {
        namespace = ns
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

    fun consumeMessage() { message = null }

    /** «Скрыть» / «Удалить» одно уведомление из карточки. */
    fun act(delete: Boolean, only: List<NotificationItem>) {
        val ids = only.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                val ok = if (delete) AppGraph.repository.deleteNotifications(ids)
                else AppGraph.repository.markNotificationsRead(ids)
                if (ok) {
                    items = items.filterNot { it.id in ids }
                    message = if (delete) "Уведомление удалено" else "Уведомление скрыто"
                    AppGraph.refreshUnread()
                } else message = "Сервер не принял действие"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message?.takeIf { it.isNotBlank() } ?: "Ошибка сети"
            }
        }
    }

    /** «Скрыть все» (все новые) или «Удалить все» (включая скрытые) — по всему списку, а не только по загруженной части. */
    fun actAll(delete: Boolean) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val repo = AppGraph.repository
                val ids = if (delete) repo.allNotificationIds(false) + repo.allNotificationIds(true) else repo.allNotificationIds(false)
                var ok = true
                ids.chunked(50).forEach { chunk ->
                    ok = (if (delete) repo.deleteNotifications(chunk) else repo.markNotificationsRead(chunk)) && ok
                }
                message = when {
                    !ok -> "Сервер не принял действие"
                    delete -> "Все уведомления удалены"
                    else -> "Все уведомления скрыты"
                }
                if (ok) { items = emptyList(); hasMore = false; AppGraph.refreshUnread() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message?.takeIf { it.isNotBlank() } ?: "Ошибка сети"
            } finally {
                busy = false
            }
        }
    }

    fun loadMore() {
        if (loading || !hasMore) return
        fetch()
    }

    private fun fetch() {
        loading = true
        val types = NAMESPACES.first { it.first == namespace }.third
        job = viewModelScope.launch {
            try {
                val page = AppGraph.repository.notifications(read, cursor, types)
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

private enum class Confirm { HIDE_ALL, DELETE_ALL }

/** Уведомления как на сайте: «Скрыть все», меню, фильтр по типу, вкладки «Новые / Скрытые», карточки с кнопкой «К главе N». */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onManga: (MangaItem) -> Unit,
    onChapter: (MangaItem, String) -> Unit = { m, _ -> onManga(m) },
    onChapterAt: (MangaItem, String, Int) -> Unit = { m, c, _ -> onChapter(m, c) },
    onPost: (String) -> Unit = {},
    onSettings: () -> Unit = {},
    vm: NotificationsViewModel = viewModel(),
) {
    LaunchedEffect(Unit) {
        vm.reload()
        AppGraph.refreshUnread()
    }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var menu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Уведомления", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { confirm = Confirm.HIDE_ALL }, enabled = !vm.busy) { Text("Скрыть все") }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Настройки уведомлений", tint = MaterialTheme.colorScheme.onBackground)
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Меню", tint = MaterialTheme.colorScheme.onBackground)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Удалить все уведомления", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; confirm = Confirm.DELETE_ALL },
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                OutlinedButton(onClick = { filterMenu = true }) {
                    Text(NAMESPACES.first { it.first == vm.namespace }.second + " ▾")
                }
                DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                    NAMESPACES.forEach { (key, label, _) ->
                        DropdownMenuItem(
                            text = { Text(label, fontWeight = if (key == vm.namespace) FontWeight.SemiBold else FontWeight.Normal) },
                            onClick = { filterMenu = false; vm.selectNamespace(key) },
                        )
                    }
                }
            }
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                NotifTab("Новые", !vm.read) { vm.select(false) }
                NotifTab("Скрытые", vm.read) { vm.select(true) }
            }
        }

        vm.message?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            LaunchedEffect(it) {
                kotlinx.coroutines.delay(2500)
                vm.consumeMessage()
            }
        }

        Box(Modifier.fillMaxSize()) {
            val error = vm.error
            when {
                error != null && vm.items.isEmpty() -> ErrorBox(error, vm::reload)
                vm.items.isEmpty() && vm.loading -> LoadingBox()
                vm.items.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Нет уведомлений", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.size(4.dp))
                    Text(
                        if (vm.namespace == "all") "У вас прочитаны все уведомления." else "Здесь нет уведомлений, но мы уверены что они ещё появятся!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "ad-notifications") { YandexAdBlock(AdPositions.NOTIFICATIONS) }
                    itemsIndexed(vm.items, key = { _, it -> it.id }) { index, item ->
                        if (index >= vm.items.size - 3) LaunchedEffect(vm.items.size) { vm.loadMore() }
                        NotificationCard(
                            item = item,
                            hidden = vm.read,
                            onManga = onManga,
                            onChapter = onChapter,
                            onChapterAt = onChapterAt,
                            onPost = onPost,
                            onHide = { vm.act(false, listOf(item)) },
                            onDelete = { vm.act(true, listOf(item)) },
                        )
                    }
                }
            }
        }
    }

    confirm?.let { kind ->
        val hide = kind == Confirm.HIDE_ALL
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (hide) "Скрытие всех уведомлений" else "Удаление всех уведомлений") },
            text = {
                Text(if (hide) "Вы действительно хотите скрыть все уведомления?" else "Вы действительно хотите удалить все уведомления, включая скрытые?")
            },
            confirmButton = {
                TextButton(onClick = { confirm = null; vm.actAll(delete = !hide) }) {
                    Text(if (hide) "Скрыть уведомления" else "Удалить уведомления", color = if (hide) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun NotifTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** «9 часов назад» из ISO-времени (lastEventAt). */
private fun relativeTime(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val time = fmt.parse(iso.take(19))?.time ?: return null
        android.text.format.DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
    } catch (_: Exception) {
        null
    }
}

/** Карточка как card-notif на сайте: обложка и название, «⋯», подпись, главы со временем, кнопка «К главе N». */
@Composable
private fun NotificationCard(
    item: NotificationItem,
    hidden: Boolean,
    onManga: (MangaItem) -> Unit,
    onChapter: (MangaItem, String) -> Unit,
    onChapterAt: (MangaItem, String, Int) -> Unit,
    onPost: (String) -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val time = relativeTime(item.at) ?: item.date
    // Читать с самой ранней из новых глав (на сайте кнопка ведёт к «своей» главе).
    val target: NotifChapter? = item.chapters.minByOrNull { it.number.toDoubleOrNull() ?: Double.MAX_VALUE }
    // Где пользователь остановился (закладка сайта) — чтобы дочитать до новой главы с того же места.
    val loggedIn by AppGraph.auth.token.collectAsState()
    var resume by remember(item.id) { mutableStateOf<Pair<Chapter, Int>?>(null) }
    LaunchedEffect(item.id, loggedIn) {
        if (loggedIn == null || target == null || item.manga == null) return@LaunchedEffect
        resume = try { AppGraph.repository.bookmarkTarget(item.manga, target.id, target.number) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (item.actors.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                    item.actors.take(3).forEach { a ->
                        AvatarWithFrame(a.avatar, a.frame, 36.dp)
                    }
                }
            } else if (item.manga != null) {
                AsyncImage(
                    model = item.manga.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 36.dp, height = 51.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface),
                )
            }
            Text(
                item.headline ?: item.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable(enabled = item.manga != null) { item.manga?.let(onManga) },
            )
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Ещё", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!hidden) DropdownMenuItem(text = { Text("Скрыть") }, onClick = { menu = false; onHide() })
                    DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        if (item.headline == null) Text(item.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        item.quote?.let { q ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.background).padding(10.dp)) {
                Box(Modifier.size(width = 3.dp, height = 18.dp).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.size(8.dp))
                Text(q, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        item.replies.take(3).forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarWithFrame(r.author.avatar, r.author.frame, 28.dp)
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(r.author.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        relativeTime(r.at)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                    }
                    if (r.text.isNotBlank()) Text(r.text, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (item.headline != null) {
            item.entityTitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            val open: (() -> Unit)? = when {
                item.postSlug != null -> ({ onPost(item.postSlug) })
                item.manga != null -> ({ onManga(item.manga) })
                else -> null
            }
            if (open != null) OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth()) { Text("Открыть страницу") }
            time?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        }
        if (item.chapters.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item.chapters.take(3).forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .clickable(enabled = item.manga != null) { item.manga?.let { onChapter(it, c.id) } }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Глава ${c.number}", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        time?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    }
                }
                if (item.chapters.size > 3) {
                    Text("и ещё ${item.chapters.size - 3}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            if (target != null && item.manga != null) {
                val r = resume
                if (r != null) {
                    Button(onClick = { onChapterAt(item.manga, r.first.id, r.second) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Продолжить: глава ${r.first.number}" + if (r.second > 1) ", стр. ${r.second}" else "")
                    }
                    OutlinedButton(onClick = { onChapter(item.manga, target.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("К новой главе ${target.number}")
                    }
                } else {
                    Button(onClick = { onChapter(item.manga, target.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("К главе ${target.number}")
                    }
                }
            }
        } else {
            time?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        }
    }
}
