package io.github.aryeh95.radarcount.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.hammerhead.karooext.models.ViewConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Settings preferences")
class SettingsPreferencesTest {

    @Test
    @DisplayName("nothing stored reads as the defaults: holds off, headers on, no field sizes")
    fun emptyIsDefaults() {
        val s = SettingsPreferences.read(mutablePreferencesOf())
        assertThat(s).isEqualTo(Settings())
        assertThat(s.speedPassHold).isEqualTo(PassHoldSetting.OFF)
        assertThat(s.comboPassHold).isEqualTo(PassHoldSetting.OFF)
        assertThat(listOf(s.countHeader, s.speedHeader, s.distanceHeader, s.rateHeader)).containsExactly(true, true, true, true)
        assertThat(s.fieldSizes).isEmpty()
    }

    @Test
    @DisplayName("an unknown hold length reads as off")
    fun unknownHoldIsOff() {
        val p = mutablePreferencesOf(
            stringPreferencesKey("speed_pass_hold") to "S7",
            stringPreferencesKey("combo_pass_hold") to ""
        )
        val s = SettingsPreferences.read(p)
        assertThat(s.speedPassHold).isEqualTo(PassHoldSetting.OFF)
        assertThat(s.comboPassHold).isEqualTo(PassHoldSetting.OFF)
    }

    @Test
    @DisplayName("every hold length and header setting round-trips, each under its own key")
    fun roundTrip() {
        for (speed in PassHoldSetting.entries) for (combo in PassHoldSetting.entries) {
            val p = mutablePreferencesOf()
            val s = Settings(speedPassHold = speed, comboPassHold = combo)
            SettingsPreferences.write(p, s)
            assertThat(SettingsPreferences.read(p)).isEqualTo(s)
        }
        val headers = Settings(countHeader = false, speedHeader = true, distanceHeader = false, rateHeader = false, speedMode = SpeedSetting.RELATIVE, comboSpeedMode = SpeedSetting.ABSOLUTE)
        val p = mutablePreferencesOf()
        SettingsPreferences.write(p, headers)
        assertThat(SettingsPreferences.read(p)).isEqualTo(headers)
        assertThat(p[stringPreferencesKey("speed_pass_hold")]).isEqualTo("OFF")
    }

    @Test
    @DisplayName("each field's size is kept under its own type id")
    fun fieldSizesPerType() {
        val p = mutablePreferencesOf()
        val combo = ViewConfig(gridSize = 30 to 12, viewSize = 238 to 126, textSize = 46)
        val speed = ViewConfig(gridSize = 60 to 15, viewSize = 478 to 160, textSize = 69, alignment = ViewConfig.Alignment.CENTER)
        SettingsPreferences.writeFieldSize(p, "radar-combo", FieldSizes.encode(combo))
        SettingsPreferences.writeFieldSize(p, "approach-speed", FieldSizes.encode(speed))
        val s = SettingsPreferences.read(p)
        assertThat(s.fieldSizes.keys).containsExactly("radar-combo", "approach-speed")
        assertThat(FieldSizes.saved(s, "radar-combo")).isEqualTo(combo)
        assertThat(FieldSizes.saved(s, "approach-speed")).isEqualTo(speed)
        assertThat(FieldSizes.saved(s, "vehicle-count")).isNull()
    }

    @Test
    @DisplayName("Vehicle Speed shows absolute speed unless relative was chosen")
    fun speedDefaultsToAbsolute() {
        val fresh = SettingsPreferences.read(mutablePreferencesOf())
        assertThat(fresh.speedMode).isEqualTo(SpeedSetting.ABSOLUTE)
        assertThat(fresh.comboSpeedMode).isEqualTo(SpeedSetting.ABSOLUTE)
        assertThat(Settings().speedMode).isEqualTo(SpeedSetting.ABSOLUTE)
    }

    @Test
    @DisplayName("the one speed setting from before 0.4.0 seeds both fields, and is dropped once saved")
    fun legacySpeedSeedsBothFields() {
        val p = mutablePreferencesOf(stringPreferencesKey("speed") to "RELATIVE")
        val s = SettingsPreferences.read(p)
        assertThat(s.speedMode).isEqualTo(SpeedSetting.RELATIVE)
        assertThat(s.comboSpeedMode).isEqualTo(SpeedSetting.RELATIVE)
        SettingsPreferences.write(p, s.copy(comboSpeedMode = SpeedSetting.ABSOLUTE))
        assertThat(p[stringPreferencesKey("speed")]).isNull()
        val after = SettingsPreferences.read(p)
        assertThat(after.speedMode).isEqualTo(SpeedSetting.RELATIVE)
        assertThat(after.comboSpeedMode).isEqualTo(SpeedSetting.ABSOLUTE)
    }

    @Test
    @DisplayName("saving the settings never touches the field sizes, so an older snapshot cannot undo a reported size")
    fun writeLeavesFieldSizes() {
        val p = mutablePreferencesOf()
        val stale = SettingsPreferences.read(p)
        val size = ViewConfig(gridSize = 30 to 15, viewSize = 238 to 148, textSize = 50)
        SettingsPreferences.writeFieldSize(p, "closest-distance", FieldSizes.encode(size))
        SettingsPreferences.write(p, stale.copy(speedMode = SpeedSetting.RELATIVE))
        val s = SettingsPreferences.read(p)
        assertThat(s.speedMode).isEqualTo(SpeedSetting.RELATIVE)
        assertThat(FieldSizes.saved(s, "closest-distance")).isEqualTo(size)
    }

    @Test
    @DisplayName("the Radar field's size saved before every field had one still reads, until a new one replaces it")
    fun legacyComboSize() {
        val legacy = stringPreferencesKey("combo_field_size")
        val p = mutablePreferencesOf(legacy to "30,12,238,126,46")
        val old = FieldSizes.saved(SettingsPreferences.read(p), "radar-combo")
        assertThat(old).isEqualTo(ViewConfig(gridSize = 30 to 12, viewSize = 238 to 126, textSize = 46))
        val now = ViewConfig(gridSize = 30 to 15, viewSize = 238 to 148, textSize = 50, alignment = ViewConfig.Alignment.LEFT)
        SettingsPreferences.writeFieldSize(p, "radar-combo", FieldSizes.encode(now))
        assertThat(p[legacy]).isNull()
        assertThat(FieldSizes.saved(SettingsPreferences.read(p), "radar-combo")).isEqualTo(now)
    }

    @Test
    @DisplayName("restoring defaults clears every setting and keeps the field sizes")
    fun clearChoicesKeepsSizes() {
        val p = mutablePreferencesOf()
        SettingsPreferences.write(p, Settings(speedPassHold = PassHoldSetting.S10, countHeader = false, theme = ThemeSetting.DARK))
        val size = ViewConfig(gridSize = 30 to 12, viewSize = 238 to 126, textSize = 41)
        SettingsPreferences.writeFieldSize(p, "vehicle-count", FieldSizes.encode(size))
        SettingsPreferences.clearChoices(p)
        val s = SettingsPreferences.read(p)
        assertThat(s.copy(fieldSizes = emptyMap())).isEqualTo(Settings())
        assertThat(FieldSizes.saved(s, "vehicle-count")).isEqualTo(size)
    }
}
