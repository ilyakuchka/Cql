@file:OptIn(ExperimentalMaterial3Api::class)

package me.senkuro.unofficial.debugtools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import me.senkuro.unofficial.data.ApiCall
import me.senkuro.unofficial.data.ApiTrace
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Журнал GraphQL-запросов приложения (только debug): что отправили, что ответил сервер, где ошибки. */
object ApiJournal {
    val calls = mutableStateListOf<ApiCall>()

    /**
     * Операции, проверенные по журналу: сервер принял запросы без ошибок и расхождений со схемой.
     * Успешные вызовы этих операций в журнал не пишутся (чтобы не флудили); при ошибке запись всё равно появится.
     * Новые проверенные операции добавляйте сюда.
     */
    val verified = setOf(
        "fetchViewer", "fetchViewerUnreadNotifications", "fetchMainAd", "fetchMainUserTops",
        "fetchMangaBookmarkNextChapters", "fetchUserBookmarks",
        "HomeLatest", "HomeNew", "HomePopular", "HomeRecommendations",
        "viewAd", "fetchNotificationFeeds",
        "searchTachiyomiManga", "fetchTachiyomiSearchFilters", "fetchTachiyomiChapters", "fetchTachiyomiManga", "viewManga",
        "fetchActivities", "fetchConversationMessages", "readConversationMessage", "updateConversationMessage",
        "fetchFeed", "fetchShortsMangaChapters",
        "fetchPosts", "fetchPost", "viewPost", "openCardPack", "fetchPendingPackOpens", "bulkChooseCardPackOpen",
        "fetchCardDecks", "fetchCardDeck", "fetchCardPacks", "fetchCardPack", "fetchCollectibles", "fetchForgeCardsSlots", "fetchCardOffers",
        "fetchUserCards", "fetchUserTasks", "fetchCards", "fetchCard", "viewCard", "voteCard",
        "fetchConversations", "fetchUser", "fetchUserAppearance", "fetchUserAchievements", "fetchUserProfileExtra",
        "fetchMangaChapters", "fetchMangaRelations", "fetchMangaViewerState", "fetchMangaCharacters", "fetchMangaStaff",
    )

    fun install() {
        calls.size // создаём состояние заранее, вне композиции
        ApiTrace.sink = { c ->
            runCatching {
                if (c.operation in verified && c.error == null && schemaProblem(c) == null) return@runCatching
                calls.add(c)
                while (calls.size > 300) calls.removeAt(0)
            }
        }
    }

    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    fun time(c: ApiCall): String = fmt.format(Date(c.time))

    /** Серверные ошибки валидации («Cannot query field…», «Unknown type…») — это и есть сверка запроса со схемой. */
    fun schemaProblem(c: ApiCall): String? {
        val r = c.response
        return if (r.contains("Cannot query field") || r.contains("Unknown type") || r.contains("Unknown argument") ||
            r.contains("Variable \"$") || r.contains("GRAPHQL_VALIDATION_FAILED") || r.contains("BAD_USER_INPUT")) {
            Regex("\"message\"\\s*:\\s*\"([^\"]{0,300})").findAll(r).map { it.groupValues[1] }.distinct().take(5).joinToString("\n")
        } else null
    }

    fun format(c: ApiCall, withQuery: Boolean = true): String = buildString {
        append("[${time(c)}] ${c.operation}  HTTP ${c.httpCode}  ${c.durationMs} мс")
        if (c.error != null) append("\nОШИБКА: ${c.error}")
        schemaProblem(c)?.let { append("\nНЕ СХОДИТСЯ СО СХЕМОЙ:\n$it") }
        append("\nvariables: ${c.variables}")
        if (withQuery) append("\nquery:\n${c.query.trim()}")
        append("\nresponse:\n${c.response}")
    }

    fun report(list: List<ApiCall>) = list.joinToString("\n\n" + "=".repeat(40) + "\n\n") { format(it) }
}

@Composable
fun ApiJournalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var onlyErrors by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<ApiCall?>(null) }
    val list = ApiJournal.calls.filter { !onlyErrors || it.error != null || ApiJournal.schemaProblem(it) != null }.asReversed()

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (open != null) open = null else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text(if (open != null) open!!.operation else "Журнал запросов (${ApiJournal.calls.size})", style = MaterialTheme.typography.titleMedium)
        }
        val cur = open
        if (cur != null) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copy(context, ApiJournal.format(cur)) }) { Text("Копировать") }
                OutlinedButton(onClick = { share(context, ApiJournal.format(cur), "senkuro-request.txt") }) { Text("Поделиться") }
            }
            SelectionContainer {
                Text(ApiJournal.format(cur), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()))
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = onlyErrors, onClick = { onlyErrors = !onlyErrors }, label = { Text("Только ошибки") })
                OutlinedButton(onClick = { share(context, ApiJournal.report(ApiJournal.calls.toList()), "senkuro-requests.txt") }) { Text("Поделиться всем") }
                OutlinedButton(onClick = { share(context, ApiJournal.report(list.reversed()), "senkuro-requests-filtered.txt") }) { Text("Поделиться списком") }
                OutlinedButton(onClick = { ApiJournal.calls.clear() }) { Text("Очистить") }
            }
            if (list.isEmpty()) Text("Пока пусто: пользуйтесь приложением, запросы появятся здесь", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxSize()) {
                items(list) { c ->
                    val bad = c.error != null || ApiJournal.schemaProblem(c) != null
                    Column(Modifier.fillMaxWidth().clickable { open = c }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text((if (bad) "✗ " else "✓ ") + c.operation, color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground)
                        Text("${ApiJournal.time(c)} · HTTP ${c.httpCode} · ${c.durationMs} мс", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        (ApiJournal.schemaProblem(c) ?: c.error)?.let { Text(it.take(160), fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    runCatching {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("request", text.take(200_000)))
        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: Context, text: String, name: String) {
    try {
        val dir = File(context.cacheDir, "debug").apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(text)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.debugfiles", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться журналом").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "Не удалось поделиться: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
