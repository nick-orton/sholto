package com.nickorton.sholto.data

import kotlin.math.roundToInt

/**
 * The "final push" in combined mode (DESIGN.md §4): during the Warning phase
 * the mean gap between calls is multiplied by [factor], so calls come faster.
 */
data class FinalPush(
    val enabled: Boolean = true,
    val factor: Float = CombinedLimits.DEFAULT_PUSH_FACTOR,
) {
    /** What the mean gap is multiplied by during Warning: 1.0 when off. */
    val meanFactor: Double get() = if (enabled) factor.toDouble() else 1.0

    fun withFactor(factor: Float): FinalPush = copy(factor = CombinedLimits.clampPushFactor(factor))

    fun sanitized(): FinalPush = withFactor(factor)
}

/**
 * Everything a combined session uses. Combined mode has no round or punch
 * settings of its own: it runs the punch caller inside the round timer, so
 * it uses the [timer] and [caller] settings as they are, plus [finalPush].
 *
 * From the caller settings it takes the pacing, punch pool and voice. The
 * caller's session length doesn't apply, since the rounds set the length.
 */
data class CombinedSettings(
    val timer: TimerSettings = TimerSettings(),
    val caller: CallerSettings = CallerSettings(),
    val finalPush: FinalPush = FinalPush(),
)

/** The range the settings screen offers for the final-push factor. */
object CombinedLimits {
    const val MIN_PUSH_FACTOR = 0.3f
    const val MAX_PUSH_FACTOR = 0.9f
    const val PUSH_FACTOR_STEP = 0.1f
    const val DEFAULT_PUSH_FACTOR = 0.6f

    /** Within range, rounded to a tenth. */
    fun clampPushFactor(factor: Float): Float {
        val tenths = (factor.coerceIn(MIN_PUSH_FACTOR, MAX_PUSH_FACTOR) * 10f).roundToInt()
        return tenths / 10f
    }

    fun stepPushFactorUp(factor: Float): Float = clampPushFactor(factor + PUSH_FACTOR_STEP)

    fun stepPushFactorDown(factor: Float): Float = clampPushFactor(factor - PUSH_FACTOR_STEP)

    fun canStepPushFactorUp(factor: Float): Boolean = clampPushFactor(factor) < MAX_PUSH_FACTOR

    fun canStepPushFactorDown(factor: Float): Boolean = clampPushFactor(factor) > MIN_PUSH_FACTOR
}
