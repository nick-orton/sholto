package com.nickorton.sholto.data

import com.nickorton.sholto.audio.Cue
import com.nickorton.sholto.engine.RoundConfig

/** What a tap does during a session (DESIGN.md §2.2). */
enum class TapBehavior {
    /** Pause and show Resume / End session. A mistaken tap loses nothing. */
    Pause,

    /** End the session right away, as in the original sketch. */
    Stop,
}

/**
 * Every timer setting from DESIGN.md §2.4.
 *
 * The round timing lives in a [RoundConfig], whose own checks make an
 * impossible combination (such as a warning longer than the round)
 * unrepresentable. [TimerLimits] adds the narrower ranges the settings screen
 * offers.
 *
 * @property sounds The cues that make a sound. Vibration is separate.
 */
data class TimerSettings(
    val round: RoundConfig = RoundConfig(),
    val tapBehavior: TapBehavior = TapBehavior.Pause,
    val sounds: Set<Cue> = Cue.entries.toSet(),
    val vibration: Boolean = true,
    val keepScreenOn: Boolean = true,
) {
    /** Sets the round length, shortening the warning if it no longer fits. */
    fun withRoundLength(ms: Long): TimerSettings {
        val length = TimerLimits.roundLength.clamp(ms)
        return copy(
            round =
                round.copy(
                    roundLengthMs = length,
                    warningLengthMs = round.warningLengthMs.coerceAtMost(length),
                ),
        )
    }

    fun withWarningLength(ms: Long): TimerSettings =
        copy(round = round.copy(warningLengthMs = TimerLimits.warningLength(round.roundLengthMs).clamp(ms)))

    fun withRestLength(ms: Long): TimerSettings = copy(round = round.copy(restLengthMs = TimerLimits.restLength.clamp(ms)))

    /** Sets the number of rounds, or null for unlimited. */
    fun withRounds(rounds: Int?): TimerSettings =
        copy(round = round.copy(rounds = rounds?.coerceIn(TimerLimits.MIN_ROUNDS, TimerLimits.MAX_ROUNDS)))

    fun withGetReadyLength(ms: Long): TimerSettings = copy(round = round.copy(getReadyLengthMs = TimerLimits.getReadyLength.clamp(ms)))

    fun withSound(
        cue: Cue,
        on: Boolean,
    ): TimerSettings = copy(sounds = if (on) sounds + cue else sounds - cue)

    /**
     * These settings pulled inside [TimerLimits]. Used on everything read from
     * storage, so a damaged or outdated file can't start an impossible session.
     */
    fun sanitized(): TimerSettings =
        withRoundLength(round.roundLengthMs)
            .withWarningLength(round.warningLengthMs)
            .withRestLength(round.restLengthMs)
            .withRounds(round.rounds)
            .withGetReadyLength(round.getReadyLengthMs)
}

/**
 * A range of lengths offered in steps, e.g. 0:30 to 10:00 in 15 s steps.
 * All values are in milliseconds.
 */
data class LengthRange(
    val minMs: Long,
    val maxMs: Long,
    val stepMs: Long,
) {
    init {
        require(minMs in 0..maxMs) { "minMs must be in 0..$maxMs, was $minMs" }
        require(stepMs > 0) { "stepMs must be positive, was $stepMs" }
    }

    fun clamp(ms: Long): Long = ms.coerceIn(minMs, maxMs)

    /** The next step above [ms], or [maxMs]. Off-step values snap to a step. */
    fun stepUp(ms: Long): Long = clamp((Math.floorDiv(clamp(ms), stepMs) + 1) * stepMs)

    /** The next step below [ms], or [minMs]. Off-step values snap to a step. */
    fun stepDown(ms: Long): Long = clamp((Math.floorDiv(clamp(ms) - 1, stepMs)) * stepMs)

    fun canStepUp(ms: Long): Boolean = ms < maxMs

    fun canStepDown(ms: Long): Boolean = ms > minMs
}

/** The ranges the settings screen offers (DESIGN.md §2.4). */
object TimerLimits {
    val roundLength = LengthRange(minMs = 30_000L, maxMs = 10 * 60_000L, stepMs = 15_000L)
    val restLength = LengthRange(minMs = 0L, maxMs = 5 * 60_000L, stepMs = 15_000L)
    val getReadyLength = LengthRange(minMs = 0L, maxMs = 30_000L, stepMs = 5_000L)

    /** The warning can be anything from none to the whole round. */
    fun warningLength(roundLengthMs: Long) = LengthRange(minMs = 0L, maxMs = roundLengthMs, stepMs = 5_000L)

    const val MIN_ROUNDS = 1
    const val MAX_ROUNDS = 99
}
