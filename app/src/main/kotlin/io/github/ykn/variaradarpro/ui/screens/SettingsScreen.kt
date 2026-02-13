package io.github.ykn.variaradarpro.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ykn.variaradarpro.BuildConfig
import io.github.ykn.variaradarpro.R
import io.github.ykn.variaradarpro.data.PreferencesRepository
import io.github.ykn.variaradarpro.data.models.AlertSettings
import io.github.ykn.variaradarpro.data.models.BuiltInSoundSet
import io.github.ykn.variaradarpro.data.models.PresetSettings
import io.github.ykn.variaradarpro.data.models.ScreenWakePolicy
import io.github.ykn.variaradarpro.engine.StatisticsCollector
import io.github.ykn.variaradarpro.ui.components.InfoRow
import io.github.ykn.variaradarpro.ui.components.SettingsItem
import io.github.ykn.variaradarpro.ui.components.SettingsSection
import io.github.ykn.variaradarpro.ui.components.SettingsSwitch
import io.github.ykn.variaradarpro.ui.theme.RadarColors
import kotlinx.coroutines.launch

/**
 * Compact settings screen for Karoo 3.
 * 2 sections: Alerts, About.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesRepository: PreferencesRepository,
    statisticsCollector: StatisticsCollector?,
    useImperial: Boolean = false,
    onTestAlert: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val settings by preferencesRepository.settingsFlow.collectAsState(initial = PresetSettings())
    val alertSettings by preferencesRepository.alertSettingsFlow.collectAsState(initial = AlertSettings())

    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        ResetDialog(
            onConfirm = {
                scope.launch { preferencesRepository.resetToDefaults() }
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = RadarColors.textPrimary
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(onClick = onTestAlert) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = stringResource(R.string.settings_test_alert),
                            tint = RadarColors.accent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RadarColors.background)
            )
        },
        containerColor = RadarColors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ALERTS
            SettingsSection(title = stringResource(R.string.settings_alert_channels)) {
                SettingsSwitch(
                    title = stringResource(R.string.settings_master_enable),
                    checked = alertSettings.globalEnabled,
                    onCheckedChange = {
                        scope.launch {
                            preferencesRepository.updateAlertSettings(alertSettings.copy(globalEnabled = it))
                        }
                    }
                )

                if (alertSettings.globalEnabled) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_visual_alert),
                        checked = alertSettings.visualAlert,
                        onCheckedChange = {
                            scope.launch {
                                preferencesRepository.updateAlertSettings(alertSettings.copy(visualAlert = it))
                            }
                        }
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.settings_sound_alert),
                        checked = alertSettings.soundAlert && settings.soundEnabled,
                        value = getSoundName(settings.soundSet),
                        onValueClick = {
                            scope.launch {
                                preferencesRepository.updateSettings(
                                    settings.copy(soundSet = SettingsLogic.nextSound(settings.soundSet))
                                )
                            }
                        },
                        onCheckedChange = {
                            scope.launch {
                                preferencesRepository.updateAlertSettings(alertSettings.copy(soundAlert = it))
                                preferencesRepository.updateSettings(settings.copy(soundEnabled = it))
                            }
                        }
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.settings_haptic_alert),
                        checked = alertSettings.hapticAlert,
                        onCheckedChange = {
                            scope.launch {
                                preferencesRepository.updateAlertSettings(alertSettings.copy(hapticAlert = it))
                            }
                        }
                    )

                    SettingsSwitch(
                        title = stringResource(R.string.settings_clear_chime),
                        checked = settings.clearChimeEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                preferencesRepository.updateSettings(settings.copy(clearChimeEnabled = enabled))
                            }
                        }
                    )
                }
            }

            // DETECTION THRESHOLDS
            SettingsSection(title = stringResource(R.string.settings_detection)) {
                SettingsItem(
                    title = stringResource(R.string.settings_approaching_distance),
                    value = SettingsLogic.formatDistance(settings.approachingDistanceM, useImperial),
                    onClick = {
                        scope.launch {
                            preferencesRepository.updateSettings(
                                settings.copy(approachingDistanceM = SettingsLogic.nextApproaching(settings.approachingDistanceM))
                            )
                        }
                    },
                    showArrow = false
                )

                SettingsItem(
                    title = stringResource(R.string.settings_warning_distance),
                    value = SettingsLogic.formatDistance(settings.warningDistanceM, useImperial),
                    onClick = {
                        scope.launch {
                            preferencesRepository.updateSettings(
                                settings.copy(warningDistanceM = SettingsLogic.nextWarning(settings.warningDistanceM))
                            )
                        }
                    },
                    showArrow = false
                )

                SettingsItem(
                    title = stringResource(R.string.settings_critical_distance),
                    value = SettingsLogic.formatDistance(settings.criticalDistanceM, useImperial),
                    onClick = {
                        scope.launch {
                            preferencesRepository.updateSettings(
                                settings.copy(criticalDistanceM = SettingsLogic.nextCritical(settings.criticalDistanceM))
                            )
                        }
                    },
                    showArrow = false
                )

                SettingsItem(
                    title = stringResource(R.string.settings_alert_cooldown),
                    value = SettingsLogic.formatCooldown(settings.alertCooldownMs),
                    onClick = {
                        scope.launch {
                            preferencesRepository.updateSettings(
                                settings.copy(alertCooldownMs = SettingsLogic.nextCooldown(settings.alertCooldownMs))
                            )
                        }
                    },
                    showArrow = false
                )

                SettingsItem(
                    title = stringResource(R.string.settings_speed_gate),
                    value = SettingsLogic.formatSpeedGate(settings.speedGateKmh, useImperial),
                    onClick = {
                        scope.launch {
                            preferencesRepository.updateSettings(
                                settings.copy(speedGateKmh = SettingsLogic.nextSpeedGate(settings.speedGateKmh))
                            )
                        }
                    },
                    showArrow = false
                )

                SettingsItem(
                    title = stringResource(R.string.settings_screen_wake),
                    value = getScreenWakeName(settings.screenWakePolicy),
                    onClick = {
                        scope.launch {
                            preferencesRepository.updateSettings(
                                settings.copy(screenWakePolicy = SettingsLogic.nextScreenWake(settings.screenWakePolicy))
                            )
                        }
                    },
                    showArrow = false
                )
            }

            // ABOUT
            SettingsSection(title = stringResource(R.string.settings_about)) {
                InfoRow(
                    label = stringResource(R.string.settings_version),
                    value = BuildConfig.VERSION_NAME
                )

                InfoRow(
                    label = stringResource(R.string.settings_radar_support),
                    value = "ANT+ Radar"
                )

                SettingsItem(
                    title = stringResource(R.string.action_reset),
                    onClick = { showResetDialog = true },
                    showArrow = false
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ResetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.action_reset_confirm_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.action_reset_confirm_message),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_confirm),
                    color = RadarColors.danger,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        containerColor = RadarColors.surface
    )
}

// Helpers

@Composable
private fun getSoundName(s: BuiltInSoundSet): String = when (s) {
    BuiltInSoundSet.CLASSIC -> stringResource(R.string.sound_set_classic)
    BuiltInSoundSet.SUBTLE -> stringResource(R.string.sound_set_subtle)
    BuiltInSoundSet.URGENT -> stringResource(R.string.sound_set_urgent)
    BuiltInSoundSet.BIKE_BELL -> stringResource(R.string.sound_set_bike_bell)
}

@Composable
private fun getScreenWakeName(policy: ScreenWakePolicy): String = when (policy) {
    ScreenWakePolicy.NEVER -> stringResource(R.string.screen_wake_never)
    ScreenWakePolicy.CRITICAL_ONLY -> stringResource(R.string.screen_wake_critical)
    ScreenWakePolicy.ALWAYS -> stringResource(R.string.screen_wake_always)
}
