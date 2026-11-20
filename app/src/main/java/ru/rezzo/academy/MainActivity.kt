package ru.rezzo.academy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.rezzo.academy.ui.RezzoNavHost
import ru.rezzo.academy.ui.theme.RezzoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RezzoApp).container
        setContent {
            RezzoTheme {
                RezzoNavHost(container)
            }
        }
    }
}
