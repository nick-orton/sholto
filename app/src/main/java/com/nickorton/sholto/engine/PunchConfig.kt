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
 * @property avoidRepeats Never call the same punch twice in a row, unless
 *   only one punch can be called.
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
) {
    init {
        require(minGapMs > 0) { "minGapMs must be positive, was $minGapMs" }
        require(meanGapMs in minGapMs..maxGapMs) {
            "meanGapMs must be in minGapMs..maxGapMs ($minGapMs..$maxGapMs), was $meanGapMs"
        }
        require(stdDevMs > 0) { "stdDevMs must be positive, was $stdDevMs" }
        require(weights.values.all { it >= 0 }) { "weights must not be negative, were $weights" }
        require(pool.isNotEmpty()) { "at least one enabled punch needs a positive weight" }
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
    }
}
