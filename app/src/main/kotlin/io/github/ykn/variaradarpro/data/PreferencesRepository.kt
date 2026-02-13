package io.github.ykn.variaradarpro.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.ykn.variaradarpro.data.models.AlertSettings
import io.github.ykn.variaradarpro.data.models.BuiltInSoundSet
import io.github.ykn.variaradarpro.data.models.PresetSettings
import io.github.ykn.variaradarpro.data.models.ScreenWakePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
        private val KEY_SOUND_VOLUME = floatPreferencesKey("sound_volume")
        private val KEY_SOUND_SET = stringPreferencesKey("sound_set")

        // Visual
        private val KEY_SCREEN_WAKE_POLICY = stringPreferencesKey("screen_wake_policy")
        private val KEY_CLEAR_CHIME = booleanPreferencesKey("clear_chime_enabled")

        // Alerts
        private val KEY_ALERTS_GLOBAL_ENABLED = booleanPreferencesKey("alerts_global_enabled")
        private val KEY_VISUAL_ALERT = booleanPreferencesKey("visual_alert")
        private val KEY_SOUND_ALERT = booleanPreferencesKey("sound_alert")
        private val KEY_HAPTIC_ALERT = booleanPreferencesKey("haptic_alert")

        // Onboarding
        private val KEY_HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")

        // Statistics
        private val KEY_SHOW_STATS_AFTER_RIDE = booleanPreferencesKey("show_stats_after_ride")
    }

    /**
     * Flow of effective settings.
     */
    val settingsFlow: Flow<PresetSettings> = context.dataStore.data
        .map { prefs ->
            PresetSettings(
                approachingDistanceM = prefs[KEY_APPROACHING_DISTANCE] ?: 150,
                warningDistanceM = prefs[KEY_WARNING_DISTANCE] ?: 50,
                criticalDistanceM = prefs[KEY_CRITICAL_DISTANCE] ?: 20,
                alertCooldownMs = prefs[KEY_ALERT_COOLDOWN] ?: 5000L,
                speedGateKmh = prefs[KEY_SPEED_GATE_KMH] ?: 5,
                soundEnabled = prefs[KEY_SOUND_ENABLED] ?: true,
                soundVolume = prefs[KEY_SOUND_VOLUME] ?: 0.7f,
                soundSet = parseEnum(prefs[KEY_SOUND_SET], BuiltInSoundSet.CLASSIC),
                screenWakePolicy = parseEnum(prefs[KEY_SCREEN_WAKE_POLICY], ScreenWakePolicy.CRITICAL_ONLY),
                clearChimeEnabled = prefs[KEY_CLEAR_CHIME] ?: false
            )
        }
        .distinctUntilChanged()

    /**
     * Flow of alert settings.
     */
    val alertSettingsFlow: Flow<AlertSettings> = context.dataStore.data
        .map { prefs ->
            AlertSettings(
                globalEnabled = prefs[KEY_ALERTS_GLOBAL_ENABLED] ?: true,
                visualAlert = prefs[KEY_VISUAL_ALERT] ?: true,
                soundAlert = prefs[KEY_SOUND_ALERT] ?: true,
                hapticAlert = prefs[KEY_HAPTIC_ALERT] ?: false
            )
        }
        .distinctUntilChanged()

    /**
     * Flow indicating onboarding status.
     */
    val hasSeenOnboardingFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_HAS_SEEN_ONBOARDING] ?: false }
        .distinctUntilChanged()

    /**
     * Flow for showing stats after ride.
     */
    val showStatsAfterRideFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_SHOW_STATS_AFTER_RIDE] ?: true }
        .distinctUntilChanged()

    // === Update methods ===

    suspend fun updateSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOUND_ENABLED] = enabled
        }
    }

    suspend fun updateSoundVolume(volume: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOUND_VOLUME] = volume.coerceIn(0f, 1f)
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
            prefs[KEY_SOUND_VOLUME] = settings.soundVolume
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
            prefs[KEY_HAPTIC_ALERT] = alertSettings.hapticAlert
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
