package com.nickorton.sholto.engine

import org.junit.Assert.assertEquals

/** An arbitrary non-zero clock reading, so nothing can assume time starts at 0. */
const val START_MS = 7_000_000L

/** Seconds to milliseconds, so tests read like the design doc: `150.s`. */
val Int.s: Long get() = this * 1_000L

/** A session driven by a [FakeClock], with times given relative to [START_MS]. */
class TestSession(
    config: RoundConfig = RoundConfig(),
) {
    val clock = FakeClock(START_MS)
    val engine = RoundEngine(config, clock)

    fun advanceTo(sinceStartMs: Long) = clock.advanceTo(START_MS + sinceStartMs)

    fun snapshotAt(sinceStartMs: Long): RoundState {
        advanceTo(sinceStartMs)
        return engine.snapshot()
    }

    fun pollAt(sinceStartMs: Long): List<PhaseChange> {
        advanceTo(sinceStartMs)
        return engine.pollEvents()
    }
}

fun assertPhase(
    state: RoundState,
    phase: Phase,
    round: Int,
    timeLeftMs: Long,
) {
    assertEquals("phase", phase, state.phase)
    assertEquals("round", round, state.round)
    assertEquals("time left", timeLeftMs, state.phaseTimeLeftMs)
}

/**
 * A second, deliberately simple model of the timeline to check the engine
 * against. It lists the phases one after another and adds up their lengths,
 * where the engine uses closed-form division.
 *
 * For unlimited configs it walks [roundsToWalk] rounds, each followed by rest.
 */
object ReferenceTimeline {
    data class Span(
        val phase: Phase,
        val round: Int,
        val startMs: Long,
        val endMs: Long,
    )

    /** Non-empty phases in order, ending with Done for a fixed round count. */
    fun spans(
        config: RoundConfig,
        roundsToWalk: Int = requireNotNull(config.rounds) { "Pass roundsToWalk for unlimited configs" },
    ): List<Span> {
        val lengths = mutableListOf(Triple(Phase.GetReady, 1, config.getReadyLengthMs))
        for (round in 1..roundsToWalk) {
            lengths += Triple(Phase.Work, round, config.roundLengthMs - config.warningLengthMs)
            lengths += Triple(Phase.Warning, round, config.warningLengthMs)
            if (round != config.rounds) lengths += Triple(Phase.Rest, round, config.restLengthMs)
        }
        val spans = mutableListOf<Span>()
        var t = 0L
        for ((phase, round, length) in lengths) {
            if (length == 0L) continue
            spans += Span(phase, round, t, t + length)
            t += length
        }
        config.rounds?.let { spans += Span(Phase.Done, it, t, Long.MAX_VALUE) }
        return spans
    }

    /** Every phase change, including the entry into the first phase at 0. */
    fun changes(
        config: RoundConfig,
        roundsToWalk: Int = requireNotNull(config.rounds) { "Pass roundsToWalk for unlimited configs" },
    ): List<PhaseChange> {
        var previous: Phase? = null
        return spans(config, roundsToWalk).map { span ->
            PhaseChange(previous, span.phase, span.round, span.startMs).also { previous = span.phase }
        }
    }
}
