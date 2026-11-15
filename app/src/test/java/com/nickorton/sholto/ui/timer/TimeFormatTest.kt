package com.nickorton.sholto.ui.timer

import com.nickorton.sholto.data.CallerSettings
import com.nickorton.sholto.engine.Combo
import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.engine.Punch
import com.nickorton.sholto.engine.PunchConfig
import com.nickorton.sholto.engine.RoundConfig
import com.nickorton.sholto.engine.Stance
import com.nickorton.sholto.ui.caller.CallerUiState
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

    @Test
    fun `notification title and text show phase, countdown, round and paused state`() {
        val work =
            TimerUiState.Running(
                phase = Phase.Work,
                round = 3,
                totalRounds = 12,
                phaseTimeLeftMs = 135_000L,
                roundProgress = 0.25f,
                warningLengthMs = 30_000L,
                isPaused = false,
            )
        assertEquals("WORK · 2:15", notificationTitle(work))
        assertEquals("ROUND 3 / 12", notificationText(work))

        val paused = work.copy(isPaused = true)
        assertEquals("PAUSED · 2:15", notificationTitle(paused))
        assertEquals("WORK · ROUND 3 / 12", notificationText(paused))

        val rest = work.copy(phase = Phase.Rest, phaseTimeLeftMs = 45_000L)
        assertEquals("REST · 0:45", notificationTitle(rest))
        assertEquals("NEXT: ROUND 4 / 12", notificationText(rest))
    }

    @Test
    fun `caller formatting and notifications`() {
        assertEquals("2.5 s", formatSecondsTenths(2_500L))
        assertEquals("0.8 s", formatSecondsTenths(800L))
        assertEquals("1.2×", formatSpeechRate(1.2f))

        assertEquals(
            "3.0 s mean · 10 punches · ∞",
            formatCallerSummary(CallerSettings()),
        )
        assertEquals(
            "4.0 s mean · 2 punches · 3:00",
            formatCallerSummary(
                CallerSettings(
                    punch = PunchConfig(meanGapMs = 4_000L, minGapMs = 1_500L, maxGapMs = 8_000L, enabled = setOf(Punch.One, Punch.Two)),
                    sessionLengthMs = 180_000L,
                ),
            ),
        )

        val running =
            CallerUiState.Running(
                currentCall = Combo.of(Punch.One),
                stance = Stance.Orthodox,
                callCount = 7,
                callAgeMs = 100L,
                elapsedMs = 15_000L,
                timeLeftMs = 165_000L,
                isPaused = false,
            )
        assertEquals("CALLER · 1 · 2:45", callerNotificationTitle(running))
        assertEquals("7 calls", callerNotificationText(running))

        val pausedUntilStopped = running.copy(timeLeftMs = null, isPaused = true)
        assertEquals("PAUSED · 0:15", callerNotificationTitle(pausedUntilStopped))
        assertEquals("PUNCH CALLER · 7 calls", callerNotificationText(pausedUntilStopped))
    }

    @Test
    fun `phase announcements read naturally aloud`() {
        assertEquals("Get ready, round 1 of 6", phaseAnnouncement(Phase.GetReady, 30_000L, 1, 6))
        assertEquals("Work, round 3 of 6", phaseAnnouncement(Phase.Work, 30_000L, 3, 6))
        assertEquals("Last 30 seconds, round 3 of 6", phaseAnnouncement(Phase.Warning, 30_000L, 3, 6))
        assertEquals("Last 1 minute, round 3", phaseAnnouncement(Phase.Warning, 60_000L, 3, null))
        assertEquals("Last 1 minute 30 seconds, round 2", phaseAnnouncement(Phase.Warning, 90_000L, 2, null))
        assertEquals("Rest, next round 4 of 12", phaseAnnouncement(Phase.Rest, 30_000L, 3, 12))
        assertEquals("Done", phaseAnnouncement(Phase.Done, 30_000L, 12, 12))
    }
}
