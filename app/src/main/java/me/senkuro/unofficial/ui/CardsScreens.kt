package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.CardDetails
import me.senkuro.unofficial.data.CardEntry
import me.senkuro.unofficial.data.CharacterProfile
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.data.TaskItem

@Composable
private fun CardGrid(pager: Pager<CardEntry>, onCard: (String) -> Unit, header: (@Composable () -> Unit)? = null) {
    LazyVerticalGrid(
        GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
        items(pager.items) { c ->
            Column(Modifier.clickable { onCard(c.slug) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    AsyncImage(c.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    c.rank?.let {
                        Text(it, fontSize = 11.sp, color = Color.White, modifier = Modifier.align(Alignment.TopStart).background(Color(0xAA000000)).padding(horizontal = 4.dp))
                    }
                }
                Text(c.title, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { LaunchedEffect(pager.items.size) { pager.more() } }
    }
}

@Composable
fun CardCatalogScreen(onBack: () -> Unit, onCard: (String) -> Unit) {
    val pager = remember { Pager { AppGraph.cards.catalog(it) } }
    LaunchedEffect(Unit) { pager.reload() }
    Column(Modifier.fillMaxSize()) {
        Top("Каталог карточек", onBack)
        when {
            pager.items.isEmpty() && pager.loading -> LoadingBox()
            pager.items.isEmpty() && pager.error != null -> PagerError(pager)
            else -> CardGrid(pager, onCard)
        }
    }
}

@Composable
fun CardScreen(slug: String, onBack: () -> Unit, onCharacter: (String) -> Unit, onManga: (String) -> Unit) {
    var card by remember { mutableStateOf<CardDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(slug, attempt) {
        error = null
        try {
            card = AppGraph.cards.card(slug)
            if (card == null) error = "Карточка не найдена"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.msg()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Top(card?.entry?.title ?: "Карточка", onBack)
        val c = card
        when {
            error != null -> ErrorBox(error!!, onRetry = { attempt++ })
            c == null -> LoadingBox()
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                AsyncImage(
                    c.entry.image, null, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                )
                Text(c.entry.title, style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 12.dp))
                Text(
                    listOfNotNull(c.entry.rank?.let { "Ранг $it" }, c.date, c.author?.let { "автор $it" }).joinToString(" · "),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    listOfNotNull(c.owners?.let { "владельцев $it" }, c.wishes?.let { "желают $it" }, c.upvotes?.let { "👍 $it" }, c.views?.let { "просмотров $it" }).joinToString(" · "),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                )
                var vote by remember(c.id) { mutableStateOf(c.vote) }
                var ups by remember(c.id) { mutableStateOf(c.upvotes ?: 0) }
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                Row(Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = {
                        val next = if (vote == true) null else true
                        scope.launch {
                            runCatching { AppGraph.cards.voteCard(c.id, next) }.onSuccess {
                                ups += (if (next == true) 1 else 0) - (if (vote == true) 1 else 0); vote = next
                            }
                        }
                    }) { Text("👍 $ups", color = if (vote == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = {
                        val next = if (vote == false) null else false
                        scope.launch {
                            runCatching { AppGraph.cards.voteCard(c.id, next) }.onSuccess {
                                ups -= if (vote == true) 1 else 0; vote = next
                            }
                        }
                    }) { Text("👎", color = if (vote == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                c.mine?.let { Text("У вас: $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                if (c.description.isNotBlank()) SelectionContainer {
                    Text(c.description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 12.dp))
                }
                c.characterTitle?.let { t -> c.characterSlug?.let { s -> TextButton(onClick = { onCharacter(s) }) { Text("Персонаж: $t") } } }
                c.entityTitle?.let { t -> if (c.entityIsManga && c.entityId != null) TextButton(onClick = { onManga(c.entityId) }) { Text("Тайтл: $t") } }
            }
        }
    }
}

@Composable
fun CharacterScreen(slug: String, onBack: () -> Unit, onManga: (MangaItem) -> Unit, onCard: (String) -> Unit) {
    var profile by remember { mutableStateOf<CharacterProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var cards by remember { mutableStateOf<Pager<CardEntry>?>(null) }
    var mangas by remember { mutableStateOf<Pager<MangaItem>?>(null) }
    LaunchedEffect(slug, attempt) {
        error = null
        try {
            val p = AppGraph.cards.character(slug)
            profile = p
            if (p == null) error = "Персонаж не найден" else {
                mangas = Pager { AppGraph.cards.mangasOf(p.id, it) }.also { it.reload() }
                cards = Pager { AppGraph.cards.catalog(it, p.id) }.also { it.reload() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.msg()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Top(profile?.name ?: "Персонаж", onBack)
        val p = profile
        val cp = cards
        val mp = mangas
        when {
            error != null -> ErrorBox(error!!, onRetry = { attempt++ })
            p == null || cp == null || mp == null -> LoadingBox()
            else -> CardGrid(cp, onCard) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(p.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(p.name, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                            p.gender?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    if (mp.items.isNotEmpty()) {
                        Text("Тайтлы", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(mp.items) { m -> MangaCard(m, onClick = { onManga(m) }, compact = true, modifier = Modifier.width(100.dp)) }
                        }
                    }
                    if (cp.items.isNotEmpty()) Text("Карточки", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
fun TasksScreen(onBack: () -> Unit) {
    val me = AppGraph.account.value?.userId.orEmpty()
    var onlyOpen by remember { mutableStateOf(true) }
    var tasks by remember { mutableStateOf<List<TaskItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(onlyOpen, attempt) {
        error = null; tasks = null
        try {
            tasks = AppGraph.cards.tasks(me, onlyOpen)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.msg()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Top("Задания", onBack)
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = { onlyOpen = true }) { Text(if (onlyOpen) "● Активные" else "Активные") }
            TextButton(onClick = { onlyOpen = false }) { Text(if (!onlyOpen) "● Все" else "Все") }
        }
        val list = tasks
        when {
            error != null -> ErrorBox(error!!, onRetry = { attempt++ })
            list == null -> LoadingBox()
            list.isEmpty() -> Text("Заданий нет", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list) { t -> TaskCard(t) }
            }
        }
    }
}

@Composable
private fun RewardTile(label: String, value: Int, tint: Color, coin: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            if (coin) Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFFFB020)), contentAlignment = Alignment.Center) {
                Text("◉", color = Color(0xFFFFE08A), fontSize = 18.sp)
            } else Text(label, color = tint, fontSize = 22.sp)
        }
        Text("$value", color = tint, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
    }
}

/** Карточка задания в стиле сайта: название, «NEW» и таймер, награда (опыт и очки), прогресс «N из M за период». */
@Composable
internal fun TaskCard(t: TaskItem, modifier: Modifier = Modifier.fillMaxWidth()) {
    var left by remember(t.endAtMillis) { mutableStateOf(t.endAtMillis?.let { it - System.currentTimeMillis() }) }
    LaunchedEffect(t.endAtMillis) {
        while (t.endAtMillis != null) {
            left = t.endAtMillis - System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✦", color = Color(0xFF5BC0FF), fontSize = 22.sp)
            Text(t.title, Modifier.weight(1f).padding(horizontal = 10.dp), fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (t.progress == 0 && !t.done) Text(
                    "NEW", fontSize = 11.sp, color = Color.Black,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 2.dp),
                )
                left?.takeIf { it > 0 }?.let { ms ->
                    val sec = ms / 1000
                    val text = if (sec >= 86400) "${sec / 86400} д ${(sec % 86400) / 3600} ч" else "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
                    Text(
                        text, fontSize = 11.sp, color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF4F6BFF)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Text("Награда", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RewardTile("XP", t.exp, Color(0xFF4CD964), coin = false, modifier = Modifier.weight(1f))
            RewardTile("", t.points, Color(0xFFFFB020), coin = true, modifier = Modifier.weight(1f))
        }
        val total = t.threshold ?: 0
        val frac = if (t.done) 1f else if (total > 0) (t.progress.toFloat() / total).coerceIn(0f, 1f) else 0f
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${t.progress}", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            androidx.compose.material3.LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp).height(6.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                if (t.done) "выполнено" else listOfNotNull(total.takeIf { it > 0 }?.let { "из $it" }, t.period.takeIf { it.isNotBlank() }).joinToString(" "),
                fontSize = 13.sp, color = if (t.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
