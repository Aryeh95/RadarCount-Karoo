package io.github.aryeh95.radarcount.data

enum class UnitsSetting { AUTO, METRIC, IMPERIAL }

enum class ThemeSetting { AUTO, LIGHT, DARK }

/** Which speed a field shows: absolute is the vehicle's road speed, relative how fast it closes on the rider. */
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

/** What the Radar combo field shows while nothing is on the radar. */
enum class ComboIdleSetting { COUNT, ALL }

/** What the Radar combo field shows while a vehicle is on the radar. */
enum class ComboActiveSetting { SPEED_DISTANCE, ALL }

/**
 * How long a field keeps showing a passed car's speed, greyed and marked
 * PASSED, after the car has gone by. Nothing below 3 s: the fields update
 * once a second, so a shorter hold would barely show.
 */
enum class PassHoldSetting(val seconds: Int) {
    OFF(0),
    S3(3),
    S5(5),
    S10(10)
}

data class Settings(
    val units: UnitsSetting = UnitsSetting.AUTO,
    val theme: ThemeSetting = ThemeSetting.AUTO,
    val sensitivity: SensitivitySetting = SensitivitySetting.NORMAL,
    /** Which speed the Vehicle Speed field shows; relative is named in its header (VEHICLE REL SPEED). */
    val speedMode: SpeedSetting = SpeedSetting.ABSOLUTE,
    /** How long the Vehicle Speed field keeps a passed car's speed. */
    val speedPassHold: PassHoldSetting = PassHoldSetting.OFF,
    /** The single fields' own header (icon and name). Off gives the value the whole tile. */
    val countHeader: Boolean = true,
    val speedHeader: Boolean = true,
    val distanceHeader: Boolean = true,
    val rateHeader: Boolean = true,
    /** Reset the ride count when a ride starts recording. */
    val resetOnRideStart: Boolean = true,
    /** Radar combo field layout while nothing is on the radar. */
    val comboIdle: ComboIdleSetting = ComboIdleSetting.COUNT,
    /** Radar combo field layout while a vehicle is on the radar. */
    val comboActive: ComboActiveSetting = ComboActiveSetting.SPEED_DISTANCE,
    /** Show the count small in the corner while speed and distance fill the combo field. */
    val comboBadge: Boolean = true,
    /** Units as captions (MPH, FT) above the digits instead of glued to them (34mph). */
    val comboUnitsInCaptions: Boolean = true,
    /** Caption line above the digits in the count-only and speed-and-distance layouts. Off gives the digits the full height. */
    val comboCaptions: Boolean = true,
    /** Draw the RADAR header strip in the combo field. Off gives the numbers the whole tile. */
    val comboHeader: Boolean = true,
    /** How long the Radar combo field keeps a passed car's speed. */
    val comboPassHold: PassHoldSetting = PassHoldSetting.OFF,
    /** Which speed the Radar field shows. */
    val comboSpeedMode: SpeedSetting = SpeedSetting.ABSOLUTE,
    /** Developer section shown in Settings; unlocked with five taps on the version line. */
    val developerMode: Boolean = false,
    /** Write a per-track decision log during rides. Developer setting; off by default. */
    val traceTracks: Boolean = false,
    /** Tint the Radar field's view so its real bounds show. Developer setting. */
    val debugFieldBounds: Boolean = false,
    /**
     * Last size the Karoo gave each field in a ride, by type id, as
     * [FieldSizes] encodes it, so the settings previews are right after a
     * restart. Written only by SettingsRepository.saveFieldSize.
     */
    val fieldSizes: Map<String, String> = emptyMap()
)
