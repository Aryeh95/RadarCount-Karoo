package io.github.ykn.variaradarpro.ui.screens

import io.github.ykn.variaradarpro.data.models.BuiltInSoundSet
import io.github.ykn.variaradarpro.data.models.ScreenWakePolicy
import io.github.ykn.variaradarpro.engine.Units

internal object SettingsLogic {

    val APPROACHING_VALUES = listOf(100, 125, 150, 175, 200)
    val WARNING_VALUES = listOf(30, 40, 50, 60, 70)
    val CRITICAL_VALUES = listOf(10, 15, 20, 25, 30)
    val COOLDOWN_VALUES = listOf(3000L, 5000L, 8000L, 12000L)
    val SPEED_GATE_VALUES = listOf(0, 3, 5, 8)

    fun nextApproaching(current: Int): Int = cycleNext(APPROACHING_VALUES, current)
    fun nextWarning(current: Int): Int = cycleNext(WARNING_VALUES, current)
    fun nextCritical(current: Int): Int = cycleNext(CRITICAL_VALUES, current)
    fun nextCooldown(current: Long): Long = cycleNext(COOLDOWN_VALUES, current)
    fun nextSpeedGate(current: Int): Int = cycleNext(SPEED_GATE_VALUES, current)

    fun nextSound(current: BuiltInSoundSet): BuiltInSoundSet = when (current) {
        BuiltInSoundSet.CLASSIC -> BuiltInSoundSet.SUBTLE
        BuiltInSoundSet.SUBTLE -> BuiltInSoundSet.URGENT
        BuiltInSoundSet.URGENT -> BuiltInSoundSet.BIKE_BELL
        BuiltInSoundSet.BIKE_BELL -> BuiltInSoundSet.CLASSIC
    }

    fun nextScreenWake(policy: ScreenWakePolicy): ScreenWakePolicy = when (policy) {
        ScreenWakePolicy.NEVER -> ScreenWakePolicy.CRITICAL_ONLY
        ScreenWakePolicy.CRITICAL_ONLY -> ScreenWakePolicy.ALWAYS
        ScreenWakePolicy.ALWAYS -> ScreenWakePolicy.NEVER
    }

    fun formatDistance(meters: Int, useImperial: Boolean): String =
        Units.formatDistance(meters, useImperial)

    fun formatCooldown(ms: Long): String = "${ms / 1000}s"

    /** Returns null when the speed gate is off; caller supplies the localized "Off". */
    fun formatSpeedGate(kmh: Int, useImperial: Boolean): String? {
        if (kmh == 0) return null
        return Units.formatSpeed(kmh, useImperial)
    }

    private fun <T> cycleNext(values: List<T>, current: T): T {
        val idx = values.indexOf(current)
        return values[(idx + 1) % values.size]
    }
}
