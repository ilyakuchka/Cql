package me.senkuro.unofficial.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.senkuro.unofficial.BuildConfig
import me.senkuro.unofficial.data.UpdateInfo
import me.senkuro.unofficial.data.UpdateState
import me.senkuro.unofficial.data.Updater

/** Автопроверка при запуске + диалог обновления поверх любого экрана. */
@Composable
fun UpdateHost() {
    LaunchedEffect(Unit) { Updater.checkIfDue() }
    val state by Updater.state.collectAsState()
    val dismissed by Updater.dismissed.collectAsState()
    val scope = rememberCoroutineScope()
    if (dismissed) return
    when (val s = state) {
        is UpdateState.Available -> UpdateDialog(s.info, onUpdate = { scope.launch { Updater.downloadAndInstall(s.info) } })
        is UpdateState.NeedPermission -> AlertDialog(
            onDismissRequest = Updater::dismiss,
            title = { Text("Нужно разрешение") },
            text = { Text("Разрешите этому приложению устанавливать обновления («Установка из неизвестных источников»), затем вернитесь и нажмите «Обновить» снова.") },
            confirmButton = { TextButton(onClick = { scope.launch { Updater.downloadAndInstall(s.info) } }) { Text("Обновить") } },
            dismissButton = { TextButton(onClick = Updater::dismiss) { Text("Позже") } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Загрузка обновления ${s.info.versionName}") },
            text = {
                Column {
                    if (s.total > 0) LinearProgressIndicator(progress = { (s.done.toFloat() / s.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("${mb(s.done)} / ${if (s.total > 0) mb(s.total) else "?"} МБ", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {},
        )
        is UpdateState.Installing -> AlertDialog(
            onDismissRequest = Updater::dismiss,
            title = { Text("Установка") },
            text = { Text("Подтвердите установку в системном окне. Если оно не появилось — нажмите «Обновить» ещё раз.") },
            confirmButton = { TextButton(onClick = Updater::dismiss) { Text("Закрыть") } },
        )
        is UpdateState.Failed -> if (s.info != null) AlertDialog(
            onDismissRequest = Updater::dismiss,
            title = { Text("Ошибка обновления") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { scope.launch { Updater.downloadAndInstall(s.info) } }) { Text("Повторить") } },
            dismissButton = { TextButton(onClick = Updater::dismiss) { Text("Закрыть") } },
        )
        else -> {}
    }
}

@Composable
private fun UpdateDialog(info: UpdateInfo, onUpdate: () -> Unit) {
    AlertDialog(
        onDismissRequest = Updater::dismiss,
        title = { Text("Доступно обновление ${info.versionName}") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text("Сейчас установлена ${BuildConfig.VERSION_NAME}" + if (info.size > 0) " · ${mb(info.size)} МБ" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (info.notes.isNotBlank()) Text(info.notes, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onUpdate) { Text("Обновить") } },
        dismissButton = { TextButton(onClick = Updater::dismiss) { Text("Позже") } },
    )
}

private fun mb(b: Long) = "%.1f".format(b / 1048576.0)

/** Блок в настройках: версия, «Проверить обновления», переключатель автопроверки. */
@Composable
fun UpdateSettings() {
    val state by Updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(Updater.autoCheck) }
    val status = when (val s = state) {
        UpdateState.Checking -> "Проверяем…"
        UpdateState.UpToDate -> "Установлена последняя версия"
        is UpdateState.Available -> "Доступна версия ${s.info.versionName}"
        is UpdateState.Failed -> s.message
        is UpdateState.Downloading -> "Загрузка…"
        else -> "Текущая версия ${BuildConfig.VERSION_NAME}"
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Обновления", color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        TextButton(enabled = state != UpdateState.Checking, onClick = { scope.launch { Updater.check(silent = false) } }) { Text("Проверить обновления") }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Проверять при запуске", color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = auto, onCheckedChange = { auto = it; Updater.autoCheck = it })
    }
}
