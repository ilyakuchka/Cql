@file:OptIn(ExperimentalLayoutApi::class)

package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.CardEntry
import me.senkuro.unofficial.data.CommentItem
import me.senkuro.unofficial.data.MangaDetails
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.data.TitlePage

/** Вкладки страницы тайтла, как на сайте. */
enum class TitleTab(val label: String) { ABOUT("О тайтле"), CHAPTERS("Главы"), COMMENTS("Комментарии"), CARDS("Карточки"), STATS("Статистика") }

@Composable
fun TitleTabBar(selected: TitleTab, onSelect: (TitleTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 13.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TitleTab.entries.forEach { t ->
            val on = t == selected
            Text(
                t.label,
                color = if (on) MaterialTheme.colorScheme.primary else AppColors.DarkSecondary,
                fontSize = 14.sp,
                fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onSelect(t) }.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
    HorizontalDivider(color = AppColors.DarkBorder, thickness = 0.5.dp)
}

private val countryNames = mapOf("KR" to "Корея", "JP" to "Япония", "CN" to "Китай", "US" to "США", "RU" to "Россия", "TW" to "Тайвань", "FR" to "Франция", "GB" to "Великобритания")
private val sourceNames = mapOf("ORIGINAL" to "Оригинал", "WEB_NOVEL" to "Веб-новелла", "LIGHT_NOVEL" to "Ранобэ", "NOVEL" to "Роман", "MANGA" to "Манга", "GAME" to "Игра")
private val formatNames = mapOf(
    "DIGEST" to "Сборник", "DOUJINSHI" to "Додзинси", "IN_COLOR" to "В цвете", "SINGLE" to "Сингл", "WEB" to "Веб",
    "WEBTOON" to "Вебтун", "YONKOMA" to "Ёнкома", "SHORT" to "Short",
)

@Composable
private fun Heading(text: String) =
    Text(text, style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 21.dp, vertical = 6.dp))

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 21.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AppColors.DarkSecondary, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
    }
}

