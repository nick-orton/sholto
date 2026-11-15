package com.nickorton.sholto.engine

/** How the wait between calls is drawn (DESIGN.md §3.4). */
enum class GapDistribution {
    /** Unpredictable, like a Poisson process: bursts and long waits. */
    Exponential,

    /** Evenly spread around the mean, as wide as min and max allow. */
    Uniform,

    /** Mostly near the mean, with a set spread (std-dev). */
    Normal,

    /** Always the mean: a metronome. */
    Fixed,
}

/**
 * Settings for the punch caller. All times are in milliseconds.
 *
 * Every gap is drawn from [distribution] around [meanGapMs], then clamped to
 * [minGapMs]..[maxGapMs]. The clamp moves the average a little for the
 * exponential and normal distributions: with the defaults the average gap is
 * about 2.94 s rather than 3.0 s.
 *
 * Only logical limits are checked here. The narrower ranges the settings
 * screen offers (e.g. mean 0.5–15 s) belong to the UI, as with [RoundConfig].
 *
 * @property minGapMs The shortest wait. Positive, and at most [meanGapMs].
 * @property maxGapMs The longest wait. At least [meanGapMs].
 * @property stdDevMs The spread, for [GapDistribution.Normal] only.
 * @property weights Relative weight of each punch. Missing punches have
 *   weight [DEFAULT_WEIGHT]; weight 0 means never called.
 * @property enabled The punches in the pool. Turning one off keeps its weight
 *   for when it's turned back on.
 * @property avoidRepeats Never give the same call twice in a row, unless
 *   only one punch can be called.
 * @property combos Calls can be combos of several punches.
 * @property maxComboLength The longest combo, from 2 to [MAX_COMBO_LENGTH].
 * @property comboLengthWeights Relative weight of each combo length, from 1
 *   punch up to [MAX_COMBO_LENGTH]. Only lengths up to [maxComboLength] are
 *   used.
 * @property naturalOnly Combos alternate lead and rear hands, as they are
 *   thrown (DESIGN.md §3.4).
 * @property allowDoubling With [naturalOnly], the lead hand may also throw
 *   twice in a row, as in `1-1-2` or `1-3`. The rear hand never doubles.
 */
data class PunchConfig(
    val meanGapMs: Long = 3_000L,
    val minGapMs: Long = 1_000L,
    val maxGapMs: Long = 8_000L,
    val distribution: GapDistribution = GapDistribution.Exponential,
    val stdDevMs: Long = 1_000L,
    val weights: Map<Punch, Int> = emptyMap(),
    val enabled: Set<Punch> = Punch.entries.toSet(),
    val avoidRepeats: Boolean = false,
    val combos: Boolean = false,
    val maxComboLength: Int = DEFAULT_MAX_COMBO_LENGTH,
    val comboLengthWeights: List<Int> = DEFAULT_COMBO_LENGTH_WEIGHTS,
    val naturalOnly: Boolean = false,
    val allowDoubling: Boolean = false,
) {
    init {
        require(minGapMs > 0) { "minGapMs must be positive, was $minGapMs" }
        require(meanGapMs in minGapMs..maxGapMs) {
            "meanGapMs must be in minGapMs..maxGapMs ($minGapMs..$maxGapMs), was $meanGapMs"
        }
        require(stdDevMs > 0) { "stdDevMs must be positive, was $stdDevMs" }
        require(weights.values.all { it >= 0 }) { "weights must not be negative, were $weights" }
        require(pool.isNotEmpty()) { "at least one enabled punch needs a positive weight" }
        require(maxComboLength in 2..MAX_COMBO_LENGTH) {
            "maxComboLength must be in 2..$MAX_COMBO_LENGTH, was $maxComboLength"
        }
        require(comboLengthWeights.size == MAX_COMBO_LENGTH && comboLengthWeights.all { it >= 0 }) {
            "comboLengthWeights must be $MAX_COMBO_LENGTH weights of 0 or more, were $comboLengthWeights"
        }
        require(lengthPool.isNotEmpty()) { "at least one combo length up to $maxComboLength needs a positive weight" }
    }

    /**
     * The combo lengths that can be drawn, with their weights. Just length 1
     * when combos are off.
     */
    val lengthPool: List<Pair<Int, Int>>
        get() =
            if (!combos) {
                listOf(1 to 1)
            } else {
                (1..maxComboLength).map { it to comboLengthWeights[it - 1] }.filter { it.second > 0 }
            }

    /** Whether [next] may follow [previous] in a combo, under the natural-combo rule. */
    fun canFollow(
        previous: Punch,
        next: Punch,
    ): Boolean =
        when {
            !naturalOnly -> true
            previous.hand != next.hand -> true
            else -> allowDoubling && previous.hand == Hand.Lead
        }

    fun weightOf(punch: Punch): Int = weights[punch] ?: DEFAULT_WEIGHT

    /** The punches that can be called, with their weights, in vocabulary order. */
    val pool: List<Pair<Punch, Int>>
        get() =
            Punch.entries
                .filter { it in enabled }
                .map { it to weightOf(it) }
                .filter { it.second > 0 }

    companion object {
        const val DEFAULT_WEIGHT = 1
        const val MAX_WEIGHT = 5
        const val MAX_COMBO_LENGTH = 6
        const val DEFAULT_MAX_COMBO_LENGTH = 4

        /** 1 = 50%, 2 = 30%, 3 = 15%, 4 = 5% (DESIGN.md §3.4), and a little for 5 and 6. */
        val DEFAULT_COMBO_LENGTH_WEIGHTS = listOf(50, 30, 15, 5, 3, 2)
    }
}
