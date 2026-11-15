package com.nickorton.sholto.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class CallSchedulerTest {
    private fun scheduler(
        round: RoundConfig,
        punch: PunchConfig,
        push: Double = 1.0,
        seed: Long = 1L,
        speechMs: (Combo) -> Long = { 0L },
    ) = CallScheduler(round, PunchEngine(punch, Random(seed)), push, speechMs = speechMs)

    /** Every call, up to [untilMs] for unlimited rounds. */
    private fun scheduled(
        s: CallScheduler,
        untilMs: Long = Long.MAX_VALUE,
    ): List<ScheduledCall> =
        generateSequence(s.firstCall()) { s.nextCallAfter(it) }
            .takeWhile { it.atMs < untilMs }
            .toList()

    /** Every call time, up to [untilMs] for unlimited rounds. */
    private fun calls(
        s: CallScheduler,
        untilMs: Long = Long.MAX_VALUE,
    ): List<Long> = scheduled(s, untilMs).map { it.atMs }

    /** Every time a cue can play: session start and each phase change. */
    private fun cueTimes(
        round: RoundConfig,
        untilMs: Long,
    ): List<Long> = listOf(0L) + RoundTimeline(round).changesBetween(0L, untilMs).map { it.atElapsedMs }

    private val fixed3s =
        PunchConfig(meanGapMs = 3_000L, minGapMs = 1_000L, maxGapMs = 8_000L, distribution = GapDistribution.Fixed)

    // Get ready 5 s, then two rounds of 15 s work + 5 s warning, 10 s rest
    // between. Rounds start at 5 s and 35 s; warnings at 20 s and 50 s; Done
    // at 55 s.
    private val twoShortRounds =
        RoundConfig(
            roundLengthMs = 20_000L,
            warningLengthMs = 5_000L,
            restLengthMs = 10_000L,
            rounds = 2,
            getReadyLengthMs = 5_000L,
        )

    @Test
    fun `fixed gaps restart each round and step around every bell`() {
        val times = calls(scheduler(twoShortRounds, fixed3s))
        assertEquals(
            listOf(
                // Round 1: one gap after the bell at 5 s.
                8_000L,
                11_000L,
                14_000L,
                17_000L,
                // 20 s is the warning clacks: wait 1 s. 24 s is exactly 1 s
                // before the bell, so it stays.
                21_000L,
                24_000L,
                // 27 s would be in rest: dropped. Round 2 restarts at 35 s.
                38_000L,
                41_000L,
                44_000L,
                47_000L,
                51_000L,
                54_000L,
            ),
            times,
        )
    }

    @Test
    fun `final push restarts the wait at the warning with the faster pace`() {
        val times = calls(scheduler(twoShortRounds, fixed3s, push = 0.5))
        assertEquals(
            listOf(
                8_000L,
                11_000L,
                14_000L,
                17_000L,
                // Warning gaps are 1.5 s, counted from the clacks at 20 s.
                21_500L,
                23_000L,
                // 24.5 s is within 1 s of the bell at 25 s, and after it is rest.
                38_000L,
                41_000L,
                44_000L,
                47_000L,
                51_500L,
                53_000L,
            ),
            times,
        )
    }

    @Test
    fun `no session start bell is talked over when there is no get ready`() {
        val round = twoShortRounds.copy(getReadyLengthMs = 0L)
        val quick = fixed3s.copy(meanGapMs = 500L, minGapMs = 300L, maxGapMs = 500L)
        val times = calls(scheduler(round, quick))
        assertEquals(1_000L, times.first())
    }

    @Test
    fun `calls are only in work and warning and never within 1 s of a cue`() {
        val rounds =
            listOf(
                RoundConfig(),
                RoundConfig(rounds = 6),
                RoundConfig(roundLengthMs = 30_000L, warningLengthMs = 0L, restLengthMs = 30_000L, rounds = 10),
                RoundConfig(roundLengthMs = 60_000L, warningLengthMs = 60_000L, restLengthMs = 15_000L, rounds = 3),
                RoundConfig(roundLengthMs = 45_000L, warningLengthMs = 10_000L, restLengthMs = 0L, getReadyLengthMs = 0L),
                RoundConfig(roundLengthMs = 30_000L, warningLengthMs = 5_000L, restLengthMs = 0L, rounds = 8),
            )
        val punches =
            listOf(
                PunchConfig(),
                PunchConfig(meanGapMs = 1_800L, minGapMs = 600L),
                PunchConfig(meanGapMs = 500L, minGapMs = 300L, maxGapMs = 2_000L),
                PunchConfig(meanGapMs = 4_000L, minGapMs = 2_000L, distribution = GapDistribution.Uniform),
                PunchConfig(meanGapMs = 6_000L, minGapMs = 1_000L, maxGapMs = 30_000L, distribution = GapDistribution.Normal),
            )
        val window = 30 * 60_000L
        for (round in rounds) {
            val cues = cueTimes(round, window + 60_000L)
            val timeline = RoundTimeline(round)
            for (punch in punches) {
                for (push in listOf(1.0, 0.6, 0.3)) {
                    for (seed in 1L..5L) {
                        val times = calls(scheduler(round, punch, push, seed), untilMs = window)
                        val label = "$round $punch push=$push seed=$seed"
                        assertTrue("no calls: $label", times.isNotEmpty())
                        times.zipWithNext().forEach { (a, b) -> assertTrue("not increasing: $label", b > a) }
                        for (t in times) {
                            val phase = timeline.spanAt(t).phase
                            assertTrue("call at $t in $phase: $label", phase == Phase.Work || phase == Phase.Warning)
                            val nearest = cues.minOf { abs(it - t) }
                            assertTrue("call at $t is ${nearest}ms from a cue: $label", nearest >= CallScheduler.BELL_GUARD_MS)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `final push makes warning gaps shorter by the factor`() {
        // Long warnings and many rounds, for plenty of gaps of each kind.
        val round = RoundConfig(roundLengthMs = 180_000L, warningLengthMs = 60_000L, restLengthMs = 30_000L, rounds = 200)
        val punch = PunchConfig(meanGapMs = 3_000L, minGapMs = 300L, maxGapMs = 30_000L)
        val timeline = RoundTimeline(round)
        val times = calls(scheduler(round, punch, push = 0.6))

        fun meanGapWithin(phase: Phase): Double =
            times
                .zipWithNext()
                .filter { (a, b) ->
                    val sa = timeline.spanAt(a)
                    sa.phase == phase && sa == timeline.spanAt(b)
                }.map { (a, b) -> (b - a).toDouble() }
                .average()

        val ratio = meanGapWithin(Phase.Warning) / meanGapWithin(Phase.Work)
        assertTrue("warning/work gap ratio was $ratio", ratio in 0.55..0.65)
    }

    @Test
    fun `final push off keeps the same pace in the warning`() {
        val round = RoundConfig(roundLengthMs = 180_000L, warningLengthMs = 60_000L, restLengthMs = 30_000L, rounds = 200)
        val timeline = RoundTimeline(round)
        val times = calls(scheduler(round, PunchConfig(minGapMs = 300L, maxGapMs = 30_000L)))

        fun meanGapWithin(phase: Phase): Double =
            times
                .zipWithNext()
                .filter { (a, b) -> timeline.spanAt(a).let { it.phase == phase && it == timeline.spanAt(b) } }
                .map { (a, b) -> (b - a).toDouble() }
                .average()

        val ratio = meanGapWithin(Phase.Warning) / meanGapWithin(Phase.Work)
        assertTrue("warning/work gap ratio was $ratio", ratio in 0.93..1.07)
    }

    @Test
    fun `no calls after the last round`() {
        val round = RoundConfig(rounds = 3)
        val s = scheduler(round, PunchConfig())
        val all = scheduled(s)
        assertTrue(all.last().atMs <= round.totalLengthMs!! - CallScheduler.BELL_GUARD_MS)
        assertNull(s.nextCallAfter(all.last()))
    }

    @Test(timeout = 5_000L)
    fun `settings that never fit a call give up instead of hanging`() {
        // Every gap is 15 s, but work and warning are 15 s each: no call fits
        // 1 s clear of both bells, in any round, forever.
        val round = RoundConfig(roundLengthMs = 30_000L, warningLengthMs = 15_000L, restLengthMs = 15_000L, rounds = null)
        val punch = PunchConfig(meanGapMs = 15_000L, minGapMs = 15_000L, maxGapMs = 15_000L, distribution = GapDistribution.Fixed)
        assertNull(scheduler(round, punch, push = 0.6).firstCall())
    }

    @Test
    fun `the same seed gives the same calls`() {
        val a = calls(scheduler(RoundConfig(rounds = 3), PunchConfig(), push = 0.6, seed = 7L))
        val b = calls(scheduler(RoundConfig(rounds = 3), PunchConfig(), push = 0.6, seed = 7L))
        assertEquals(a, b)
    }

    @Test
    fun `the wait after a combo starts once it has been said and thrown`() {
        // Every call is a 2-punch combo taking 0.6 s to say, then 0.8 s to throw.
        val pairs = fixed3s.copy(combos = true, maxComboLength = 2, comboLengthWeights = listOf(0, 1, 0, 0, 0, 0))
        val all = scheduled(scheduler(twoShortRounds, pairs, speechMs = { 600L }))
        assertTrue(all.all { it.combo.size == 2 })
        assertEquals(
            listOf(8_000L, 12_400L, 16_800L, 21_200L, 38_000L, 42_400L, 46_800L, 51_200L),
            all.map { it.atMs },
        )
    }

    @Test
    fun `a call's speech ends at least 1 s before the next bell`() {
        val times = calls(scheduler(twoShortRounds, fixed3s, speechMs = { 1_500L }))
        assertEquals(
            listOf(
                8_000L,
                11_000L,
                14_000L,
                // Said from 17 s to 18.5 s, 1.5 s before the clacks at 20 s.
                17_000L,
                // 24 s would talk until 25.5 s, over the bell: moved past it,
                // into rest, so dropped.
                21_000L,
                38_000L,
                41_000L,
                44_000L,
                47_000L,
                51_000L,
            ),
            times,
        )
    }

    @Test
    fun `long combos in names never talk over a bell`() {
        val round = RoundConfig(roundLengthMs = 60_000L, warningLengthMs = 10_000L, restLengthMs = 20_000L, rounds = 20)
        val punch = PunchConfig(meanGapMs = 1_500L, minGapMs = 300L, combos = true, maxComboLength = 6, naturalOnly = true)
        val speech = { c: Combo -> CallTiming.speechMs(c.spokenText(CalloutStyle.Both, Stance.Orthodox), 0.8f) }
        val timeline = RoundTimeline(round)
        for (seed in 1L..10L) {
            val all = scheduled(scheduler(round, punch, push = 0.6, seed = seed, speechMs = speech))
            assertTrue(all.isNotEmpty())
            for (call in all) {
                val span = timeline.spanAt(call.atMs)
                assertTrue("$call starts too soon", call.atMs >= span.startMs + CallScheduler.BELL_GUARD_MS)
                assertTrue("$call ends too late", call.atMs + speech(call.combo) <= span.endMs - CallScheduler.BELL_GUARD_MS)
            }
            // No call starts before the one before it has been said and thrown.
            all.zipWithNext().forEach { (a, b) ->
                assertTrue("$b overlaps $a", b.atMs >= a.atMs + CallTiming.busyMs(a.combo, speech(a.combo)))
            }
        }
    }
}
