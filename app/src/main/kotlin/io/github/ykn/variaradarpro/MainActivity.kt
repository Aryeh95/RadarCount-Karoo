package io.github.ykn.variaradarpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.ykn.variaradarpro.ui.screens.DashboardScreen
import io.github.ykn.variaradarpro.ui.theme.VariaRadarProTheme

/**
 * Status screen. All configuration comes from the Karoo profile
 * (units) so there is nothing to set up here.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VariaRadarProTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DashboardScreen(extension = VariaRadarExtension.instance)
                }
            }
        }
    }
}
