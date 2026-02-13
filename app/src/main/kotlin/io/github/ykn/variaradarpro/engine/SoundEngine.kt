package io.github.ykn.variaradarpro.engine

import io.github.ykn.variaradarpro.data.models.BuiltInSoundSet
import io.github.ykn.variaradarpro.data.models.ThreatLevel
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.PlayBeepPattern

/**
 * Sound engine for playing alert beeps.
 *
 * Supports multiple sound sets with different tone patterns
 * for each threat level.
 */
class SoundEngine(private val karooSystem: KarooSystemService) {

    companion object {
        private const val TAG = "SoundEngine"

        // Tone durations (milliseconds)
        private const val SHORT_TONE = 100
        private const val MEDIUM_TONE = 150
        private const val LONG_TONE = 200
        private const val PAUSE = 50
        private const val LONG_PAUSE = 100
    }

    private var currentSoundSet: BuiltInSoundSet = BuiltInSoundSet.CLASSIC
    private var volume: Float = 0.7f

    /**
     * Set the active sound set.
     */
    fun setSoundSet(soundSet: BuiltInSoundSet) {
        currentSoundSet = soundSet
        android.util.Log.d(TAG, "Sound set changed to: $soundSet")
    }

    /**
     * Set the volume level (0.0 to 1.0).
     * Note: Karoo doesn't support volume control via SDK,
     * this is for future compatibility.
     */
    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
    }

    /**
     * Play alert sound for the given threat level.
     */
    fun playAlert(threatLevel: ThreatLevel) {
        if (threatLevel == ThreatLevel.CLEAR) {
            return
        }

        val tones = when (currentSoundSet) {
            BuiltInSoundSet.CLASSIC -> getClassicTones(threatLevel)
            BuiltInSoundSet.SUBTLE -> getSubtleTones(threatLevel)
            BuiltInSoundSet.URGENT -> getUrgentTones(threatLevel)
            BuiltInSoundSet.BIKE_BELL -> getBikeBellTones(threatLevel)
        }

        android.util.Log.d(TAG, "Playing $currentSoundSet alert for $threatLevel")
        karooSystem.dispatch(PlayBeepPattern(tones))
    }

    /**
     * Play the "all clear" chime.
     */
    fun playClearChime() {
        val tones = when (currentSoundSet) {
            BuiltInSoundSet.CLASSIC -> listOf(
                PlayBeepPattern.Tone(500, SHORT_TONE),
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(600, SHORT_TONE)
            )
            BuiltInSoundSet.SUBTLE -> listOf(
                PlayBeepPattern.Tone(400, SHORT_TONE)
            )
            BuiltInSoundSet.URGENT -> listOf(
                PlayBeepPattern.Tone(600, SHORT_TONE),
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(800, SHORT_TONE)
            )
            BuiltInSoundSet.BIKE_BELL -> listOf(
                PlayBeepPattern.Tone(1200, MEDIUM_TONE)
            )
        }

        android.util.Log.d(TAG, "Playing clear chime")
        karooSystem.dispatch(PlayBeepPattern(tones))
    }

    // ==================== Sound Sets ====================

    /**
     * Classic - Traditional beep patterns.
     * Clear, distinct tones that are easy to identify.
     */
    private fun getClassicTones(level: ThreatLevel): List<PlayBeepPattern.Tone> {
        return when (level) {
            ThreatLevel.APPROACHING -> listOf(
                PlayBeepPattern.Tone(600, MEDIUM_TONE)
            )
            ThreatLevel.WARNING -> listOf(
                PlayBeepPattern.Tone(800, MEDIUM_TONE),
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(800, MEDIUM_TONE)
            )
            ThreatLevel.CRITICAL -> listOf(
                PlayBeepPattern.Tone(1000, SHORT_TONE),
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(1000, SHORT_TONE),
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(1000, SHORT_TONE)
            )
            ThreatLevel.CLEAR -> emptyList()
        }
    }

    /**
     * Subtle - Quiet, gentle tones.
     * Less intrusive for urban riding.
     */
    private fun getSubtleTones(level: ThreatLevel): List<PlayBeepPattern.Tone> {
        return when (level) {
            ThreatLevel.APPROACHING -> listOf(
                PlayBeepPattern.Tone(400, SHORT_TONE)
            )
            ThreatLevel.WARNING -> listOf(
                PlayBeepPattern.Tone(500, SHORT_TONE),
                PlayBeepPattern.Tone(null, LONG_PAUSE),
                PlayBeepPattern.Tone(500, SHORT_TONE)
            )
            ThreatLevel.CRITICAL -> listOf(
                PlayBeepPattern.Tone(600, MEDIUM_TONE),
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(700, MEDIUM_TONE)
            )
            ThreatLevel.CLEAR -> emptyList()
        }
    }

    /**
     * Urgent - Loud, attention-grabbing tones.
     * For noisy environments or important alerts.
     */
    private fun getUrgentTones(level: ThreatLevel): List<PlayBeepPattern.Tone> {
        return when (level) {
            ThreatLevel.APPROACHING -> listOf(
                PlayBeepPattern.Tone(800, MEDIUM_TONE),
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(800, MEDIUM_TONE)
            )
            ThreatLevel.WARNING -> listOf(
                PlayBeepPattern.Tone(1000, MEDIUM_TONE),
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(1000, MEDIUM_TONE),
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(1000, MEDIUM_TONE)
            )
            ThreatLevel.CRITICAL -> listOf(
                PlayBeepPattern.Tone(1200, SHORT_TONE),
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(1200, SHORT_TONE),
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(1200, SHORT_TONE),
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(1200, SHORT_TONE),
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(1200, SHORT_TONE)
            )
            ThreatLevel.CLEAR -> emptyList()
        }
    }

    /**
     * Bike Bell - Bell-like tones.
     * Friendly, natural sounds that blend with cycling.
     */
    private fun getBikeBellTones(level: ThreatLevel): List<PlayBeepPattern.Tone> {
        return when (level) {
            ThreatLevel.APPROACHING -> listOf(
                PlayBeepPattern.Tone(1047, LONG_TONE) // C6 note
            )
            ThreatLevel.WARNING -> listOf(
                PlayBeepPattern.Tone(1319, MEDIUM_TONE), // E6 note
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(1047, MEDIUM_TONE)  // C6 note
            )
            ThreatLevel.CRITICAL -> listOf(
                PlayBeepPattern.Tone(1568, SHORT_TONE),  // G6 note
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(1319, SHORT_TONE),  // E6 note
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(1568, SHORT_TONE),  // G6 note
                PlayBeepPattern.Tone(null, PAUSE),
                PlayBeepPattern.Tone(1319, SHORT_TONE)   // E6 note
            )
            ThreatLevel.CLEAR -> emptyList()
        }
    }
}
