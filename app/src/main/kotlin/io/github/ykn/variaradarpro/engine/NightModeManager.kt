package io.github.ykn.variaradarpro.engine

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages night mode detection using Karoo's built-in SUNRISE/SUNSET data.
 *
 * Karoo calculates sunrise/sunset based on GPS location automatically.
 * No need for manual NOAA calculations or LOCATION permission.
 *
 * The sunrise/sunset streams only emit when the values change, which may
 * be never during a ride, so a periodic ticker re-evaluates the current
 * time against the cached values.
 */
class NightModeManager(private val karooSystem: KarooSystemService) {

    companion object {
        private const val TAG = "NightModeManager"
        private const val RECHECK_INTERVAL_MS = 60_000L

        /** Pure rule so it can be unit tested. */
        internal fun isNight(currentMinutes: Int, sunriseMinutes: Int, sunsetMinutes: Int): Boolean {
            return currentMinutes < sunriseMinutes || currentMinutes > sunsetMinutes
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null

    private val _isNightMode = MutableStateFlow(false)
    val isNightMode: StateFlow<Boolean> = _isNightMode.asStateFlow()

    // Sunrise/sunset in minutes from midnight (from Karoo)
    @Volatile private var sunriseMinutes: Int? = null
    @Volatile private var sunsetMinutes: Int? = null

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
            (event.state as? StreamState.Streaming)?.dataPoint?.singleValue?.let {
                sunriseMinutes = it.toInt()
                updateNightMode()
            }
        })

        sunsetConsumerId.set(karooSystem.addConsumer(
            OnStreamState.StartStreaming(DataType.Type.SUNSET)
        ) { event: OnStreamState ->
            (event.state as? StreamState.Streaming)?.dataPoint?.singleValue?.let {
                sunsetMinutes = it.toInt()
                updateNightMode()
            }
        })

        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(RECHECK_INTERVAL_MS)
                updateNightMode()
            }
        }
    }

    private fun updateNightMode() {
        val sunrise = sunriseMinutes ?: return
        val sunset = sunsetMinutes ?: return

        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        val night = isNight(currentMinutes, sunrise, sunset)
        if (night != _isNightMode.value) {
            android.util.Log.i(TAG, "Night mode: $night (now=$currentMinutes, sunrise=$sunrise, sunset=$sunset)")
        }
        _isNightMode.value = night
    }

    /**
     * Stop monitoring and clean up consumers.
     */
    fun destroy() {
        android.util.Log.i(TAG, "Stopping night mode monitoring")
        tickerJob?.cancel()
        tickerJob = null
        sunriseConsumerId.getAndSet(null)?.let { karooSystem.removeConsumer(it) }
        sunsetConsumerId.getAndSet(null)?.let { karooSystem.removeConsumer(it) }
    }
}
