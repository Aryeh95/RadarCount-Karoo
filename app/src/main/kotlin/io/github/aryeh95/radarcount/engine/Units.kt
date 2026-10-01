package io.github.aryeh95.radarcount.engine

import kotlin.math.roundToInt

/**
 * Single place for metric/imperial conversion and formatting.
 */
object Units {
    private const val FEET_PER_METER = 3.28084
    private const val MPH_PER_KMH = 0.621371

    fun metersToFeet(meters: Int): Int = (meters * FEET_PER_METER).roundToInt()

    fun kmhToMph(kmh: Int): Int = (kmh * MPH_PER_KMH).roundToInt()

    /** e.g. "45m" or "148ft". */
    fun formatDistance(meters: Int, useImperial: Boolean): String {
        return if (useImperial) "${metersToFeet(meters)}ft" else "${meters}m"
    }

    fun speedUnitLabel(useImperial: Boolean): String = if (useImperial) "mph" else "km/h"

    /** Unit as a field caption: MPH or KPH. */
    fun speedUnitCaption(useImperial: Boolean): String = if (useImperial) "MPH" else "KPH"

    /** Unit as a field caption: FT or M. */
    fun distanceUnitCaption(useImperial: Boolean): String = if (useImperial) "FT" else "M"

    /** Distance in the user's unit, digits only. */
    fun distanceValue(meters: Int, useImperial: Boolean): Int = if (useImperial) metersToFeet(meters) else meters

    /** e.g. "5 km/h" or "3 mph". */
    fun formatSpeed(kmh: Int, useImperial: Boolean): String {
        return if (useImperial) "${kmhToMph(kmh)} mph" else "$kmh km/h"
    }
}
