package me.senkuro.unofficial.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.SenkuroConfig
import me.senkuro.unofficial.data.SsoClient

/**
 * Вход: нативная форма (почта, пароль, код 2FA). Капча проходит в маленьком WebView,
 * остальное (логин, OAuth, токен) делает SsoClient. «Войти через сайт» — прежний вход через браузер.
 */
@Composable
fun LoginScreen(onBack: () -> Unit, onDone: () -> Unit) {
    var web by remember { mutableStateOf(false) }
    if (web) {
        WebLoginScreen(onBack = { web = false }, onDone = onDone)
        return
    }
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<SsoClient.CaptchaConfig?>(null) }
    var configError by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var twoFactor by remember { mutableStateOf("") }
    var needTwoFactor by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var captchaRun by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            config = SsoClient.captchaConfig()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            configError = e.message ?: "Нет связи с SSO"
        }
    }

    fun onCaptcha(token: String) {
        val cfg = config ?: return
        scope.launch {
            try {
                val access = SsoClient.login(email.trim(), password, cfg, token, twoFactor.takeIf { needTwoFactor })
                if (AppGraph.tryLogin(access)) {
                    onDone()
                    return@launch
                }
                message = "Токен получен, но сайт его не принял"
            } catch (e: CancellationException) {
                throw e
            } catch (e: SsoClient.SsoException) {
                if (e.code == 4104) needTwoFactor = true
                message = e.message
            } catch (e: Exception) {
                message = e.message ?: "Ошибка сети"
            }
            busy = false
            captchaRun = 0
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Вход в аккаунт", style = HeadingStyle, color = MaterialTheme.colorScheme.onBackground)
        }
        OutlinedTextField(
            value = email, onValueChange = { email = it }, singleLine = true, enabled = !busy,
            label = { Text("Почта") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it }, singleLine = true, enabled = !busy,
            label = { Text("Пароль") }, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        if (needTwoFactor) {
            OutlinedTextField(
                value = twoFactor, onValueChange = { twoFactor = it.filter(Char::isDigit).take(8) }, singleLine = true,
                enabled = !busy, label = { Text("Код двухфакторной защиты") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        configError?.let { Text("$it. Войдите через сайт.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        Button(
            enabled = !busy && config != null && email.isNotBlank() && password.isNotBlank() && (!needTwoFactor || twoFactor.length >= 6),
            onClick = {
                message = null
                busy = true
                captchaRun++
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Проверка…" else "Войти") }

        if (busy && captchaRun > 0) {
            config?.let { cfg ->
                // Капча (Cloudflare Turnstile / Yandex SmartCaptcha) выполняется в крошечном WebView и при необходимости показывает проверку.
                CaptchaView(
                    key = captchaRun, config = cfg,
                    onToken = { onCaptcha(it) },
                    onError = { message = "Капча: $it"; busy = false; captchaRun = 0 },
                )
            }
        }

        TextButton(onClick = { web = true }, enabled = !busy) { Text("Войти через сайт (браузер)") }
        Text(
            "Пароль отправляется только на ${SenkuroConfig.current.ssoHost} и нигде не сохраняется. В приложении хранится лишь токен доступа.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
        )
    }
}

private class CaptchaBridge(val onToken: (String) -> Unit, val onError: (String) -> Unit) {
    @JavascriptInterface
    fun token(value: String) = onToken(value)

    @JavascriptInterface
    fun error(value: String) = onError(value)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CaptchaView(key: Int, config: SsoClient.CaptchaConfig, onToken: (String) -> Unit, onError: (String) -> Unit) {
    val keyJson = JsonPrimitive(config.siteKey).toString()
    val html = if (config.service == "smart_captcha") {
        """<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<script src="https://smartcaptcha.cloud.yandex.ru/captcha.js?render=onload&onload=ready" defer></script></head>
<body style="margin:0"><div id="c"></div><script>
function ready(){try{var id=window.smartCaptcha.render('c',{sitekey:$keyJson,invisible:true,callback:function(t){AndroidCaptcha.token(t)}});window.smartCaptcha.execute(id);}catch(e){AndroidCaptcha.error(String(e));}}
</script></body></html>"""
    } else {
        """<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&onload=ready" async defer></script></head>
<body style="margin:0"><div id="c"></div><script>
function ready(){try{turnstile.render('#c',{sitekey:$keyJson,appearance:'interaction-only',action:'auth',size:'compact',execution:'execute',callback:function(t){AndroidCaptcha.token(t)},'error-callback':function(e){AndroidCaptcha.error(String(e))}});turnstile.execute('#c');}catch(e){AndroidCaptcha.error(String(e));}}
</script></body></html>"""
    }
    val main = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(
                    CaptchaBridge({ t -> main.post { onToken(t) } }, { e -> main.post { onError(e) } }),
                    "AndroidCaptcha",
                )
                loadDataWithBaseURL(SenkuroConfig.current.ssoUrl + "/", html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
        update = { },
    )
}
