package com.nickorton.sholto.audio

import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.engine.PhaseChange
import com.nickorton.sholto.engine.RoundState
import com.nickorton.sholto.engine.SessionStatus

/**
 * Something in a session worth hearing or feeling (DESIGN.md §2.3). Each
 * [CuePlayer] decides what a cue sounds or feels like.
 */
enum class Cue {
    /** A round begins: single bell. */
    RoundStart,

    /** Work turns into Warning: wood-block clack ×3. */
    Warning,

    /** A round ends and Rest begins: triple bell. */
    RoundEnd,

    /** Each of the last 3 seconds before a round starts: short beep. */
    Countdown,

    /** The last round ends: long bell. */
    SessionDone,
}

/** Plays cues as sound, vibration or anything else. */
fun interface CuePlayer {
    fun play(cue: Cue)

    companion object {
        /** Plays nothing. */
        val None = CuePlayer {}
    }
}

/** Plays each cue on every player in turn. */
class CompositeCuePlayer(
    private vararg val players: CuePlayer,
) : CuePlayer {
    override fun play(cue: Cue) = players.forEach { it.play(cue) }
}

/**
 * Works out which cues are due from the engine's phase changes and its
 * current state. Pure Kotlin, so it can be tested on the JVM.
 *
 * One planner follows one session. Each countdown beep is given once, even if
 * the session is paused and resumed around it.
 *
 * @param countdownSeconds How many beeps before a round starts.
 * @param lateToleranceMs A beep this late or later is dropped: a beep at the
 *   wrong moment is worse than none, for example right after resuming 2.5 s
 *   before a round.
 */
class CuePlanner(
    private val countdownSeconds: Int = 3,
    private val lateToleranceMs: Long = 250L,
) {
    private var lastCountdown: Triple<Phase, Int, Long>? = null

    /**
     * The cues to play now, given the phase changes since the last call and
     * the state right after them.
     */
    fun cuesFor(
        changes: List<PhaseChange>,
        state: RoundState,
    ): List<Cue> =
        listOfNotNull(
            // A late poll can bring several boundaries at once. Only the
            // latest is still worth hearing.
            changes.lastOrNull()?.let(::cueFor),
            countdownCue(state),
        )

    private fun cueFor(change: PhaseChange): Cue? =
        when (change.to) {
            Phase.GetReady -> null

            Phase.Work -> Cue.RoundStart

            // With work = 0 a round starts straight in Warning. The round
            // start is the one that matters.
            Phase.Warning -> if (change.from == Phase.Work) Cue.Warning else Cue.RoundStart

            Phase.Rest -> Cue.RoundEnd

            Phase.Done -> Cue.SessionDone
        }

    private fun countdownCue(state: RoundState): Cue? {
        if (state.status != SessionStatus.Running) return null
        if (state.phase != Phase.Rest && state.phase != Phase.GetReady) return null
        val left = state.phaseTimeLeftMs
        if (left <= 0L) return null
        val second = (left + 999L) / 1000L
        if (second > countdownSeconds) return null
        val markMs = second * 1000L
        // A phase shorter than the countdown starts inside it, and its own
        // cue (if any) is enough for that first mark.
        if (markMs >= state.phaseLengthMs) return null
        if (left <= markMs - lateToleranceMs) return null
        val key = Triple(state.phase, state.round, second)
        if (key == lastCountdown) return null
        lastCountdown = key
        return Cue.Countdown
    }
}
