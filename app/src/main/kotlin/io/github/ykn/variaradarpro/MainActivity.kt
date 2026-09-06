package io.github.ykn.variaradarpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.ykn.variaradarpro.data.PreferencesRepository
import io.github.ykn.variaradarpro.data.models.ThreatLevel
import io.github.ykn.variaradarpro.ui.screens.DashboardScreen
import io.github.ykn.variaradarpro.ui.screens.OnboardingScreen
import io.github.ykn.variaradarpro.ui.screens.SettingsScreen
import io.github.ykn.variaradarpro.ui.theme.VariaRadarProTheme
import kotlinx.coroutines.launch

/**
 * Main activity for eiRadar.
 *
 * Shows onboarding for first-time users, then dashboard with radar status.
 * Settings accessible from dashboard.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var preferencesRepository: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        android.util.Log.i(TAG, "MainActivity created")

        // Use singleton to avoid DataStore multiple-instance crash
        preferencesRepository = PreferencesRepository.getInstance(this)

        setContent {
            VariaRadarProTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val hasSeenOnboarding by preferencesRepository.hasSeenOnboardingFlow
                        .collectAsState(initial = true) // Default to true to avoid flash
                    val scope = rememberCoroutineScope()
                    var showSettings by remember { mutableStateOf(false) }

                    when {
                        !hasSeenOnboarding -> {
                            // Show onboarding for first-time users
                            OnboardingScreen(
                                onComplete = {
                                    scope.launch {
                                        preferencesRepository.markOnboardingSeen()
                                    }
                                }
                            )
                        }
                        showSettings -> {
                            // Show settings screen
                            val extension = VariaRadarExtension.instance
                            val isImperial = extension?.useImperial?.collectAsState()?.value ?: false
                            SettingsScreen(
                                preferencesRepository = preferencesRepository,
                                statisticsCollector = extension?.statisticsCollector,
                                useImperial = isImperial,
                                onTestAlert = { testAlert() },
                                onBack = { showSettings = false }
                            )
                        }
                        else -> {
                            // Show main dashboard
                            val extension = VariaRadarExtension.instance
                            DashboardScreen(
                                widgetStateFlow = extension?.radarEngine?.widgetState,
                                isNightModeFlow = extension?.nightModeManager?.isNightMode,
                                useImperialFlow = extension?.useImperial,
                                onSettingsClick = { showSettings = true }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun testAlert() {
        android.util.Log.d(TAG, "Testing alert")
        VariaRadarExtension.instance?.let { extension ->
            extension.alertManager.forceAlert(ThreatLevel.WARNING)
        }
    }
}
