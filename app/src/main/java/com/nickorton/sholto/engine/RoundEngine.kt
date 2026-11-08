package com.nickorton.sholto.engine

/** A phase of a round-timer session, in the order DESIGN.md §2.1 gives. */
enum class Phase { GetReady, Work, Warning, Rest, Done }

/** Where a session is in its life. */
enum class SessionStatus {
    /** Not started yet. */
    Idle,
    Running,
    Paused,

    /** Reached Done on its own. Only happens with a fixed number of rounds. */
    Finished,

    /** Ended early by [RoundEngine.stop]. */
    Stopped,
}

/**
 * One reading of a session. Every field comes from the same instant.
 *
 * @property round The current round, starting at 1. During GetReady it is
 *   the round about to start. During Rest it is the round just finished.
 * @property totalRounds Null when rounds are unlimited.
 * @property roundsCompleted Rounds whose Work and Warning have fully passed.
 * @property phaseLengthMs Length of the current phase. 0 for Done.
 * @property phaseTimeLeftMs Time until the next phase change. 0 for Done.
 * @property roundProgress How much of the current round's Work and Warning
 *   has passed, from 0 to 1. It is 0 during GetReady, and 1 during Rest and
 *   Done.
 * @property elapsedMs Active session time: time since start, minus pauses.
 */
data class RoundState(
    val status: SessionStatus,
    val phase: Phase,
    val round: Int,
    val totalRounds: Int?,
    val roundsCompleted: Int,
    val phaseLengthMs: Long,
    val phaseTimeLeftMs: Long,
    val roundProgress: Float,
    val elapsedMs: Long,
)

/**
 * The session moved into a new phase.
 *
 * @property from The phase it left, or null for the first phase, entered at
 *   start. It can equal [to] when a round follows another with nothing in
 *   between (for example rest = 0 and warning = 0).
 * @property round The round of the phase entered.
 * @property atElapsedMs When it happened, in active session time. This is the
 *   exact boundary, however late the change is polled.
 */
data class PhaseChange(
    val from: Phase?,
    val to: Phase,
    val round: Int,
    val atElapsedMs: Long,
)

/**
 * The round-timer state machine. Pure Kotlin with no Android dependencies.
 *
 * It never adds up ticks. It stores only when the session started and how
 * long it has been paused, and computes everything else from the [clock] when
 * asked. Polling late or irregularly can't make it drift, and phase boundaries
 * move by exactly the time spent paused.
 *
 * One engine runs one session. To run again, create a new engine.
 *
 * Commands return true if they changed the state and false if they didn't
 * apply (for example pausing a session that is already paused or has
 * finished). A false return is not an error, so a double tap is harmless.
 *
 * All methods are synchronized, so commands and polling can come from
 * different threads.
 */
class RoundEngine(
    val config: RoundConfig,
    private val clock: Clock,
) {
    private val timeline = RoundTimeline(config)
    private val totalLengthMs = config.totalLengthMs

    private var startedAtMs: Long? = null
    private var pausedTotalMs = 0L
    private var pausedAtMs: Long? = null
    private var stoppedAtElapsedMs: Long? = null

    // Active time up to which phase changes have been returned by pollEvents.
    // Null until the first poll after start.
    private var polledThroughMs: Long? = null

    /** Starts the session. Only works once. */
    @Synchronized
    fun start(): Boolean {
        if (startedAtMs != null) return false
        startedAtMs = clock.now()
        return true
    }

    /** Freezes the session where it is. Only works while running. */
    @Synchronized
    fun pause(): Boolean {
        val now = clock.now()
        if (statusAt(elapsedAt(now)) != SessionStatus.Running) return false
        pausedAtMs = now
        return true
    }

    /** Continues a paused session from where it froze. */
    @Synchronized
    fun resume(): Boolean {
        val now = clock.now()
        val pausedAt = pausedAtMs ?: return false
        if (statusAt(elapsedAt(now)) != SessionStatus.Paused) return false
        pausedTotalMs += (now - pausedAt).coerceAtLeast(0L)
        pausedAtMs = null
        return true
    }

    /**
     * Ends the session early, while running or paused. The state stays frozen
     * where it stopped. No phase change is reported for stopping.
     */
    @Synchronized
    fun stop(): Boolean {
        val elapsed = elapsedAt(clock.now())
        val status = statusAt(elapsed)
        if (status != SessionStatus.Running && status != SessionStatus.Paused) return false
        stoppedAtElapsedMs = elapsed
        return true
    }

    /**
     * The session as of now. Before start, it shows the first phase with its
     * full length. It has no side effects.
     */
    @Synchronized
    fun snapshot(): RoundState {
        val elapsed = elapsedAt(clock.now())
        val span = timeline.spanAt(elapsed)
        val sinceRoundStartMs = elapsed - timeline.roundStartMs(span.round)
        val roundProgress =
            when (span.phase) {
                Phase.GetReady -> 0f
                Phase.Work, Phase.Warning -> sinceRoundStartMs.toFloat() / config.roundLengthMs
                Phase.Rest, Phase.Done -> 1f
            }
        val roundsCompleted =
            when (span.phase) {
                Phase.GetReady -> 0
                Phase.Work, Phase.Warning -> span.round - 1
                Phase.Rest, Phase.Done -> span.round
            }
        return RoundState(
            status = statusAt(elapsed),
            phase = span.phase,
            round = span.round,
            totalRounds = config.rounds,
            roundsCompleted = roundsCompleted,
            phaseLengthMs = span.lengthMs,
            phaseTimeLeftMs = if (span.phase == Phase.Done) 0L else span.endMs - elapsed,
            roundProgress = roundProgress,
            elapsedMs = elapsed,
        )
    }

    /**
     * The phase changes since the last call, in order. Each change is returned
     * once. The first call after start includes the entry into the first
     * phase. If a poll comes late, every boundary passed since the last one is
     * included, each with its exact time.
     *
     * Meant for a single consumer, such as the one that plays sounds.
     */
    @Synchronized
    fun pollEvents(): List<PhaseChange> {
        if (startedAtMs == null) return emptyList()
        val elapsed = elapsedAt(clock.now())
        val polledThrough = polledThroughMs
        val changes =
            if (polledThrough == null) {
                val first = timeline.spanAt(0L)
                listOf(PhaseChange(from = null, to = first.phase, round = first.round, atElapsedMs = 0L)) +
                    timeline.changesBetween(0L, elapsed)
            } else {
                timeline.changesBetween(polledThrough, elapsed)
            }
        polledThroughMs = maxOf(polledThrough ?: 0L, elapsed)
        return changes
    }

    private fun elapsedAt(now: Long): Long {
        val startedAt = startedAtMs ?: return 0L
        stoppedAtElapsedMs?.let { return it }
        val activeMs = ((pausedAtMs ?: now) - startedAt - pausedTotalMs).coerceAtLeast(0L)
        return totalLengthMs?.let { minOf(activeMs, it) } ?: activeMs
    }

    private fun statusAt(elapsed: Long): SessionStatus =
        when {
            startedAtMs == null -> SessionStatus.Idle
            stoppedAtElapsedMs != null -> SessionStatus.Stopped
            totalLengthMs != null && elapsed >= totalLengthMs -> SessionStatus.Finished
            pausedAtMs != null -> SessionStatus.Paused
            else -> SessionStatus.Running
        }
}
