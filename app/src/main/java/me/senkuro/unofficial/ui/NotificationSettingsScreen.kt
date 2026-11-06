package me.senkuro.unofficial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph

private const val CHANNEL = "IN_APP"

private class Pref(val type: String, val title: String, val hint: String)

private val GROUPS: List<Pair<String, List<Pref>>> = listOf(
    "Социальное" to listOf(
        Pref("COMMENT_REPLIED", "Ответы на комментарии", "Кто-то ответил на ваш комментарий"),
        Pref("COMMENT_UPVOTED", "Оценки комментариев", "Ваш комментарий оценили"),
        Pref("COMMENT_MENTIONED", "Упоминания", "Вас упомянули в комментарии"),
        Pref("POST_COMMENTED", "Комментарии к постам", "Новый комментарий под вашим постом"),
        Pref("NEW_FRIEND_REQUEST", "Заявки в друзья", "Вам отправили заявку в друзья"),
    ),
    "Карты" to listOf(
        Pref("NEW_FRIEND_WAIFU", "Карты друзей", "Друг получил новую карту"),
    ),
)

class NotificationSettingsViewModel : ViewModel() {
    /** Ключ type:channel → включено. */
    val enabled: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    private val userId get() = AppGraph.account.value?.userId

    fun load() {
        val uid = userId ?: run { loading = false; error = "Нужно войти в аккаунт"; return }
        viewModelScope.launch {
            loading = true; error = null
            try {
                val off = AppGraph.repository.notificationOff(uid)
                GROUPS.flatMap { it.second }.forEach { enabled[it.type + ":" + CHANNEL] = (it.type + ":" + CHANNEL) !in off }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                error = e.message ?: "Не удалось загрузить"
            }
            loading = false
        }
    }

    fun toggle(type: String, value: Boolean) {
        val uid = userId ?: return
        val key = "$type:$CHANNEL"
        enabled[key] = value
        viewModelScope.launch {
            try {
                AppGraph.repository.setNotificationEnabled(uid, type, CHANNEL, value)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                enabled[key] = !value
                error = e.message ?: "Не удалось сохранить"
            }
        }
    }
}

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, vm: NotificationSettingsViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = MaterialTheme.colorScheme.onBackground)
            }
            Column {
                Text("Настройки уведомлений", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground)
                Text("Выберите уведомления которые хотите получать", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GROUPS.forEach { (group, prefs) ->
                item(key = "h-$group") {
                    Text(group, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 8.dp))
                }
                prefs.forEach { p ->
                    item(key = p.type) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                                Text(p.hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Switch(
                                checked = vm.enabled[p.type + ":" + CHANNEL] ?: true,
                                enabled = !vm.loading,
                                onCheckedChange = { vm.toggle(p.type, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}
