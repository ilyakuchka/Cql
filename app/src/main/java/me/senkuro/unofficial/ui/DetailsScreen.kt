@file:OptIn(ExperimentalLayoutApi::class)

package me.senkuro.unofficial.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.Chapter
import me.senkuro.unofficial.data.MangaDetails
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.data.SenkuroConfig

/**
 * Страница тайтла по экрану «Episode list» из кита Webtoon: тёмный фон #161616, обложка под затемнением,
 * жанр, название 23sp, автор, строка описания, кнопка-«таблетка», затем строки глав с миниатюрой слева и «#N» справа.
 */
@Composable
fun DetailsScreen(
    mangaId: String,
    onBack: () -> Unit,
    onRead: (Chapter) -> Unit,
    onReadAt: (Chapter, Int) -> Unit = { c, _ -> onRead(c) },
    onLogin: () -> Unit,
    onOpenManga: (MangaItem) -> Unit = {},
    onOpenCharacter: (String) -> Unit = {},
    onOpenPerson: (String) -> Unit = {},
    onOpenCard: (String) -> Unit = {},
    vm: DetailsViewModel = viewModel(key = "details-$mangaId"),
) {
    LaunchedEffect(mangaId) { vm.load(mangaId) }
    // Засчитываем просмотр тайтла на сайте (как это делает веб-клиент).
    LaunchedEffect(mangaId) {
        if (AppGraph.auth.current != null) {
            try {
                AppGraph.repository.viewManga(mangaId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }
    val library = AppGraph.library
    val readIds by library.readIds.collectAsState()
    val context = LocalContext.current
    val item = (vm.state as? UiState.Ready)?.data?.details?.item

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val state = vm.state) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(state.message, { vm.load(mangaId, force = true) })
            is UiState.Ready -> DetailsContent(state.data, readIds, onRead, onReadAt, onOpenManga, onOpenCharacter, onOpenPerson, onOpenCard)
        }
        var bookmarkDialog by remember { mutableStateOf(false) }
        if (bookmarkDialog) {
            BookmarkDialog(mangaId = mangaId, onLogin = onLogin, onDismiss = { bookmarkDialog = false })
        }
        var rateDialog by remember { mutableStateOf(false) }
        if (rateDialog && item != null) RateDialog(item.id, item.slug, onLogin) { rateDialog = false }
        TopActions(
            onBack = onBack,
            onRate = { rateDialog = true },
            onBookmark = { bookmarkDialog = true },
            loaded = item != null,
            onShare = { item?.let { shareManga(context, it) } },
        )
    }
}

/** Верхняя строка поверх обложки: стрелка назад слева; справа белая «таблетка» закладок и «поделиться». */
@Composable
private fun TopActions(
    onBack: () -> Unit,
    onBookmark: () -> Unit,
    onRate: () -> Unit,
    loaded: Boolean,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
        }
        if (loaded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "★ Оценка",
                    color = Color.Black,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color.White)
                        .clickable(onClick = onRate)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 3.dp))
                Text(
                    text = "+ Закладки",
                    color = Color.Black,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color.White)
                        .clickable(onClick = onBookmark)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = "Поделиться", tint = Color.White)
                }
            }
        }
    }
}

