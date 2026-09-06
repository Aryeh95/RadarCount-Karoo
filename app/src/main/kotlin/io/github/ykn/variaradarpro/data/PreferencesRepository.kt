package io.github.ykn.variaradarpro.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.ykn.variaradarpro.data.models.AlertSettings
import io.github.ykn.variaradarpro.data.models.BuiltInSoundSet
import io.github.ykn.variaradarpro.data.models.PresetSettings
import io.github.ykn.variaradarpro.data.models.ScreenWakePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "varia_radar_settings")

/**
 * Repository for app settings using DataStore.
 * Must be a singleton — DataStore does not support multiple instances for the same file.
 */
class PreferencesRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: PreferencesRepository? = null

        fun getInstance(context: Context): PreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Thresholds
        private val KEY_APPROACHING_DISTANCE = intPreferencesKey("approaching_distance_m")
        private val KEY_WARNING_DISTANCE = intPreferencesKey("warning_distance_m")
        private val KEY_CRITICAL_DISTANCE = intPreferencesKey("critical_distance_m")
        private val KEY_ALERT_COOLDOWN = longPreferencesKey("alert_cooldown_ms")

        // Speed gate
        private val KEY_SPEED_GATE_KMH = intPreferencesKey("speed_gate_kmh")

        // Sound
        private val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        private val KEY_SOUND_SET = stringPreferencesKey("sound_set")

        // Visual
        private val KEY_SCREEN_WAKE_POLICY = stringPreferencesKey("screen_wake_policy")
        private val KEY_CLEAR_CHIME = booleanPreferencesKey("clear_chime_enabled")

        // Alerts
        private val KEY_ALERTS_GLOBAL_ENABLED = booleanPreferencesKey("alerts_global_enabled")
        private val KEY_VISUAL_ALERT = booleanPreferencesKey("visual_alert")
        private val KEY_SOUND_ALERT = booleanPreferencesKey("sound_alert")

        // Onboarding
        private val KEY_HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
    }

    // Lives as long as the process; the repository is a process-wide singleton.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Flow of effective settings.
     */
    val settingsFlow: Flow<PresetSettings> = context.dataStore.data
        .map { prefs ->
            PresetSettings(
                approachingDistanceM = prefs[KEY_APPROACHING_DISTANCE] ?: 100,
                warningDistanceM = prefs[KEY_WARNING_DISTANCE] ?: 50,
                criticalDistanceM = prefs[KEY_CRITICAL_DISTANCE] ?: 20,
                alertCooldownMs = prefs[KEY_ALERT_COOLDOWN] ?: 5000L,
                speedGateKmh = prefs[KEY_SPEED_GATE_KMH] ?: 0,
                soundEnabled = prefs[KEY_SOUND_ENABLED] ?: true,
                soundSet = parseEnum(prefs[KEY_SOUND_SET], BuiltInSoundSet.CLASSIC),
                screenWakePolicy = parseEnum(prefs[KEY_SCREEN_WAKE_POLICY], ScreenWakePolicy.CRITICAL_ONLY),
                clearChimeEnabled = prefs[KEY_CLEAR_CHIME] ?: true
            )
        }
        .distinctUntilChanged()

    /**
     * Hot, cached view of [settingsFlow]. Read `.value` from hot paths
     * (widget rendering) instead of hitting DataStore each time.
     */
    val settingsState: StateFlow<PresetSettings> = settingsFlow
        .stateIn(scope, SharingStarted.Eagerly, PresetSettings())

    /**
     * Flow of alert settings.
     */
    val alertSettingsFlow: Flow<AlertSettings> = context.dataStore.data
        .map { prefs ->
            AlertSettings(
                globalEnabled = prefs[KEY_ALERTS_GLOBAL_ENABLED] ?: true,
                visualAlert = prefs[KEY_VISUAL_ALERT] ?: true,
                soundAlert = prefs[KEY_SOUND_ALERT] ?: true
            )
        }
        .distinctUntilChanged()

    /**
     * Flow indicating onboarding status.
     */
    val hasSeenOnboardingFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_HAS_SEEN_ONBOARDING] ?: false }
        .distinctUntilChanged()

    // === Update methods ===

    suspend fun updateSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOUND_ENABLED] = enabled
        }
    }

    suspend fun updateGlobalAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ALERTS_GLOBAL_ENABLED] = enabled
        }
    }

    suspend fun markOnboardingSeen() {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_ONBOARDING] = true
        }
    }

    /**
     * Update all settings at once.
     */
    suspend fun updateSettings(settings: PresetSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APPROACHING_DISTANCE] = settings.approachingDistanceM
            prefs[KEY_WARNING_DISTANCE] = settings.warningDistanceM
            prefs[KEY_CRITICAL_DISTANCE] = settings.criticalDistanceM
            prefs[KEY_ALERT_COOLDOWN] = settings.alertCooldownMs
            prefs[KEY_SPEED_GATE_KMH] = settings.speedGateKmh
            prefs[KEY_SOUND_ENABLED] = settings.soundEnabled
            prefs[KEY_SOUND_SET] = settings.soundSet.name
            prefs[KEY_SCREEN_WAKE_POLICY] = settings.screenWakePolicy.name
            prefs[KEY_CLEAR_CHIME] = settings.clearChimeEnabled
        }
    }

    /**
     * Update alert channel settings.
     */
    suspend fun updateAlertSettings(alertSettings: AlertSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ALERTS_GLOBAL_ENABLED] = alertSettings.globalEnabled
            prefs[KEY_VISUAL_ALERT] = alertSettings.visualAlert
            prefs[KEY_SOUND_ALERT] = alertSettings.soundAlert
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    // Helper
    private inline fun <reified T : Enum<T>> parseEnum(value: String?, default: T): T {
        return try {
            value?.let { enumValueOf<T>(it) } ?: default
        } catch (e: Exception) {
            default
        }
    }
}
