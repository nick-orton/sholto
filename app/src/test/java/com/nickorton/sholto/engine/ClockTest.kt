package com.nickorton.sholto.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ClockTest {
    @Test
    fun fixedClockReturnsItsTime() {
        val clock = Clock { 1_000L }
        assertEquals(1_000L, clock.now())
    }

    @Test
    fun fakeClockMovesOnlyWhenTold() {
        val clock = FakeClock(startMs = 500L)
        assertEquals(500L, clock.now())

        clock.advanceBy(250L)
        assertEquals(750L, clock.now())

        clock.advanceTo(2_000L)
        assertEquals(2_000L, clock.now())
    }

    @Test
    fun fakeClockNeverGoesBackwards() {
        val clock = FakeClock(startMs = 1_000L)
        assertThrows(IllegalArgumentException::class.java) { clock.advanceBy(-1L) }
        assertThrows(IllegalArgumentException::class.java) { clock.advanceTo(999L) }
        assertEquals(1_000L, clock.now())
    }

    // Smoke test that coroutines-test is set up, for the ViewModel tests in M2.
    @Test
    fun runTestSkipsDelays() =
        runTest {
            // A 1-hour delay finishes instantly under virtual time.
            delay(60 * 60 * 1_000L)
            assertEquals(1, 1)
        }
}
