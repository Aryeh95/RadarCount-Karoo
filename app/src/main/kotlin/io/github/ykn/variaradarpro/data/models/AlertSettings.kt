package io.github.ykn.variaradarpro.data.models

import androidx.compose.runtime.Immutable

/**
 * Alert channel settings.
 */
@Immutable
data class AlertSettings(
    /** Master switch for all alerts */
    val globalEnabled: Boolean = true,

    /** Visual alerts (InRideAlert) */
    val visualAlert: Boolean = true,

    /** Sound alerts (PlayBeepPattern) */
    val soundAlert: Boolean = true,

    /** Haptic alerts (Vibration) — most reliable for cycling (wind can't drown it out) */
    val hapticAlert: Boolean = true
)
