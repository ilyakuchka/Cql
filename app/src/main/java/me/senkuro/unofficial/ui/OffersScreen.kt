package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.OfferItem
import me.senkuro.unofficial.data.mainAd
import me.senkuro.unofficial.data.viewAd
import me.senkuro.unofficial.data.PreviewItem

@Composable
private fun PreviewCard(p: PreviewItem?, caption: String, onCard: (String) -> Unit) {
    Column(Modifier.width(96.dp).clickable(enabled = p != null && p.slug.isNotBlank()) { p?.let { onCard(it.slug) } }) {
        Text(caption, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (p == null) {
            Text("—", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
        } else {
            AsyncImage(
                p.image, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Text(p.title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
            if (p.note.isNotBlank()) Text(p.note, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OffersScreen(onBack: () -> Unit, onCard: (String) -> Unit) {
    val me = AppGraph.account.value?.userId.orEmpty()
    val scope = rememberCoroutineScope()
    var mine by remember { mutableStateOf(false) }
    val pager = remember(mine) { Pager { AppGraph.offers.offers(it, if (mine) me else null) } }
    var actionError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pager) { pager.reload() }
    Column(Modifier.fillMaxSize()) {
        Top("Обмены карточек", onBack)
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = { mine = false }) { Text(if (!mine) "● Открытые" else "Открытые") }
            TextButton(onClick = { mine = true }) { Text(if (mine) "● Мои" else "Мои") }
        }
        actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
        when {
            pager.items.isEmpty() && pager.loading -> LoadingBox()
            pager.items.isEmpty() && pager.error != null -> PagerError(pager)
            pager.items.isEmpty() -> Text("Обменов нет", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(pager.items, key = { it.id }) { o: OfferItem ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(o.avatar, 28)
                            Text(o.user, Modifier.weight(1f).padding(start = 8.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text(listOfNotNull(o.status, o.date).joinToString(" · "), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            PreviewCard(o.offer, "Отдаёт", onCard)
                            PreviewCard(o.request, "Хочет", onCard)
                        }
                        Row {
                            if (o.canAccept) TextButton(onClick = {
                                scope.launch {
                                    runCatching { AppGraph.offers.accept(o.id) }
                                        .onSuccess { actionError = null; pager.reload() }
                                        .onFailure { actionError = it.msg() }
                                }
                            }) { Text("Принять обмен") }
                            if (o.canClose) TextButton(onClick = {
                                scope.launch {
                                    runCatching { AppGraph.offers.close(o.id) }
                                        .onSuccess { actionError = null; pager.reload() }
                                        .onFailure { actionError = it.msg() }
                                }
                            }) { Text("Закрыть") }
                        }
                    }
                    HorizontalDivider()
                }
                item { LaunchedEffect(pager.items.size) { pager.more() } }
            }
        }
    }
}

/** Тонкий рекламный баннер (как «Реклама» на сайте). Пусто или не удалось — не показывается. viewAd уходит через 2 секунды видимости. */
@Composable
fun AdStrip() {
    val account by AppGraph.account.collectAsState()
    if (account?.adsDisabled == true) return
    var ad by remember { mutableStateOf<me.senkuro.unofficial.data.AdBanner?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        ad = try {
            AppGraph.offers.mainAd()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    val a = ad ?: return
    LaunchedEffect(a.id) {
        kotlinx.coroutines.delay(2000)
        try { AppGraph.offers.viewAd(a.id) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Реклама", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(
            model = a.image, contentDescription = "Реклама", contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = !a.link.isNullOrBlank()) {
                    runCatching {
                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(a.link)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
        )
    }
}
