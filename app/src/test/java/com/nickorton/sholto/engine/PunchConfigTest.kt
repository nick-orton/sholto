package com.nickorton.sholto.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class PunchConfigTest {
    @Test
    fun defaultsMatchTheDesignDoc() {
        val config = PunchConfig()

        assertEquals(3.s, config.meanGapMs)
        assertEquals(1.s, config.minGapMs)
        assertEquals(8.s, config.maxGapMs)
        assertEquals(GapDistribution.Exponential, config.distribution)
        assertEquals(1.s, config.stdDevMs)
        assertFalse(config.avoidRepeats)
        assertEquals(Punch.entries.map { it to 1 }, config.pool)
    }

    @Test
    fun poolLeavesOutDisabledPunchesAndZeroWeights() {
        val config =
            PunchConfig(
                weights = mapOf(Punch.One to 5, Punch.Two to 0),
                enabled = setOf(Punch.One, Punch.Two, Punch.Three),
            )
        assertEquals(listOf(Punch.One to 5, Punch.Three to 1), config.pool)
    }

    @Test
    fun disablingAPunchKeepsItsWeight() {
        val config = PunchConfig(weights = mapOf(Punch.Six to 4), enabled = Punch.entries.toSet() - Punch.Six)
        assertEquals(4, config.weightOf(Punch.Six))
        assertEquals(4, config.copy(enabled = Punch.entries.toSet()).pool.toMap()[Punch.Six])
    }

    @Test
    fun acceptsMinMeanAndMaxAllEqual() {
        PunchConfig(meanGapMs = 2.s, minGapMs = 2.s, maxGapMs = 2.s)
    }

    @Test
    fun rejectsImpossibleGaps() {
        assertThrows(IllegalArgumentException::class.java) { PunchConfig(minGapMs = 0L) }
        assertThrows(IllegalArgumentException::class.java) { PunchConfig(meanGapMs = 500L, minGapMs = 1.s) }
        assertThrows(IllegalArgumentException::class.java) { PunchConfig(meanGapMs = 9.s, maxGapMs = 8.s) }
        assertThrows(IllegalArgumentException::class.java) { PunchConfig(stdDevMs = 0L) }
    }

    @Test
    fun rejectsAPoolThatCantCallAnything() {
        assertThrows(IllegalArgumentException::class.java) { PunchConfig(enabled = emptySet()) }
        assertThrows(IllegalArgumentException::class.java) {
            PunchConfig(weights = Punch.entries.associateWith { 0 })
        }
        assertThrows(IllegalArgumentException::class.java) {
            // The only enabled punch has weight 0.
            PunchConfig(weights = mapOf(Punch.One to 3, Punch.Two to 0), enabled = setOf(Punch.Two))
        }
    }

    @Test
    fun rejectsNegativeWeights() {
        assertThrows(IllegalArgumentException::class.java) { PunchConfig(weights = mapOf(Punch.One to -1)) }
    }
}
