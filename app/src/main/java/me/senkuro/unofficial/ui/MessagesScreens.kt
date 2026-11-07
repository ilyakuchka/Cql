package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.MessageItem

/** Палитра в стиле WhatsApp (макет WhatsApp Mobile UI Kit) для светлой и тёмной темы. */
private class Wa(val dark: Boolean) {
    val header = if (dark) Color(0xFF1F2C34) else Color(0xFFF0F2F5)
    val list = if (dark) Color(0xFF111B21) else Color.White
    val field = if (dark) Color(0xFF202C33) else Color(0xFFF0F2F5)
    val text = if (dark) Color(0xFFE9EDEF) else Color(0xFF111B21)
    val sub = if (dark) Color(0xFF8696A0) else Color(0xFF667781)
    val divider = if (dark) Color(0xFF222D34) else Color(0xFFE9EDEF)
    val chatBg = if (dark) Color(0xFF0B141A) else Color(0xFFEFEAE2)
    val bubbleIn = if (dark) Color(0xFF202C33) else Color.White
    val bubbleOut = if (dark) Color(0xFF005C4B) else Color(0xFFD9FDD3)
    val chip = if (dark) Color(0xFF182229) else Color.White
    val green = Color(0xFF00A884)
    val badge = Color(0xFF25D366)
}

@Composable
private fun wa(): Wa = Wa(MaterialTheme.colorScheme.background.luminance() < 0.5f)

private fun dayLabel(date: String?): String {
    val d = date?.take(10).orEmpty()
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    return if (d == today) "СЕГОДНЯ" else d
}

// ---------------- Список чатов

@Composable
fun ConversationsScreen(onBack: () -> Unit, onOpen: (String, String) -> Unit) {
    val c = wa()
    val pager = remember { Pager { AppGraph.social.conversations(it) } }
    var query by remember { mutableStateOf("") }
    val account by AppGraph.account.collectAsState()
    LaunchedEffect(Unit) { pager.reload() }
    Column(Modifier.fillMaxSize().background(c.list)) {
        // Шапка: аватар, заголовок
        Row(
            Modifier.fillMaxWidth().background(c.header).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(account?.avatar, 40, account?.frame)
            Text("Сообщения", Modifier.padding(start = 12.dp), fontSize = 20.sp, color = c.text)
        }
        // Поиск (пилюля)
        Row(
            Modifier.fillMaxWidth().background(c.header).padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (c.dark) c.field else Color(0xFFE9EDEF)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = c.sub, modifier = Modifier.size(20.dp))
                Box(Modifier.padding(start = 12.dp).weight(1f)) {
                    if (query.isEmpty()) Text("Поиск диалога", color = c.sub, fontSize = 15.sp)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 15.sp), cursorBrush = SolidColor(c.green), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        val shown = pager.items.filter { query.isBlank() || it.title.contains(query, true) || it.last.contains(query, true) }
        when {
            pager.items.isEmpty() && pager.loading -> LoadingBox()
            pager.items.isEmpty() && pager.error != null -> PagerError(pager)
            shown.isEmpty() -> Text("Диалогов нет", Modifier.padding(24.dp), color = c.sub)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { conv ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(conv.id, conv.title) }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(conv.avatar, 52, conv.frame)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Column(Modifier.padding(vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(conv.title, Modifier.weight(1f), fontSize = 17.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(conv.date.orEmpty(), fontSize = 12.sp, color = if (conv.unread > 0) c.badge else c.sub)
                                }
                                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(conv.last, Modifier.weight(1f), fontSize = 14.sp, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (conv.unread > 0) Box(
                                        Modifier.padding(start = 8.dp).defaultMinSize(22.dp).clip(CircleShape).background(c.badge).padding(horizontal = 6.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Text("${conv.unread}", color = Color.White, fontSize = 11.sp) }
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(0.6.dp).background(c.divider))
                        }
                    }
                }
                item { LaunchedEffect(pager.items.size) { pager.more() } }
            }
        }
    }
}

