package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
fun ProfileScreen(onBack: () -> Unit, onOpenTasks: () -> Unit = {}) {
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
                item { TasksCarousel(onOpenTasks) }
                item {
                    Text(
                        "Достижения (${achievements.size})",
                        style = HeadingStyle,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(achievements.take(3)) { AchievementRow(it) }
                if (achievements.size > 3) item {
                    androidx.compose.material3.TextButton(
                        onClick = { showAll = true },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("Посмотреть все достижения (${achievements.size})") }
                }
            }
        }
    }
}

@Composable
private fun Header(p: Profile) {
    Box(Modifier.fillMaxWidth().height(110.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(p.banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
    Row(
        Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            p.avatar, null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                p.name + if (p.verified) " ✓" else "",
                color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp,
            )
            p.level?.let { Text("Уровень $it", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            p.createdAt?.let { Text("На сайте с $it", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            p.lastOnlineAt?.let { Text("Был(а) онлайн: $it", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        }
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
