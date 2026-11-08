package com.nickorton.sholto.ui.timer

import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.engine.RoundConfig

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
