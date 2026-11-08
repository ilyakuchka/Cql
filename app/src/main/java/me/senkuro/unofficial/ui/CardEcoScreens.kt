package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
import me.senkuro.unofficial.data.*

// ---------------- общие кусочки

private val muted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun CardThumb(url: String?, title: String, sub: String, badge: String?, selected: Int, onClick: (() -> Unit)?) {
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(8.dp)).then(if (selected > 0) Modifier.border(2.dp, accent, RoundedCornerShape(8.dp)) else Modifier)) {
            AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (badge != null) Text(badge, fontSize = 11.sp, color = Color.White, modifier = Modifier.align(Alignment.TopEnd).background(Color(0xAA000000)).padding(horizontal = 4.dp))
            if (selected > 0) Text("✓ $selected", fontSize = 12.sp, color = Color.White, modifier = Modifier.align(Alignment.BottomStart).background(accent).padding(horizontal = 6.dp))
        }
        Text(title, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
        if (sub.isNotBlank()) Text(sub, fontSize = 10.sp, color = muted)
    }
}

/** Сетка моих карт с выбором: тап добавляет экземпляр (до имеющегося количества), затем сбрасывает. */
@Composable
private fun OwnedPicker(pager: Pager<OwnedCard>, picked: SnapshotStateMap<String, Int>, modifier: Modifier = Modifier) {
    when {
        pager.items.isEmpty() && pager.loading -> LoadingBox(modifier)
        pager.items.isEmpty() && pager.error != null -> PagerError(pager)
        pager.items.isEmpty() -> Text("У вас пока нет карточек", modifier.padding(16.dp), color = muted)
        else -> LazyVerticalGrid(GridCells.Fixed(3), modifier, contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pager.items, key = { it.key }) { c ->
                val n = picked[c.key] ?: 0
                CardThumb(c.image, c.title, listOfNotNull(c.rank, c.modifier.toString().takeIf { it != "0" && it != "\"0\"" }).joinToString(" · "), if (c.quantity > 1) "×${c.quantity}" else null, n) {
                    if (c.locked) return@CardThumb
                    if (n >= c.quantity) picked.remove(c.key) else picked[c.key] = n + 1
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { LaunchedEffect(pager.items.size) { pager.more() } }
        }
    }
}

@Composable
private fun DroppedDialog(title: String, cards: List<DroppedCard>, note: String?, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column {
                if (note != null) Text(note, fontSize = 13.sp, color = muted, modifier = Modifier.padding(bottom = 8.dp))
                if (cards.isEmpty()) Text("Сервер не вернул карточек", color = muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cards) { c ->
                        Column(Modifier.width(96.dp)) {
                            CardThumb(c.image, c.title, c.rank.orEmpty(), if (c.quantity > 1) "×${c.quantity}" else null, 0, null)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Ок") } },
    )
}

private fun Throwable.friendly(): String {
    if (this is CancellationException) throw this
    val m = msg()
    return when {
        m.contains("same rank", true) -> "Все карты должны быть одного ранга"
        m.contains("enough rights", true) -> "Недостаточно прав для этого действия"
        m.contains("Time expired", true) -> "Время на это действие истекло"
        else -> m
    }
}


// ---------------- хаб

@Composable
fun CardsHubScreen(onBack: () -> Unit, onShorts: () -> Unit, onDecks: () -> Unit, onPacks: () -> Unit, onForge: () -> Unit, onContest: () -> Unit, onShop: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Top("Карточки и магазин", onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            val rows = listOf(
                Triple("Колоды", "Колоды пользователей, создание своей", onDecks),
                Triple("Паки карт", "Открытие паков за очки и кредиты", onPacks),
                Triple("Кузница", "Переплавка, раскол и объединение карт", onForge),
                Triple("Турнир карт", "Голосование за новые карточки", onContest),
                Triple("Senkoshop", "Аватарки, рамки, баннеры, обои, статусы", onShop),
                Triple("Shorts", "Короткая вертикальная лента глав", onShorts),
            )
            items(rows) { (t, d, go) ->
                Column(Modifier.fillMaxWidth().clickable(onClick = go).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(t, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text(d, fontSize = 12.sp, color = muted)
                }
                HorizontalDivider()
            }
        }
    }
}

// ---------------- колоды

@Composable
fun DecksScreen(onBack: () -> Unit, onDeck: (String) -> Unit, onCreate: () -> Unit) {
    val pager = remember { Pager { AppGraph.eco.decks(it) } }
    LaunchedEffect(Unit) { pager.reload() }
    Column(Modifier.fillMaxSize()) {
        Top("Колоды", onBack)
        if (AppGraph.account.value != null) TextButton(onClick = onCreate, modifier = Modifier.padding(horizontal = 8.dp)) { Text("+ Создать колоду") }
        when {
            pager.items.isEmpty() && pager.loading -> LoadingBox()
            pager.items.isEmpty() && pager.error != null -> PagerError(pager)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(pager.items) { d: DeckItem ->
                    Row(Modifier.fillMaxWidth().clickable { onDeck(d.slug) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.width(150.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            d.preview.forEach { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).aspectRatio(0.7f).clip(RoundedCornerShape(4.dp))) }
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(d.title, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                            Text(listOfNotNull(d.author, "${d.quantity} карт").joinToString(" · "), fontSize = 12.sp, color = muted)
                            Text("▲ ${d.up}  ▼ ${d.down}  👁 ${d.views}", fontSize = 12.sp, color = muted)
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
fun DeckScreen(slug: String, onBack: () -> Unit, onCard: (String) -> Unit) {
    var deck by remember { mutableStateOf<DeckDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { error = null; try { deck = AppGraph.eco.deck(slug) ?: run { error = "Колода не найдена"; null } } catch (e: Exception) { error = e.friendly() } } }
    LaunchedEffect(slug) { load() }
    Column(Modifier.fillMaxSize()) {
        Top(deck?.item?.title ?: "Колода", onBack)
        val d = deck
        when {
            d != null -> LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(listOfNotNull(d.item.author, d.date, "${d.item.quantity} карт").joinToString(" · "), fontSize = 13.sp, color = muted)
                        Text("▲ ${d.item.up}  ▼ ${d.item.down}  👁 ${d.item.views}", fontSize = 13.sp, color = muted, modifier = Modifier.padding(top = 2.dp))
                        if (AppGraph.account.value != null) Row {
                            fun vote(up: Boolean) {
                                val next = if (d.vote == up) null else up
                                scope.launch { try { AppGraph.eco.voteDeck(d.item.id, next); load() } catch (e: Exception) { info = e.friendly() } }
                            }
                            TextButton(onClick = { vote(true) }) { Text(if (d.vote == true) "▲ Нравится ✓" else "▲ Нравится") }
                            TextButton(onClick = { vote(false) }) { Text(if (d.vote == false) "▼ Не нравится ✓" else "▼ Не нравится") }
                            if (d.canDelete) TextButton(onClick = { confirmDelete = true }) { Text("Удалить") }
                        }
                        info?.let { Text(it, fontSize = 12.sp, color = muted) }
                    }
                }
                items(d.cards) { c -> CardThumb(c.image, c.title, listOfNotNull(c.rank, c.modifier).joinToString(" · "), if (c.quantity > 1) "×${c.quantity}" else null, 0) { if (c.slug.isNotBlank()) onCard(c.slug) } }
            }
            error != null -> ErrorBox(error.orEmpty(), onRetry = { load() })
            else -> LoadingBox()
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Удалить колоду?") },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            scope.launch { try { AppGraph.eco.deleteDeck(deck!!.item.id); onBack() } catch (e: Exception) { info = e.friendly() } }
        }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
    )
}

@Composable
fun DeckCreateScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val me = AppGraph.account.value?.userId.orEmpty()
    val pager = remember { Pager { AppGraph.eco.ownedCards(me, it) } }
    val picked = remember { mutableStateMapOf<String, Int>() }
    var title by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { pager.reload() }
    val total = picked.values.sum()
    Column(Modifier.fillMaxSize()) {
        Top("Новая колода", onBack)
        OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("Название") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        Text("Выберите карты (тап — добавить экземпляр)", fontSize = 12.sp, color = muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        OwnedPicker(pager, picked, Modifier.weight(1f))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp)) }
        Button(
            enabled = !busy && title.isNotBlank() && total > 0,
            onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        val slug = AppGraph.eco.createDeck(title.trim(), pager.items.toList(), picked.toMap())
                        if (slug != null) onCreated(slug) else onBack()
                    } catch (e: Exception) { error = e.friendly() } finally { busy = false }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(),
        ) { Text(if (busy) "Создаю…" else "Создать колоду ($total карт)") }
    }
}

// ---------------- паки

@Composable
fun PacksScreen(onBack: () -> Unit, onPack: (String) -> Unit) {
    val pager = remember { Pager { AppGraph.eco.packs(it) } }
    LaunchedEffect(Unit) { pager.reload() }
    Column(Modifier.fillMaxSize()) {
        Top("Паки карт", onBack)
        when {
            pager.items.isEmpty() && pager.loading -> LoadingBox()
            pager.items.isEmpty() && pager.error != null -> PagerError(pager)
            else -> LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(pager.items) { p: PackItem ->
                    Column(Modifier.clickable { onPack(p.slug) }) {
                        AsyncImage(p.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(10.dp)))
                        Text(p.title, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                        Text(listOfNotNull(p.points?.let { "$it очк." }, p.credits?.let { "$it кр." }).joinToString(" / "), fontSize = 11.sp, color = muted)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { LaunchedEffect(pager.items.size) { pager.more() } }
            }
        }
    }
}

@Composable
fun PackScreen(slug: String, onBack: () -> Unit) {
    var pack by remember { mutableStateOf<PackDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) } // POINTS / CREDITS
    var result by remember { mutableStateOf<PackOpenResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { error = null; try { pack = AppGraph.eco.pack(slug) ?: run { error = "Пак не найден"; null } } catch (e: Exception) { error = e.friendly() } } }
    LaunchedEffect(slug) { load() }
    Column(Modifier.fillMaxSize()) {
        Top(pack?.item?.title ?: "Пак", onBack)
        val p = pack
        when {
            p != null -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { AsyncImage(p.item.cover, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(220.dp)) }
                item {
                    Text(listOfNotNull(p.item.points?.let { "$it очков" }, p.item.credits?.let { "$it кредитов" }).joinToString(" или ") + " за открытие", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                    if (p.item.freeOpens > 0) Text("Бесплатных открытий: ${p.item.freeOpens}", fontSize = 12.sp, color = muted)
                    if (p.pickCount > 1) Text("Из выпавших карт нужно выбрать ${p.pickCount} (выбор — на сайте)", fontSize = 12.sp, color = muted)
                }
                if (AppGraph.account.value != null) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (p.item.points != null) Button(enabled = !busy, onClick = { confirm = "POINTS" }) { Text("За очки") }
                        if (p.item.credits != null) Button(enabled = !busy, onClick = { confirm = "CREDITS" }) { Text("За кредиты") }
                    }
                    info?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                }
                if (p.rates.isNotEmpty()) {
                    item { Text("Шансы выпадения", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground) }
                    items(p.rates) { (rank, chance) -> Row { Text(rank, Modifier.width(60.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground); Text(chance, fontSize = 13.sp, color = muted) } }
                }
                if (p.covers.isNotEmpty()) item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(p.covers) { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.width(90.dp).aspectRatio(0.7f).clip(RoundedCornerShape(6.dp))) } }
                }
            }
            error != null -> ErrorBox(error.orEmpty(), onRetry = { load() })
            else -> LoadingBox()
        }
    }
    confirm?.let { cur ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Открыть пак?") },
            text = { Text("Будет списано ${if (cur == "POINTS") pack?.item?.points else pack?.item?.credits} ${if (cur == "POINTS") "очков" else "кредитов"}.") },
            confirmButton = { TextButton(onClick = {
                confirm = null; busy = true; info = null
                scope.launch {
                    try { result = AppGraph.eco.openPack(pack!!.item.id, AppGraph.account.value?.userId.orEmpty(), cur, 1); load() } catch (e: Exception) { info = e.friendly() } finally { busy = false }
                }
            }) { Text("Открыть") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена") } },
        )
    }
    result?.let { DroppedDialog("Выпало", it.cards, if (it.needsChoice) "Нужно выбрать карты — завершите открытие на сайте" else null) { result = null } }
}

