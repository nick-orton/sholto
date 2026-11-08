package com.nickorton.sholto.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RoundConfigTest {
    @Test
    fun defaultsMatchTheDesignDoc() {
        val config = RoundConfig()

        assertEquals(180.s, config.roundLengthMs)
        assertEquals(30.s, config.warningLengthMs)
        assertEquals(150.s, config.workLengthMs)
        assertEquals(60.s, config.restLengthMs)
        assertNull(config.rounds)
        assertEquals(10.s, config.getReadyLengthMs)
        assertNull(config.totalLengthMs)
    }

    @Test
    fun workIsTheRoundMinusTheWarning() {
        assertEquals(90.s, RoundConfig(roundLengthMs = 120.s, warningLengthMs = 30.s).workLengthMs)
        assertEquals(0L, RoundConfig(roundLengthMs = 120.s, warningLengthMs = 120.s).workLengthMs)
        assertEquals(120.s, RoundConfig(roundLengthMs = 120.s, warningLengthMs = 0L).workLengthMs)
    }

    @Test
    fun totalLengthLeavesOutRestAfterTheLastRound() {
        assertEquals(10.s + 12 * 180.s + 11 * 60.s, RoundConfig(rounds = 12).totalLengthMs)
        assertEquals(10.s + 180.s, RoundConfig(rounds = 1).totalLengthMs)
        assertEquals(10 * 30.s + 9 * 30.s, hiit().totalLengthMs)
    }

    @Test
    fun acceptsTheEdgesOfEachRange() {
        RoundConfig(roundLengthMs = 1L, warningLengthMs = 1L, restLengthMs = 0L, rounds = 1, getReadyLengthMs = 0L)
        RoundConfig(warningLengthMs = 0L)
    }

    @Test
    fun rejectsImpossibleConfigs() {
        assertThrows(IllegalArgumentException::class.java) { RoundConfig(roundLengthMs = 0L, warningLengthMs = 0L) }
        assertThrows(IllegalArgumentException::class.java) { RoundConfig(roundLengthMs = -1L, warningLengthMs = 0L) }
        assertThrows(IllegalArgumentException::class.java) { RoundConfig(roundLengthMs = 60.s, warningLengthMs = 61.s) }
        assertThrows(IllegalArgumentException::class.java) { RoundConfig(warningLengthMs = -1L) }
        assertThrows(IllegalArgumentException::class.java) { RoundConfig(restLengthMs = -1L) }
        assertThrows(IllegalArgumentException::class.java) { RoundConfig(getReadyLengthMs = -1L) }
        assertThrows(IllegalArgumentException::class.java) { RoundConfig(rounds = 0) }
        assertThrows(IllegalArgumentException::class.java) { RoundConfig(rounds = -3) }
        // copy() checks too.
        assertThrows(IllegalArgumentException::class.java) { RoundConfig().copy(warningLengthMs = 200.s) }
    }

    // DESIGN.md §2.4: HIIT is 0:30 × 10, 0:30 rest, no warning.
    private fun hiit() =
        RoundConfig(
            roundLengthMs = 30.s,
            warningLengthMs = 0L,
            restLengthMs = 30.s,
            rounds = 10,
            getReadyLengthMs = 0L,
        )
}
