package com.nickorton.sholto.service

import com.nickorton.sholto.audio.CallAnnouncer
import com.nickorton.sholto.audio.Cue
import com.nickorton.sholto.audio.CuePlayer
import com.nickorton.sholto.data.CallerSettings
import com.nickorton.sholto.data.CombinedSettings
import com.nickorton.sholto.data.FinalPush
import com.nickorton.sholto.data.TimerSettings
import com.nickorton.sholto.engine.Combo
import com.nickorton.sholto.engine.GapDistribution
import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.engine.Punch
import com.nickorton.sholto.engine.PunchConfig
import com.nickorton.sholto.engine.RoundConfig
import com.nickorton.sholto.engine.RoundTimeline
import com.nickorton.sholto.ui.combined.CombinedUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Runs whole combined sessions on the coroutine test scheduler, whose virtual
 * time is also the session clock, and checks when calls and bells actually
 * go out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CombinedSessionTest {
    private val bells = setOf(Cue.RoundStart, Cue.Warning, Cue.RoundEnd, Cue.SessionDone)

    /** A session started at virtual time 0, recording everything it plays and says. */
    private class Recorder(
        private val scope: TestScope,
    ) {
        val calls = mutableListOf<Pair<Long, Combo>>()
        val cues = mutableListOf<Pair<Long, Cue>>()

        fun session(
            settings: CombinedSettings,
            seed: Long = 1L,
            backgroundScope: CoroutineScope = scope.backgroundScope,
        ) = CombinedSession(
            scope = backgroundScope,
            settings = kotlinx.coroutines.flow.flowOf(settings),
            clock = { scope.testScheduler.currentTime },
            randomFactory = { Random(seed) },
            announcer = CallAnnouncer { punch, _ -> calls += scope.testScheduler.currentTime to punch },
            cuePlayer = CuePlayer { cues += scope.testScheduler.currentTime to it },
        )
    }

    private fun settings(
        round: RoundConfig,
        punch: PunchConfig = PunchConfig(),
        push: FinalPush = FinalPush(),
    ) = CombinedSettings(
        timer = TimerSettings(round = round),
        caller = CallerSettings(punch = punch),
        finalPush = push,
    )

    // Get ready 5 s, then two rounds of 15 s work + 5 s warning, 10 s rest
    // between. Rounds start at 5 s and 35 s; warnings at 20 s and 50 s; Done
    // at 55 s.
    private val twoShortRounds =
        RoundConfig(roundLengthMs = 20_000L, warningLengthMs = 5_000L, restLengthMs = 10_000L, rounds = 2, getReadyLengthMs = 5_000L)

    private val fixed3s =
        PunchConfig(meanGapMs = 3_000L, minGapMs = 1_000L, maxGapMs = 8_000L, distribution = GapDistribution.Fixed)

    @Test
    fun `calls and bells go out at their exact times`() =
        runTest {
            val rec = Recorder(this)
            val session = rec.session(settings(twoShortRounds, fixed3s, FinalPush(factor = 0.5f)))
            runCurrent()
            session.start()
            advanceTimeBy(60_000L)
            runCurrent()

            assertEquals(
                listOf(8_000L, 11_000L, 14_000L, 17_000L, 21_500L, 23_000L, 38_000L, 41_000L, 44_000L, 47_000L, 51_500L, 53_000L),
                rec.calls.map { it.first },
            )
            assertEquals(
                listOf(
                    5_000L to Cue.RoundStart,
                    20_000L to Cue.Warning,
                    25_000L to Cue.RoundEnd,
                    35_000L to Cue.RoundStart,
                    50_000L to Cue.Warning,
                    55_000L to Cue.SessionDone,
                ),
                rec.cues.filter { it.second in bells },
            )
            assertEquals(
                CombinedUiState.Done(roundsCompleted = 2, callsMade = 12, totalTimeMs = 55_000L, finished = true),
                session.uiState.value,
            )
        }

    @Test
    fun `a full random session never calls in rest, speeds up in the warning and keeps clear of every bell`() =
        runTest {
            val round = RoundConfig(roundLengthMs = 180_000L, warningLengthMs = 30_000L, restLengthMs = 60_000L, rounds = 12)
            val rec = Recorder(this)
            val session = rec.session(settings(round, PunchConfig(minGapMs = 300L, maxGapMs = 30_000L)), seed = 42L)
            runCurrent()
            session.start()
            advanceTimeBy(round.totalLengthMs!! + 10_000L)
            runCurrent()

            val timeline = RoundTimeline(round)
            val bellTimes = rec.cues.filter { it.second in bells }.map { it.first }
            assertEquals(12 + 12 + 11 + 1, bellTimes.size)
            for ((t, _) in rec.calls) {
                val phase = timeline.spanAt(t).phase
                assertTrue("call at $t in $phase", phase == Phase.Work || phase == Phase.Warning)
                val nearest = bellTimes.minOf { abs(it - t) }
                assertTrue("call at $t is ${nearest}ms from a bell", nearest >= 1_000L)
            }

            // Calls per second, with the 1 s guards cut from each phase.
            val inWork = rec.calls.count { timeline.spanAt(it.first).phase == Phase.Work }
            val inWarning = rec.calls.count { timeline.spanAt(it.first).phase == Phase.Warning }
            val workRate = inWork / (12 * (round.workLengthMs - 2_000L) / 1000.0)
            val warningRate = inWarning / (12 * (round.warningLengthMs - 2_000L) / 1000.0)
            assertTrue("work $workRate/s vs warning $warningRate/s", warningRate > 1.4 * workRate)
            assertEquals(rec.calls.size, (session.uiState.value as CombinedUiState.Done).callsMade)
        }

    @Test
    fun `pausing stops calls and resuming shifts them by exactly the pause`() =
        runTest {
            val config = settings(RoundConfig(rounds = 2), PunchConfig(minGapMs = 300L))
            val plain = Recorder(this)
            val first = plain.session(config, seed = 9L)
            runCurrent()
            first.start()
            advanceTimeBy(500_000L)
            runCurrent()

            val paused = Recorder(this)
            val second = paused.session(config, seed = 9L)
            runCurrent()
            val start = testScheduler.currentTime
            second.start()
            advanceTimeBy(40_000L)
            second.pause()
            assertTrue((second.uiState.value as CombinedUiState.Running).isPaused)
            advanceTimeBy(7_000L)
            val callsWhilePaused = paused.calls.count { it.first > start + 40_000L }
            assertEquals(0, callsWhilePaused)
            second.resume()
            advanceTimeBy(500_000L)
            runCurrent()

            val expected = plain.calls.map { (t, p) -> (if (t > 40_000L) t + 7_000L else t) to p }
            assertEquals(expected, paused.calls.map { (t, p) -> (t - start) to p })
        }

    @Test
    fun `ending early says nothing more and rings no bell`() =
        runTest {
            val rec = Recorder(this)
            val session = rec.session(settings(twoShortRounds, fixed3s))
            runCurrent()
            session.start()
            advanceTimeBy(18_000L)
            runCurrent()
            val callsBefore = rec.calls.size
            val cuesBefore = rec.cues.size
            session.end()
            advanceTimeBy(60_000L)
            runCurrent()

            assertEquals(callsBefore, rec.calls.size)
            assertEquals(cuesBefore, rec.cues.size)
            val done = session.uiState.value as CombinedUiState.Done
            assertEquals(false, done.finished)
            assertEquals(callsBefore, done.callsMade)
        }

    @Test
    fun `the call shows during its round and clears for rest`() =
        runTest {
            val rec = Recorder(this)
            val session = rec.session(settings(twoShortRounds, fixed3s))
            runCurrent()
            session.start()

            advanceTimeBy(8_500L)
            runCurrent()
            val working = session.uiState.value as CombinedUiState.Running
            assertEquals(Phase.Work, working.timer.phase)
            assertEquals(rec.calls.last().second, working.currentCall)
            assertEquals(500L, working.callAgeMs)
            assertEquals(1, working.callCount)

            advanceTimeBy(18_000L) // 26.5 s: resting.
            runCurrent()
            val resting = session.uiState.value as CombinedUiState.Running
            assertEquals(Phase.Rest, resting.timer.phase)
            assertNull(resting.currentCall)

            advanceTimeBy(10_000L) // 36.5 s: round 2, before its first call.
            runCurrent()
            val nextRound = session.uiState.value as CombinedUiState.Running
            assertEquals(Phase.Work, nextRound.timer.phase)
            assertNull(nextRound.currentCall)
        }

    @Test
    fun `settings changes apply from the next session`() =
        runTest {
            val flow = kotlinx.coroutines.flow.MutableStateFlow(settings(twoShortRounds, fixed3s))
            val calls = mutableListOf<Long>()
            val session =
                CombinedSession(
                    scope = backgroundScope,
                    settings = flow,
                    clock = { testScheduler.currentTime },
                    announcer = CallAnnouncer { _, _ -> calls += testScheduler.currentTime },
                )
            runCurrent()
            session.start()
            flow.value = settings(twoShortRounds, fixed3s.copy(meanGapMs = 5_000L))
            advanceTimeBy(12_000L)
            runCurrent()
            assertEquals(listOf(8_000L, 11_000L), calls)
        }
}