// ---------------- кузница

@Composable
fun ForgeScreen(onBack: () -> Unit) {
    val me = AppGraph.account.value?.userId.orEmpty()
    val pager = remember { Pager { AppGraph.eco.ownedCards(me, it) } }
    val picked = remember { mutableStateMapOf<String, Int>() }
    var mode by remember { mutableStateOf("UPGRADE") }
    var slots by remember { mutableStateOf<ForgeSlots?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<List<DroppedCard>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { pager.reload() }
    LaunchedEffect(mode) { slots = null; picked.clear(); slots = runCatching { AppGraph.eco.forgeSlots(mode) }.getOrNull() }
    val total = picked.values.sum()
    Column(Modifier.fillMaxSize()) {
        Top("Кузница", onBack)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("UPGRADE" to "Переплавка", "SPLIT" to "Раскол", "MERGE" to "Объединение").forEach { (m, name) ->
                FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(name) })
            }
        }
        Text(
            when (mode) { "SPLIT" -> "Раскол: карта распадается на несколько других"; "MERGE" -> "Объединение: несколько карт сливаются в одну"; else -> "Переплавка: карты одного ранга улучшаются в более высокий" } +
                (slots?.let { " · попыток осталось: ${it.available} из ${it.total}" } ?: ""),
            fontSize = 12.sp, color = muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        OwnedPicker(pager, picked, Modifier.weight(1f))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp)) }
        Button(
            enabled = !busy && total > 0,
            onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        result = AppGraph.eco.forge(mode, pager.items.toList(), picked.toMap())
                        picked.clear(); pager.reload()
                        slots = runCatching { AppGraph.eco.forgeSlots(mode) }.getOrNull()
                    } catch (e: Exception) { error = e.friendly() } finally { busy = false }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(),
        ) { Text(if (busy) "Кую…" else when (mode) { "SPLIT" -> "Расколоть"; "MERGE" -> "Объединить"; else -> "Переплавить" } + " ($total)") }
    }
    result?.let { DroppedDialog("Результат кузницы", it, null) { result = null } }
}