// ---------------- Диалог

@Composable
fun ChatScreen(conversationId: String, title: String, onBack: () -> Unit) {
    val c = wa()
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

    Column(Modifier.fillMaxSize().background(c.chatBg).imePadding()) {
        // Шапка диалога
        Row(Modifier.fillMaxWidth().background(c.header).statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Назад", tint = c.text) }
            messages.firstOrNull { !it.mine }.let { Avatar(it?.avatar, 40, it?.frame) }
            Text(title, Modifier.padding(start = 12.dp), fontSize = 18.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && messages.isEmpty() -> LoadingBox()
                error != null && messages.isEmpty() -> ErrorBox(error!!, onRetry = { scope.launch { load() } })
                else -> LazyColumn(
                    state = state, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (older) item {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching {
                                    val (list, prev) = AppGraph.social.messages(conversationId, messages.firstOrNull()?.cursor)
                                    messages.addAll(0, list); older = prev
                                }.onFailure { error = it.msg() }
                            }
                        }) { Text("Показать более ранние", color = c.green) }
                    }
                    items(messages.size, key = { messages[it].id }) { i ->
                        val m = messages[i]
                        val prev = messages.getOrNull(i - 1)
                        if (prev == null || prev.date?.take(10) != m.date?.take(10)) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(dayLabel(m.date), Modifier.clip(RoundedCornerShape(8.dp)).background(c.chip).padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 12.sp, color = c.sub)
                            }
                        }
                        val first = prev == null || prev.mine != m.mine || prev.author != m.author || prev.date?.take(10) != m.date?.take(10)
                        Row(Modifier.fillMaxWidth().padding(top = if (first) 6.dp else 0.dp), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                            val shape = RoundedCornerShape(
                                topStart = if (!m.mine && first) 0.dp else 10.dp, topEnd = if (m.mine && first) 0.dp else 10.dp,
                                bottomStart = 10.dp, bottomEnd = 10.dp,
                            )
                            Column(
                                Modifier.widthIn(max = 300.dp).clip(shape).background(if (m.mine) c.bubbleOut else c.bubbleIn).padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                if (!m.mine && first) Text(m.author, fontSize = 13.sp, color = c.green)
                                SelectionContainer { Text(m.text, fontSize = 15.sp, color = c.text) }
                                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                                    if (m.mine) Text("Изменить  ", fontSize = 11.sp, color = c.green, modifier = Modifier.clickable { editing = m; text = m.text })
                                    if (m.canDelete) Text("Удалить  ", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable {
                                        scope.launch {
                                            runCatching { AppGraph.social.deleteMessage(conversationId, m.id) }
                                                .onSuccess { messages.remove(m) }
                                                .onFailure { error = it.msg() }
                                        }
                                    })
                                    Text(m.date?.takeLast(5).orEmpty() + if (m.mine) " ✓" else "", fontSize = 11.sp, color = c.sub)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (error != null && messages.isNotEmpty()) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
        editing?.let {
            Row(Modifier.fillMaxWidth().background(c.header).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Изменение: ${it.text}", Modifier.weight(1f), fontSize = 13.sp, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Отмена", color = c.green, fontSize = 13.sp, modifier = Modifier.clickable { editing = null; text = "" })
            }
        }
        // Ввод: поле-пилюля и круглая зелёная кнопка отправки
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(c.bubbleIn).padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (text.isEmpty()) Text("Сообщение", color = c.sub, fontSize = 16.sp)
                BasicTextField(text, { text = it }, maxLines = 5, textStyle = TextStyle(color = c.text, fontSize = 16.sp), cursorBrush = SolidColor(c.green), modifier = Modifier.fillMaxWidth())
            }
            Box(
                Modifier.padding(start = 8.dp).size(48.dp).clip(CircleShape).background(if (text.isNotBlank() && !sending) c.green else c.green.copy(alpha = 0.5f))
                    .clickable(enabled = text.isNotBlank() && !sending) {
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
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Send, "Отправить", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
    }
}
