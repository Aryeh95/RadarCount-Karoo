package io.github.aryeh95.radarcount

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.aryeh95.radarcount.data.SettingsRepository
import io.github.aryeh95.radarcount.ui.screens.MainScreen
import io.github.aryeh95.radarcount.ui.theme.RadarCountTheme

/**
 * The app's screen (see [MainScreen]). While it is in view it holds the
 * radar, as a field on a ride page does, so the Status tab is live
 * outside a ride too.
 */
class MainActivity : ComponentActivity() {

    private var holdingRadar = false

    override fun onStart() {
        super.onStart()
        RadarCountExtension.running?.let {
            it.acquireRadar()
            holdingRadar = true
        }
    }

    override fun onStop() {
        if (holdingRadar) {
            RadarCountExtension.running?.releaseRadar()
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
                    val repository = SettingsRepository.of(this)
                    MainScreen(
                        repository = repository,
                        extension = RadarCountExtension.running,
                        onClose = { finish() }
                    )
                }
            }
        }
    }
}
