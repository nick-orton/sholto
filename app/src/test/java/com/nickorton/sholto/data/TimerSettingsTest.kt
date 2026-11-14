package com.nickorton.sholto.data

import com.nickorton.sholto.audio.Cue
import com.nickorton.sholto.engine.RoundConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerSettingsTest {
    @Test
    fun `defaults match DESIGN_md`() {
        val s = TimerSettings()
        assertEquals(3 * 60_000L, s.round.roundLengthMs)
        assertEquals(30_000L, s.round.warningLengthMs)
        assertEquals(60_000L, s.round.restLengthMs)
        assertEquals(null, s.round.rounds)
        assertEquals(10_000L, s.round.getReadyLengthMs)
        assertEquals(TapBehavior.Pause, s.tapBehavior)
        assertEquals(Cue.entries.toSet(), s.sounds)
        assertTrue(s.vibration)
        assertTrue(s.keepScreenOn)
    }

    @Test
    fun `withRoundLength clamps to limits and shortens warning when needed`() {
        val s = TimerSettings(round = RoundConfig(roundLengthMs = 120_000L, warningLengthMs = 60_000L))
        val shorter = s.withRoundLength(45_000L)
        assertEquals(45_000L, shorter.round.roundLengthMs)
        assertEquals(45_000L, shorter.round.warningLengthMs)
        assertEquals(0L, shorter.round.workLengthMs)

        assertEquals(30_000L, s.withRoundLength(5_000L).round.roundLengthMs)
        assertEquals(10 * 60_000L, s.withRoundLength(99 * 60_000L).round.roundLengthMs)
    }

    @Test
    fun `withWarningLength cannot exceed round length`() {
        val s = TimerSettings(round = RoundConfig(roundLengthMs = 60_000L, warningLengthMs = 15_000L))
        assertEquals(60_000L, s.withWarningLength(90_000L).round.warningLengthMs)
        assertEquals(0L, s.withWarningLength(-5_000L).round.warningLengthMs)
    }

    @Test
    fun `rest, rounds and get-ready clamp to their ranges`() {
        val s = TimerSettings()
        assertEquals(0L, s.withRestLength(-10_000L).round.restLengthMs)
        assertEquals(5 * 60_000L, s.withRestLength(10 * 60_000L).round.restLengthMs)

        assertEquals(1, s.withRounds(0).round.rounds)
        assertEquals(99, s.withRounds(200).round.rounds)
        assertEquals(
            null,
            s
                .withRounds(5)
                .withRounds(null)
                .round.rounds,
        )

        assertEquals(0L, s.withGetReadyLength(-1_000L).round.getReadyLengthMs)
        assertEquals(30_000L, s.withGetReadyLength(60_000L).round.getReadyLengthMs)
    }

    @Test
    fun `length range steps up and down and snaps off-step values`() {
        val range = TimerLimits.roundLength // 0:30 .. 10:00 in 0:15 steps
        assertFalse(range.canStepDown(30_000L))
        assertTrue(range.canStepUp(30_000L))
        assertEquals(45_000L, range.stepUp(30_000L))
        assertEquals(30_000L, range.stepDown(45_000L))
        assertEquals(30_000L, range.stepDown(30_000L))

        // Off-step value snaps to the nearest step boundary in the direction moved.
        assertEquals(45_000L, range.stepUp(37_000L))
        assertEquals(30_000L, range.stepDown(37_000L))

        assertFalse(range.canStepUp(10 * 60_000L))
        assertEquals(10 * 60_000L, range.stepUp(10 * 60_000L))
    }

    @Test
    fun `withSound toggles individual cues`() {
        val s = TimerSettings().withSound(Cue.Warning, on = false)
        assertFalse(Cue.Warning in s.sounds)
        assertTrue(Cue.RoundStart in s.sounds)
        assertTrue(Cue.Warning in s.withSound(Cue.Warning, on = true).sounds)
    }
}
