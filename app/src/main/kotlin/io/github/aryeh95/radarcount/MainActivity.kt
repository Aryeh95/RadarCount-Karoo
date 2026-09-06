package io.github.aryeh95.radarcount

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.aryeh95.radarcount.data.SettingsRepository
import io.github.aryeh95.radarcount.ui.screens.DashboardScreen
import io.github.aryeh95.radarcount.ui.screens.SettingsScreen
import io.github.aryeh95.radarcount.ui.theme.RadarCountTheme

/**
 * Status screen. All configuration comes from the Karoo profile
 * (units) so there is nothing to set up here.
 */
class MainActivity : ComponentActivity() {

    private var holdingRadar = false

    override fun onStart() {
        super.onStart()
        RadarCountExtension.instance?.let {
            it.acquireRadar()
            holdingRadar = true
        }
    }

    override fun onStop() {
        if (holdingRadar) {
            RadarCountExtension.instance?.releaseRadar()
            holdingRadar = false
        }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RadarCountTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showSettings by remember { mutableStateOf(false) }
                    val repository = SettingsRepository.getInstance(this)
                    if (showSettings) {
                        SettingsScreen(repository = repository, onBack = { showSettings = false })
                    } else {
                        DashboardScreen(
                            extension = RadarCountExtension.instance,
                            onSettingsClick = { showSettings = true },
                            onResetCount = { RadarCountExtension.instance?.radarEngine?.resetPassCounts() }
                        )
                    }
                }
            }
        }
    }
}
