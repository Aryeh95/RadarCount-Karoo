package io.github.aryeh95.radarcount.engine

/**
 * One radar packet as the Karoo sent it: its threat level (0-3, anything
 * else read as 0) and the ranges of the targets that have one, in the
 * order the radar listed them.
 */
data class RadarPacket(val level: Int, val rangesM: List<Int>)

/** What the fields and the dashboard show about the radar. */
sealed interface RadarStatus {
    /** Not streaming, or streaming but the radar has not been heard from yet. */
    data object Off : RadarStatus

    /** The Karoo is looking for the radar. */
    data object Searching : RadarStatus

    /** The radar reported an error, or went away after it had been heard from. */
    data object Lost : RadarStatus

    /**
     * The radar is sending. Holds only what is shown, not the raw ranges,
     * so the fields redraw when the picture changes rather than on every
     * packet a car moves a few metres in.
     */
    data class Live(val level: Int, val vehicles: Int, val nearestM: Int) : RadarStatus {
        companion object {
            val CLEAR = Live(level = 0, vehicles = 0, nearestM = 0)

            /** What [packet] shows. A raised level with no range yet is still traffic: one vehicle, nearestM 0 for unknown. */
            fun of(packet: RadarPacket): Live = when {
                packet.rangesM.isNotEmpty() -> Live(packet.level, packet.rangesM.size, packet.rangesM.min())
                packet.level > 0 -> Live(packet.level, vehicles = 1, nearestM = 0)
                else -> CLEAR
            }
        }
    }

    /** This status while a vehicle is on the radar, else null. */
    val traffic: Live? get() = (this as? Live)?.takeIf { it.vehicles > 0 }
}
