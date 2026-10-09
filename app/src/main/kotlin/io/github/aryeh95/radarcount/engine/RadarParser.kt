package io.github.aryeh95.radarcount.engine

/**
 * Reads the Karoo RADAR data point's value map. The keys are passed in
 * so the tests can run without the Karoo SDK.
 */
class RadarParser(
    private val levelKey: String,
    private val errorKey: String,
    private val rangeKeys: List<String>
) {
    /** The radar's error code, or null when it reports none (missing or 0). */
    fun errorCode(values: Map<String, Double>): Int? =
        values[errorKey]?.takeIf { it > 0 }?.toInt()

    /** The packet in [values]; a range of 0 or less means that slot has no target. */
    fun parse(values: Map<String, Double>): RadarPacket {
        val level = values[levelKey]?.toInt()?.takeIf { it in 0..3 } ?: 0
        val ranges = rangeKeys.mapNotNull { key -> values[key]?.takeIf { it > 0 }?.toInt() }
        return RadarPacket(level, ranges)
    }
}
