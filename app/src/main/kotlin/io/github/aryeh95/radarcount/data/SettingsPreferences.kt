package io.github.aryeh95.radarcount.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * How [Settings] map to DataStore preferences: every key, its default when
 * missing, and how it is written. Pure, so it is tested without a device.
 */
internal object SettingsPreferences {

    private val KEY_UNITS = stringPreferencesKey("units")
    private val KEY_THEME = stringPreferencesKey("theme")
    private val KEY_SENSITIVITY = stringPreferencesKey("sensitivity")
    /** The one speed setting both fields shared before 0.4.0; read only to seed the per-field ones. */
    private val KEY_LEGACY_SPEED = stringPreferencesKey("speed")
    private val KEY_SPEED_MODE = stringPreferencesKey("speed_mode")
    private val KEY_COMBO_SPEED_MODE = stringPreferencesKey("combo_speed_mode")
    private val KEY_SPEED_PASS_HOLD = stringPreferencesKey("speed_pass_hold")
    private val KEY_COUNT_HEADER = booleanPreferencesKey("count_header")
    private val KEY_SPEED_HEADER = booleanPreferencesKey("speed_header")
    private val KEY_DISTANCE_HEADER = booleanPreferencesKey("distance_header")
    private val KEY_RATE_HEADER = booleanPreferencesKey("rate_header")
    private val KEY_RESET_ON_RIDE_START = booleanPreferencesKey("reset_on_ride_start")
    private val KEY_COMBO_IDLE = stringPreferencesKey("combo_idle")
    private val KEY_COMBO_ACTIVE = stringPreferencesKey("combo_active")
    private val KEY_COMBO_BADGE = booleanPreferencesKey("combo_badge")
    private val KEY_COMBO_UNITS_IN_CAPTIONS = booleanPreferencesKey("combo_units_in_captions")
    private val KEY_COMBO_CAPTIONS = booleanPreferencesKey("combo_captions")
    private val KEY_COMBO_HEADER = booleanPreferencesKey("combo_header")
    private val KEY_COMBO_PASS_HOLD = stringPreferencesKey("combo_pass_hold")
    private val KEY_DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
    private val KEY_TRACE_TRACKS = booleanPreferencesKey("trace_tracks")
    private val KEY_DEBUG_FIELD_BOUNDS = booleanPreferencesKey("debug_field_bounds")

    /** Field sizes are kept one key per field type: "field_size_" and its type id. */
    private const val FIELD_SIZE_PREFIX = "field_size_"
    /** Where the Radar field's size was kept before every field had one. */
    private val KEY_LEGACY_COMBO_FIELD_SIZE = stringPreferencesKey("combo_field_size")
    private const val COMBO_TYPE_ID = "radar-combo"

    private fun fieldSizeKey(typeId: String) = stringPreferencesKey(FIELD_SIZE_PREFIX + typeId)

    fun read(p: Preferences): Settings = Settings(
        units = parseEnum(p[KEY_UNITS], UnitsSetting.AUTO),
        theme = parseEnum(p[KEY_THEME], ThemeSetting.AUTO),
        sensitivity = parseEnum(p[KEY_SENSITIVITY], SensitivitySetting.NORMAL),
        speedMode = parseEnum(p[KEY_SPEED_MODE] ?: p[KEY_LEGACY_SPEED], SpeedSetting.ABSOLUTE),
        comboSpeedMode = parseEnum(p[KEY_COMBO_SPEED_MODE] ?: p[KEY_LEGACY_SPEED], SpeedSetting.ABSOLUTE),
        speedPassHold = parseEnum(p[KEY_SPEED_PASS_HOLD], PassHoldSetting.OFF),
        countHeader = p[KEY_COUNT_HEADER] ?: true,
        speedHeader = p[KEY_SPEED_HEADER] ?: true,
        distanceHeader = p[KEY_DISTANCE_HEADER] ?: true,
        rateHeader = p[KEY_RATE_HEADER] ?: true,
        resetOnRideStart = p[KEY_RESET_ON_RIDE_START] ?: true,
        comboIdle = parseEnum(p[KEY_COMBO_IDLE], ComboIdleSetting.COUNT),
        comboActive = parseEnum(p[KEY_COMBO_ACTIVE], ComboActiveSetting.SPEED_DISTANCE),
        comboBadge = p[KEY_COMBO_BADGE] ?: true,
        comboUnitsInCaptions = p[KEY_COMBO_UNITS_IN_CAPTIONS] ?: true,
        comboCaptions = p[KEY_COMBO_CAPTIONS] ?: true,
        comboHeader = p[KEY_COMBO_HEADER] ?: true,
        comboPassHold = parseEnum(p[KEY_COMBO_PASS_HOLD], PassHoldSetting.OFF),
        developerMode = p[KEY_DEVELOPER_MODE] ?: false,
        traceTracks = p[KEY_TRACE_TRACKS] ?: false,
        debugFieldBounds = p[KEY_DEBUG_FIELD_BOUNDS] ?: false,
        fieldSizes = readFieldSizes(p)
    )

