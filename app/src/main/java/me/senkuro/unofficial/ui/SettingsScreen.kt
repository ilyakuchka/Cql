package me.senkuro.unofficial.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.SenkuroConfig

/** Настройки: пока только переключатель тёмной темы. */
@Composable
fun SettingsScreen(onCards: () -> Unit = {}, onCardCatalog: () -> Unit = {}, onOffers: () -> Unit = {}, onPosts: () -> Unit = {}, onCardHub: () -> Unit = {}, onJournal: (() -> Unit)? = null, onCapture: (() -> Unit)? = null, onSiteVersion: (() -> Unit)? = null) {
    val dark by AppGraph.themeDark.collectAsState()
    val token by AppGraph.auth.token.collectAsState()
    val account by AppGraph.account.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Настройки",
            style = HeadingStyle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Тёмная тема", color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
                Text(
                    "Страница тайтла и ридер всегда тёмные",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            Switch(checked = dark, onCheckedChange = AppGraph::setDarkTheme)
        }
        if (onJournal != null || onCapture != null || onSiteVersion != null) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionLabel("Debug")
            if (onJournal != null) TextButton(onClick = onJournal, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Журнал запросов (debug)") }
            if (onCapture != null) TextButton(onClick = onCapture, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Отладка: перехват запросов сайта (debug)") }
            if (onSiteVersion != null) TextButton(onClick = onSiteVersion, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Версия сайта: App-Version и App-Id (debug)") }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionLabel("Сайт")
        TextButton(onClick = onPosts, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Новости сайта (посты и объявления)") }
        if (token != null) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionLabel("Мой аккаунт")
            TextButton(onClick = onCards, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Мои карточки") }
            TextButton(onClick = onCardCatalog, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Каталог карточек") }
            TextButton(onClick = onOffers, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Обмены карточек") }
            TextButton(onClick = onCardHub, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Колоды, паки, кузница, магазин, Shorts") }
        }
        if (token != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Аккаунт", color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
                    Text(
                        account?.name ?: SenkuroConfig.current.title,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                TextButton(onClick = AppGraph::logout) { Text("Выйти") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}