// ---------------- турнир карт

@Composable
fun ContestScreen(onBack: () -> Unit) {
    var contest by remember { mutableStateOf<Contest?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf<ContestCard?>(null) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { error = null; try { contest = AppGraph.eco.contest(); loaded = true } catch (e: Exception) { error = e.friendly() } } }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize()) {
        Top("Турнир карт", onBack)
        val c = contest
        when {
            error != null -> ErrorBox(error.orEmpty(), onRetry = { load() })
            !loaded -> LoadingBox()
            c == null || c.cards.isEmpty() -> Text("Сейчас нет активного турнира", Modifier.padding(16.dp), color = muted)
            else -> Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text(listOfNotNull(c.character, c.rank?.let { "ранг $it" }).joinToString(" · "), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                Text("Выберите карточку, которая должна победить", fontSize = 12.sp, color = muted, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    c.cards.take(2).forEach { k ->
                        Column(Modifier.weight(1f)) { CardThumb(k.image, k.title, listOfNotNull(k.rank, k.author).joinToString(" · "), null, 0) { if (AppGraph.account.value != null) pick = k } }
                    }
                }
                info?.let { Text(it, fontSize = 13.sp, color = muted, modifier = Modifier.padding(top = 12.dp)) }
                if (AppGraph.account.value == null) Text("Войдите, чтобы голосовать", fontSize = 12.sp, color = muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    pick?.let { k ->
        AlertDialog(
            onDismissRequest = { pick = null },
            title = { Text("Голос за «${k.title}»?") },
            confirmButton = { TextButton(onClick = {
                pick = null
                scope.launch { info = try { AppGraph.eco.voteContest(contest!!.id, k.id); "Голос учтён"; } catch (e: Exception) { e.friendly() } }
            }) { Text("Голосовать") } },
            dismissButton = { TextButton(onClick = { pick = null }) { Text("Отмена") } },
        )
    }
}

// ---------------- Senkoshop

@Composable
fun ShopScreen(onBack: () -> Unit) {
    var type by remember { mutableStateOf("AVATAR") }
    val pager = remember(type) { Pager { AppGraph.eco.shop(type, it) } }
    var buy by remember { mutableStateOf<ShopItem?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val me = AppGraph.account.value?.userId.orEmpty()
    LaunchedEffect(type) { pager.reload() }
    Column(Modifier.fillMaxSize()) {
        Top("Senkoshop", onBack)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("AVATAR" to "Аватарки", "FRAME" to "Рамки", "BANNER" to "Баннеры", "WALLPAPER" to "Обои", "STATUS" to "Статусы").forEach { (t, n) ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(n) })
            }
        }
        info?.let { Text(it, fontSize = 12.sp, color = muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
        when {
            pager.items.isEmpty() && pager.loading -> LoadingBox()
            pager.items.isEmpty() && pager.error != null -> PagerError(pager)
            else -> LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(pager.items) { s: ShopItem ->
                    Column {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            AsyncImage(s.image, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
                        }
                        Text(s.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                        when {
                            s.active -> Text("Надето", fontSize = 12.sp, color = muted)
                            s.owned -> TextButton(onClick = {
                                scope.launch {
                                    info = try { AppGraph.eco.equip(s.id, s.type, me); pager.reload(); "Надето: ${s.title}" } catch (e: Exception) { e.friendly() }
                                }
                            }) { Text("Надеть") }
                            else -> TextButton(enabled = AppGraph.account.value != null, onClick = { buy = s }) { Text(s.price?.let { "Купить · $it" } ?: "Купить") }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { LaunchedEffect(pager.items.size) { pager.more() } }
            }
        }
    }
    buy?.let { s ->
        AlertDialog(
            onDismissRequest = { buy = null },
            title = { Text("Купить «${s.title}»?") },
            text = { Text("Цена: ${s.price ?: "—"}. Покупка спишет средства с аккаунта.") },
            confirmButton = { TextButton(onClick = {
                buy = null
                scope.launch { info = try { AppGraph.eco.buy(s.id, me); pager.reload(); "Куплено: ${s.title}" } catch (e: Exception) { e.friendly() } }
            }) { Text("Купить") } },
            dismissButton = { TextButton(onClick = { buy = null }) { Text("Отмена") } },
        )
    }
}
