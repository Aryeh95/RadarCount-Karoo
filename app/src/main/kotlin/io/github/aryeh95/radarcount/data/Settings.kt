package io.github.aryeh95.radarcount.data

enum class UnitsSetting { AUTO, METRIC, IMPERIAL }

enum class ThemeSetting { AUTO, LIGHT, DARK }

/** Which speed the Vehicle Speed field and the Radar combo show as the main value. */
enum class SpeedSetting { RELATIVE, ABSOLUTE }

/**
 * How eager the pass counter is: the distance a car must have come within
 * before it dropped off the radar, in metres. Ranges arrive in 3.125 m bins,
 * so the three options are one bin apart around the beam edge.
 */
enum class SensitivitySetting(val closeThresholdM: Int) {
    STRICT(6),
    NORMAL(9),
    RELAXED(12)
}

data class Settings(
    val units: UnitsSetting = UnitsSetting.AUTO,
    val theme: ThemeSetting = ThemeSetting.AUTO,
    val sensitivity: SensitivitySetting = SensitivitySetting.NORMAL,
    val speed: SpeedSetting = SpeedSetting.RELATIVE,
    /** Reset the ride count when a ride starts recording. */
    val resetOnRideStart: Boolean = true,
    /** Developer section shown in Settings; unlocked with five taps on the version line. */
    val developerMode: Boolean = false,
    /** Write a per-track decision log during rides. Developer setting; off by default. */
    val traceTracks: Boolean = false
)