    private fun readFieldSizes(p: Preferences): Map<String, String> {
        val sizes = p.asMap().mapNotNull { (key, value) ->
            if (key.name.startsWith(FIELD_SIZE_PREFIX) && value is String) key.name.removePrefix(FIELD_SIZE_PREFIX) to value else null
        }.toMap()
        val legacy = p[KEY_LEGACY_COMBO_FIELD_SIZE]
        return if (legacy != null && COMBO_TYPE_ID !in sizes) sizes + (COMBO_TYPE_ID to legacy) else sizes
    }

    /**
     * Writes every setting the user chooses. Field sizes are left alone:
     * only [writeFieldSize] writes them, so a settings change made from an
     * older snapshot never puts back a size a field has since reported.
     */
    fun write(p: MutablePreferences, settings: Settings) {
        p[KEY_UNITS] = settings.units.name
        p[KEY_THEME] = settings.theme.name
        p[KEY_SENSITIVITY] = settings.sensitivity.name
        p[KEY_SPEED_MODE] = settings.speedMode.name
        p[KEY_COMBO_SPEED_MODE] = settings.comboSpeedMode.name
        p.remove(KEY_LEGACY_SPEED)
        p[KEY_SPEED_PASS_HOLD] = settings.speedPassHold.name
        p[KEY_COUNT_HEADER] = settings.countHeader
        p[KEY_SPEED_HEADER] = settings.speedHeader
        p[KEY_DISTANCE_HEADER] = settings.distanceHeader
        p[KEY_RATE_HEADER] = settings.rateHeader
        p[KEY_RESET_ON_RIDE_START] = settings.resetOnRideStart
        p[KEY_COMBO_IDLE] = settings.comboIdle.name
        p[KEY_COMBO_ACTIVE] = settings.comboActive.name
        p[KEY_COMBO_BADGE] = settings.comboBadge
        p[KEY_COMBO_UNITS_IN_CAPTIONS] = settings.comboUnitsInCaptions
        p[KEY_COMBO_CAPTIONS] = settings.comboCaptions
        p[KEY_COMBO_HEADER] = settings.comboHeader
        p[KEY_COMBO_PASS_HOLD] = settings.comboPassHold.name
        p[KEY_DEVELOPER_MODE] = settings.developerMode
        p[KEY_TRACE_TRACKS] = settings.traceTracks
        p[KEY_DEBUG_FIELD_BOUNDS] = settings.debugFieldBounds
    }

    /** Records the size field [typeId] was given, as [FieldSizes.encode] gives it. */
    fun writeFieldSize(p: MutablePreferences, typeId: String, encoded: String) {
        p[fieldSizeKey(typeId)] = encoded
        if (typeId == COMBO_TYPE_ID) p.remove(KEY_LEGACY_COMBO_FIELD_SIZE)
    }

    /** Removes every setting the user chooses, so each reads as its default, and keeps the field sizes. */
    fun clearChoices(p: MutablePreferences) {
        for (key in p.asMap().keys.toList()) {
            if (!key.name.startsWith(FIELD_SIZE_PREFIX) && key != KEY_LEGACY_COMBO_FIELD_SIZE) p.remove(key)
        }
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String?, default: T): T {
        return try {
            value?.let { enumValueOf<T>(it) } ?: default
        } catch (e: IllegalArgumentException) {
            default
        }
    }
}
