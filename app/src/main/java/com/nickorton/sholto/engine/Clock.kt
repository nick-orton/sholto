package com.nickorton.sholto.engine

import android.os.SystemClock

/**
 * Monotonic time source in milliseconds. It must never go backwards.
 *
 * The engine reads time only through this interface, so tests can drive it
 * with a fake clock.
 */
fun interface Clock {
    fun now(): Long
}

/**
 * The real clock: milliseconds since boot, counting deep sleep. It doesn't
 * jump when the user or network changes the wall-clock time.
 */
object ElapsedRealtimeClock : Clock {
    override fun now(): Long = SystemClock.elapsedRealtime()
}
