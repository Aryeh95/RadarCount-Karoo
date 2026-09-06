package io.github.aryeh95.radarcount.engine

import io.github.aryeh95.radarcount.data.models.ThreatLevel

/**
 * One parsed radar packet.
 *
 * [targetDistancesM] contains only targets with a valid (> 0) range,
 * in the order the radar reported them.
 */
data class RadarSnapshot(
    val threatLevel: ThreatLevel,
    val targetDistancesM: List<Int>
) {
    val vehicleCount: Int get() = targetDistancesM.size
    val nearestDistanceM: Int get() = targetDistancesM.minOrNull() ?: 0
}

/** Result of parsing a raw radar data point. */
sealed class RadarParseResult {
    data class Data(val snapshot: RadarSnapshot) : RadarParseResult()
    data class Error(val code: Int) : RadarParseResult()
}

/**
 * Pure parser for the Karoo RADAR data type value map.
 *
 * Field keys are injected so the parser can be unit tested without the
 * Karoo SDK on the classpath.
 */
class RadarParser(
    private val threatLevelField: String,
    private val errorField: String,
    private val targetRangeFields: List<String>
) {

    companion object {
        fun mapThreatLevel(karooLevel: Int): ThreatLevel = when (karooLevel) {
            0 -> ThreatLevel.CLEAR
            1 -> ThreatLevel.APPROACHING
            2 -> ThreatLevel.WARNING
            3 -> ThreatLevel.CRITICAL
            else -> ThreatLevel.CLEAR
        }
    }

    fun parse(values: Map<String, Double>): RadarParseResult {
        val error = values[errorField]
        if (error != null && error > 0) {
            return RadarParseResult.Error(error.toInt())
        }

        val threat = mapThreatLevel(values[threatLevelField]?.toInt() ?: 0)

        val distances = ArrayList<Int>(targetRangeFields.size)
        for (field in targetRangeFields) {
            val range = values[field] ?: continue
            if (range > 0) {
                distances.add(range.toInt())
            }
        }

        return RadarParseResult.Data(RadarSnapshot(threat, distances))
    }
}
