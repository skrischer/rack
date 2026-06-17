package de.rack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import de.rack.app.ui.RackNavHost
import de.rack.app.ui.theme.RecompTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as RackApplication).container
        setContent {
            RecompTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = RecompTheme.colors.bg,
                ) {
                    RackNavHost(container = container)
                }
            }
        }
    }
}
