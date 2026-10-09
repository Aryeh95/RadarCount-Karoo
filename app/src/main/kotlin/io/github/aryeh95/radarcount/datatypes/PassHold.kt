package io.github.aryeh95.radarcount.datatypes

import io.github.aryeh95.radarcount.data.PassHoldSetting
import io.github.aryeh95.radarcount.engine.FitRecordWriter
import io.github.aryeh95.radarcount.engine.TargetTracker

/**
 * The fields' rule for showing a passed car's speed after it has gone by.
 * There is one pass record in the engine (TargetTracker.lastPass); each
 * field applies its own hold length to it when it draws, so two fields with
 * different lengths end independently and nothing per view is kept.
 */
object PassHold {

    /**
     * The pass to show at [nowMs], or null: the hold is on, the pass's speed
     * was measured, it was decided less than the hold length ago (a clock
     * that went backwards counts as expired), and no live car with a
     * measured speed is on the radar ([liveKnown]), which always wins.
     */
    fun held(pass: TargetTracker.Pass?, hold: PassHoldSetting, liveKnown: Boolean, nowMs: Long): TargetTracker.Pass? =
        pass?.takeIf { hold.seconds > 0 && !liveKnown && it.closingMps != null && nowMs - it.atMs in 0 until hold.seconds * 1000L }

    /**
     * The speed a held [pass] shows, in the rider's units: its closing
     * speed, plus for [absolute] the rider's speed at the pass (not now, so
     * braking after a close pass does not change it), added even when the
     * closing speed is 0, as the live value does.
     */
    fun shownSpeed(pass: TargetTracker.Pass, imperial: Boolean, absolute: Boolean): Int {
        val relative = FitRecordWriter.toUserSpeedUnits(pass.closingMps ?: 0.0, imperial)
        return if (absolute) relative + FitRecordWriter.toUserSpeedUnits(pass.riderMps, imperial) else relative
    }
}
