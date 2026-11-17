package io.github.aryeh95.radarcount.engine

import android.os.SystemClock

/**
 * The time every timing in the extension runs on: milliseconds since boot, which only counts up.
 * The wall clock steps when the Karoo sets it from GPS or the network, often just after it wakes;
 * a step back froze the fields until the clock caught up, and a step forward mid-ride added itself
 * to the ride time. Pass and hold times share this clock, so they compare only with each other.
 */
object Clock {
    fun nowMs(): Long = SystemClock.elapsedRealtime()
}
