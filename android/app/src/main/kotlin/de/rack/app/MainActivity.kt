package de.rack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import de.rack.app.ui.RackNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as RackApplication).container
        setContent {
            MaterialTheme {
                Surface {
                    RackNavHost(container = container)
                }
            }
        }
    }
}
