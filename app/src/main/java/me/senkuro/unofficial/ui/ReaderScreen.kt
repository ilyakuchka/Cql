@file:OptIn(ExperimentalMaterial3Api::class)

package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.Chapter

/** Вертикальный ридер: страницы идут сплошной лентой, тап по экрану показывает/прячет панели. */
@Composable
fun ReaderScreen(
    mangaId: String,
    chapterId: String,
    startPage: Int = 1,
    onBack: () -> Unit,
    onToTitle: () -> Unit = onBack,
    onOpenChapter: (Chapter) -> Unit,
    vm: ReaderViewModel = viewModel(key = "reader-$mangaId-$chapterId"),
) {
    LaunchedEffect(mangaId, chapterId) { vm.load(mangaId, chapterId) }
    var controls by remember { mutableStateOf(true) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showComments by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val widthPx = ctx.resources.displayMetrics.widthPixels
    val errorCount = me.senkuro.unofficial.data.ReaderErrorLog.entries.size
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (val state = vm.state) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }

            is UiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.message, color = Color.White, textAlign = TextAlign.Center)
                Button(onClick = { vm.load(mangaId, chapterId, force = true) }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Повторить")
                }
                androidx.compose.material3.TextButton(onClick = { showErrors = true }) { Text("Журнал ошибок ($errorCount)", color = Color.White) }
                if (showErrors) ReaderErrorsDialog(context = "Манга $mangaId, глава $chapterId", onDismiss = { showErrors = false })
            }

            is UiState.Ready -> {
                val data = state.data
                var urls by remember(data.chapter.id) { mutableStateOf(data.pages) }
                val screenWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                    androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.roundToPx()
                }
                var siteTried by remember(data.chapter.id) { mutableStateOf(false) }
                val hasToken by AppGraph.auth.token.collectAsState()
                fun report(block: suspend () -> Unit) {
                    if (hasToken == null) return
                    scope.launch {
                        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
                    }
                }
                LaunchedEffect(data.chapter.id, hasToken) {
                    if (hasToken != null) {
                        try { AppGraph.repository.readChapter(mangaId, data.chapter) }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
                    }
                }
                LaunchedEffect(data.chapter.id, hasToken) {
                    if (hasToken == null) return@LaunchedEffect
                    var sent = false
                    androidx.compose.runtime.snapshotFlow { listState.canScrollForward }
                        .collect { forward ->
                            if (!forward && !sent && listState.layoutInfo.totalItemsCount > 1) {
                                sent = true
                                try { AppGraph.repository.readerEvent(mangaId, data.chapter, "COMPLETED_READING") }
                                catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
                            }
                        }
                }
                // закладка с сайта: открываем главу сразу на сохранённой странице (нумерация с 1)
                LaunchedEffect(data.chapter.id) {
                    if (startPage > 1 && urls.isNotEmpty()) listState.scrollToItem((startPage - 1).coerceIn(0, urls.size - 1))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { controls = !controls },
                ) {
                    itemsIndexed(urls) { index, url ->
                        SubcomposeAsyncImage(
                            // Высокие ленты не помещаются в аппаратный bitmap: декодируем программно,
                            // сжимая до ширины экрана и в RGB_565 (вдвое меньше памяти).
                            model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(url)
                                .size(coil3.size.Size(screenWidthPx, coil3.size.Dimension.Undefined))
                                .allowHardware(false)
                                .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                                .build(),
                            contentDescription = "Страница ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                            onError = { st ->
                                if (st.result.throwable is OutOfMemoryError) siteTried = true
                                me.senkuro.unofficial.data.ReaderErrorLog.add(
                                    "Картинка: страница ${index + 1}" + if (siteTried) " (после переключения на метод сайта)" else "",
                                    url, st.result.throwable,
                                )
                            },
                            loading = {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(420.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator(color = Color.White) }
                            },
                            error = {
                                // Страницы по умолчанию не загрузились — один раз переключаемся на метод сайта.
                                LaunchedEffect(data.chapter.id) {
                                    if (!siteTried) {
                                        siteTried = true
                                        me.senkuro.unofficial.data.ReaderErrorLog.add("Переключение на метод сайта", "глава ${data.chapter.slug}")
                                        try {
                                            val alt = AppGraph.repository.sitePages(data.chapter)
                                            if (alt.isNotEmpty() && alt != urls) urls = alt
                                            else me.senkuro.unofficial.data.ReaderErrorLog.add("Метод сайта", "новых адресов нет (${alt.size} шт.)")
                                        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                                            me.senkuro.unofficial.data.ReaderErrorLog.add("Метод сайта", "запрос не удался", e)
                                        }
                                    }
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text("Не удалось загрузить страницу ${index + 1}", color = Color.White) }
                            },
                        )
                    }
                    item(key = "ad-manga-vertical") { YandexAdBlock(AdPositions.MANGA_VERTICAL) }
                    item(key = "end") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Конец главы", color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = { data.previous?.let(onOpenChapter) },
                                    enabled = data.previous != null,
                                ) { Text("Назад") }
                                Button(
                                    onClick = { data.next?.let(onOpenChapter) ?: onToTitle() },
                                ) { Text(if (data.next != null) "Следующая" else "На страницу тайтла") }
                            }
                        }
                    }
                }

                if (controls) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(Color(0xCC000000))
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                        Column(Modifier.weight(1f)) {
                            data.manga?.let {
                                Text(it.title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(data.chapter.title, color = Color(0xFFCCCCCC), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (errorCount > 0) androidx.compose.material3.TextButton(onClick = { showErrors = true }) {
                            Text("Ошибки ($errorCount)", color = Color(0xFFFF8A80))
                        }
                        androidx.compose.material3.TextButton(onClick = {
                            showComments = true
                            report { AppGraph.repository.readerEvent(mangaId, data.chapter, "OPEN_COMMENTS") }
                        }) {
                            Text("Комментарии", color = Color.White)
                        }
                    }
                }
                if (controls && urls.isNotEmpty()) {
                    val total = urls.size
                    val current = listState.firstVisibleItemIndex.coerceIn(0, total - 1)
                    var dragging by remember { mutableStateOf<Float?>(null) }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color(0xCC000000))
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.TextButton(
                                onClick = { data.previous?.let(onOpenChapter) },
                                enabled = data.previous != null,
                            ) { Text("‹ Глава", color = if (data.previous != null) Color.White else Color(0xFF777777)) }
                            Text(
                                "${(dragging?.toInt() ?: current) + 1} / $total",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                            androidx.compose.material3.TextButton(
                                onClick = { data.next?.let(onOpenChapter) ?: onToTitle() },
                            ) { Text(if (data.next != null) "Глава ›" else "К тайтлу", color = Color.White) }
                        }
                        if (total > 1) {
                            androidx.compose.material3.Slider(
                                value = dragging ?: current.toFloat(),
                                onValueChange = {
                                    dragging = it
                                    scope.launch { listState.scrollToItem(it.toInt().coerceIn(0, total - 1)) }
                                },
                                onValueChangeFinished = { dragging = null },
                                valueRange = 0f..(total - 1).toFloat(),
                                steps = if (total in 3..60) total - 2 else 0,
                            )
                        }
                    }
                }
                if (showErrors) ReaderErrorsDialog(
                    context = "Манга ${data.manga?.title.orEmpty()} (${mangaId}), глава ${data.chapter.title} [${data.chapter.slug}], страниц: ${urls.size}",
                    onDismiss = { showErrors = false },
                )
                if (showComments) {
                    CommentsSheet(
                        chapterSlug = data.chapter.slug,
                        pageIndex = listState.firstVisibleItemIndex.coerceAtMost((data.pages.size - 1).coerceAtLeast(0)),
                        onDismiss = { showComments = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderErrorsDialog(context: String, onDismiss: () -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val log = me.senkuro.unofficial.data.ReaderErrorLog
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ошибки ридера") },
        text = {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    log.text(context).ifBlank { "Ошибок нет" },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(log.text(context))) }) { Text("Копировать") }
        },
        dismissButton = {
            Row {
                androidx.compose.material3.TextButton(onClick = { log.clear() }) { Text("Очистить") }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        },
    )
}
