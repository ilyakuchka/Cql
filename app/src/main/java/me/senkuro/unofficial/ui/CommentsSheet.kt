@file:OptIn(ExperimentalMaterial3Api::class)

package me.senkuro.unofficial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.CommentItem

/** Комментарии к странице главы (на сайте тред привязан к странице): список, отправка, правка, удаление. */
@Composable
fun CommentsSheet(chapterSlug: String, pageIndex: Int, onDismiss: () -> Unit) {
    val openUser = LocalOpenUser.current
    val scope = rememberCoroutineScope()
    val token by AppGraph.auth.token.collectAsState()
    var subject by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<CommentItem>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<CommentItem?>(null) }
    var replyTo by remember { mutableStateOf<CommentItem?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(CommentSort.NORMAL) }

    fun guarded(block: suspend () -> Unit) {
        scope.launch {
            try {
                error = null
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: "Ошибка сети"
            }
            loading = false
            busy = false
        }
    }

    fun load(reset: Boolean) {
        val id = subject ?: return
        loading = true
        guarded {
            val page = AppGraph.repository.comments(id, if (reset) null else cursor, sort.asc)
            items = if (reset) page.items else (items + page.items).distinctBy { it.id }
            cursor = page.next
        }
    }

    LaunchedEffect(chapterSlug, pageIndex) {
        guarded {
            val ids = AppGraph.repository.pageIds(chapterSlug)
            subject = ids.getOrNull(pageIndex) ?: ids.firstOrNull()
            if (subject == null) error = "Для этой главы комментарии недоступны" else load(true)
        }
    }

    val shown = remember(items, sort) { sortComments(items, sort) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
      var visible by remember { mutableStateOf(false) }
      LaunchedEffect(Unit) { visible = true }
      androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { it }),
      ) {
       androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = onDismiss) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Text("Комментарии · стр. ${pageIndex + 1}", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground)
            }
            CommentSortRow(sort) { new ->
                val reload = new.asc != sort.asc
                sort = new
                if (reload) load(true)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            LazyColumn(Modifier.weight(1f)) {
                if (items.isEmpty() && !loading && error == null) {
                    item { Text("Комментариев пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
                }
                itemsIndexed(shown, key = { _, it -> it.id }) { index, c ->
                    CommentRow(
                        c,
                        threadGap = index > 0,
                        afterReplies = index > 0 && shown[index - 1].depth > 0,
                        onUser = { onDismiss(); openUser(it) },
                        onVote = { up ->
                            if (!busy) {
                                busy = true
                                guarded {
                                    if (c.vote == up) AppGraph.repository.deleteCommentVote(c.id) else AppGraph.repository.voteComment(c.id, up)
                                    val now = if (c.vote == up) null else up
                                    items = items.map {
                                        if (it.id != c.id) it else it.copy(
                                            vote = now,
                                            up = it.up - (if (it.vote == true) 1 else 0) + (if (now == true) 1 else 0),
                                            down = it.down - (if (it.vote == false) 1 else 0) + (if (now == false) 1 else 0),
                                        )
                                    }
                                }
                            }
                        },
                        onEdit = { editing = c; replyTo = null; input = c.text },
                        onReply = if (token != null) ({ replyTo = c; editing = null }) else null,
                        onDelete = {
                            if (!busy) {
                                busy = true
                                guarded {
                                    AppGraph.repository.deleteComment(c.id, c.threadId)
                                    items = items.filterNot { it.id == c.id }
                                }
                            }
                        },
                    )
                }
                if (cursor != null) item {
                    TextButton(onClick = { load(false) }, enabled = !loading) { Text("Ещё") }
                }
            }
            if (token == null) {
                Text("Войдите в аккаунт, чтобы писать комментарии.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            } else {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(if (editing != null) "Правка комментария" else replyTo?.let { "Ответ: ${it.author}" } ?: "Новый комментарий") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (editing != null || replyTo != null) TextButton(onClick = { editing = null; replyTo = null; input = "" }) { Text("Отмена") }
                    Row(Modifier.weight(1f)) {}
                    TextButton(
                        enabled = !busy && input.isNotBlank() && subject != null,
                        onClick = {
                            val text = input.trim()
                            val target = editing
                            busy = true
                            guarded {
                                if (target != null) AppGraph.repository.editComment(target.id, text)
                                else AppGraph.repository.addComment(replyTo?.id ?: subject!!, text)
                                input = ""
                                editing = null
                                replyTo = null
                                load(true)
                            }
                        },
                    ) { Text(if (editing != null) "Сохранить" else "Отправить") }
                }
            }
        }
       }
      }
    }
}

enum class CommentSort(val title: String, val asc: Boolean = false) {
    NORMAL("Обычная"), REPLIES("Популярные по ответам"), LIKES("Популярные по лайкам"), NEWEST("Самые новые"), OLDEST("Самые старые", asc = true),
}

