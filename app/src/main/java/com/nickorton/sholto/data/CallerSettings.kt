package com.nickorton.sholto.data

import com.nickorton.sholto.engine.Callout
import com.nickorton.sholto.engine.CalloutStyle
import com.nickorton.sholto.engine.GapDistribution
import com.nickorton.sholto.engine.Punch
import com.nickorton.sholto.engine.PunchConfig
import com.nickorton.sholto.engine.Stance
import com.nickorton.sholto.engine.Voice
import kotlin.math.roundToInt

/**
 * Every punch-caller setting from DESIGN.md §3.5.
 *
 * The gap and pool rules live in [PunchConfig], whose checks make an
 * impossible combination (such as `minGapMs > meanGapMs` or an empty pool)
 * unrepresentable. [CallerLimits] adds the ranges the settings screen offers.
 *
 * @property sessionLengthMs Fixed session duration in ms, or null to run
 *   until stopped.
 */
data class CallerSettings(
    val punch: PunchConfig = PunchConfig(),
    val stance: Stance = Stance.Orthodox,
    val calloutStyle: CalloutStyle = CalloutStyle.Numbers,
    val speechRate: Float = CallerLimits.DEFAULT_SPEECH_RATE,
    val voice: Voice = Voice.SystemTts,
    val sessionLengthMs: Long? = null,
    val tapBehavior: TapBehavior = TapBehavior.Pause,
    val keepScreenOn: Boolean = true,
    val finishSound: Boolean = true,
    val finishVibration: Boolean = true,
) {
    /**
     * Sets the mean gap, pulling [PunchConfig.minGapMs] down or
     * [PunchConfig.maxGapMs] up if needed so `min <= mean <= max` always holds.
     */
    fun withMeanGap(ms: Long): CallerSettings {
        val mean = CallerLimits.meanGap.clamp(ms)
        return copy(
            punch =
                punch.copy(
                    meanGapMs = mean,
                    minGapMs = punch.minGapMs.coerceIn(CallerLimits.minGap.minMs, mean),
                    maxGapMs = punch.maxGapMs.coerceIn(mean, CallerLimits.maxGap.maxMs),
                ),
        )
    }

    fun withMinGap(ms: Long): CallerSettings = copy(punch = punch.copy(minGapMs = CallerLimits.minGap(punch.meanGapMs).clamp(ms)))

    fun withMaxGap(ms: Long): CallerSettings = copy(punch = punch.copy(maxGapMs = CallerLimits.maxGap(punch.meanGapMs).clamp(ms)))

    fun withDistribution(distribution: GapDistribution): CallerSettings = copy(punch = punch.copy(distribution = distribution))

    fun withStdDev(ms: Long): CallerSettings = copy(punch = punch.copy(stdDevMs = CallerLimits.stdDev.clamp(ms)))

    /**
     * Switches [target] on or off in the pool. Turning off the last callable
     * punch is ignored, so the pool can never become empty.
     */
    fun withPunchEnabled(
        target: Punch,
        on: Boolean,
    ): CallerSettings {
        val next = if (on) punch.enabled + target else punch.enabled - target
        if (next.none { punch.weightOf(it) > 0 }) return this
        return copy(punch = punch.copy(enabled = next))
    }

    fun withAvoidRepeats(avoid: Boolean): CallerSettings = copy(punch = punch.copy(avoidRepeats = avoid))

    /**
     * Sets [target]'s weight, from 0 to [PunchConfig.MAX_WEIGHT]. A change
     * that would leave nothing to call is ignored.
     */
    fun withWeight(
        target: Punch,
        weight: Int,
    ): CallerSettings {
        val w = weight.coerceIn(0, PunchConfig.MAX_WEIGHT)
        val next = punch.weights + (target to w)
        if (punch.enabled.none { (next[it] ?: PunchConfig.DEFAULT_WEIGHT) > 0 }) return this
        return copy(punch = punch.copy(weights = next))
    }

    fun withCombos(on: Boolean): CallerSettings = copy(punch = punch.copy(combos = on).withCallableLengths())

    fun withMaxComboLength(length: Int): CallerSettings =
        copy(punch = punch.copy(maxComboLength = length.coerceIn(2, PunchConfig.MAX_COMBO_LENGTH)).withCallableLengths())

    /**
     * Sets the weight of combos of [length] punches, from 0 to
     * [CallerLimits.MAX_LENGTH_WEIGHT]. A change that would leave no length
     * to draw is ignored.
     */
    fun withComboLengthWeight(
        length: Int,
        weight: Int,
    ): CallerSettings {
        if (length !in 1..PunchConfig.MAX_COMBO_LENGTH) return this
        val next = punch.comboLengthWeights.toMutableList().also { it[length - 1] = weight.coerceIn(0, CallerLimits.MAX_LENGTH_WEIGHT) }
        if (next.take(punch.maxComboLength).none { it > 0 }) return this
        return copy(punch = punch.copy(comboLengthWeights = next))
    }

    fun withNaturalOnly(on: Boolean): CallerSettings = copy(punch = punch.copy(naturalOnly = on))

    fun withDoubling(on: Boolean): CallerSettings = copy(punch = punch.copy(allowDoubling = on))

    fun withStance(stance: Stance): CallerSettings = copy(stance = stance)

    fun withCalloutStyle(style: CalloutStyle): CallerSettings = copy(calloutStyle = style)

    /** How calls are said, for the announcer. */
    val callout: Callout get() = Callout(calloutStyle, stance, speechRate, voice)

    fun withVoice(voice: Voice): CallerSettings = copy(voice = voice)

    fun withSpeechRate(rate: Float): CallerSettings = copy(speechRate = CallerLimits.clampSpeechRate(rate))

    /** Sets a fixed session length in ms, or null to run until stopped. */
    fun withSessionLength(ms: Long?): CallerSettings = copy(sessionLengthMs = ms?.let(CallerLimits.sessionLength::clamp))

    /**
     * These settings pulled inside [CallerLimits]. Used on everything read
     * from storage.
     */
    fun sanitized(): CallerSettings =
        copy(
            punch =
                punch
                    .copy(
                        weights = punch.weights.mapValues { it.value.coerceIn(0, PunchConfig.MAX_WEIGHT) },
                        maxComboLength = punch.maxComboLength.coerceIn(2, PunchConfig.MAX_COMBO_LENGTH),
                        comboLengthWeights = punch.comboLengthWeights.map { it.coerceIn(0, CallerLimits.MAX_LENGTH_WEIGHT) },
                    ).withCallableLengths(),
        ).withMeanGap(punch.meanGapMs)
            .withMinGap(punch.minGapMs)
            .withMaxGap(punch.maxGapMs)
            .withStdDev(punch.stdDevMs)
            .withSpeechRate(speechRate)
            .withSessionLength(sessionLengthMs)
}

