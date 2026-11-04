package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.ConversationItem
import me.senkuro.unofficial.data.GuildItem
import me.senkuro.unofficial.data.GuildMemberItem
import me.senkuro.unofficial.data.GuildProfile
import me.senkuro.unofficial.data.MessageItem
import me.senkuro.unofficial.data.UserCardItem

internal fun Throwable.msg() = message?.takeIf { it.isNotBlank() } ?: "Ошибка запроса"

@Composable
internal fun Top(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("Назад") }
        Text(title, style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun Avatar(url: String?, size: Int = 40) {
    AsyncImage(
        model = url, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.size(size.dp).clip(CircleShape),
    )
}

/** Универсальная постраничная загрузка списка. */
internal class Pager<T>(private val fetch: suspend (String?) -> Pair<List<T>, String?>) {
    val items = mutableStateListOf<T>()
    var cursor: String? = null
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var done by mutableStateOf(false)

    suspend fun more() {
        if (loading || done) return
        loading = true; error = null
        try {
            val (list, next) = fetch(cursor)
            items.addAll(list); cursor = next; done = next == null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.msg()
        } finally {
            loading = false
        }
    }

    suspend fun reload() { items.clear(); cursor = null; done = false; more() }
}

@Composable
internal fun <T> PagerError(pager: Pager<T>) {
    val scope = rememberCoroutineScope()
    ErrorBox(pager.error.orEmpty(), onRetry = { scope.launch { pager.reload() } })
}

// ---------------- Переписка

@Composable
fun ConversationsScreen(onBack: () -> Unit, onOpen: (String, String) -> Unit) {
    val pager = remember { Pager { AppGraph.social.conversations(it) } }
    LaunchedEffect(Unit) { pager.reload() }
    Column(Modifier.fillMaxSize()) {
        Top("Сообщения", onBack)
        when {
            pager.items.isEmpty() && pager.loading -> LoadingBox()
            pager.items.isEmpty() && pager.error != null -> PagerError(pager)
            pager.items.isEmpty() -> Text("Диалогов нет", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(pager.items) { c: ConversationItem ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(c.id, c.title) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(c.avatar, 44)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(c.title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.last, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (c.unread > 0) Text("${c.unread}", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                    HorizontalDivider()
                }
                item {
                    LaunchedEffect(pager.items.size) { pager.more() }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(conversationId: String, title: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<MessageItem>() }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var older by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MessageItem?>(null) }
    val state = rememberLazyListState()
    val me = AppGraph.account.value?.userId.orEmpty()

    suspend fun load() {
        loading = true; error = null
        try {
            val (list, prev) = AppGraph.social.messages(conversationId, null)
            messages.clear(); messages.addAll(list); older = prev
            list.lastOrNull()?.let { runCatching { AppGraph.social.markRead(conversationId, me, it.id) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.msg()
        } finally {
            loading = false
        }
    }
    LaunchedEffect(conversationId) { load() }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) state.scrollToItem(messages.size - 1) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Top(title, onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && messages.isEmpty() -> LoadingBox()
                error != null && messages.isEmpty() -> ErrorBox(error!!, onRetry = { scope.launch { load() } })
                else -> LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (older) item {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching {
                                    val (list, prev) = AppGraph.social.messages(conversationId, messages.firstOrNull()?.cursor)
                                    messages.addAll(0, list); older = prev
                                }.onFailure { error = it.msg() }
                            }
                        }) { Text("Показать более ранние") }
                    }
                    items(messages, key = { it.id }) { m ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start) {
                            Column(
                                Modifier.clip(RoundedCornerShape(12.dp))
                                    .background(if (m.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                if (!m.mine) Text(m.author, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Text(m.text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(m.date.orEmpty(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (m.mine) {
                                        Spacer(Modifier.width(8.dp))
                                        Text("Изменить", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { editing = m; text = m.text })
                                    }
                                    if (m.canDelete) {
                                        Spacer(Modifier.width(8.dp))
                                        Text("Удалить", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable {
                                            scope.launch {
                                                runCatching { AppGraph.social.deleteMessage(conversationId, m.id) }
                                                    .onSuccess { messages.remove(m) }
                                                    .onFailure { error = it.msg() }
                                            }
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (error != null && messages.isNotEmpty()) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("Сообщение") }, maxLines = 4)
            Button(
                enabled = text.isNotBlank() && !sending,
                onClick = {
                    val t = text.trim(); sending = true
                    scope.launch {
                        try {
                            val ed = editing
                            if (ed != null) AppGraph.social.editMessage(conversationId, ed.id, t) else AppGraph.social.send(conversationId, t)
                            editing = null
                            text = ""
                            load()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.msg()
                        } finally {
                            sending = false
                        }
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text(if (editing != null) "Сохр." else "Отпр.") }
        }
    }
}

// ---------------- Гильдии

@Composable
fun GuildsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val pager = remember { Pager { AppGraph.social.guilds(it) } }
    LaunchedEffect(Unit) { pager.reload() }
    Column(Modifier.fillMaxSize()) {
        Top("Гильдии", onBack)
        when {
            pager.items.isEmpty() && pager.loading -> LoadingBox()
            pager.items.isEmpty() && pager.error != null -> PagerError(pager)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(pager.items) { g: GuildItem ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(g.slug) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(g.avatar, 44)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(g.name, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(g.level?.let { "ур. $it" }, g.rank?.let { "место $it" }).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
                item { LaunchedEffect(pager.items.size) { pager.more() } }
            }
        }
    }
}

@Composable
fun GuildScreen(slug: String, onBack: () -> Unit) {
    var guild by remember { mutableStateOf<GuildProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var members by remember { mutableStateOf<Pager<GuildMemberItem>?>(null) }
    LaunchedEffect(slug, attempt) {
        error = null
        try {
            val g = AppGraph.social.guild(slug)
            guild = g
            if (g != null) runCatching { AppGraph.social.viewGuild(g.item.id) }
            if (g == null) error = "Гильдия не найдена" else members = Pager { AppGraph.social.guildMembers(g.item.id, it) }.also { it.reload() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.msg()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Top(guild?.item?.name ?: "Гильдия", onBack)
        val g = guild
        val pager = members
        when {
            error != null -> ErrorBox(error!!, onRetry = { attempt++ })
            g == null || pager == null -> LoadingBox()
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(g.item.avatar, 64)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(g.item.name, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                                Text(listOfNotNull(g.tag?.let { "[$it]" }, g.item.level?.let { "ур. $it" }, g.item.rank?.let { "место $it" }).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Участников: ${g.members ?: "?"}" + (g.maxMembers?.let { " / $it" } ?: ""), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (g.viewerIsMember) Text("Вы состоите в гильдии", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                GuildActions(g)
                            }
                        }
                        if (g.description.isNotBlank()) Text(g.description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 12.dp))
                        Text("Участники", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 16.dp))
                    }
                }
                items(pager.items) { m ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(m.avatar, 36)
                        Text(m.name, Modifier.weight(1f).padding(start = 12.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text(listOfNotNull(m.role, m.level?.let { "ур. $it" }).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item { LaunchedEffect(pager.items.size) { pager.more() } }
            }
        }
    }
}

// ---------------- Карточки

@Composable
fun CardsScreen(onBack: () -> Unit, onCard: (String) -> Unit = {}) {
    val me = AppGraph.account.value?.userId.orEmpty()
    val pager = remember { Pager { AppGraph.social.userCards(me, it) } }
    LaunchedEffect(Unit) { pager.reload() }
    Column(Modifier.fillMaxSize()) {
        Top("Мои карточки", onBack)
        when {
            pager.items.isEmpty() && pager.loading -> LoadingBox()
            pager.items.isEmpty() && pager.error != null -> PagerError(pager)
            pager.items.isEmpty() -> Text("Карточек нет", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pager.items) { c: UserCardItem ->
                    Column(Modifier.clickable(enabled = c.slug.isNotBlank()) { onCard(c.slug) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(8.dp))) {
                            AsyncImage(c.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (c.quantity > 1) Text("×${c.quantity}", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.align(Alignment.TopEnd).background(androidx.compose.ui.graphics.Color(0xAA000000)).padding(horizontal = 4.dp))
                        }
                        Text(c.title, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                        Text(listOfNotNull(c.rank, c.modifier).joinToString(" · "), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { LaunchedEffect(pager.items.size) { pager.more() } }
            }
        }
    }
}


@Composable
private fun GuildActions(g: GuildProfile) {
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<String?>(null) }
    var deposit by remember { mutableStateOf(false) }
    var points by remember { mutableStateOf("") }
    val me = AppGraph.account.value?.userId.orEmpty()
    fun run(block: suspend () -> Unit, ok: String) {
        scope.launch {
            info = try { block(); ok } catch (e: CancellationException) { throw e } catch (e: Exception) { e.msg() }
        }
    }
    Row {
        if (!g.viewerIsMember) TextButton(onClick = { run({ AppGraph.social.requestGuildMember(g.item.id) }, "Заявка отправлена") }) {
            Text(if (g.inviteOnly) "Подать заявку" else "Вступить")
        } else {
            TextButton(onClick = { deposit = true }) { Text("Внести очки") }
            TextButton(onClick = { run({ AppGraph.social.leaveGuild(g.item.id, me) }, "Вы вышли из гильдии") }) { Text("Выйти") }
        }
    }
    info?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (deposit) androidx.compose.material3.AlertDialog(
        onDismissRequest = { deposit = false },
        title = { Text("Внести очки в гильдию") },
        text = { OutlinedTextField(points, { points = it.filter(Char::isDigit) }, singleLine = true, label = { Text("Сколько") }) },
        confirmButton = {
            TextButton(enabled = points.toIntOrNull()?.let { it > 0 } == true, onClick = {
                deposit = false
                run({ AppGraph.social.depositGuildPoints(g.item.id, points.toInt()) }, "Очки внесены")
            }) { Text("Внести") }
        },
        dismissButton = { TextButton(onClick = { deposit = false }) { Text("Отмена") } },
    )
}
