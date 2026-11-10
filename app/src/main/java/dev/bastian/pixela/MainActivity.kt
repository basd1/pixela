package dev.bastian.pixela

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.bastian.pixela.ui.PixelaNavHost
import dev.bastian.pixela.ui.theme.PixelaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelaTheme {
                PixelaNavHost()
            }
        }
    }
}
