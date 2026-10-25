package io.github.aryeh95.radarcount.engine

import kotlin.math.roundToInt

/**
 * Single place for metric/imperial conversion and formatting.
 */
object Units {
    private const val FEET_PER_METER = 3.28084

    fun metersToFeet(meters: Int): Int = (meters * FEET_PER_METER).roundToInt()

    /** Distance with its unit and no space, as the fields show it: 45m or 148ft. */
    fun distanceLabel(meters: Int, imperial: Boolean): String =
        distanceValue(meters, imperial).toString() + if (imperial) "ft" else "m"

    fun speedUnitLabel(useImperial: Boolean): String = if (useImperial) "mph" else "km/h"

    /** Unit as a field caption: MPH or KPH. */
    fun speedUnitCaption(useImperial: Boolean): String = if (useImperial) "MPH" else "KPH"

    /** Unit as a field caption: FT or M. */
    fun distanceUnitCaption(useImperial: Boolean): String = if (useImperial) "FT" else "M"

    /** Distance in the user's unit, digits only. */
    fun distanceValue(meters: Int, useImperial: Boolean): Int = if (useImperial) metersToFeet(meters) else meters
}
