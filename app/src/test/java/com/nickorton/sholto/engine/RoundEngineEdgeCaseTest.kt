package com.nickorton.sholto.engine

import com.nickorton.sholto.engine.Phase.Done
import com.nickorton.sholto.engine.Phase.GetReady
import com.nickorton.sholto.engine.Phase.Rest
import com.nickorton.sholto.engine.Phase.Warning
import com.nickorton.sholto.engine.Phase.Work
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundEngineEdgeCaseTest {
    @Test
    fun warningZeroGoesStraightFromWorkToRest() {
        val config = RoundConfig(warningLengthMs = 0L, rounds = 2)

        assertEquals(
            listOf(
                PhaseChange(null, GetReady, 1, 0L),
                PhaseChange(GetReady, Work, 1, 10.s),
                PhaseChange(Work, Rest, 1, 190.s),
                PhaseChange(Rest, Work, 2, 250.s),
                PhaseChange(Work, Done, 2, 430.s),
            ),
            runToEnd(config),
        )

        val session = startedSession(config)
        session.snapshotAt(10.s).let {
            assertPhase(it, Work, 1, 180.s)
            assertEquals(180.s, it.phaseLengthMs)
        }
        assertPhase(session.snapshotAt(190.s - 1), Work, 1, 1L)
        assertPhase(session.snapshotAt(190.s), Rest, 1, 60.s)
    }

    @Test
    fun restZeroGoesStraightIntoTheNextRound() {
        val config = RoundConfig(restLengthMs = 0L, rounds = 3)

        assertEquals(
            listOf(
                PhaseChange(null, GetReady, 1, 0L),
                PhaseChange(GetReady, Work, 1, 10.s),
                PhaseChange(Work, Warning, 1, 160.s),
                PhaseChange(Warning, Work, 2, 190.s),
                PhaseChange(Work, Warning, 2, 340.s),
                PhaseChange(Warning, Work, 3, 370.s),
                PhaseChange(Work, Warning, 3, 520.s),
                PhaseChange(Warning, Done, 3, 550.s),
            ),
            runToEnd(config),
        )

        val session = startedSession(config)
        assertPhase(session.snapshotAt(190.s - 1), Warning, 1, 1L)
        session.snapshotAt(190.s).let {
            assertPhase(it, Work, 2, 150.s)
            assertEquals(1, it.roundsCompleted)
        }
    }

    @Test
    fun getReadyZeroStartsInRoundOne() {
        val config = RoundConfig(getReadyLengthMs = 0L, rounds = 1)

        assertEquals(
            listOf(
                PhaseChange(null, Work, 1, 0L),
                PhaseChange(Work, Warning, 1, 150.s),
                PhaseChange(Warning, Done, 1, 180.s),
            ),
            runToEnd(config),
        )

        val session = TestSession(config)
        assertPhase(session.engine.snapshot(), Work, 1, 150.s)
        session.engine.start()
        assertEquals(listOf(PhaseChange(null, Work, 1, 0L)), session.engine.pollEvents())
    }

    @Test
    fun warningAsLongAsTheRoundSkipsWork() {
        val config = RoundConfig(warningLengthMs = 180.s, rounds = 2)

        assertEquals(
            listOf(
                PhaseChange(null, GetReady, 1, 0L),
                PhaseChange(GetReady, Warning, 1, 10.s),
                PhaseChange(Warning, Rest, 1, 190.s),
                PhaseChange(Rest, Warning, 2, 250.s),
                PhaseChange(Warning, Done, 2, 430.s),
            ),
            runToEnd(config),
        )

        val session = startedSession(config)
        session.snapshotAt(10.s).let {
            assertPhase(it, Warning, 1, 180.s)
            assertEquals(180.s, it.phaseLengthMs)
            assertEquals(0f, it.roundProgress, 0f)
        }
    }

    @Test
    fun oneRoundHasNoRest() {
        val config = RoundConfig(rounds = 1)

        assertEquals(
            listOf(
                PhaseChange(null, GetReady, 1, 0L),
                PhaseChange(GetReady, Work, 1, 10.s),
                PhaseChange(Work, Warning, 1, 160.s),
                PhaseChange(Warning, Done, 1, 190.s),
            ),
            runToEnd(config),
        )

        val done = startedSession(config).snapshotAt(190.s)
        assertPhase(done, Done, 1, 0L)
        assertEquals(SessionStatus.Finished, done.status)
        assertEquals(1, done.roundsCompleted)
    }

    @Test
    fun unlimitedRoundsKeepGoingUntilStopped() {
        val config = RoundConfig() // Unlimited is the default.
        assertNull(config.totalLengthMs)
        val session = startedSession(config)

        // 100 full rounds (3:00 + 1:00 rest each), then 5 s into round 101.
        val round101Start = 10.s + 100 * 240.s
        val state = session.snapshotAt(round101Start + 5.s)

        assertEquals(SessionStatus.Running, state.status)
        assertPhase(state, Work, 101, 145.s)
        assertEquals(100, state.roundsCompleted)
        assertNull(state.totalRounds)
        assertEquals(
            ReferenceTimeline.changes(config, roundsToWalk = 100) + PhaseChange(Rest, Work, 101, round101Start),
            session.engine.pollEvents(),
        )
        assertTrue(session.engine.stop())
        assertEquals(SessionStatus.Stopped, session.engine.snapshot().status)
    }

    @Test
    fun onlyTheRoundLengthSet() {
        val config =
            RoundConfig(
                roundLengthMs = 1.s,
                warningLengthMs = 0L,
                restLengthMs = 0L,
                rounds = 3,
                getReadyLengthMs = 0L,
            )

        assertEquals(
            listOf(
                PhaseChange(null, Work, 1, 0L),
                PhaseChange(Work, Work, 2, 1.s),
                PhaseChange(Work, Work, 3, 2.s),
                PhaseChange(Work, Done, 3, 3.s),
            ),
            runToEnd(config),
        )
    }

    @Test
    fun warningOnlyRoundsBackToBack() {
        val config =
            RoundConfig(
                warningLengthMs = 180.s,
                restLengthMs = 0L,
                rounds = 3,
                getReadyLengthMs = 0L,
            )

        assertEquals(
            listOf(
                PhaseChange(null, Warning, 1, 0L),
                PhaseChange(Warning, Warning, 2, 180.s),
                PhaseChange(Warning, Warning, 3, 360.s),
                PhaseChange(Warning, Done, 3, 540.s),
            ),
            runToEnd(config),
        )
    }

    /**
     * Every combination of small lengths, including every zero, checked at
     * every millisecond against [ReferenceTimeline].
     */
    @Test
    fun everySmallConfigMatchesTheReferenceAtEveryMillisecond() {
        var checked = 0
        for (round in 1L..4L) {
            for (warning in 0L..round) {
                for (rest in 0L..3L) {
                    for (getReady in 0L..2L) {
                        for (rounds in listOf(1, 2, 3, null)) {
                            checkAgainstReference(RoundConfig(round, warning, rest, rounds, getReady))
                            checked++
                        }
                    }
                }
            }
        }
        assertEquals(672, checked)
    }

    private fun checkAgainstReference(config: RoundConfig) {
        val spans = ReferenceTimeline.spans(config, roundsToWalk = config.rounds ?: 3)
        val session = startedSession(config)
        val polled = mutableListOf<PhaseChange>()
        // For unlimited configs, stop where the reference stops: the end of round 3's rest.
        val lastMs = config.totalLengthMs?.plus(5L) ?: (spans.last().endMs - 1)

        for (t in 0L..lastMs) {
            session.advanceTo(t)
            polled += session.engine.pollEvents()
            val state = session.engine.snapshot()
            val expected = spans.last { it.startMs <= t }
            val message = "$config at $t ms"
            assertEquals(message, expected.phase, state.phase)
            assertEquals(message, expected.round, state.round)
            val timeLeft = if (expected.phase == Done) 0L else expected.endMs - t
            assertEquals(message, timeLeft, state.phaseTimeLeftMs)
            val status = if (expected.phase == Done) SessionStatus.Finished else SessionStatus.Running
            assertEquals(message, status, state.status)
        }
        assertEquals(config.toString(), ReferenceTimeline.changes(config, config.rounds ?: 3), polled)
    }

    private fun startedSession(config: RoundConfig) = TestSession(config).also { it.engine.start() }

    private fun runToEnd(config: RoundConfig): List<PhaseChange> {
        val session = startedSession(config)
        val changes = session.pollAt(config.totalLengthMs!!)
        assertEquals(SessionStatus.Finished, session.engine.snapshot().status)
        assertEquals(ReferenceTimeline.changes(config), changes)
        return changes
    }
}
