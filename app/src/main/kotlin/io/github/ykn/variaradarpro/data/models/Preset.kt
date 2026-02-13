package io.github.ykn.variaradarpro.data.models

/**
 * App settings with sensible defaults.
 * Night mode is auto-detected and adjusts thresholds automatically.
 */
data class PresetSettings(
    // Distance thresholds (meters)
    // Radar detects up to ~140m. 100m default leaves a 40m "silent awareness"
    // zone where the widget updates but no alert fires — reduces alert fatigue.
    val approachingDistanceM: Int = 100,
    val warningDistanceM: Int = 50,
    val criticalDistanceM: Int = 20,

    // Throttling
    val alertCooldownMs: Long = 5000L,

    // Speed gate: suppress APPROACHING alerts below this speed (km/h).
    // WARNING and CRITICAL always fire regardless — stopped cyclists are vulnerable.
    // 0 = disabled (recommended).
    val speedGateKmh: Int = 0,

    // Sound
    val soundEnabled: Boolean = true,
    val soundSet: BuiltInSoundSet = BuiltInSoundSet.CLASSIC,

    // Visual
    val screenWakePolicy: ScreenWakePolicy = ScreenWakePolicy.CRITICAL_ONLY,

    // Clear chime: sound when all vehicles pass — confirms safe to maneuver.
    val clearChimeEnabled: Boolean = true
)

enum class BuiltInSoundSet {
    CLASSIC,
    SUBTLE,
    URGENT,
    BIKE_BELL
}

enum class ScreenWakePolicy {
    /** Never wake screen */
    NEVER,
    /** Only on critical threats */
    CRITICAL_ONLY,
    /** On any threat level */
    ALWAYS
}
