package io.github.aryeh95.radarcount

import android.content.Intent
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
import io.github.aryeh95.radarcount.probe.ProbeRunner
import io.github.aryeh95.radarcount.probe.ProbeStore
import io.github.aryeh95.radarcount.ui.screens.ProbeScreen
import io.github.aryeh95.radarcount.ui.screens.DashboardScreen
import io.github.aryeh95.radarcount.ui.screens.SettingsScreen
import io.github.aryeh95.radarcount.ui.theme.RadarCountTheme

/**
 * Status screen. All configuration comes from the Karoo profile
 * (units) so there is nothing to set up here.
 */
class MainActivity : ComponentActivity() {

    private var holdingRadar = false

    /** Bumped when an OAuth redirect arrives so the probe screen reacts. */
    private val redirectTick = mutableStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRedirect(intent)
    }

    private fun handleRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == ProbeRunner.REDIRECT_URI.substringBefore("://")) {
            ProbeStore(this).lastRedirect = data.toString()
            redirectTick.value++
        }
    }

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
        handleRedirect(intent)

        setContent {
            RadarCountTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showSettings by remember { mutableStateOf(false) }
                    var showProbe by remember { mutableStateOf(intent?.data != null) }
                    val repository = SettingsRepository.getInstance(this)
                    val tick by redirectTick
                    if (tick > 0) showProbe = true
                    if (showProbe) {
                        ProbeScreen(store = ProbeStore(this), redirectTick = tick, onBack = { showProbe = false })
                    } else if (showSettings) {
                        SettingsScreen(repository = repository, onBack = { showSettings = false }, onProbe = { showProbe = true })
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
