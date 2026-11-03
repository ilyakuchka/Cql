package me.senkuro.unofficial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph

/** Оценка тайтла от 1 до 10 (voteManga); текущая оценка читается из fetchMangaViewerState. */
@Composable
fun RateDialog(mangaId: String, slug: String, onLogin: () -> Unit, onDismiss: () -> Unit) {
    val token by AppGraph.auth.token.collectAsState()
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(slug, token) {
        if (token == null) return@LaunchedEffect
        try {
            current = AppGraph.repository.viewerVote(slug)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ваша оценка" + (current?.let { ": $it" } ?: "")) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (token == null) {
                    Text("Чтобы оценивать тайтлы, войдите в аккаунт.")
                    TextButton(onClick = { onDismiss(); onLogin() }) { Text("Войти") }
                } else {
                    listOf(1..5, 6..10).forEach { range ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            range.forEach { n ->
                                TextButton(
                                    enabled = !busy,
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            try {
                                                AppGraph.repository.vote(mangaId, n)
                                                current = n
                                                message = null
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                message = e.message ?: "Ошибка сети"
                                            }
                                            busy = false
                                        }
                                    },
                                ) { Text(if (current == n) "[$n]" else "$n", fontSize = 16.sp) }
                            }
                        }
                    }
                    if (current != null) {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        AppGraph.repository.deleteVote(mangaId)
                                        current = null
                                        message = null
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        message = e.message ?: "Ошибка сети"
                                    }
                                    busy = false
                                }
                            },
                        ) { Text("Убрать оценку") }
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
