package com.nickorton.sholto.audio

import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.engine.PhaseChange
import com.nickorton.sholto.engine.RoundState
import com.nickorton.sholto.engine.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CuePlannerTest {
    private fun state(
        phase: Phase,
        timeLeftMs: Long,
        lengthMs: Long = 60_000L,
        round: Int = 1,
        status: SessionStatus = SessionStatus.Running,
    ) = RoundState(status, phase, round, null, 0, lengthMs, timeLeftMs, 0f, 0L)

    private fun change(
        from: Phase?,
        to: Phase,
    ) = PhaseChange(from, to, round = 1, atElapsedMs = 0L)

    private val midWork = state(Phase.Work, 30_000L)

    @Test
    fun `each phase change maps to its cue`() {
        val planner = CuePlanner()
        assertEquals(listOf<Cue>(), planner.cuesFor(listOf(change(null, Phase.GetReady)), state(Phase.GetReady, 10_000L)))
        assertEquals(listOf(Cue.RoundStart), planner.cuesFor(listOf(change(Phase.GetReady, Phase.Work)), midWork))
        assertEquals(listOf(Cue.Warning), planner.cuesFor(listOf(change(Phase.Work, Phase.Warning)), midWork))
        assertEquals(listOf(Cue.RoundEnd), planner.cuesFor(listOf(change(Phase.Warning, Phase.Rest)), midWork))
        assertEquals(listOf(Cue.SessionDone), planner.cuesFor(listOf(change(Phase.Warning, Phase.Done)), midWork))
    }

    @Test
    fun `a round that starts in Warning gets the round start bell`() {
        val planner = CuePlanner()
        assertEquals(listOf(Cue.RoundStart), planner.cuesFor(listOf(change(Phase.Rest, Phase.Warning)), midWork))
    }

    @Test
    fun `back to back rounds get one bell`() {
        val planner = CuePlanner()
        assertEquals(listOf(Cue.RoundStart), planner.cuesFor(listOf(change(Phase.Work, Phase.Work)), midWork))
    }

    @Test
    fun `a late poll plays only the latest change`() {
        val planner = CuePlanner()
        val changes = listOf(change(Phase.Work, Phase.Warning), change(Phase.Warning, Phase.Rest))
        assertEquals(listOf(Cue.RoundEnd), planner.cuesFor(changes, state(Phase.Rest, 50_000L)))
    }

    @Test
    fun `countdown beeps once per second in the last 3 s of rest`() {
        val planner = CuePlanner()
        assertEquals(listOf<Cue>(), planner.cuesFor(emptyList(), state(Phase.Rest, 3_001L)))
        assertEquals(listOf(Cue.Countdown), planner.cuesFor(emptyList(), state(Phase.Rest, 3_000L)))
        assertEquals(listOf<Cue>(), planner.cuesFor(emptyList(), state(Phase.Rest, 2_900L)))
        assertEquals(listOf(Cue.Countdown), planner.cuesFor(emptyList(), state(Phase.Rest, 2_000L)))
        assertEquals(listOf(Cue.Countdown), planner.cuesFor(emptyList(), state(Phase.Rest, 1_000L)))
        assertEquals(listOf<Cue>(), planner.cuesFor(emptyList(), state(Phase.Rest, 500L)))
    }

    @Test
    fun `countdown also runs during get ready but never in a round`() {
        val planner = CuePlanner()
        assertEquals(listOf(Cue.Countdown), planner.cuesFor(emptyList(), state(Phase.GetReady, 2_000L)))
        assertEquals(listOf<Cue>(), planner.cuesFor(emptyList(), state(Phase.Work, 2_000L)))
        assertEquals(listOf<Cue>(), planner.cuesFor(emptyList(), state(Phase.Warning, 2_000L)))
    }

    @Test
    fun `a late beep is dropped`() {
        val planner = CuePlanner()
        assertEquals(listOf(Cue.Countdown), planner.cuesFor(emptyList(), state(Phase.Rest, 2_751L)))
        assertEquals(listOf<Cue>(), planner.cuesFor(emptyList(), state(Phase.Rest, 1_750L)))
    }

    @Test
    fun `no beep while paused`() {
        val planner = CuePlanner()
        assertEquals(listOf<Cue>(), planner.cuesFor(emptyList(), state(Phase.Rest, 2_000L, status = SessionStatus.Paused)))
    }

    @Test
    fun `a rest shorter than the countdown skips the mark at its start`() {
        val planner = CuePlanner()
        // A 2 s rest: the 2 s mark is its first instant, where the triple bell plays.
        val atStart = state(Phase.Rest, 2_000L, lengthMs = 2_000L)
        assertEquals(listOf(Cue.RoundEnd), planner.cuesFor(listOf(change(Phase.Warning, Phase.Rest)), atStart))
        assertEquals(listOf(Cue.Countdown), planner.cuesFor(emptyList(), state(Phase.Rest, 1_000L, lengthMs = 2_000L)))
    }

    @Test
    fun `each rest gets its own countdown`() {
        val planner = CuePlanner()
        assertEquals(listOf(Cue.Countdown), planner.cuesFor(emptyList(), state(Phase.Rest, 1_000L, round = 1)))
        assertEquals(listOf(Cue.Countdown), planner.cuesFor(emptyList(), state(Phase.Rest, 1_000L, round = 2)))
    }
}
