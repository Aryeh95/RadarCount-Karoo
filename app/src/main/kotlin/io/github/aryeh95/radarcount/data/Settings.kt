package io.github.aryeh95.radarcount.data

enum class UnitsSetting { AUTO, METRIC, IMPERIAL }

enum class ThemeSetting { AUTO, LIGHT, DARK }

/** Which speed the Vehicle Speed field and the Radar combo show as the main value. */
enum class SpeedSetting { RELATIVE, ABSOLUTE }

/**
 * How eager the pass counter is. Thresholds are the "came within" distance
 * and the "was closing and last seen within" distance, in metres.
 */
enum class SensitivitySetting(val closeThresholdM: Int, val closingThresholdM: Int) {
    STRICT(12, 40),
    NORMAL(20, 60),
    RELAXED(30, 90)
}

data class Settings(
    val units: UnitsSetting = UnitsSetting.AUTO,
    val theme: ThemeSetting = ThemeSetting.AUTO,
    val sensitivity: SensitivitySetting = SensitivitySetting.NORMAL,
    val speed: SpeedSetting = SpeedSetting.RELATIVE,
    /** Reset the ride count when a ride starts recording. */
    val resetOnRideStart: Boolean = true,
    /** Reset the lap count on each Karoo lap. */
    val resetLapOnLap: Boolean = true
)
