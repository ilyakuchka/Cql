package me.senkuro.unofficial.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph

/** Выбор коллекции серверных закладок для тайтла (нужен вход на сайте). */
@Composable
fun BookmarkDialog(mangaId: String, onLogin: () -> Unit, onDismiss: () -> Unit) {
    val token by AppGraph.auth.token.collectAsState()
    val account by AppGraph.account.collectAsState()
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(mangaId, token) {
        if (token == null) return@LaunchedEffect
        try {
            current = AppGraph.repository.bookmarkOf(mangaId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message
        }
    }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
                message = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "Ошибка сети"
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Закладки на сайте") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                val acc = account
                when {
                    token == null -> {
                        Text("Чтобы добавлять тайтлы в закладки, войдите в аккаунт.")
                        TextButton(onClick = { onDismiss(); onLogin() }) { Text("Войти через сайт") }
                    }
                    acc == null -> Text("Загружаю аккаунт…")
                    else -> {
                        acc.collections.forEach { c ->
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    run {
                                        AppGraph.repository.setBookmark(mangaId, c.id)
                                        current = c.id
                                        AppGraph.setMark(mangaId, c.name)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (current == c.id) "✓ ${c.name}" else c.name) }
                        }
                        if (current != null) {
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    run {
                                        AppGraph.repository.removeBookmark(mangaId)
                                        current = null
                                        AppGraph.setMark(mangaId, null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Убрать из закладок") }
                        }
                    }
                }
                message?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