/** «О тайтле»: сведения, ссылки, издательства, переводчики, рекомендации. */
fun LazyListScope.aboutItems(details: MangaDetails, page: TitlePage?, chapterCount: Int?, onOpenManga: (MangaItem) -> Unit) {
    item(key = "about-info") {
        Column(Modifier.padding(top = 8.dp)) {
            Heading("Сведения")
            InfoLine("Статус тайтла", details.status)
            InfoLine("Статус перевода", details.translationStatus)
            InfoLine("Главы", (chapterCount ?: page?.chapters)?.toString())
            InfoLine("Формат", (page?.formats.orEmpty().ifEmpty { details.formats }).joinToString(", ") { formatNames[it] ?: it })
            InfoLine("Просмотры", page?.views)
            InfoLine("Год", page?.year)
            InfoLine("Страна", page?.country?.let { countryNames[it.uppercase()] ?: it })
            InfoLine("Источник", page?.source?.let { sourceNames[it.uppercase()] ?: it })
            InfoLine("Возраст", details.ageRating)
            page?.score?.let { InfoLine("Оценка", "%.2f (%d)".format(java.util.Locale.US, it, page.votes)) }
        }
    }
    if (details.altNames.isNotEmpty()) item(key = "about-alt") {
        Column(Modifier.padding(top = 10.dp)) {
            Heading("Другие названия")
            details.altNames.forEach { name ->
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Row(Modifier.fillMaxWidth().padding(start = 21.dp, end = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = AppColors.DarkSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                    androidx.compose.material3.IconButton(
                        onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(name))
                            android.widget.Toast.makeText(ctx, "Название скопировано", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        androidx.compose.material3.Icon(CopyIcon, contentDescription = "Копировать название", tint = AppColors.DarkSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
    if (page != null && page.links.isNotEmpty()) item(key = "about-links") {
        val uri = LocalUriHandler.current
        Column(Modifier.padding(top = 10.dp)) {
            Heading("Ссылки")
            FlowRow(Modifier.padding(horizontal = 21.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                page.links.forEach { l ->
                    Text(
                        l.label, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(AppColors.DarkItem)
                            .clickable { runCatching { uri.openUri(l.url) } }.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
    if (page != null && page.publishers.isNotEmpty()) item(key = "about-publishers") {
        Column(Modifier.padding(top = 10.dp)) {
            Heading("Издательства")
            page.publishers.forEach { p ->
                Row(Modifier.padding(horizontal = 21.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(p.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.size(28.dp).clip(CircleShape).background(AppColors.DarkItem))
                    Text(p.name, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
    if (page != null && page.teams.isNotEmpty()) item(key = "about-teams") {
        var all by androidx.compose.runtime.remember { mutableStateOf(false) }
        Column(Modifier.padding(top = 10.dp)) {
            Heading("Переводчики")
            page.teams.take(2).forEach { TeamRow(it) }
            if (page.teams.size > 2) {
                TextButton(onClick = { all = true }, modifier = Modifier.padding(horizontal = 13.dp)) { Text("Показать всех (${page.teams.size})") }
            }
        }
        if (all) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { all = false },
                title = { Text("Переводчики (${page.teams.size})") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        page.teams.forEach { TeamRow(it, horizontal = 0.dp) }
                    }
                },
                confirmButton = { TextButton(onClick = { all = false }) { Text("Закрыть") } },
            )
        }
    }
    if (page != null && page.recommendations.isNotEmpty()) item(key = "about-recs") {
        Column(Modifier.padding(top = 12.dp)) {
            Heading("Рекомендуем")
            LazyRow(contentPadding = PaddingValues(horizontal = 21.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(page.recommendations, key = { it.id }) { r -> MangaCard(r, onClick = { onOpenManga(r) }, modifier = Modifier.width(110.dp)) }
            }
        }
    }
}

/** «Статистика»: распределение оценок и число пользователей по спискам. */
fun LazyListScope.statsItems(page: TitlePage?, loading: Boolean) {
    item(key = "stats") {
        Column(Modifier.padding(top = 10.dp, bottom = 8.dp)) {
            if (page == null) {
                Text(if (loading) "Загрузка…" else "Статистика недоступна", color = AppColors.DarkSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 21.dp))
                return@Column
            }
            Heading("Оценки тайтла")
            Text(
                (page.score?.let { "%.2f".format(java.util.Locale.US, it) } ?: "—") + " · " + page.votes + " оценок",
                color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 21.dp, vertical = 2.dp),
            )
            val byScore = page.scores.associate { it.collectionId.toInt() to it.count }
            val max = (byScore.values.maxOrNull() ?: 0).coerceAtLeast(1)
            for (s in 10 downTo 1) {
                val n = byScore[s] ?: 0
                Row(Modifier.fillMaxWidth().padding(horizontal = 21.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$s", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(22.dp))
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(AppColors.DarkItem)) {
                        Box(Modifier.fillMaxWidth(n.toFloat() / max).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
                    }
                    Text("$n", color = AppColors.DarkSecondary, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
                }
            }
            val names = AppGraph.account.value?.collections.orEmpty().associate { it.id to it.name }
            val total = page.lists.sumOf { it.count }
            if (page.lists.isNotEmpty()) {
                Heading("В списках у $total пользователей")
                page.lists.forEachIndexed { i, l ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 21.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(systemListName(l.collectionId) ?: names[l.collectionId] ?: (if (l.collectionId.isBlank()) "Другое" else "Список ${i + 1}"), color = AppColors.DarkSecondary, fontSize = 13.sp)
                        Text("${l.count}", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** Состояние ленты карточек тайтла: «Все карты» (по популярности) / «По рангу», направление. */
internal class TitleCards(private val mangaId: String) {
    var byRank by mutableStateOf(false)
    var ascending by mutableStateOf(false)
    val pager = Pager { AppGraph.cards.catalog(it, null, me.senkuro.unofficial.data.CardFilters(order = if (byRank) "RANK" else "POPULARITY_SCORE"), mangaId, if (ascending) "ASC" else "DESC") }
}

internal fun LazyListScope.cardsItems(state: TitleCards, scope: CoroutineScope, onCard: (String) -> Unit) {
    item(key = "cards-bar") {
        Row(Modifier.padding(horizontal = 21.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineToggle("Все карты", !state.byRank) { if (state.byRank) { state.byRank = false; scope.launch { state.pager.reload() } } }
            OutlineToggle("По рангу", state.byRank) { if (!state.byRank) { state.byRank = true; scope.launch { state.pager.reload() } } }
            OutlineToggle(if (state.ascending) "Сначала старые" else "Сначала новые", false) { state.ascending = !state.ascending; scope.launch { state.pager.reload() } }
        }
    }
    val rows = state.pager.items.chunked(3)
    items(rows.size, key = { "cards-row-$it" }) { i ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 21.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rows[i].forEach { c -> TitleCardTile(c, Modifier.weight(1f), onCard) }
            repeat(3 - rows[i].size) { Box(Modifier.weight(1f)) }
        }
    }
    item(key = "cards-foot") {
        androidx.compose.runtime.LaunchedEffect(state.pager.items.size) { state.pager.more() }
        when {
            state.pager.error != null -> Text("Ошибка: ${state.pager.error}", color = AppColors.DarkSecondary, fontSize = 12.sp, modifier = Modifier.padding(21.dp))
            state.pager.loading -> Text("Загрузка…", color = AppColors.DarkSecondary, fontSize = 12.sp, modifier = Modifier.padding(21.dp))
            state.pager.items.isEmpty() -> Text("Карточек этого тайтла нет", color = AppColors.DarkSecondary, fontSize = 13.sp, modifier = Modifier.padding(21.dp))
        }
    }
}

@Composable
private fun TitleCardTile(c: CardEntry, modifier: Modifier, onCard: (String) -> Unit) {
    Column(modifier.clickable { onCard(c.slug) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(8.dp)).background(AppColors.DarkItem)) {
            AsyncImage(c.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(0.7f))
            c.rank?.let { Text(it, fontSize = 11.sp, color = Color.White, modifier = Modifier.align(Alignment.TopStart).background(Color(0xAA000000)).padding(horizontal = 4.dp)) }
        }
        Text(c.title, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White)
    }
}

@Composable
private fun OutlineToggle(text: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        text, fontSize = 12.sp, color = if (on) MaterialTheme.colorScheme.primary else Color.White,
        modifier = Modifier.clip(shape).background(if (on) AppColors.DarkItem else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Комментарии к самому тайтлу (тред привязан к id тайтла). */
class TitleComments(private val subjectId: String, private val scope: CoroutineScope) {
    val items = mutableStateListOf<CommentItem>()
    var cursor by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var loaded by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var input by mutableStateOf("")
    var editing by mutableStateOf<CommentItem?>(null)
    var replyTo by mutableStateOf<CommentItem?>(null)
    var busy by mutableStateOf(false)

    private fun run(block: suspend () -> Unit) {
        scope.launch {
            try { error = null; block() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: "Ошибка сети"
            }
            loading = false; busy = false; loaded = true
        }
    }

    fun load(reset: Boolean) {
        if (loading) return
        loading = true
        run {
            val page = AppGraph.repository.comments(subjectId, if (reset) null else cursor)
            if (reset) items.clear()
            items.addAll(page.items.filter { n -> items.none { it.id == n.id } })
            cursor = page.next
        }
    }

    fun vote(c: CommentItem, up: Boolean) {
        if (busy) return
        busy = true
        run {
            if (c.vote == up) AppGraph.repository.deleteCommentVote(c.id) else AppGraph.repository.voteComment(c.id, up)
            val now = if (c.vote == up) null else up
            val i = items.indexOfFirst { it.id == c.id }
            if (i >= 0) items[i] = c.copy(
                vote = now,
                up = c.up - (if (c.vote == true) 1 else 0) + (if (now == true) 1 else 0),
                down = c.down - (if (c.vote == false) 1 else 0) + (if (now == false) 1 else 0),
            )
        }
    }

    fun delete(c: CommentItem) {
        if (busy) return
        busy = true
        run { AppGraph.repository.deleteComment(c.id, c.threadId); items.removeAll { it.id == c.id } }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        val target = editing
        busy = true
        run {
            if (target != null) AppGraph.repository.editComment(target.id, text) else AppGraph.repository.addComment(replyTo?.id ?: subjectId, text)
            input = ""; editing = null; replyTo = null
            loading = false
            load(true)
        }
    }
}

fun LazyListScope.commentsItems(state: TitleComments, loggedIn: Boolean, enabled: Boolean) {
    if (!enabled) {
        item(key = "comments-off") { Text("Комментарии к тайтлу отключены", color = AppColors.DarkSecondary, fontSize = 13.sp, modifier = Modifier.padding(21.dp)) }
        return
    }
    item(key = "comments-form") {
        Column(Modifier.padding(horizontal = 21.dp, vertical = 8.dp)) {
            if (!loggedIn) {
                Text("Войдите в аккаунт, чтобы писать комментарии.", color = AppColors.DarkSecondary, fontSize = 12.sp)
            } else {
                OutlinedTextField(
                    value = state.input, onValueChange = { state.input = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (state.editing != null) "Правка комментария" else state.replyTo?.let { "Ответ: ${it.author}" } ?: "Новый комментарий") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.editing != null || state.replyTo != null) TextButton(onClick = { state.editing = null; state.replyTo = null; state.input = "" }) { Text("Отмена") }
                    Box(Modifier.weight(1f))
                    TextButton(enabled = !state.busy && state.input.isNotBlank(), onClick = { state.send() }) {
                        Text(if (state.editing != null) "Сохранить" else "Отправить")
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }
    itemsIndexed(state.items.toList(), key = { _, it -> "c-" + it.id }) { index, c ->
        Column(Modifier.padding(horizontal = 21.dp)) {
            CommentRow(c, threadGap = index > 0, afterReplies = index > 0 && state.items[index - 1].depth > 0, onVote = { state.vote(c, it) }, onEdit = { state.editing = c; state.replyTo = null; state.input = c.text }, onDelete = { state.delete(c) },
                onReply = if (loggedIn) ({ state.replyTo = c; state.editing = null }) else null)
        }
    }
    item(key = "comments-foot") {
        androidx.compose.runtime.LaunchedEffect(Unit) { if (!state.loaded && !state.loading) state.load(true) }
        Column(Modifier.padding(horizontal = 21.dp)) {
            if (state.loading) Text("Загрузка…", color = AppColors.DarkSecondary, fontSize = 12.sp)
            else if (state.cursor != null) TextButton(onClick = { state.load(false) }) { Text("Ещё") }
            else if (state.loaded && state.items.isEmpty() && state.error == null) Text("Комментариев пока нет", color = AppColors.DarkSecondary, fontSize = 13.sp)
        }
    }
}

/** id системных коллекций сайта — base64 от «USER_BOOKMARK_COLLECTION:N»; N=7..12 одинаковы у всех пользователей. */
private fun systemListName(id: String): String? {
    if (id.isBlank()) return null
    val n = runCatching {
        String(android.util.Base64.decode(id, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)).substringAfterLast(':').toInt()
    }.getOrNull()
    return when (n) { 7 -> "Читаю"; 8 -> "В планах"; 9 -> "Прочитано"; 10 -> "Любимое"; 11 -> "Отложено"; 12 -> "Брошено"; else -> null }
}

@Composable
private fun TeamRow(t: me.senkuro.unofficial.data.TitleTeam, horizontal: androidx.compose.ui.unit.Dp = 21.dp) {
    Row(Modifier.padding(horizontal = horizontal, vertical = 4.dp), verticalAlignment = Alignment.Top) {
        AsyncImage(t.avatar, null, contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp).clip(CircleShape).background(AppColors.DarkItem))
        Column(Modifier.padding(start = 10.dp)) {
            Text(t.name + if (t.verified) " ✓" else "", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            t.volumes.forEach { Text(it, color = AppColors.DarkSecondary, fontSize = 11.sp) }
        }
    }
}

/** Иконка «копировать» (в базовом наборе material-icons её нет, рисуем по пути из Material Icons). */
private val CopyIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "ContentCopy", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString("M16,1L4,1c-1.1,0 -2,0.9 -2,2v14h2L4,3h12L16,1zM19,5L8,5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2L21,7c0,-1.1 -0.9,-2 -2,-2zM19,21L8,21L8,7h11v14z")
            .toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
    ).build()
}
