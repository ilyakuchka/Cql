@file:OptIn(ExperimentalMaterial3Api::class)

package me.senkuro.unofficial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
            val page = AppGraph.repository.comments(id, if (reset) null else cursor)
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Комментарии · стр. ${pageIndex + 1}", style = HeadingStyle, color = MaterialTheme.colorScheme.onSurface)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                if (items.isEmpty() && !loading && error == null) {
                    item { Text("Комментариев пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
                }
                items(items, key = { it.id }) { c ->
                    CommentRow(
                        c,
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
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

@Composable
internal fun CommentRow(c: CommentItem, onVote: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onReply: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = (c.depth.coerceIn(0, 3) * 14).dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            listOfNotNull(c.author.ifBlank { null }, c.date).joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
        )
        Text(c.text.ifBlank { "…" }, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onVote(true) }) {
                Text("▲ ${c.up}", fontSize = 12.sp, color = if (c.vote == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { onVote(false) }) {
                Text("▼ ${c.down}", fontSize = 12.sp, color = if (c.vote == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.weight(1f)) {}
            if (onReply != null) TextButton(onClick = onReply) { Text("Ответить", fontSize = 12.sp) }
            if (c.canEdit) TextButton(onClick = onEdit) { Text("Изменить", fontSize = 12.sp) }
            if (c.canDelete) TextButton(onClick = onDelete) { Text("Удалить", fontSize = 12.sp) }
        }
    }
}
