@file:OptIn(ExperimentalLayoutApi::class)

package me.senkuro.unofficial.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

/** Страница тайтла по макету «Chapters»: обложка-герой, плавающая карточка с описанием, красные карточки глав. */
@Composable
fun DetailsScreen(
    mangaId: String,
    onBack: () -> Unit,
    onRead: (Chapter) -> Unit,
    onLogin: () -> Unit,
    vm: DetailsViewModel = viewModel(key = "details-$mangaId"),
) {
    LaunchedEffect(mangaId) { vm.load(mangaId) }
    val library = AppGraph.library
    val favorites by library.favorites.collectAsState()
    val readIds by library.readIds.collectAsState()
    val context = LocalContext.current
    val item = (vm.state as? UiState.Ready)?.data?.details?.item

    Box(Modifier.fillMaxSize()) {
        when (val state = vm.state) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(state.message, { vm.load(mangaId, force = true) })
            is UiState.Ready -> DetailsContent(state.data, readIds, onRead)
        }
        var bookmarkDialog by remember { mutableStateOf(false) }
        if (bookmarkDialog) {
            BookmarkDialog(mangaId = mangaId, onLogin = onLogin, onDismiss = { bookmarkDialog = false })
        }
        TopActions(
            onBack = onBack,
            onBookmark = { bookmarkDialog = true },
            favorite = item?.let { manga -> favorites.any { it.id == manga.id } },
            onFavorite = { item?.let(library::toggleFavorite) },
            onShare = { item?.let { shareManga(context, it) } },
        )
    }
}

@Composable
private fun TopActions(onBack: () -> Unit, onBookmark: () -> Unit, favorite: Boolean?, onFavorite: () -> Unit, onShare: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад", Color.White, onBack)
        if (favorite != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundIconButton(
                    icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    description = "В избранное",
                    tint = if (favorite) MaterialTheme.colorScheme.primary else Color.White,
                    onClick = onFavorite,
                )
                RoundIconButton(Icons.Filled.Star, "Закладки на сайте", Color.White, onBookmark)
                RoundIconButton(Icons.Filled.Share, "Поделиться", Color.White, onShare)
            }
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint)
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
private fun DetailsContent(data: DetailsData, readIds: Set<String>, onRead: (Chapter) -> Unit) {
    val details = data.details
    val branches = data.chapters.branches
    var branchIndex by rememberSaveable { mutableIntStateOf(0) }
    val branch = branches.getOrNull(branchIndex) ?: branches.firstOrNull()
    val chaptersDescending = remember(branch) { branch?.chapters.orEmpty().asReversed() }
    val continueFrom = remember(branch, readIds) {
        val list = branch?.chapters.orEmpty()
        list.firstOrNull { it.id !in readIds } ?: list.firstOrNull()
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = navBottom + 16.dp),
    ) {
        item(key = "hero") { HeroWithInfo(details) }

        if (details.labels.isNotEmpty()) {
            item(key = "labels") {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    details.labels.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                }
            }
        }

        item(key = "read") {
            Button(
                onClick = { continueFrom?.let(onRead) },
                enabled = continueFrom != null,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    when {
                        continueFrom == null -> "Нет глав"
                        continueFrom.id in readIds -> "Читать заново"
                        branch?.chapters?.any { it.id in readIds } == true -> "Продолжить"
                        else -> "Начать читать"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        item(key = "chapters-title") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Главы" + if (branch != null) " (${branch.chapters.size})" else "",
                    style = HeadingStyle,
                    color = AppColors.Text,
                )
                if (branches.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        branches.forEachIndexed { index, b ->
                            FilterChip(
                                selected = index == branchIndex,
                                onClick = { branchIndex = index },
                                label = { Text(b.label) },
                            )
                        }
                    }
                }
                data.chapters.message?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                data.chaptersError?.let {
                    Text("Не удалось загрузить главы: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        items(chaptersDescending, key = { it.id }) { chapter ->
            ChapterCard(
                chapter = chapter,
                cover = details.item.cover,
                read = chapter.id in readIds,
                onClick = { onRead(chapter) },
            )
        }
    }
}

/** Обложка на всю ширину с затемнением и плавающей карточкой «название + синопсис» с красной кнопкой раскрытия. */
@Composable
private fun HeroWithInfo(details: MangaDetails) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val longText = details.description.length > 160
    val dim = AppColors.Text.copy(alpha = 0.5f)
    val meta = listOfNotNull(
        details.type,
        details.status,
        details.ageRating,
        details.translationStatus?.let { "Перевод: $it" },
    ).joinToString(" · ")

    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = details.item.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0f to Color(0x80000000), 0.5f to Color.Transparent, 1f to AppColors.Bg)),
            )
        }

        Column(
            Modifier
                .padding(top = 220.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = if (longText) 28.dp else 8.dp),
        ) {
            Box {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = AppColors.Bg,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = details.item.title,
                            style = HeadingStyle,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (meta.isNotBlank()) {
                            Text(meta, color = AppColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        val people = listOfNotNull(
                            details.authors?.let { "Автор: $it" },
                            details.artists?.let { "Художник: $it" },
                        )
                        if (people.isNotEmpty()) {
                            Text(people.joinToString(" · "), color = dim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        if (expanded && details.altNames.isNotEmpty()) {
                            Text(
                                "Также: " + details.altNames.joinToString(" / "),
                                color = dim,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        if (details.description.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Синопсис", style = HeadingSmallStyle, color = dim)
                                Text(
                                    text = details.description,
                                    color = dim,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (longText) Spacer(Modifier.height(16.dp))
                    }
                }
                if (longText) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 24.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { expanded = !expanded },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (expanded) "Свернуть" else "Развернуть",
                            tint = Color.White,
                            modifier = Modifier.rotate(if (expanded) 180f else 0f),
                        )
                    }
                }
            }
        }
    }
}

/** Карточка главы: красный контур, скругление 24, миниатюра 84×80 слева, подпись и название красным справа. */
@Composable
private fun ChapterCard(chapter: Chapter, cover: String?, read: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val accent = MaterialTheme.colorScheme.primary
    val teams = chapter.teams.joinToString(", ")
    val top = buildString {
        if (chapter.volume.isNotBlank() && chapter.volume != "0") append("Том ").append(chapter.volume).append(" · ")
        append("Глава ").append(chapter.number)
        chapter.date?.let { append(" · ").append(it) }
    }
    val main = chapter.name?.takeIf { it.isNotBlank() } ?: teams.ifBlank { "Глава ${chapter.number}" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(96.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, accent), shape)
            .clickable(onClick = onClick)
            .alpha(if (read) 0.5f else 1f)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier
                .width(84.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(top, color = AppColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(main, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (chapter.name?.isNotBlank() == true && teams.isNotBlank()) {
                Text(teams, color = AppColors.Text.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
