package com.nickorton.sholto.engine

/**
 * The stretch of active session time one phase covers: from [startMs]
 * (inclusive) to [endMs] (exclusive). Done never ends, so its [endMs] is
 * [Long.MAX_VALUE].
 */
internal data class PhaseSpan(
    val phase: Phase,
    val round: Int,
    val startMs: Long,
    val endMs: Long,
) {
    val lengthMs: Long get() = if (phase == Phase.Done) 0L else endMs - startMs
}

/**
 * Maps active session time (time since start, minus pauses) to a phase. This
 * is pure math on a [RoundConfig], with no clock and no state.
 *
 * Each phase covers a half-open range `[start, end)`. At a boundary the next
 * phase has already begun, and a zero-length phase covers no time, so it is
 * never returned.
 */
internal class RoundTimeline(
    private val config: RoundConfig,
) {
    // Round k starts every cycle after the get-ready countdown. The last round
    // has no rest, but nothing after it is a round, so the cycle still holds.
    private val cycleMs = config.roundLengthMs + config.restLengthMs

    fun roundStartMs(round: Int): Long = config.getReadyLengthMs + (round - 1) * cycleMs

    fun spanAt(elapsedMs: Long): PhaseSpan {
        require(elapsedMs >= 0) { "elapsedMs must not be negative, was $elapsedMs" }

        val lastRound = config.rounds
        if (lastRound != null) {
            val doneAtMs = roundStartMs(lastRound) + config.roundLengthMs
            if (elapsedMs >= doneAtMs) return PhaseSpan(Phase.Done, lastRound, doneAtMs, Long.MAX_VALUE)
        }

        val getReadyMs = config.getReadyLengthMs
        if (elapsedMs < getReadyMs) return PhaseSpan(Phase.GetReady, 1, 0L, getReadyMs)

        val round = ((elapsedMs - getReadyMs) / cycleMs + 1).toInt()
        val roundStart = roundStartMs(round)
        val workEnd = roundStart + config.workLengthMs
        val roundEnd = roundStart + config.roundLengthMs
        return when {
            elapsedMs < workEnd -> PhaseSpan(Phase.Work, round, roundStart, workEnd)
            elapsedMs < roundEnd -> PhaseSpan(Phase.Warning, round, workEnd, roundEnd)
            else -> PhaseSpan(Phase.Rest, round, roundEnd, roundEnd + config.restLengthMs)
        }
    }

    /**
     * The phase changes at boundaries after [afterMs], up to and including
     * [throughMs], in order. The first phase's entry at time 0 is not a
     * boundary, so it is never included.
     */
    fun changesBetween(
        afterMs: Long,
        throughMs: Long,
    ): List<PhaseChange> {
        val changes = mutableListOf<PhaseChange>()
        var span = spanAt(afterMs)
        // Every span ends after it starts, so this always moves forward.
        while (span.endMs <= throughMs) {
            val next = spanAt(span.endMs)
            changes += PhaseChange(from = span.phase, to = next.phase, round = next.round, atElapsedMs = span.endMs)
            span = next
        }
        return changes
    }
}
