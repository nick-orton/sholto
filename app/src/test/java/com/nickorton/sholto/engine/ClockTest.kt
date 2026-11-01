package com.nickorton.sholto.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Smoke test that the JVM unit-test setup (JUnit + coroutines-test) works. */
class ClockTest {
    @Test
    fun fixedClockReturnsItsTime() {
        val clock = Clock { 1_000L }
        assertEquals(1_000L, clock.now())
    }

    @Test
    fun runTestSkipsDelays() =
        runTest {
            // A 1-hour delay finishes instantly under virtual time.
            delay(60 * 60 * 1_000L)
            assertEquals(1, 1)
        }
}
