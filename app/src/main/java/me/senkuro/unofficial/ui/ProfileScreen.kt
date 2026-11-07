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
import androidx.compose.foundation.horizontalScroll
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
    var extra by remember { mutableStateOf<me.senkuro.unofficial.data.ProfileExtra?>(null) }
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
            extra = AppGraph.repository.profileExtra(acc.slug)
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
                extra?.let { e -> item { ProfileExtraBlocks(p, e, onLibrary) } }
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


private fun fmt(n: Long?): String = n?.let { "%,d".format(java.util.Locale("ru"), it).replace('\u00A0', ' ') } ?: "—"

private fun yearsWith(createdAt: String?): String? {
    val y = createdAt?.take(4)?.toIntOrNull() ?: return null
    val n = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - y
    if (n < 1) return "меньше года"
    val word = if (n % 100 in 11..14) "лет" else when (n % 10) { 1 -> "год"; in 2..4 -> "года"; else -> "лет" }
    return "$n $word"
}

/** Блоки профиля как на сайте: статистика, библиотека манги, отношения, друзья, история действий, «Обо мне». */
@Composable
private fun ProfileExtraBlocks(p: Profile, e: me.senkuro.unofficial.data.ProfileExtra, onLibrary: () -> Unit) {
    Column(Modifier.offset(y = (-30).dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Друзей", fmt(e.friends), Modifier.weight(1f))
            StatTile("Подписчиков", fmt(e.subscribers), Modifier.weight(1f))
            StatTile("Подписок", fmt(e.follows), Modifier.weight(1f))
            StatTile("Комментариев", fmt(e.comments), Modifier.weight(1f))
        }
        Block("Статистика") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyValue("Глав прочитано", fmt(e.readChapters))
                KeyValue("Серий просмотрено", fmt(e.watchEpisodes))
                KeyValue("Тайтлов оценено", fmt(e.rated))
                e.betterPercentile?.let { KeyValue("Лучше, чем", "%.1f%% пользователей".format(java.util.Locale.US, it)) }
                e.rank?.let { KeyValue("Ранг", "#" + fmt(it)) }
            }
        }
        if (e.libraryTotal > 0) Block("Библиотека манги · ${fmt(e.libraryTotal)}", "Вся манга ›", onLibrary) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    e.libraryByStatus.forEach { (name, n) -> Chip("$name $n") }
                }
                if (e.libraryPreview.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(e.libraryPreview) { m ->
                        Column(Modifier.width(84.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AsyncImage(m.cover, null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(width = 84.dp, height = 118.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                            Text(m.title, fontSize = 11.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }
        if (e.waifus.isNotEmpty()) Block("Отношения · ${e.waifus.size}") {
            e.waifus.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(w.cover, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                    Column {
                        Text(w.name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                        if (w.relation.isNotBlank()) Text(w.relation, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (e.activities.isNotEmpty()) Block("История действий") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                e.activities.take(6).forEach { a ->
                    Column {
                        Text(listOfNotNull(a.text.takeIf { it.isNotBlank() }, a.subject).joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                        profileRelative(a.at)?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        if (e.friendsList.isNotEmpty() || e.followsList.isNotEmpty()) Block("Друзья и подписчики") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (e.friendsList.isNotEmpty()) PeopleRow("Друзья · ${fmt(e.friends)}", e.friendsList)
                if (e.followsList.isNotEmpty()) PeopleRow("Подписки · ${fmt(e.follows)}", e.followsList)
            }
        }
        if (e.gender != null || e.birthday != null) Block("Обо мне") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                e.birthday?.let { KeyValue("День рождения", it) }
                e.gender?.let { KeyValue("Пол", it) }
            }
        }
        yearsWith(p.createdAt)?.let {
            Text("Вместе с нами уже $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Block(title: String, more: String? = null, onMore: () -> Unit = {}, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            if (more != null) Text(more, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onMore))
        }
        content()
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(key, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun Chip(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.background).padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
private fun PeopleRow(title: String, list: List<me.senkuro.unofficial.data.PeopleItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list) { u ->
                Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(u.avatar, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background))
                    Text(u.name, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}

private fun profileRelative(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        android.text.format.DateUtils.getRelativeTimeSpanString(f.parse(iso.take(19))?.time ?: return null, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
    } catch (_: Exception) { null }
}
