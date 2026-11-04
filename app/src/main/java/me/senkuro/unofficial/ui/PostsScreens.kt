package me.senkuro.unofficial.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.data.PostFull

@Composable
fun PostsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val pager = remember { Pager { AppGraph.posts.posts(it) } }
    LaunchedEffect(Unit) { pager.reload() }
    Column(Modifier.fillMaxSize()) {
        Top("Посты", onBack)
        when {
            pager.items.isEmpty() && pager.loading -> LoadingBox()
            pager.items.isEmpty() && pager.error != null -> PagerError(pager)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(pager.items) { p ->
                    Column(Modifier.fillMaxWidth().clickable { onOpen(p.slug) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(p.title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.avatar, 20)
                            Text(
                                listOfNotNull(p.author.ifBlank { null }, p.team, p.date, p.views?.let { "👁 $it" }, p.comments?.let { "💬 $it" }).joinToString(" · "),
                                Modifier.padding(start = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
fun PostScreen(slug: String, onBack: () -> Unit) {
    var post by remember { mutableStateOf<PostFull?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(slug, attempt) {
        error = null
        try {
            post = AppGraph.posts.post(slug)
            if (post == null) error = "Пост не найден"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.msg()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Top("Пост", onBack)
        val p = post
        when {
            error != null -> ErrorBox(error!!, onRetry = { attempt++ })
            p == null -> LoadingBox()
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(p.item.title, style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground)
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.item.avatar, 24)
                    Text(
                        listOfNotNull(p.item.author.ifBlank { null }, p.item.team, p.item.date, p.item.views?.let { "👁 $it" }, p.item.comments?.let { "💬 $it" }).joinToString(" · "),
                        Modifier.padding(start = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SelectionContainer { Text(p.text.ifBlank { "Пустой пост" }, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground) }
            }
        }
    }
}

@Composable
fun FeedScreen(onBack: () -> Unit, onManga: (MangaItem) -> Unit) {
    val pager = remember { Pager { AppGraph.posts.feed(it) } }
    LaunchedEffect(Unit) { pager.reload() }
    Column(Modifier.fillMaxSize()) {
        Top("Новые главы", onBack)
        when {
            pager.items.isEmpty() && pager.loading -> LoadingBox()
            pager.items.isEmpty() && pager.error != null -> PagerError(pager)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(pager.items) { f ->
                    Row(Modifier.fillMaxWidth().clickable { onManga(f.manga) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        coil3.compose.AsyncImage(f.manga.cover, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.padding(end = 12.dp).then(Modifier.fillMaxWidth(0.14f)).androidxAspect())
                        Column {
                            Text(f.manga.title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(f.chapter.ifBlank { null }, f.date).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
                item { LaunchedEffect(pager.items.size) { pager.more() } }
            }
        }
    }
}

private fun Modifier.androidxAspect(): Modifier = this.then(Modifier.aspectRatio(0.7f))
