package me.senkuro.unofficial.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.SenkuroConfig
import java.net.URLDecoder

private const val SSO_URL = "https://sso.senkuro.net/"

/**
 * Вход через сайт: логин (с капчей) проходит в WebView, после этого токен берётся из cookie `access_token`
 * (запасной вариант — ключ с «access token» в localStorage) и проверяется запросом fetchViewer.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Войдите в аккаунт — приложение подхватит вход само.") }
    var checking by remember { mutableStateOf(false) }
    var lastTried by remember { mutableStateOf<String?>(null) }
    var openedSite by remember { mutableStateOf(false) }

    fun candidate(cookies: String): String? = cookies.split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("access_token=") }
        ?.substringAfter('=')
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        ?.takeIf { it.length > 20 }

    fun verify(token: String, manual: Boolean) {
        if (checking || token == lastTried) return
        lastTried = token
        checking = true
        status = "Проверяю вход…"
        scope.launch {
            val ok = AppGraph.tryLogin(token)
            checking = false
            if (ok) {
                onDone()
            } else {
                status = "Токен не принят, войдите ещё раз."
                if (manual) Toast.makeText(context, "Вход не подтверждён", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun check(web: WebView?, manual: Boolean) {
        val cookies = CookieManager.getInstance().getCookie(SenkuroConfig.BASE_URL).orEmpty()
        val fromCookie = candidate(cookies)
        if (fromCookie != null) {
            verify(fromCookie, manual)
            return
        }
        web?.evaluateJavascript(
            "(function(){try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);" +
                "if(/access.?token/i.test(k)){return localStorage.getItem(k)||'';}}}catch(e){}return '';})()",
        ) { raw ->
            val value = raw.orEmpty().trim('"')
            if (value.length > 20 && value.none { it == ' ' || it == '{' || it == '\\' }) {
                verify(value, manual)
            } else if (manual) {
                // Только имена cookie, без значений.
                val names = cookies.split(';').map { it.trim().substringBefore('=') }.filter { it.isNotEmpty() }
                Toast.makeText(
                    context,
                    "Токен не найден. Cookie: " + names.joinToString().ifEmpty { "нет" },
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val web = remember {
        WebView(context).apply {
            val webView = this
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = Unit
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Токен ищем только на самом сайте, не на странице SSO.
                    val host = runCatching { java.net.URI(url.orEmpty()).host }.getOrNull()
                    if (host == java.net.URI(SenkuroConfig.BASE_URL).host) {
                        check(view, manual = false)
                    } else if (host == java.net.URI(SSO_URL).host && !openedSite &&
                        runCatching { java.net.URI(url.orEmpty()).path }.getOrNull().orEmpty().startsWith("/account")
                    ) {
                        openedSite = true
                        view?.loadUrl(SenkuroConfig.BASE_URL)
                    }
                }
            }
            // Сразу на страницу входа SSO; после входа переходим на сайт, чтобы он получил токен.
            loadUrl(SSO_URL)
        }
    }

    DisposableEffect(web) {
        onDispose {
            web.stopLoading()
            web.destroy()
        }
    }
    BackHandler { if (web.canGoBack()) web.goBack() else onBack() }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text("Вход (sso.senkuro.net)", style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { check(web, manual = true) }) { Text("Проверить вход") }
        }
        AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
    }
}