private fun shareManga(context: Context, item: MangaItem) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${SenkuroConfig.BASE_URL}/manga/${item.slug}")
    }
    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun DetailsContent(data: DetailsData, readIds: Set<String>, onRead: (Chapter) -> Unit, onReadAt: (Chapter, Int) -> Unit, onOpenManga: (MangaItem) -> Unit, onOpenCharacter: (String) -> Unit, onOpenPerson: (String) -> Unit, onOpenCard: (String) -> Unit) {
    val details = data.details
    val extras by androidx.compose.runtime.produceState(DetailsExtras(), details.item.id) {
        value = DetailsExtras(
            relations = runCatching { AppGraph.repository.relations(details.item.id) }.getOrDefault(emptyList()),
            characters = runCatching { AppGraph.repository.characters(details.item.id) }.getOrDefault(emptyList()),
            staff = runCatching { AppGraph.directory.staff(details.item.id) }.getOrDefault(emptyList()),
        )
    }
    val titlePage by androidx.compose.runtime.produceState<me.senkuro.unofficial.data.TitlePage?>(null, details.item.slug) {
        value = runCatching { AppGraph.title.page(details.item.slug) }.getOrNull()
    }
    var tab by rememberSaveable { mutableStateOf(TitleTab.ABOUT) }
    val tabScope = androidx.compose.runtime.rememberCoroutineScope()
    val comments = remember(details.item.id) { TitleComments(details.item.id, tabScope) }
    val titleCards = remember(details.item.id) { TitleCards(details.item.id) }
    val loggedIn = AppGraph.account.collectAsState().value != null
    val branches = data.chapters.branches
    var branchIndex by rememberSaveable { mutableIntStateOf(0) }
    val branch = branches.getOrNull(branchIndex) ?: branches.firstOrNull()
    var chapterQuery by rememberSaveable { mutableStateOf("") }
    val chaptersDescending = remember(branch, chapterQuery) {
        val all = branch?.chapters.orEmpty().asReversed()
        val q = chapterQuery.trim().removePrefix("#").lowercase()
        if (q.isEmpty()) all else all.filter {
            it.number.lowercase().startsWith(q) || it.title.lowercase().contains(q) ||
                (it.name?.lowercase()?.contains(q) == true) || "том ${it.volume}".lowercase() == q || it.date?.contains(q) == true
        }
    }
    val bookmarkScope = androidx.compose.runtime.rememberCoroutineScope()
    var bookmarkNumber by remember(data.details.item.slug) { mutableStateOf<String?>(null) }
    var bookmarkPage by remember(data.details.item.slug) { mutableStateOf(1) }
    androidx.compose.runtime.LaunchedEffect(data.details.item.slug, AppGraph.account.collectAsState().value?.userId) {
        bookmarkNumber = if (AppGraph.account.value == null) null else try {
            AppGraph.repository.bookmarkNumber(data.details.item.slug).also {
                bookmarkPage = try { AppGraph.repository.bookmarkPage(data.details.item.slug) ?: 1 } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { 1 }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
    }
    // Закладка с сайта (viewerBookmark.number): продолжаем с той главы, где остановились, а не с первой.
    val bookmarkChapter = remember(branch, bookmarkNumber) {
        bookmarkNumber?.let { num -> branch?.chapters.orEmpty().firstOrNull { it.number == num } }
    }
    val continueFrom = remember(branch, readIds, bookmarkChapter) {
        bookmarkChapter ?: run {
            val list = branch?.chapters.orEmpty()
            list.firstOrNull { it.id !in readIds } ?: list.firstOrNull()
        }
    }
    val readLabel = when {
        continueFrom == null -> "Нет глав"
        bookmarkChapter != null -> "Продолжить: глава ${bookmarkChapter.number}" + if (bookmarkPage > 1) ", стр. $bookmarkPage" else ""
        continueFrom.id in readIds -> "Читать заново"
        branch?.chapters?.any { it.id in readIds } == true -> "Продолжить"
        else -> "Начать читать"
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var expanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = navBottom + 16.dp),
    ) {
        item(key = "hero") {
            Hero(details, chapterCount = branch?.chapters?.size, readLabel = readLabel, enabled = continueFrom != null) {
                continueFrom?.let { c -> if (c == bookmarkChapter) onReadAt(c, bookmarkPage) else onRead(c) }
            }
        }

        if (details.description.isNotBlank()) {
            item(key = "synopsis") {
                val long = details.description.length > 160
                Column(Modifier.padding(horizontal = 21.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (expanded && details.altNames.isNotEmpty()) {
                        Text(
                            "Также: " + details.altNames.joinToString(" / "),
                            color = AppColors.DarkSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        text = details.description,
                        color = AppColors.DarkSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (long) {
                        Text(
                            text = if (expanded) "Свернуть" else "Показать полностью",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { expanded = !expanded }
                                .padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        if (details.labels.isNotEmpty()) {
            item(key = "labels") {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 21.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    details.labels.forEach { OutlineChip(it, selected = false, onClick = null) }
                }
            }
        }

        item(key = "tabs") { TitleTabBar(tab) { tab = it } }

        if (tab == TitleTab.ABOUT) aboutItems(details, titlePage, branch?.chapters?.size, onOpenManga)
        if (tab == TitleTab.STATS) statsItems(titlePage, loading = true)
        if (tab == TitleTab.COMMENTS) commentsItems(comments, loggedIn, titlePage?.commentsEnabled ?: true)
        if (tab == TitleTab.CARDS) cardsItems(titleCards, tabScope, onOpenCard)

        if (tab == TitleTab.ABOUT && extras.relations.isNotEmpty()) {
            item(key = "relations") {
                Column(Modifier.padding(top = 12.dp)) {
                    Text("Связанное", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 21.dp, vertical = 6.dp))
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 21.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(extras.relations, key = { it.manga.id }) { r ->
                            Column(Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                MangaCard(r.manga, onClick = { onOpenManga(r.manga) })
                                r.relation?.let { Text(relationName(it), color = AppColors.DarkSecondary, fontSize = 10.sp) }
                            }
                        }
                    }
                }
            }
        }

        if (tab == TitleTab.ABOUT && extras.staff.isNotEmpty()) {
            item(key = "staff") {
                Column(Modifier.padding(top = 12.dp)) {
                    Text("Авторы", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 21.dp, vertical = 6.dp))
                    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 21.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(extras.staff) { p ->
                            Column(Modifier.width(80.dp).clickable(enabled = p.slug.isNotBlank()) { onOpenPerson(p.slug) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                coil3.compose.AsyncImage(
                                    model = p.cover, contentDescription = p.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                Text(p.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text(p.role, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        if (tab == TitleTab.ABOUT && extras.characters.isNotEmpty()) {
            item(key = "characters") {
                Column(Modifier.padding(top = 12.dp)) {
                    Text("Персонажи", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 21.dp, vertical = 6.dp))
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 21.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(extras.characters) { c ->
                            Column(Modifier.width(80.dp).clickable(enabled = c.slug.isNotBlank()) { onOpenCharacter(c.slug) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                coil3.compose.AsyncImage(
                                    model = c.cover, contentDescription = c.name,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                Text(c.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }

        bookmarkNumber?.takeIf { tab == TitleTab.CHAPTERS }?.let { num ->
            item(key = "bookmark-jump") {
                androidx.compose.material3.TextButton(
                    modifier = Modifier.padding(horizontal = 13.dp),
                    onClick = {
                        val all = branches.flatMap { it.chapters }
                        val local = all.firstOrNull { it.number == num && (branch == null || it.branchId == branch.id) } ?: all.firstOrNull { it.number == num }
                        if (local != null) onReadAt(local, bookmarkPage) else bookmarkScope.launch {
                            val slug = try {
                                AppGraph.repository.bookmarkChapterSlug(branch?.id.orEmpty(), num)
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
                            all.firstOrNull { it.slug == slug }?.let { onReadAt(it, bookmarkPage) }
                        }
                    },
                ) { Text("К закладке: глава $num") }
            }
        }

        if (tab == TitleTab.CHAPTERS) item(key = "chapters-title") {
            Column(Modifier.padding(horizontal = 21.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Главы" + if (branch != null) " (${branch.chapters.size})" else "",
                    style = HeadingStyle,
                    color = Color.White,
                )
                if (branches.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        branches.forEachIndexed { index, b ->
                            OutlineChip(b.label, selected = index == branchIndex, onClick = { branchIndex = index })
                        }
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = chapterQuery,
                    onValueChange = { chapterQuery = it },
                    singleLine = true,
                    placeholder = { Text("Поиск по главам: номер, название, дата") },
                    trailingIcon = {
                        if (chapterQuery.isNotEmpty()) {
                            androidx.compose.material3.TextButton(onClick = { chapterQuery = "" }) { Text("✕") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (chapterQuery.isNotBlank() && chaptersDescending.isEmpty()) {
                    Text("Ничего не найдено", color = AppColors.DarkSecondary, fontSize = 12.sp)
                }
                data.chapters.message?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = AppColors.DarkSecondary, fontSize = 12.sp)
                }
                data.chaptersError?.let {
                    Text("Не удалось загрузить главы: $it", color = AppColors.DarkSecondary, fontSize = 12.sp)
                }
            }
        }

        if (tab == TitleTab.CHAPTERS && chaptersDescending.isNotEmpty()) {
            item(key = "ad-manga-chapters") { YandexAdBlock(AdPositions.MANGA_CHAPTERS) }
        }

        if (tab == TitleTab.CHAPTERS) items(chaptersDescending, key = { it.id }) { chapter ->
            ChapterRow(
                chapter = chapter,
                cover = details.item.cover,
                read = chapter.id in readIds,
                onClick = { onRead(chapter) },
            )
        }
    }
}

/** Обложка под затемнением, поверх неё жанр, название, автор, строка описания, мета и кнопка «Начать читать ›». */
@Composable
private fun Hero(details: MangaDetails, chapterCount: Int?, readLabel: String, enabled: Boolean, onRead: () -> Unit) {
    val people = listOfNotNull(details.authors, details.artists).joinToString(", ")
    val tagline = details.description.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    val meta = listOfNotNull(
        details.status,
        details.ageRating,
        details.type,
        chapterCount?.let { "Глав: $it" },
    ).joinToString("  ·  ")

    Box(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = details.item.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        var poster by remember { mutableStateOf(false) }
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0f to Color(0xCC161616), 0.6f to Color(0xE6161616), 1f to AppColors.DarkBg))
                // нажатие на обложку — просмотрщик постера
                .clickable(enabled = details.item.cover != null) { poster = true },
        )
        if (poster) details.item.cover?.let { PosterViewer(it) { poster = false } }
        Column(
            Modifier
                .statusBarsPadding()
                .padding(top = 56.dp, start = 21.dp, end = 21.dp, bottom = 20.dp),
        ) {
            (details.labels.firstOrNull() ?: details.type)?.let {
                Text(it, color = Color.White, fontSize = 13.sp)
            }
            Text(
                text = details.item.title,
                color = Color.White,
                fontSize = 23.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (people.isNotBlank()) {
                Text(
                    people,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (tagline.isNotBlank()) {
                Text(
                    tagline,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (meta.isNotBlank()) {
                Text(meta, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Text(
                text = "$readLabel  ›",
                color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
                fontSize = 11.sp,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(0.8.dp, Color.White.copy(alpha = if (enabled) 1f else 0.5f), RoundedCornerShape(32.dp))
                    .clickable(enabled = enabled, onClick = onRead)
                    .padding(horizontal = 15.dp, vertical = 7.dp),
            )
        }
    }
}

/** Контурная «таблетка» как у «Recent searches» в ките: рамка 0.8 и скругление 20; выбранная — зелёная. */
@Composable
private fun OutlineChip(text: String, selected: Boolean, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(20.dp)
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text = text,
        color = if (selected) accent else Color.White,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(shape)
            .border(0.8.dp, if (selected) accent else AppColors.DarkBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Строка главы из кита: высота 67, миниатюра 71 слева, «Глава N» 15sp, дата серым, справа «#N»; фон #2C2C2C, линия #464545. */
@Composable
private fun ChapterRow(chapter: Chapter, cover: String?, read: Boolean, onClick: () -> Unit) {
    val line = AppColors.DarkBorder
    val sub = listOfNotNull(chapter.date, chapter.teams.joinToString(", ").ifBlank { null }).joinToString("  ·  ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(67.dp)
            .background(AppColors.DarkItem)
            .drawBehind {
                val y = size.height - 0.4.dp.toPx()
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8.dp.toPx())
            }
            .clickable(onClick = onClick)
            .alpha(if (read) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(71.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 13.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                chapter.title,
                color = Color.White,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(sub, color = AppColors.DarkSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("#${chapter.number}", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(end = 12.dp))
    }
}

private class DetailsExtras(
    val relations: List<me.senkuro.unofficial.data.RelatedManga> = emptyList(),
    val characters: List<me.senkuro.unofficial.data.CharacterItem> = emptyList(),
    val staff: List<me.senkuro.unofficial.data.StaffItem> = emptyList(),
)

private fun relationName(raw: String): String = when (raw.uppercase()) {
    "SEQUEL" -> "Продолжение"
    "PREQUEL" -> "Предыстория"
    "SIDE_STORY" -> "Побочная история"
    "SPIN_OFF" -> "Спин-офф"
    "ADAPTATION" -> "Адаптация"
    "ALTERNATIVE_VERSION" -> "Альтернативная версия"
    "ALTERNATIVE_SETTING" -> "Альтернативный сеттинг"
    "SUMMARY" -> "Краткое изложение"
    "FULL_STORY" -> "Полная история"
    "CHARACTER" -> "Общие персонажи"
    "OTHER" -> "Другое"
    else -> raw.lowercase().replace('_', ' ')
}
