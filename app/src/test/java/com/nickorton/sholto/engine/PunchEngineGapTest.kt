package com.nickorton.sholto.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Statistical checks on gaps. Each test uses a fixed seed, so results are
 * deterministic. Tolerances are still set from the sampling error (about 4
 * standard errors), so a different seed would pass too: they test the
 * distribution, not one lucky sequence.
 */
class PunchEngineGapTest {
    private fun gaps(
        config: PunchConfig,
        seed: Long = SEED,
        n: Int = SAMPLES,
    ): List<Long> {
        val engine = PunchEngine(config, Random(seed))
        return List(n) { engine.nextGap() }
    }

    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double,
        what: String,
    ) {
        assertTrue("$what: expected $expected ± $tolerance, was $actual", abs(expected - actual) <= tolerance)
    }

    @Test
    fun everyGapIsWithinMinAndMaxForEveryDistribution() {
        // Narrow bounds and a wide spread, so the clamp is exercised hard.
        val base = PunchConfig(meanGapMs = 3.s, minGapMs = 2.s, maxGapMs = 4.s, stdDevMs = 3.s)
        GapDistribution.entries.forEach { distribution ->
            val all = gaps(base.copy(distribution = distribution))
            assertTrue("$distribution: ${all.min()}..${all.max()}", all.all { it in 2.s..4.s })
        }
    }

    @Test
    fun exponentialMeanMatchesTheClampedTarget() {
        // E[clamp(X, a, b)] for X ~ Exp(mean μ) is a + μ(e^(-a/μ) - e^(-b/μ)).
        listOf(
            PunchConfig(), // Defaults: about 2941 ms.
            PunchConfig(meanGapMs = 3.s, minGapMs = 2.s, maxGapMs = 4.s), // Heavy clamping: about 2750 ms.
            PunchConfig(meanGapMs = 1_800L, minGapMs = 600L, maxGapMs = 8.s), // Advanced preset.
        ).forEach { config ->
            val mu = config.meanGapMs.toDouble()
            val a = config.minGapMs.toDouble()
            val b = config.maxGapMs.toDouble()
            val target = a + mu * (exp(-a / mu) - exp(-b / mu))
            val all = gaps(config)
            assertClose(target, all.average(), tolerance = 0.02 * target, what = "mean for $config")
        }
    }

    @Test
    fun exponentialFloorAndCeilingCatchTheRightShareOfGaps() {
        // With the defaults, P(X < 1 s) = 1 - e^(-1/3) ≈ 0.283 of raw gaps
        // are too short to react to and get raised to the floor, and
        // P(X > 8 s) = e^(-8/3) ≈ 0.069 get cut to the ceiling.
        val config = PunchConfig()
        val all = gaps(config)
        val atMin = all.count { it == config.minGapMs }.toDouble() / all.size
        val atMax = all.count { it == config.maxGapMs }.toDouble() / all.size
        assertClose(1 - exp(-1.0 / 3), atMin, tolerance = 0.02, what = "share at min")
        assertClose(exp(-8.0 / 3), atMax, tolerance = 0.01, what = "share at max")
    }

    @Test
    fun normalCentersOnTheMeanWithTheSetSpread() {
        // Bounds 6 std-devs away, so clamping doesn't move the moments.
        val config =
            PunchConfig(
                meanGapMs = 5.s,
                minGapMs = 2.s,
                maxGapMs = 8.s,
                distribution = GapDistribution.Normal,
                stdDevMs = 500L,
            )
        val all = gaps(config)
        val mean = all.average()
        val sd = sqrt(all.sumOf { (it - mean) * (it - mean) } / (all.size - 1))
        assertClose(5_000.0, mean, tolerance = 25.0, what = "mean")
        assertClose(500.0, sd, tolerance = 25.0, what = "std-dev")
    }

    @Test
    fun uniformIsSymmetricAroundTheMeanAndAsWideAsTheBoundsAllow() {
        // Beginner preset: mean 4 s, min 2 s. The half-width is
        // min(4 - 2, 8 - 4) = 2 s, so gaps spread evenly over 2..6 s.
        val config = PunchConfig(meanGapMs = 4.s, minGapMs = 2.s, maxGapMs = 8.s, distribution = GapDistribution.Uniform)
        val all = gaps(config)
        assertTrue("${all.min()}..${all.max()}", all.all { it in 2.s..6.s })
        assertClose(4_000.0, all.average(), tolerance = 50.0, what = "mean")
        // Evenly spread: each quarter of the range gets about a quarter.
        val quarters = all.groupingBy { ((it - 2.s) / 1_000L).coerceAtMost(3L) }.eachCount()
        (0L..3L).forEach { q -> assertClose(0.25, quarters.getValue(q).toDouble() / all.size, 0.02, "quarter $q") }
    }

    @Test
    fun fixedIsAMetronome() {
        assertEquals(setOf(3.s), gaps(PunchConfig(distribution = GapDistribution.Fixed)).toSet())
    }

    @Test
    fun minMeanAndMaxAllEqualGivesTheSameGapForEveryDistribution() {
        val base = PunchConfig(meanGapMs = 2.s, minGapMs = 2.s, maxGapMs = 2.s)
        GapDistribution.entries.forEach { distribution ->
            assertEquals(distribution.name, setOf(2.s), gaps(base.copy(distribution = distribution), n = 1_000).toSet())
        }
    }

    @Test
    fun changingTheMeanChangesThePace() {
        val slow = gaps(PunchConfig(meanGapMs = 6.s, minGapMs = 500L, maxGapMs = 30.s)).average()
        val fast = gaps(PunchConfig(meanGapMs = 1_500L, minGapMs = 500L, maxGapMs = 30.s)).average()
        assertTrue("slow $slow vs fast $fast", slow > 3 * fast)
    }

    private companion object {
        const val SEED = 20_261_114L
        const val SAMPLES = 10_000
    }
}