/** Сортирует ветки целиком (корень + ответы). Обычная/новые/старые опираются на порядок сервера; популярные — среди загруженных. */
internal fun sortComments(items: List<CommentItem>, sort: CommentSort): List<CommentItem> {
    if (sort == CommentSort.NORMAL) return items
    val groups = LinkedHashMap<String, MutableList<CommentItem>>()
    items.forEach { groups.getOrPut(it.threadId) { mutableListOf() }.add(it) }
    fun root(g: List<CommentItem>) = g.firstOrNull { it.depth == 0 } ?: g.first()
    val list = groups.values.toList()
    val sorted = when (sort) {
        CommentSort.REPLIES -> list.sortedByDescending { g -> g.count { it.depth > 0 } }
        CommentSort.LIKES -> list.sortedByDescending { g -> root(g).up - root(g).down }
        CommentSort.NEWEST -> list.sortedByDescending { g -> root(g).createdAt.orEmpty() }
        CommentSort.OLDEST -> list.sortedBy { g -> root(g).createdAt.orEmpty() }
        CommentSort.NORMAL -> list
    }
    return sorted.flatten()
}

@Composable
internal fun CommentSortRow(current: CommentSort, onPick: (CommentSort) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CommentSort.entries.forEach {
            androidx.compose.material3.FilterChip(selected = it == current, onClick = { onPick(it) }, label = { Text(it.title) })
        }
    }
}

@Composable
internal fun CommentRow(c: CommentItem, onVote: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onReply: (() -> Unit)? = null, onUser: ((String) -> Unit)? = null, threadGap: Boolean = false, afterReplies: Boolean = false) {
    val openUser = onUser ?: LocalOpenUser.current
    var confirmDelete by remember { mutableStateOf(false) }
    val reply = c.depth > 0
    val indent = c.depth.coerceIn(0, 4) * 18
    val lineColor = MaterialTheme.colorScheme.primary
    val bandColor = MaterialTheme.colorScheme.surfaceVariant
    // Новая ветка (корневой комментарий) отделяется широкой полосой, ответы — отступом и линией слева
    // Широкая полоса — только после ветки с ответами; между одиночными комментариями тонкая линия.
    if (!reply && threadGap) {
        if (afterReplies) androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(10.dp).background(bandColor))
        else HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (reply) Modifier.background(bandColor.copy(alpha = 0.45f)).drawBehind {
                    val x = (indent - 9).dp.toPx()
                    drawLine(lineColor.copy(alpha = 0.7f), Offset(x, 4.dp.toPx()), Offset(x, size.height - 4.dp.toPx()), strokeWidth = 2.dp.toPx())
                } else Modifier,
            )
            .padding(start = indent.dp, top = 8.dp, bottom = 8.dp, end = if (reply) 4.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (reply) Text("↳ ответ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        Row(
            Modifier.clickable(enabled = c.authorSlug.isNotBlank()) { openUser(c.authorSlug) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val mine = isMe(slug = c.authorSlug)
            AvatarWithFrame(c.avatar, c.frame, 30.dp, Modifier.streamerBlur(mine, 10.dp))
            Text(
                c.author.ifBlank { "—" },
                color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).streamerBlur(mine),
            )
            if (c.status != null) coil3.compose.AsyncImage(c.status, c.statusCaption, modifier = Modifier.size(18.dp))
            c.date?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        }
        Text(c.text.ifBlank { "…" }, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onVote(true) }) {
                val tint = if (c.vote == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.ThumbUp, contentDescription = "Нравится", tint = tint, modifier = Modifier.size(18.dp))
                Text(" ${c.up}", fontSize = 12.sp, color = tint)
            }
            TextButton(onClick = { onVote(false) }) {
                val tint = if (c.vote == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                // ThumbDown есть только в расширенном наборе иконок — берём ThumbUp, перевёрнутый вверх ногами
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.ThumbUp, contentDescription = "Не нравится", tint = tint,
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 180f },
                )
                Text(" ${c.down}", fontSize = 12.sp, color = tint)
            }
            Row(Modifier.weight(1f)) {}
            if (onReply != null) TextButton(onClick = onReply) { Text("Ответить", fontSize = 12.sp) }
            if (c.canEdit) androidx.compose.material3.IconButton(onClick = onEdit) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Edit, contentDescription = "Изменить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (c.canDelete) androidx.compose.material3.IconButton(onClick = { confirmDelete = true }) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить комментарий?") },
            text = { Text("Комментарий будет удалён с сайта от вашего имени. Это действие нельзя отменить.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

/** Переход в профиль пользователя; задаётся в AppRoot. */
val LocalOpenUser = androidx.compose.runtime.staticCompositionLocalOf<(String) -> Unit> { {} }
