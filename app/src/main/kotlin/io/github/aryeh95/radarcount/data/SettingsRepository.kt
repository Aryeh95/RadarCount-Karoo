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
        private val KEY_COMBO_IDLE = stringPreferencesKey("combo_idle")
        private val KEY_COMBO_ACTIVE = stringPreferencesKey("combo_active")
        private val KEY_COMBO_BADGE = booleanPreferencesKey("combo_badge")
        private val KEY_COMBO_UNITS_IN_CAPTIONS = booleanPreferencesKey("combo_units_in_captions")
        private val KEY_COMBO_CAPTIONS = booleanPreferencesKey("combo_captions")
        private val KEY_COMBO_HEADER = booleanPreferencesKey("combo_header")
        private val KEY_COMBO_FIELD_SIZE = stringPreferencesKey("combo_field_size")
        private val KEY_DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        private val KEY_TRACE_TRACKS = booleanPreferencesKey("trace_tracks")
        private val KEY_DEBUG_FIELD_BOUNDS = booleanPreferencesKey("debug_field_bounds")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: StateFlow<Settings> = context.dataStore.data
        .map { p ->
            Settings(
                units = parseEnum(p[KEY_UNITS], UnitsSetting.AUTO),
                theme = parseEnum(p[KEY_THEME], ThemeSetting.AUTO),
                sensitivity = parseEnum(p[KEY_SENSITIVITY], SensitivitySetting.NORMAL),
                speed = parseEnum(p[KEY_SPEED], SpeedSetting.RELATIVE),
                resetOnRideStart = p[KEY_RESET_ON_RIDE_START] ?: true,
                comboIdle = parseEnum(p[KEY_COMBO_IDLE], ComboIdleSetting.COUNT),
                comboActive = parseEnum(p[KEY_COMBO_ACTIVE], ComboActiveSetting.SPEED_DISTANCE),
                comboBadge = p[KEY_COMBO_BADGE] ?: true,
                comboUnitsInCaptions = p[KEY_COMBO_UNITS_IN_CAPTIONS] ?: true,
                comboCaptions = p[KEY_COMBO_CAPTIONS] ?: true,
                comboHeader = p[KEY_COMBO_HEADER] ?: true,
                developerMode = p[KEY_DEVELOPER_MODE] ?: false,
                traceTracks = p[KEY_TRACE_TRACKS] ?: false,
                debugFieldBounds = p[KEY_DEBUG_FIELD_BOUNDS] ?: false,
                comboFieldSize = p[KEY_COMBO_FIELD_SIZE] ?: ""
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
            p[KEY_COMBO_IDLE] = settings.comboIdle.name
            p[KEY_COMBO_ACTIVE] = settings.comboActive.name
            p[KEY_COMBO_BADGE] = settings.comboBadge
            p[KEY_COMBO_UNITS_IN_CAPTIONS] = settings.comboUnitsInCaptions
            p[KEY_COMBO_CAPTIONS] = settings.comboCaptions
            p[KEY_COMBO_HEADER] = settings.comboHeader
            p[KEY_DEVELOPER_MODE] = settings.developerMode
            p[KEY_TRACE_TRACKS] = settings.traceTracks
            p[KEY_DEBUG_FIELD_BOUNDS] = settings.debugFieldBounds
            p[KEY_COMBO_FIELD_SIZE] = settings.comboFieldSize
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
