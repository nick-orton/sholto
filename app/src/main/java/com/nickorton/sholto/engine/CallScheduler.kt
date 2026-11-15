package com.nickorton.sholto.engine

/**
 * Decides when punch calls happen inside a round-timer session, for combined
 * mode (DESIGN.md §4). Pure math on a [RoundConfig] and a [PunchEngine], with
 * no clock: every time here is active session time, as in [RoundState.elapsedMs],
 * so pauses shift calls automatically.
 *
 * The rules:
 * - Calls happen only during Work and Warning. A call that would land in Rest
 *   (or Get ready) is dropped, and the next round starts fresh: its first call
 *   comes one gap after the round starts.
 * - **Final push:** during Warning the mean gap is multiplied by
 *   [finalPushFactor]. When the pace changes at a phase change, the wait
 *   restarts from that change, so the faster pace begins with the warning
 *   clacks instead of after one more slow gap.
 * - **Bell protection:** every phase change plays a cue, and no call is
 *   spoken within [bellGuardMs] of one. A call starts at least [bellGuardMs]
 *   after a phase change, and its speech (from [speechMs]) ends at least
 *   [bellGuardMs] before the next one. A call that would end too close moves
 *   to [bellGuardMs] after the change (or is dropped if that is Rest or
 *   Done).
 * - **Combos:** the wait to the next call starts once a combo has been spoken
 *   and thrown ([CallTiming.busyMs]).
 *
 * Each call and its gap are drawn from [punches] when the call is scheduled,
 * so for a given seed the calls are the same whenever the session is paused.
 *
 * @param finalPushFactor Multiplies the mean gap during Warning. 1.0 is off.
 * @param speechMs About how long a call takes to say.
 */
class CallScheduler(
    config: RoundConfig,
    private val punches: PunchEngine,
    private val finalPushFactor: Double = 1.0,
    private val bellGuardMs: Long = BELL_GUARD_MS,
    private val speechMs: (Combo) -> Long = { 0L },
) {
    init {
        require(finalPushFactor > 0.0) { "finalPushFactor must be positive, was $finalPushFactor" }
        require(bellGuardMs >= 0L) { "bellGuardMs must not be negative, was $bellGuardMs" }
    }

    private val timeline = RoundTimeline(config)

    /** The first call of the session, or null if none fits. */
    fun firstCall(): ScheduledCall? {
        val start = timeline.spanAt(0L)
        return if (start.phase.hasCalls) {
            val pace = paceOf(start.phase)
            place(punches.nextGap(pace), pace)
        } else {
            // Get ready: the round start resets the gap, as after Rest.
            place(0L, PACE_UNSET)
        }
    }

    /**
     * The call after [previous], or null if the session ends first.
     * [previous] must be a call this scheduler returned.
     */
    fun nextCallAfter(previous: ScheduledCall): ScheduledCall? {
        val pace = paceOf(timeline.spanAt(previous.atMs).phase)
        val waitFromMs = previous.atMs + CallTiming.busyMs(previous.combo, speechMs(previous.combo))
        return place(waitFromMs + punches.nextGap(pace), pace)
    }

    /**
     * Moves [candidateMs], drawn at [drawnPace], to the first time that
     * follows the rules above. Each pass either returns or moves to a later
     * phase. With unlimited rounds and gaps too long for any round to fit a
     * call, it gives up after [MAX_EMPTY_ROUNDS] rounds instead of looping
     * forever.
     */
    private fun place(
        candidateMs: Long,
        drawnPace: Double,
    ): ScheduledCall? {
        val combo = punches.nextCall()
        val speaking = speechMs(combo)
        var t = candidateMs
        var pace = drawnPace
        var emptyRounds = 0
        while (true) {
            val span = timeline.spanAt(t)
            when (span.phase) {
                Phase.Done -> {
                    return null
                }

                Phase.GetReady, Phase.Rest -> {
                    if (++emptyRounds > MAX_EMPTY_ROUNDS) return null
                    // No calls here. Start over one gap into the next phase,
                    // which is always a round (Rest never ends a session).
                    pace = paceOf(timeline.spanAt(span.endMs).phase)
                    t = span.endMs + punches.nextGap(pace)
                }

                Phase.Work, Phase.Warning -> {
                    val phasePace = paceOf(span.phase)
                    if (phasePace != pace) {
                        pace = phasePace
                        t = span.startMs + punches.nextGap(pace)
                    }
                    t = maxOf(t, span.startMs + bellGuardMs)
                    when {
                        // Overshot this phase: look again from where it landed.
                        t >= span.endMs -> {}

                        // Would still be talking near the next cue: try just
                        // after it.
                        t + speaking > span.endMs - bellGuardMs -> {
                            t = span.endMs
                        }

                        else -> {
                            return ScheduledCall(t, combo)
                        }
                    }
                }
            }
        }
    }

    private fun paceOf(phase: Phase): Double = if (phase == Phase.Warning) finalPushFactor else 1.0

    private val Phase.hasCalls: Boolean get() = this == Phase.Work || this == Phase.Warning

    companion object {
        /** No call within this long of a phase change (DESIGN.md §4). */
        const val BELL_GUARD_MS = 1_000L

        private const val PACE_UNSET = -1.0

        // A round with room for a call almost never draws a gap past its end
        // this many times running, so this only stops impossible settings.
        private const val MAX_EMPTY_ROUNDS = 100
    }
}

/** A call and when it's due, in active session time. */
data class ScheduledCall(
    val atMs: Long,
    val combo: Combo,
)
