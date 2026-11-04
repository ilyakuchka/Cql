package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.data.PersonProfile
import me.senkuro.unofficial.data.SearchHit
import me.senkuro.unofficial.data.TeamProfile

@Composable
fun SiteSearchScreen(
    onBack: () -> Unit,
    onManga: (String) -> Unit,
    onTeam: (String) -> Unit,
    onGuild: (String) -> Unit,
    onPerson: (String) -> Unit,
    onCharacter: (String) -> Unit,
) {
    val types = listOf("MANGA" to "Тайтлы", "CHARACTER" to "Персонажи", "PERSON" to "Люди", "TEAM" to "Команды", "GUILD" to "Гильдии", "USER" to "Пользователи")
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("MANGA") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query, type) {
        error = null
        if (query.trim().length < 2) { hits = emptyList(); return@LaunchedEffect }
        delay(400)
        loading = true
        try {
            hits = AppGraph.directory.search(query.trim(), type)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.msg(); hits = emptyList()
        } finally {
            loading = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        Top("Поиск по сайту", onBack)
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Что искать") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(types) { (key, label) -> FilterChip(selected = type == key, onClick = { type = key }, label = { Text(label) }) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp), fontSize = 12.sp) }
        if (loading) LoadingBox() else LazyColumn(Modifier.fillMaxSize()) {
            items(hits) { h ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        when (type) {
                            "MANGA" -> onManga(h.id)
                            "TEAM" -> onTeam(h.slug)
                            "GUILD" -> onGuild(h.slug)
                            "PERSON" -> onPerson(h.slug)
                            "CHARACTER" -> onCharacter(h.slug)
                        }
                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(h.image, 44)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(h.title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        h.subtitle?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun TeamsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var pager by remember { mutableStateOf(Pager { AppGraph.directory.teams(it, null) }) }
    LaunchedEffect(name) {
        delay(if (name.isEmpty()) 0 else 400)
        pager = Pager { AppGraph.directory.teams(it, name) }.also { it.reload() }
    }
    Column(Modifier.fillMaxSize()) {
        Top("Команды", onBack)
        OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Название команды") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        when {
            pager.items.isEmpty() && pager.loading -> LoadingBox()
            pager.items.isEmpty() && pager.error != null -> PagerError(pager)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(pager.items) { t ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(t.slug) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(t.avatar, 44)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(t.name + if (t.verified) " ✓" else "", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text(t.line, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun TeamScreen(slug: String, onBack: () -> Unit) {
    var team by remember { mutableStateOf<TeamProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var members by remember { mutableStateOf<Pager<me.senkuro.unofficial.data.GuildMemberItem>?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(slug, attempt) {
        error = null
        try {
            val t = AppGraph.directory.team(slug)
            team = t
            if (t == null) error = "Команда не найдена" else members = Pager { AppGraph.directory.teamMembers(t.item.id, it) }.also { it.reload() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.msg()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Top(team?.item?.name ?: "Команда", onBack)
        val t = team
        val pager = members
        when {
            error != null -> ErrorBox(error!!, onRetry = { attempt++ })
            t == null || pager == null -> LoadingBox()
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(t.item.avatar, 64)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(t.item.name + if (t.item.verified) " ✓" else "", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                                Text(listOfNotNull(t.projects?.let { "проектов $it" }, t.chapters?.let { "глав $it" }, t.views?.let { "просмотров $it" }).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(listOfNotNull(t.members?.let { "участников $it" }, t.created?.let { "с $it" }).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (t.about.isNotBlank()) SelectionContainer { Text(t.about, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 12.dp)) }
                        t.links.forEach { (title, url) ->
                            androidx.compose.material3.TextButton(onClick = {
                                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }) { Text(title) }
                        }
                        Text("Участники", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                items(pager.items) { m ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(m.avatar, 36)
                        Text(m.name, Modifier.weight(1f).padding(start = 12.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text(m.role.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item { LaunchedEffect(pager.items.size) { pager.more() } }
            }
        }
    }
}

@Composable
fun PersonScreen(slug: String, onBack: () -> Unit, onManga: (MangaItem) -> Unit) {
    var person by remember { mutableStateOf<PersonProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var works by remember { mutableStateOf<Pager<MangaItem>?>(null) }
    LaunchedEffect(slug, attempt) {
        error = null
        try {
            val p = AppGraph.directory.person(slug)
            person = p
            if (p == null) error = "Не найдено" else works = Pager { AppGraph.directory.mangasByPerson(p.id, it) }.also { it.reload() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.msg()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Top(person?.name ?: "Автор", onBack)
        val p = person
        val w = works
        when {
            error != null -> ErrorBox(error!!, onRetry = { attempt++ })
            p == null || w == null -> LoadingBox()
            else -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                androidx.compose.foundation.lazy.grid.GridCells.Fixed(3), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.cover, 72)
                        Text(p.name, Modifier.padding(start = 12.dp), fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
                items(w.items) { m -> MangaCard(m, onClick = { onManga(m) }, compact = true) }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { LaunchedEffect(w.items.size) { w.more() } }
            }
        }
    }
}
