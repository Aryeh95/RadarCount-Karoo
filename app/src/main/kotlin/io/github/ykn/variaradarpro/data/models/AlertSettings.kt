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
    val soundAlert: Boolean = true
)
