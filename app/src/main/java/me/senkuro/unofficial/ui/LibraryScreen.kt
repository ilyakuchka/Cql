package me.senkuro.unofficial.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.senkuro.unofficial.data.MangaItem

/** Закладки: коллекции аккаунта на сайте (вход через аватарку на главной или кнопку на этой странице). */
@Composable
fun LibraryScreen(onManga: (MangaItem) -> Unit, onLogin: () -> Unit, onDebug: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Text(
            "Закладки",
            style = HeadingStyle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (onDebug != null) {
            OutlinedButton(
                onClick = onDebug,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) { Text("Отладка: перехват запросов сайта") }
        }
        SiteBookmarksTab(onManga = onManga, onLogin = onLogin)
    }
}
