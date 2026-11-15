package com.nickorton.sholto.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Checks on which punch is called. Fixed seeds keep every run the same. */
class PunchEngineCallTest {
    private fun calls(
        config: PunchConfig,
        seed: Long = SEED,
        n: Int = SAMPLES,
    ): List<Punch> {
        val engine = PunchEngine(config, Random(seed))
        return List(n) { engine.nextCall().punches.single() }
    }

    @Test
    fun weightsAreRespected() {
        val weights =
            mapOf(
                Punch.One to 5,
                Punch.Two to 4,
                Punch.Three to 3,
                Punch.Four to 2,
                Punch.Five to 1,
                Punch.Six to 1,
                Punch.OneBody to 2,
                Punch.TwoBody to 1,
                Punch.ThreeBody to 0, // Weight 0: never called.
                Punch.FourBody to 3, // Disabled below: never called.
            )
        val config = PunchConfig(weights = weights, enabled = Punch.entries.toSet() - Punch.FourBody)
        val counts = calls(config).groupingBy { it }.eachCount()

        assertEquals(0, counts[Punch.ThreeBody] ?: 0)
        assertEquals(0, counts[Punch.FourBody] ?: 0)

        // Chi-squared goodness of fit over the 8 callable punches.
        val pool = config.pool
        val total = pool.sumOf { it.second }.toDouble()
        val chiSquared =
            pool.sumOf { (punch, weight) ->
                val expected = SAMPLES * weight / total
                val observed = (counts[punch] ?: 0).toDouble()
                (observed - expected) * (observed - expected) / expected
            }
        // Critical value for 7 degrees of freedom at p = 0.001.
        assertTrue("chi-squared $chiSquared, counts $counts", chiSquared < 24.322)
    }

    @Test
    fun equalWeightsAreRoughlyEven() {
        val counts = calls(PunchConfig()).groupingBy { it }.eachCount()
        val chiSquared =
            Punch.entries.sumOf {
                val expected = SAMPLES / 10.0
                val observed = (counts[it] ?: 0).toDouble()
                (observed - expected) * (observed - expected) / expected
            }
        // Critical value for 9 degrees of freedom at p = 0.001.
        assertTrue("chi-squared $chiSquared, counts $counts", chiSquared < 27.877)
    }

    @Test
    fun avoidRepeatsNeverCallsTheSamePunchTwiceInARow() {
        // Heavy weight on 1 makes repeats very likely without the rule.
        val config = PunchConfig(weights = mapOf(Punch.One to 5), avoidRepeats = true)
        val all = calls(config)
        assertTrue(all.zipWithNext().none { (a, b) -> a == b })
        assertEquals(Punch.entries.toSet(), all.toSet())

        // The same config without the rule does repeat.
        assertTrue(calls(config.copy(avoidRepeats = false)).zipWithNext().any { (a, b) -> a == b })
    }

    @Test
    fun avoidRepeatsWithTwoPunchesAlternates() {
        val config = PunchConfig(enabled = setOf(Punch.One, Punch.Two), avoidRepeats = true)
        val all = calls(config, n = 1_000)
        assertTrue(all.zipWithNext().all { (a, b) -> a != b })
    }

    @Test
    fun avoidRepeatsWithOnePunchRepeatsRatherThanStalling() {
        val config = PunchConfig(enabled = setOf(Punch.Three), avoidRepeats = true)
        assertEquals(List(100) { Punch.Three }, calls(config, n = 100))
    }

    @Test
    fun theSameSeedGivesTheSameSequence() {
        val config = PunchConfig(avoidRepeats = true)

        fun sequence(seed: Long): List<Pair<Long, Combo>> {
            val engine = PunchEngine(config, Random(seed))
            return List(1_000) { engine.nextGap() to engine.nextCall() }
        }
        assertEquals(sequence(SEED), sequence(SEED))
        assertNotEquals(sequence(SEED), sequence(SEED + 1))
    }

    private companion object {
        const val SEED = 20_261_114L
        const val SAMPLES = 10_000
    }
}
