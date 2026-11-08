package com.nickorton.sholto.engine

/**
 * Settings for one round-timer session. All lengths are in milliseconds.
 *
 * Each round is a Work phase followed by a Warning phase. Work is not set
 * directly: it is always `roundLengthMs - warningLengthMs` (DESIGN.md §2.4),
 * so the two can never disagree. Rest comes between rounds, never after the
 * last one.
 *
 * Every length except [roundLengthMs] may be zero. A zero-length phase is
 * skipped.
 *
 * @property roundLengthMs Work plus Warning. Must be positive.
 * @property warningLengthMs The last part of each round. 0 to [roundLengthMs].
 * @property restLengthMs The break between rounds.
 * @property rounds How many rounds, or null to repeat until stopped.
 * @property getReadyLengthMs The countdown before round 1.
 */
data class RoundConfig(
    val roundLengthMs: Long = 3 * 60_000L,
    val warningLengthMs: Long = 30_000L,
    val restLengthMs: Long = 60_000L,
    val rounds: Int? = null,
    val getReadyLengthMs: Long = 10_000L,
) {
    init {
        require(roundLengthMs > 0) { "roundLengthMs must be positive, was $roundLengthMs" }
        require(warningLengthMs in 0..roundLengthMs) {
            "warningLengthMs must be in 0..$roundLengthMs, was $warningLengthMs"
        }
        require(restLengthMs >= 0) { "restLengthMs must not be negative, was $restLengthMs" }
        require(getReadyLengthMs >= 0) { "getReadyLengthMs must not be negative, was $getReadyLengthMs" }
        require(rounds == null || rounds >= 1) { "rounds must be at least 1, or null for unlimited, was $rounds" }
    }

    val workLengthMs: Long get() = roundLengthMs - warningLengthMs

    /**
     * Active time from start to Done, not counting pauses. Null when rounds
     * are unlimited.
     */
    val totalLengthMs: Long?
        get() = rounds?.let { n -> getReadyLengthMs + n * roundLengthMs + (n - 1) * restLengthMs }
}
