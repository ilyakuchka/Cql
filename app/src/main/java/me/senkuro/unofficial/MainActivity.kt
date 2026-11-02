package me.senkuro.unofficial

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.ui.AppRoot
import me.senkuro.unofficial.ui.UnofficialTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Страницы светлые (значки панелей тёмные); на тёмных страницах тема переключает их сама.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val dark by AppGraph.themeDark.collectAsState()
            UnofficialTheme(dark = dark) {
                AppRoot()
            }
        }
    }
}
