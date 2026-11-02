package me.senkuro.unofficial

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import me.senkuro.unofficial.ui.AppRoot
import me.senkuro.unofficial.ui.UnofficialTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Тема приложения тёмная, поэтому иконки системных панелей всегда светлые.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            UnofficialTheme {
                AppRoot()
            }
        }
    }
}
