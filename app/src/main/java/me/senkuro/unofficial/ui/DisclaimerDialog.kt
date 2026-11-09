package me.senkuro.unofficial.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import me.senkuro.unofficial.data.AppGraph

private const val SOURCE_URL = "https://github.com/ilyakuchka/Cql"
private const val WAIT_SECONDS = 10

/**
 * Одноразовое окно при первом запуске: приложение неофициальное. Закрыть его можно только кнопкой «ОК»,
 * которая становится доступной через 10 секунд; назад и касание вне окна не закрывают.
 */
@Composable
fun DisclaimerDialog() {
    var visible by rememberSaveable { mutableStateOf(!AppGraph.disclaimerSeen()) }
    if (!visible) return
    var left by remember { mutableIntStateOf(WAIT_SECONDS) }
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(Unit) {
        while (left > 0) { delay(1000); left-- }
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Важная информация") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Проект «Client GraphQL» — неофициальное приложение для сайта Senkuro.\n\n" +
                        "Оно никак не связано напрямую с сайтом Senkuro и его администрацией, не одобрено и не поддерживается ими " +
                        "и не аффилировано с ними. Все названия, материалы и торговые марки принадлежат их владельцам.\n\n" +
                        "Приложение распространяется «как есть» (AS IS) по лицензии MIT, без каких-либо гарантий. " +
                        "Это ранняя альфа-версия: возможны ошибки и вылеты. Вы используете приложение на свой риск.\n\n" +
                        "Исходный код:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = { runCatching { uriHandler.openUri(SOURCE_URL) } }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(SOURCE_URL, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = left == 0,
                onClick = { AppGraph.markDisclaimerSeen(); visible = false },
            ) { Text(if (left > 0) "ОК ($left)" else "ОК") }
        },
    )
}
