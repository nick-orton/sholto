package com.nickorton.sholto.engine

import com.nickorton.sholto.engine.Phase.Done
import com.nickorton.sholto.engine.Phase.GetReady
import com.nickorton.sholto.engine.Phase.Rest
import com.nickorton.sholto.engine.Phase.Warning
import com.nickorton.sholto.engine.Phase.Work
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundEngineTest {
    // Defaults: 0:10 get ready, 2:30 work + 0:30 warning, 1:00 rest.
    private val threeRounds = RoundConfig(rounds = 3)

    @Test
    fun phaseOrderForThreeRoundsWithDefaultTimes() {
        val session = TestSession(threeRounds)
        session.engine.start()

        val changes = session.pollAt(670.s)

        assertEquals(
            listOf(
                PhaseChange(null, GetReady, 1, 0L),
                PhaseChange(GetReady, Work, 1, 10.s),
                PhaseChange(Work, Warning, 1, 160.s),
                PhaseChange(Warning, Rest, 1, 190.s),
                PhaseChange(Rest, Work, 2, 250.s),
                PhaseChange(Work, Warning, 2, 400.s),
                PhaseChange(Warning, Rest, 2, 430.s),
                PhaseChange(Rest, Work, 3, 490.s),
                PhaseChange(Work, Warning, 3, 640.s),
                PhaseChange(Warning, Done, 3, 670.s),
            ),
            changes,
        )
        assertEquals(ReferenceTimeline.changes(threeRounds), changes)
    }

    @Test
    fun snapshotsThroughThreeRounds() {
        val session = TestSession(threeRounds)
        session.engine.start()

        session.snapshotAt(0L).let {
            assertPhase(it, GetReady, 1, 10.s)
            assertEquals(SessionStatus.Running, it.status)
            assertEquals(10.s, it.phaseLengthMs)
            assertEquals(3, it.totalRounds)
            assertEquals(0, it.roundsCompleted)
            assertEquals(0f, it.roundProgress, DELTA)
        }
        assertPhase(session.snapshotAt(10.s - 1), GetReady, 1, 1L)
        session.snapshotAt(10.s).let {
            assertPhase(it, Work, 1, 150.s)
            assertEquals(150.s, it.phaseLengthMs)
            assertEquals(0f, it.roundProgress, DELTA)
        }
        session.snapshotAt(100.s).let {
            assertPhase(it, Work, 1, 60.s)
            assertEquals(0.5f, it.roundProgress, DELTA)
            assertEquals(100.s, it.elapsedMs)
        }
        session.snapshotAt(160.s).let {
            assertPhase(it, Warning, 1, 30.s)
            assertEquals(30.s, it.phaseLengthMs)
            assertEquals(150f / 180f, it.roundProgress, DELTA)
            assertEquals(0, it.roundsCompleted)
        }
        session.snapshotAt(190.s).let {
            assertPhase(it, Rest, 1, 60.s)
            assertEquals(1, it.roundsCompleted)
            assertEquals(1f, it.roundProgress, DELTA)
        }
        session.snapshotAt(250.s).let {
            assertPhase(it, Work, 2, 150.s)
            assertEquals(1, it.roundsCompleted)
            assertEquals(0f, it.roundProgress, DELTA)
        }
        session.snapshotAt(670.s - 1).let {
            assertPhase(it, Warning, 3, 1L)
            assertEquals(SessionStatus.Running, it.status)
            assertEquals(2, it.roundsCompleted)
        }
        session.snapshotAt(670.s).let {
            assertPhase(it, Done, 3, 0L)
            assertEquals(SessionStatus.Finished, it.status)
            assertEquals(0L, it.phaseLengthMs)
            assertEquals(3, it.roundsCompleted)
            assertEquals(1f, it.roundProgress, DELTA)
            assertEquals(670.s, it.elapsedMs)
        }
        // Time stops counting once the session is done.
        assertEquals(670.s, session.snapshotAt(2_000.s).elapsedMs)
    }

    @Test
    fun beforeStartTheSessionIsIdleAtTheFirstPhase() {
        val session = TestSession(threeRounds)

        val state = session.snapshotAt(5.s)

        assertEquals(SessionStatus.Idle, state.status)
        assertPhase(state, GetReady, 1, 10.s)
        assertEquals(0L, state.elapsedMs)
        assertEquals(emptyList<PhaseChange>(), session.engine.pollEvents())
        assertFalse(session.engine.pause())
        assertFalse(session.engine.resume())
        assertFalse(session.engine.stop())
        assertEquals(SessionStatus.Idle, session.engine.snapshot().status)
    }

    @Test
    fun timeCountsFromStartNotFromCreation() {
        val session = TestSession(threeRounds)
        session.advanceTo(30.s)
        session.engine.start()

        val state = session.snapshotAt(40.s)

        assertPhase(state, Work, 1, 150.s)
        assertEquals(10.s, state.elapsedMs)
    }

    @Test
    fun startOnlyWorksOnce() {
        val session = TestSession(threeRounds)
        assertTrue(session.engine.start())

        session.advanceTo(5.s)
        assertFalse(session.engine.start())

        // Timing still runs from the first start.
        assertPhase(session.snapshotAt(10.s), Work, 1, 150.s)
    }

    @Test
    fun pauseAndResumeOnlyApplyInTheRightState() {
        val session = TestSession(threeRounds)
        session.engine.start()

        assertFalse(session.engine.resume())
        assertTrue(session.engine.pause())
        assertFalse(session.engine.pause())
        assertEquals(SessionStatus.Paused, session.engine.snapshot().status)
        assertTrue(session.engine.resume())
        assertFalse(session.engine.resume())
        assertEquals(SessionStatus.Running, session.engine.snapshot().status)
    }

    @Test
    fun stopFreezesTheSession() {
        val session = TestSession(threeRounds)
        session.engine.start()
        session.advanceTo(100.s)

        assertTrue(session.engine.stop())
        val stopped = session.snapshotAt(500.s)

        assertEquals(SessionStatus.Stopped, stopped.status)
        assertPhase(stopped, Work, 1, 60.s)
        assertEquals(100.s, stopped.elapsedMs)
        assertFalse(session.engine.start())
        assertFalse(session.engine.pause())
        assertFalse(session.engine.resume())
        assertFalse(session.engine.stop())
        assertEquals(stopped, session.snapshotAt(5_000.s))
    }

    @Test
    fun stopWhilePausedKeepsThePausedPosition() {
        val session = TestSession(threeRounds)
        session.engine.start()
        session.advanceTo(100.s)
        session.engine.pause()
        session.advanceTo(150.s)

        assertTrue(session.engine.stop())

        val stopped = session.snapshotAt(200.s)
        assertEquals(SessionStatus.Stopped, stopped.status)
        assertEquals(100.s, stopped.elapsedMs)
        assertFalse(session.engine.resume())
    }

    @Test
    fun commandsDoNothingOnceTheSessionHasFinished() {
        val session = TestSession(threeRounds)
        session.engine.start()
        session.advanceTo(670.s)

        assertFalse(session.engine.pause())
        assertFalse(session.engine.resume())
        assertFalse(session.engine.stop())
        assertEquals(SessionStatus.Finished, session.engine.snapshot().status)
    }

    @Test
    fun eachPhaseChangeIsReportedOnceAndInOrder() {
        val session = TestSession(threeRounds)
        val all = ReferenceTimeline.changes(threeRounds)
        session.engine.start()

        assertEquals(all.subList(0, 1), session.pollAt(0L))
        assertEquals(emptyList<PhaseChange>(), session.pollAt(5.s))
        // A poll exactly on a boundary includes it.
        assertEquals(all.subList(1, 2), session.pollAt(10.s))
        assertEquals(emptyList<PhaseChange>(), session.pollAt(10.s))
        // A late poll gets every boundary it missed, with exact times.
        assertEquals(all.subList(2, 4), session.pollAt(200.s))
        assertEquals(all.subList(4, all.size), session.pollAt(10_000.s))
        assertEquals(emptyList<PhaseChange>(), session.pollAt(10_001.s))
    }

    @Test
    fun phaseChangesDoNotDependOnHowOftenYouPoll() {
        val config =
            RoundConfig(
                roundLengthMs = 3.s,
                warningLengthMs = 1.s,
                restLengthMs = 2.s,
                rounds = 3,
                getReadyLengthMs = 1.s,
            )
        val everyMs = TestSession(config)
        val once = TestSession(config)
        everyMs.engine.start()
        once.engine.start()

        val polledEveryMs = (0L..config.totalLengthMs!!).flatMap { everyMs.pollAt(it) }
        val polledOnce = once.pollAt(config.totalLengthMs!!)

        assertEquals(ReferenceTimeline.changes(config), polledOnce)
        assertEquals(polledOnce, polledEveryMs)
    }

    @Test
    fun changesBeforeAStopAreStillReportedButNoneAfter() {
        val session = TestSession(threeRounds)
        val all = ReferenceTimeline.changes(threeRounds)
        session.engine.start()
        session.pollAt(0L)

        session.advanceTo(200.s)
        session.engine.stop()

        assertEquals(all.subList(1, 4), session.engine.pollEvents())
        assertEquals(emptyList<PhaseChange>(), session.pollAt(1_000.s))
    }

    private companion object {
        const val DELTA = 1e-6f
    }
}
