package com.nickorton.sholto.ui.timer

import com.nickorton.sholto.data.CallerLimits
import com.nickorton.sholto.data.CallerSettings
import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.engine.RoundConfig
import com.nickorton.sholto.ui.caller.CallerUiState

/**
 * Time left in a phase, as `M:SS` (or `H:MM:SS`). It rounds up, so a fresh
 * 3-minute phase shows `3:00` and the last second shows `0:01`, never `0:00`.
 */
fun formatCountdown(timeLeftMs: Long): String = formatSeconds(ceilDiv(timeLeftMs.coerceAtLeast(0L), 1000L))

/** Time that has passed, as `M:SS` (or `H:MM:SS`). It rounds down. */
fun formatDuration(durationMs: Long): String = formatSeconds(durationMs.coerceAtLeast(0L) / 1000L)

/** The Home summary line, e.g. `3:00 × ∞ · 1:00 rest`. */
fun formatSummary(config: RoundConfig): String {
    val rounds = config.rounds?.toString() ?: "∞"
    val rest = if (config.restLengthMs == 0L) "no rest" else "${formatDuration(config.restLengthMs)} rest"
    return "${formatDuration(config.roundLengthMs)} × $rounds · $rest"
}

/** A sub-minute duration in seconds with one decimal place, e.g. `3.0 s` or `0.6 s`. */
fun formatSecondsTenths(ms: Long): String {
    val tenths = (ms.coerceAtLeast(0L) + 50L) / 100L
    return "${tenths / 10}.${tenths % 10} s"
}

/** A TTS speech rate, e.g. `1.2×`. */
fun formatSpeechRate(rate: Float): String = "${CallerLimits.clampSpeechRate(rate)}×"

/** The Home summary line for the punch caller, e.g. `3.0 s mean · 10 punches · ∞`. */
fun formatCallerSummary(settings: CallerSettings): String {
    val mean = "${formatSecondsTenths(settings.punch.meanGapMs)} mean"
    val count = settings.punch.pool.size
    val punches = if (count == 1) "1 punch" else "$count punches"
    val length = settings.sessionLengthMs?.let(::formatDuration) ?: "∞"
    return "$mean · $punches · $length"
}

/** The label for a phase, e.g. `WORK` or `30 SEC` (DESIGN.md §5.5). */
fun phaseLabel(
    phase: Phase,
    warningLengthMs: Long,
): String =
    when (phase) {
        Phase.GetReady -> "GET READY"
        Phase.Work -> "WORK"
        Phase.Warning -> warningLabel(warningLengthMs)
        Phase.Rest -> "REST"
        Phase.Done -> "DONE"
    }

/**
 * The round counter, e.g. `ROUND 3 / 6`, or `ROUND 3` when unlimited. During
 * Rest it names the round coming up, since that's what you're waiting for.
 */
fun roundLabel(
    phase: Phase,
    round: Int,
    totalRounds: Int?,
): String {
    val shown = if (phase == Phase.Rest) round + 1 else round
    val counter = if (totalRounds == null) "ROUND $shown" else "ROUND $shown / $totalRounds"
    return if (phase == Phase.Rest) "NEXT: $counter" else counter
}

/**
 * The ongoing notification title, e.g. `WORK · 2:15` or `PAUSED · 2:15`.
 */
fun notificationTitle(state: TimerUiState.Running): String {
    val head = if (state.isPaused) "PAUSED" else phaseLabel(state.phase, state.warningLengthMs)
    return "$head · ${formatCountdown(state.phaseTimeLeftMs)}"
}

/**
 * The ongoing notification subtitle, e.g. `ROUND 3 / 6` or
 * `WORK · ROUND 3 / 6` when paused.
 */
fun notificationText(state: TimerUiState.Running): String {
    val round = roundLabel(state.phase, state.round, state.totalRounds)
    return if (state.isPaused) "${phaseLabel(state.phase, state.warningLengthMs)} · $round" else round
}

/**
 * The ongoing notification title for the punch caller, e.g.
 * `CALLER · 3B · 1:20` or `PAUSED · 1:20`.
 */
fun callerNotificationTitle(state: CallerUiState.Running): String {
    val clock = state.timeLeftMs?.let(::formatCountdown) ?: formatDuration(state.elapsedMs)
    if (state.isPaused) return "PAUSED · $clock"
    val call = state.currentCall?.label ?: "READY"
    return "CALLER · $call · $clock"
}

/**
 * The ongoing notification subtitle for the punch caller, e.g. `14 calls`.
 */
fun callerNotificationText(state: CallerUiState.Running): String {
    val calls = if (state.callCount == 1) "1 call" else "${state.callCount} calls"
    return if (state.isPaused) "PUNCH CALLER · $calls" else calls
}

private fun warningLabel(warningLengthMs: Long): String {
    val seconds = warningLengthMs / 1000L
    return when {
        seconds < 60 -> "$seconds SEC"
        seconds % 60 == 0L -> "${seconds / 60} MIN"
        else -> "${formatSeconds(seconds)} LEFT"
    }
}

private fun formatSeconds(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:$ss"
    } else {
        "$minutes:$ss"
    }
}

private fun ceilDiv(
    a: Long,
    b: Long,
): Long = (a + b - 1) / b