/**
 * This config with at least one combo length that can be drawn: if every
 * length up to the max has weight 0, single punches get the default weight.
 */
private fun PunchConfig.withCallableLengths(): PunchConfig {
    if (comboLengthWeights.take(maxComboLength).any { it > 0 }) return this
    return copy(comboLengthWeights = comboLengthWeights.toMutableList().also { it[0] = PunchConfig.DEFAULT_COMBO_LENGTH_WEIGHTS[0] })
}

/** The ranges the caller settings screen offers (DESIGN.md §3.5). */
object CallerLimits {
    const val MAX_LENGTH_WEIGHT = 100
    const val LENGTH_WEIGHT_STEP = 5

    val meanGap = LengthRange(minMs = 500L, maxMs = 15_000L, stepMs = 500L)
    val minGap = LengthRange(minMs = 300L, maxMs = 15_000L, stepMs = 100L)
    val maxGap = LengthRange(minMs = 500L, maxMs = 30_000L, stepMs = 1_000L)
    val stdDev = LengthRange(minMs = 100L, maxMs = 5_000L, stepMs = 100L)
    val sessionLength = LengthRange(minMs = 30_000L, maxMs = 30 * 60_000L, stepMs = 30_000L)

    fun minGap(meanGapMs: Long) = LengthRange(minMs = minGap.minMs, maxMs = meanGapMs, stepMs = minGap.stepMs)

    fun maxGap(meanGapMs: Long) = LengthRange(minMs = meanGapMs, maxMs = maxGap.maxMs, stepMs = maxGap.stepMs)

    const val MIN_SPEECH_RATE = 0.8f
    const val MAX_SPEECH_RATE = 2.0f
    const val SPEECH_RATE_STEP = 0.1f
    const val DEFAULT_SPEECH_RATE = 1.2f

    fun clampSpeechRate(rate: Float): Float {
        val tenths = (rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE) * 10f).roundToInt()
        return tenths / 10f
    }

    fun stepSpeechRateUp(rate: Float): Float = clampSpeechRate(rate + SPEECH_RATE_STEP)

    fun stepSpeechRateDown(rate: Float): Float = clampSpeechRate(rate - SPEECH_RATE_STEP)

    fun canStepSpeechRateUp(rate: Float): Boolean = clampSpeechRate(rate) < MAX_SPEECH_RATE

    fun canStepSpeechRateDown(rate: Float): Boolean = clampSpeechRate(rate) > MIN_SPEECH_RATE
}
