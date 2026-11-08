package com.nickorton.sholto.engine

/**
 * A [Clock] that only moves when a test moves it. Like the real clock, it
 * never goes backwards.
 */
class FakeClock(
    startMs: Long = 0L,
) : Clock {
    var nowMs: Long = startMs
        private set

    override fun now(): Long = nowMs

    fun advanceBy(deltaMs: Long) {
        require(deltaMs >= 0) { "A clock can't go backwards (deltaMs = $deltaMs)" }
        nowMs += deltaMs
    }

    fun advanceTo(timeMs: Long) {
        require(timeMs >= nowMs) { "A clock can't go backwards (from $nowMs to $timeMs)" }
        nowMs = timeMs
    }
}
