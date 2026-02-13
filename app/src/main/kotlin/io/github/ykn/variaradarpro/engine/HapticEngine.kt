package io.github.ykn.variaradarpro.engine

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.github.ykn.variaradarpro.data.models.ThreatLevel

/**
 * Haptic feedback engine for vibration alerts.
 *
 * Provides tactile feedback for threat levels,
 * useful when sound is disabled or in noisy environments.
 */
class HapticEngine(context: Context) {

    companion object {
        private const val TAG = "HapticEngine"

        // Vibration durations (milliseconds)
        private const val SHORT_PULSE = 50L
        private const val MEDIUM_PULSE = 100L
        private const val LONG_PULSE = 150L
        private const val PAUSE = 100L
        private const val SHORT_PAUSE = 50L
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var enabled: Boolean = true

    init {
        val hasVibrator = vibrator?.hasVibrator() == true
        android.util.Log.i(TAG, "HapticEngine initialized, vibrator available: $hasVibrator")
    }

    /**
     * Enable or disable haptic feedback.
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /**
     * Check if haptic feedback is available.
     */
    fun isAvailable(): Boolean = vibrator?.hasVibrator() == true

    /**
     * Vibrate for the given threat level.
     */
    fun vibrate(threatLevel: ThreatLevel) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) {
            return
        }

        if (threatLevel == ThreatLevel.CLEAR) {
            return
        }

        android.util.Log.d(TAG, "Vibrating for threat level: $threatLevel")

        val pattern = when (threatLevel) {
            ThreatLevel.APPROACHING -> getApproachingPattern()
            ThreatLevel.WARNING -> getWarningPattern()
            ThreatLevel.CRITICAL -> getCriticalPattern()
            ThreatLevel.CLEAR -> return
        }

        playPattern(pattern)
    }

    /**
     * Play a gentle "clear" vibration.
     */
    fun vibrateClear() {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) {
            return
        }

        android.util.Log.d(TAG, "Vibrating clear chime")
        playPattern(getClearPattern())
    }

    /**
     * Cancel any ongoing vibration.
     */
    fun cancel() {
        vibrator?.cancel()
    }

    // ==================== Vibration Patterns ====================

    /**
     * Single short pulse - gentle notification.
     */
    private fun getApproachingPattern(): LongArray {
        return longArrayOf(
            0,              // Start immediately
            MEDIUM_PULSE    // Single pulse
        )
    }

    /**
     * Double pulse - getting your attention.
     */
    private fun getWarningPattern(): LongArray {
        return longArrayOf(
            0,              // Start immediately
            MEDIUM_PULSE,   // First pulse
            PAUSE,          // Pause
            MEDIUM_PULSE    // Second pulse
        )
    }

    /**
     * Rapid triple pulse - urgent alert.
     */
    private fun getCriticalPattern(): LongArray {
        return longArrayOf(
            0,              // Start immediately
            LONG_PULSE,     // First pulse (longer)
            SHORT_PAUSE,    // Short pause
            MEDIUM_PULSE,   // Second pulse
            SHORT_PAUSE,    // Short pause
            MEDIUM_PULSE    // Third pulse
        )
    }

    /**
     * Soft double tap - road is clear.
     */
    private fun getClearPattern(): LongArray {
        return longArrayOf(
            0,              // Start immediately
            SHORT_PULSE,    // First soft tap
            PAUSE,          // Pause
            SHORT_PULSE     // Second soft tap
        )
    }

    private fun playPattern(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ - use VibrationEffect
                val amplitudes = IntArray(pattern.size) { index ->
                    if (index % 2 == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE
                }
                val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
                vibrator?.vibrate(effect)
            } else {
                // Legacy vibration
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to vibrate", e)
        }
    }
}
