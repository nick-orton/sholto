package com.nickorton.sholto.ui.timer

import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.engine.RoundConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test
    fun `countdown rounds up so the last second shows 0_01`() {
        assertEquals("3:00", formatCountdown(180_000L))
        assertEquals("3:00", formatCountdown(179_001L))
        assertEquals("2:59", formatCountdown(179_000L))
        assertEquals("0:01", formatCountdown(1L))
        assertEquals("0:00", formatCountdown(0L))
        assertEquals("0:10", formatCountdown(9_950L))
    }

    @Test
    fun `duration rounds down and shows hours past an hour`() {
        assertEquals("0:59", formatDuration(59_999L))
        assertEquals("10:10", formatDuration(610_000L))
        assertEquals("1:00:00", formatDuration(3_600_000L))
        assertEquals("1:02:03", formatDuration(3_723_000L))
    }

    @Test
    fun `summary for the default config`() {
        assertEquals("3:00 × ∞ · 1:00 rest", formatSummary(RoundConfig()))
    }

    @Test
    fun `summary with fixed rounds and no rest`() {
        assertEquals(
            "0:30 × 10 · no rest",
            formatSummary(RoundConfig(roundLengthMs = 30_000L, warningLengthMs = 0L, restLengthMs = 0L, rounds = 10)),
        )
    }

    @Test
    fun `phase labels follow the design`() {
        assertEquals("GET READY", phaseLabel(Phase.GetReady, 30_000L))
        assertEquals("WORK", phaseLabel(Phase.Work, 30_000L))
        assertEquals("30 SEC", phaseLabel(Phase.Warning, 30_000L))
        assertEquals("1 MIN", phaseLabel(Phase.Warning, 60_000L))
        assertEquals("1:30 LEFT", phaseLabel(Phase.Warning, 90_000L))
        assertEquals("REST", phaseLabel(Phase.Rest, 30_000L))
    }

    @Test
    fun `round label shows the total when there is one`() {
        assertEquals("ROUND 3 / 6", roundLabel(Phase.Work, 3, 6))
        assertEquals("ROUND 3", roundLabel(Phase.Warning, 3, null))
        assertEquals("ROUND 1", roundLabel(Phase.GetReady, 1, null))
    }

    @Test
    fun `round label during rest names the next round`() {
        assertEquals("NEXT: ROUND 4 / 6", roundLabel(Phase.Rest, 3, 6))
        assertEquals("NEXT: ROUND 2", roundLabel(Phase.Rest, 1, null))
    }
}
