package me.senkuro.unofficial.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Рекламные позиции senkuro.me (ads.* из конфигурации сайта): Yandex-блоки. */
object AdPositions {
    const val NOTIFICATIONS = "R-A-20309842-3"
    const val ANIME_EPISODES = "R-A-20309842-4"
    const val MANGA_CHAPTERS = "R-A-20309842-5"
    const val MANGA_VERTICAL = "R-A-20309842-6"
}

/** Yandex-блок высотой 250dp — так же, как на сайте (YandexAd.vue: render({renderTo, blockId, darkTheme})). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YandexAdBlock(blockId: String, modifier: Modifier = Modifier) {
    val account by me.senkuro.unofficial.data.AppGraph.account.collectAsState()
    if (account?.adsDisabled == true) return
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val html = remember(blockId, dark) {
        """
        <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>html,body{margin:0;padding:0;background:transparent}</style>
        <script>window.yaContextCb=window.yaContextCb||[]</script>
        <script src="https://yandex.ru/ads/system/context.js" async></script></head>
        <body><div id="ad-$blockId"></div>
        <script>window.yaContextCb.push(function(){Ya.Context.AdvManager.render({renderTo:"ad-$blockId",blockId:"$blockId",darkTheme:$dark})})</script>
        </body></html>
        """.trimIndent()
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Реклама", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(250.dp).padding(top = 2.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (!request.hasGesture()) return false
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url.toString())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            return true
                        }
                    }
                    tag = html
                    loadDataWithBaseURL("https://senkuro.me/", html, "text/html", "utf-8", null)
                }
            },
            update = { view ->
                if (view.tag != html) {
                    view.tag = html
                    view.loadDataWithBaseURL("https://senkuro.me/", html, "text/html", "utf-8", null)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
