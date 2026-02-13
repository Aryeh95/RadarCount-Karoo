package io.github.ykn.variaradarpro.engine

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages night mode detection using Karoo's built-in SUNRISE/SUNSET data.
 *
 * Karoo calculates sunrise/sunset based on GPS location automatically.
 * No need for manual NOAA calculations or LOCATION permission.
 */
class NightModeManager(private val karooSystem: KarooSystemService) {

    companion object {
        private const val TAG = "NightModeManager"
    }

    private val _isNightMode = MutableStateFlow(false)
    val isNightMode: StateFlow<Boolean> = _isNightMode.asStateFlow()

    // Sunrise/sunset in minutes from midnight (from Karoo)
    private var sunriseMinutes: Int? = null
    private var sunsetMinutes: Int? = null

    // Consumer IDs for cleanup
    private val sunriseConsumerId = AtomicReference<String?>(null)
    private val sunsetConsumerId = AtomicReference<String?>(null)

    /**
     * Start listening to sunrise/sunset data from Karoo.
     */
    fun startMonitoring() {
        android.util.Log.i(TAG, "Starting night mode monitoring via Karoo SUNRISE/SUNSET")

        sunriseConsumerId.set(karooSystem.addConsumer(
            OnStreamState.StartStreaming(DataType.Type.SUNRISE)
        ) { event: OnStreamState ->
            if (event.state is StreamState.Streaming) {
                val value = (event.state as StreamState.Streaming).dataPoint.singleValue
                if (value != null) {
                    sunriseMinutes = value.toInt()
                    updateNightMode()
                }
            }
        })

        sunsetConsumerId.set(karooSystem.addConsumer(
            OnStreamState.StartStreaming(DataType.Type.SUNSET)
        ) { event: OnStreamState ->
            if (event.state is StreamState.Streaming) {
                val value = (event.state as StreamState.Streaming).dataPoint.singleValue
                if (value != null) {
                    sunsetMinutes = value.toInt()
                    updateNightMode()
                }
            }
        })
    }

    private fun updateNightMode() {
        val sunrise = sunriseMinutes ?: return
        val sunset = sunsetMinutes ?: return

        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        _isNightMode.value = currentMinutes < sunrise || currentMinutes > sunset
        android.util.Log.d(TAG, "Night mode: ${_isNightMode.value} (now=$currentMinutes, sunrise=$sunrise, sunset=$sunset)")
    }

    /**
     * Stop monitoring and clean up consumers.
     */
    fun destroy() {
        android.util.Log.i(TAG, "Stopping night mode monitoring")
        sunriseConsumerId.getAndSet(null)?.let { karooSystem.removeConsumer(it) }
        sunsetConsumerId.getAndSet(null)?.let { karooSystem.removeConsumer(it) }
    }
}
