package com.nickorton.sholto.engine

import com.nickorton.sholto.engine.Phase.Done
import com.nickorton.sholto.engine.Phase.Warning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Full 12-round sessions, polled the way a real UI loop would. */
class RoundEngineSimulationTest {
    // Pro preset: 3:00 × 12, 1:00 rest, plus the default 0:30 warning and 0:10 get ready.
    private val twelveRounds = RoundConfig(rounds = 12)
    private val totalMs = 10.s + 12 * 180.s + 11 * 60.s // 47:10

    @Test
    fun twelveRoundsEndToEndTakeExactlyTheConfiguredTime() {
        assertEquals(totalMs, twelveRounds.totalLengthMs)
        val session = TestSession(twelveRounds)
        val random = Random(seed = 1234)
        val changes = mutableListOf<PhaseChange>()
        session.engine.start()

        var state = session.engine.snapshot()
        while (state.status != SessionStatus.Finished) {
            // Uneven steps, like a UI loop that sometimes runs late.
            session.clock.advanceBy(random.nextLong(1L, MAX_STEP_MS))
            changes += session.engine.pollEvents()
            state = session.engine.snapshot()
            val sinceStart = session.clock.nowMs - START_MS
            assertEquals(minOf(sinceStart, totalMs), state.elapsedMs)
        }

        val finishedAt = session.clock.nowMs - START_MS
        assertTrue("finished at $finishedAt", finishedAt in totalMs until totalMs + MAX_STEP_MS)
        assertEquals(totalMs, state.elapsedMs)
        assertEquals(12, state.roundsCompleted)
        // Get ready, then work + warning for each round, rest between rounds, then done.
        assertEquals(1 + 12 * 2 + 11 + 1, changes.size)
        assertEquals(ReferenceTimeline.changes(twelveRounds), changes)
        assertEquals(PhaseChange(Warning, Done, 12, totalMs), changes.last())
    }

    @Test
    fun twelveRoundsWithRandomPausesTakeTheConfiguredTimePlusThePauses() {
        val session = TestSession(twelveRounds)
        val random = Random(seed = 99)
        val changes = mutableListOf<PhaseChange>()
        var pausedTotal = 0L
        var pauseCount = 0
        session.engine.start()

        var state = session.engine.snapshot()
        while (state.status != SessionStatus.Finished) {
            session.clock.advanceBy(random.nextLong(1L, MAX_STEP_MS))
            if (random.nextInt(50) == 0 && session.engine.pause()) {
                val pauseMs = random.nextLong(1L, 30.s)
                session.clock.advanceBy(pauseMs)
                changes += session.engine.pollEvents()
                assertTrue(session.engine.resume())
                pausedTotal += pauseMs
                pauseCount++
            }
            changes += session.engine.pollEvents()
            state = session.engine.snapshot()
            val sinceStart = session.clock.nowMs - START_MS
            assertEquals(minOf(sinceStart - pausedTotal, totalMs), state.elapsedMs)
        }

        assertTrue("expected some pauses, got $pauseCount", pauseCount > 10)
        assertEquals(totalMs, state.elapsedMs)
        assertEquals(ReferenceTimeline.changes(twelveRounds), changes)
    }

    @Test
    fun twelveRoundsFinishOnTheExactMillisecondAfterALongPause() {
        val session = TestSession(twelveRounds)
        session.engine.start()

        session.advanceTo(1_000.s)
        session.engine.pause()
        session.advanceTo(1_000.s + 60 * 60.s)
        session.engine.resume()

        val oneHour = 60 * 60.s
        assertPhase(session.snapshotAt(totalMs + oneHour - 1), Warning, 12, 1L)
        session.snapshotAt(totalMs + oneHour).let {
            assertEquals(SessionStatus.Finished, it.status)
            assertEquals(totalMs, it.elapsedMs)
        }
    }

    private companion object {
        const val MAX_STEP_MS = 250L
    }
}
