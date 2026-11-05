package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import me.senkuro.unofficial.data.AchievementItem
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.Profile

/** Профиль текущего пользователя и его достижения (fetchUser + fetchUserAchievements). */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenTasks: () -> Unit = {},
    onCards: () -> Unit = {},
    onTeams: () -> Unit = {},
    onLibrary: () -> Unit = {},
) {
    val account by AppGraph.account.collectAsState()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var achievements by remember { mutableStateOf<List<AchievementItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var attempt by remember { mutableStateOf(0) }
    var showAll by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = showAll) { showAll = false }

    LaunchedEffect(account?.slug, attempt) {
        val acc = account ?: return@LaunchedEffect
        loading = true
        error = null
        try {
            profile = AppGraph.repository.profile(acc.slug)
            achievements = AppGraph.repository.achievements(acc.userId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: "Ошибка сети"
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (showAll) showAll = false else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(if (showAll) "Достижения" else "Профиль", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground)
        }
        val p = profile
        when {
            account == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Войдите в аккаунт", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error != null && p == null -> ErrorBox(error!!, onRetry = { attempt++ })
            p == null && loading -> LoadingBox()
            p == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Профиль не найден", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            showAll -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                items(achievements) { AchievementRow(it) }
            }
            else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                item { Header(p) }
                item { StatsRow(p, achievements) }
                item { TasksCarousel(onOpenTasks) }
                if (achievements.isNotEmpty()) item {
                    ProfileSection("Достижения", "Все ${achievements.size} ›", onMore = { showAll = true }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            achievements.take(4).forEach { a -> AchievementTile(a, Modifier.weight(1f)) }
                            repeat(4 - achievements.take(4).size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
                item {
                    ProfileSection("Быстрые разделы", null, onMore = {}) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            NavRow("Закладки", "Моя библиотека", onLibrary)
                            NavRow("Карточки и обмены", "Коллекция и торговля", onCards)
                            NavRow("Команды переводчиков", "Каталог команд", onTeams)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(p: Profile) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier.fillMaxWidth().height(132.dp).background(
            androidx.compose.ui.graphics.Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.45f))),
        ),
    ) {
        AsyncImage(p.banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
    Column(Modifier.padding(horizontal = 16.dp).offset(y = (-46).dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AsyncImage(
            p.avatar, null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(92.dp)
                .border(4.dp, MaterialTheme.colorScheme.background, CircleShape)
                .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                p.name + if (p.verified) " ✓" else "",
                color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            p.level?.let {
                Text(
                    "LVL $it", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(accent).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        val meta = listOfNotNull(p.createdAt?.let { "На сайте с $it" }, p.lastOnlineAt?.let { "Был(а) онлайн: $it" }).joinToString(" · ")
        if (meta.isNotBlank()) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun StatsRow(p: Profile, achievements: List<AchievementItem>) {
    Row(
        Modifier.fillMaxWidth().offset(y = (-30).dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile("Уровень", p.level?.toString() ?: "—", Modifier.weight(1f))
        StatTile("Достижения", achievements.size.toString(), Modifier.weight(1f))
        StatTile("Выполнено", achievements.count { it.done }.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
private fun ProfileSection(title: String, more: String?, onMore: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.offset(y = (-30).dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            if (more != null) Text(more, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onMore))
        }
        content()
    }
}

@Composable
private fun AchievementTile(a: AchievementItem, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AsyncImage(
            a.cover, null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background),
        )
        Text(a.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 11.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun NavRow(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AchievementRow(a: AchievementItem) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            a.cover, null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.weight(1f)) {
            Text(a.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            val parts = listOfNotNull(
                a.level?.let { "ур. $it" },
                a.points?.let { "$it оч." },
                if (a.done) "выполнено" else a.progress?.let { "прогресс $it" },
            )
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}


/** Карусель «Задания для тебя» (как в профиле на сайте); нажатие на заголовок открывает полный список. */
@Composable
private fun TasksCarousel(onOpen: () -> Unit) {
    val me = AppGraph.account.value?.userId.orEmpty()
    var tasks by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<me.senkuro.unofficial.data.TaskItem>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(me) {
        tasks = try {
            AppGraph.cards.tasks(me, true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
    if (tasks.isEmpty()) return
    Column(Modifier.padding(top = 8.dp)) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().clickable { onOpen() }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Задания для тебя", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tasks) { t -> TaskCard(t, Modifier.width(290.dp)) }
        }
    }
}
