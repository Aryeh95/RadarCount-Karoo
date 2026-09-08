package io.github.aryeh95.radarcount.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "radarcount_settings")

/**
 * Settings backed by DataStore. Process-wide singleton because DataStore
 * does not allow two instances on one file.
 */
class SettingsRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        private val KEY_UNITS = stringPreferencesKey("units")
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_SENSITIVITY = stringPreferencesKey("sensitivity")
        private val KEY_SPEED = stringPreferencesKey("speed")
        private val KEY_RESET_ON_RIDE_START = booleanPreferencesKey("reset_on_ride_start")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: StateFlow<Settings> = context.dataStore.data
        .map { p ->
            Settings(
                units = parseEnum(p[KEY_UNITS], UnitsSetting.AUTO),
                theme = parseEnum(p[KEY_THEME], ThemeSetting.AUTO),
                sensitivity = parseEnum(p[KEY_SENSITIVITY], SensitivitySetting.NORMAL),
                speed = parseEnum(p[KEY_SPEED], SpeedSetting.RELATIVE),
                resetOnRideStart = p[KEY_RESET_ON_RIDE_START] ?: true
            )
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    suspend fun update(settings: Settings) {
        context.dataStore.edit { p ->
            p[KEY_UNITS] = settings.units.name
            p[KEY_THEME] = settings.theme.name
            p[KEY_SENSITIVITY] = settings.sensitivity.name
            p[KEY_SPEED] = settings.speed.name
            p[KEY_RESET_ON_RIDE_START] = settings.resetOnRideStart
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String?, default: T): T {
        return try {
            value?.let { enumValueOf<T>(it) } ?: default
        } catch (e: IllegalArgumentException) {
            default
        }
    }
}
