package com.nickorton.sholto.engine

import com.nickorton.sholto.engine.Phase.Rest
import com.nickorton.sholto.engine.Phase.Warning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundEnginePauseTest {
    private val threeRounds = RoundConfig(rounds = 3)
    private val spans = ReferenceTimeline.spans(threeRounds)

    @Test
    fun pauseAndResumeMoveEveryLaterBoundaryByExactlyThePausedTime() {
        val pauseMs = 12_345L
        val session = TestSession(threeRounds)
        session.engine.start()

        val beforePause = session.snapshotAt(100.s)
        assertTrue(session.engine.pause())
        val duringPause = session.snapshotAt(100.s + pauseMs)
        assertTrue(session.engine.resume())
        val afterResume = session.engine.snapshot()

        assertEquals(beforePause.copy(status = SessionStatus.Paused), duringPause)
        assertEquals(beforePause, afterResume)

        // On the clock, every boundary after the pause now comes pauseMs later.
        var previous = spans.last { it.startMs <= 100.s }
        for (span in spans.filter { it.startMs > 100.s }) {
            val boundaryOnClock = span.startMs + pauseMs
            assertPhase(session.snapshotAt(boundaryOnClock - 1), previous.phase, previous.round, 1L)
            val entered = session.snapshotAt(boundaryOnClock)
            assertEquals(span.phase, entered.phase)
            assertEquals(span.round, entered.round)
            previous = span
        }
        assertEquals(SessionStatus.Finished, session.engine.snapshot().status)
        assertEquals(670.s, session.engine.snapshot().elapsedMs)
        // In active time, nothing moved.
        assertEquals(ReferenceTimeline.changes(threeRounds), session.engine.pollEvents())
    }

    @Test
    fun pausesInDifferentPhasesAddUp() {
        val session = TestSession(threeRounds)
        session.engine.start()
        // (active time to pause at, how long to stay paused)
        val pauses =
            listOf(
                5.s to 1.s, // GetReady
                100.s to 3_333L, // Work
                170.s to 2_500L, // Warning
                450.s to 60.s, // Rest
                600.s to 1L, // Work, round 3
            )

        var pausedSoFar = 0L
        for ((activeMs, pauseMs) in pauses) {
            session.advanceTo(activeMs + pausedSoFar)
            assertEquals(activeMs, session.engine.snapshot().elapsedMs)
            assertTrue(session.engine.pause())
            session.advanceTo(activeMs + pausedSoFar + pauseMs)
            assertEquals(activeMs, session.engine.snapshot().elapsedMs)
            assertTrue(session.engine.resume())
            pausedSoFar += pauseMs
        }

        assertEquals(SessionStatus.Running, session.snapshotAt(670.s + pausedSoFar - 1).status)
        assertEquals(SessionStatus.Finished, session.snapshotAt(670.s + pausedSoFar).status)
        assertEquals(ReferenceTimeline.changes(threeRounds), session.engine.pollEvents())
    }

    @Test
    fun manyShortPausesCauseNoDrift() {
        val session = TestSession(threeRounds)
        session.engine.start()

        var pausedSoFar = 0L
        // Run 997 ms, pause 13 ms, over and over, stopping short of the end.
        while (session.clock.nowMs - START_MS - pausedSoFar < 660.s) {
            session.clock.advanceBy(997L)
            val sinceStart = session.clock.nowMs - START_MS
            assertEquals(sinceStart - pausedSoFar, session.engine.snapshot().elapsedMs)
            assertTrue(session.engine.pause())
            session.clock.advanceBy(13L)
            assertTrue(session.engine.resume())
            pausedSoFar += 13L
        }

        assertPhase(session.snapshotAt(670.s + pausedSoFar - 1), Warning, 3, 1L)
        assertEquals(SessionStatus.Finished, session.snapshotAt(670.s + pausedSoFar).status)
        assertEquals(ReferenceTimeline.changes(threeRounds), session.engine.pollEvents())
    }

    @Test
    fun pollingWhilePausedReportsOnlyWhatHappenedBeforeThePause() {
        val all = ReferenceTimeline.changes(threeRounds)
        val session = TestSession(threeRounds)
        session.engine.start()
        session.pollAt(0L)

        session.advanceTo(200.s)
        session.engine.pause()

        assertEquals(all.subList(1, 4), session.engine.pollEvents())
        assertEquals(emptyList<PhaseChange>(), session.pollAt(10_000.s))
        assertPhase(session.engine.snapshot(), Rest, 1, 50.s)
    }

    @Test
    fun pausingExactlyOnABoundaryReportsItOnce() {
        val session = TestSession(threeRounds)
        session.engine.start()

        session.advanceTo(160.s)
        session.engine.pause()
        assertPhase(session.engine.snapshot(), Warning, 1, 30.s)
        session.advanceTo(165.s)
        session.engine.resume()

        val changes = session.pollAt(670.s + 5.s)
        assertEquals(1, changes.count { it.to == Warning && it.round == 1 })
        assertEquals(ReferenceTimeline.changes(threeRounds), changes)
    }
}
