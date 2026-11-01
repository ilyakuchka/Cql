package me.senkuro.unofficial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import me.senkuro.unofficial.ui.AppRoot
import me.senkuro.unofficial.ui.UnofficialTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            UnofficialTheme {
                AppRoot()
            }
        }
    }
}
