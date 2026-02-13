package io.github.ykn.variaradarpro.data.models

/**
 * Sealed class representing all possible widget states.
 */
sealed class WidgetState {
    /** Radar not connected */
    data object NotConnected : WidgetState()

    /** Attempting to connect to radar */
    data object Connecting : WidgetState()

    /** Connected, no threats detected */
    data object Clear : WidgetState()

    /** Active threat detected */
    data class Threat(
        val level: ThreatLevel,
        val vehicleCount: Int,
        val nearestDistanceM: Int
    ) : WidgetState()

    /** Connection was established but lost */
    data object ConnectionLost : WidgetState()
}
