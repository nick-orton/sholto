package com.nickorton.sholto.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Generates punch calls and the waits between them (DESIGN.md §3.4). Pure
 * Kotlin with no clock: the caller decides when to ask for the next call, so
 * the engine can be tested with a seeded [random] and no timing at all.
 *
 * Gaps and calls draw from the same [random], so the sequence for a seed
 * depends on the order of [nextGap] and [nextCall] calls.
 */
class PunchEngine(
    private val config: PunchConfig,
    private val random: Random,
) {
    private val pool = config.pool
    private var lastCall: Combo? = null

    /**
     * The wait before the next call, in ms, within
     * [PunchConfig.minGapMs]..[PunchConfig.maxGapMs].
     *
     * @param meanFactor Multiplies the mean gap, e.g. 0.6 for the faster
     *   "final push" in combined mode. The scaled mean is kept within min and
     *   max, so the min gap still guarantees time to react.
     */
    fun nextGap(meanFactor: Double = 1.0): Long {
        require(meanFactor > 0.0) { "meanFactor must be positive, was $meanFactor" }
        val minMs = config.minGapMs.toDouble()
        val maxMs = config.maxGapMs.toDouble()
        val mean = (config.meanGapMs * meanFactor).coerceIn(minMs, maxMs)
        val sample =
            when (config.distribution) {
                // 1 - nextDouble() is in (0, 1], so the log is always finite.
                GapDistribution.Exponential -> {
                    -mean * ln(1.0 - random.nextDouble())
                }

                GapDistribution.Uniform -> {
                    // Symmetric around the mean, so the mean holds exactly.
                    val halfWidth = min(mean - minMs, maxMs - mean)
                    mean - halfWidth + 2.0 * halfWidth * random.nextDouble()
                }

                GapDistribution.Normal -> {
                    mean + config.stdDevMs * standardNormal()
                }

                GapDistribution.Fixed -> {
                    mean
                }
            }
        return sample.coerceIn(minMs, maxMs).roundToLong()
    }

    /**
     * The next call: a single punch, or a combo when combos are on.
     *
     * The length is drawn from [PunchConfig.lengthPool], then each punch is
     * picked at random in proportion to its weight. With natural combos on,
     * only punches that can follow the one before are candidates; if none
     * can, the combo ends early.
     *
     * With avoid-repeats on, a single punch never repeats the last single
     * punch (unless it's the only one that can be called), and a combo is
     * drawn again, a few times at most, if it equals the last call.
     */
    fun nextCall(): Combo {
        val last = lastCall
        var call = drawCall(last)
        if (config.avoidRepeats && call.size > 1) {
            repeat(MAX_REDRAWS) { if (call == last) call = drawCall(last) }
        }
        lastCall = call
        return call
    }

    private fun drawCall(last: Combo?): Combo {
        val lengths = config.lengthPool
        val length = if (lengths.size == 1) lengths.single().first else pick(lengths)
        val punches = ArrayList<Punch>(length)
        while (punches.size < length) {
            val previous = punches.lastOrNull()
            var candidates = if (previous == null) pool else pool.filter { config.canFollow(previous, it.first) }
            if (previous == null && length == 1 && config.avoidRepeats && last != null && last.size == 1) {
                val withoutLast = candidates.filter { it.first != last.punches.single() }
                if (withoutLast.isNotEmpty()) candidates = withoutLast
            }
            if (candidates.isEmpty()) break
            punches += pick(candidates)
        }
        return Combo(punches)
    }

    /** One item, picked at random in proportion to its weight. */
    private fun <T> pick(weighted: List<Pair<T, Int>>): T {
        var ticket = random.nextInt(weighted.sumOf { it.second })
        for ((item, weight) in weighted) {
            if (ticket < weight) return item
            ticket -= weight
        }
        error("ticket outside the total weight") // Unreachable: tickets are < the total.
    }

    /** A standard normal sample, by the Box–Muller transform. */
    private fun standardNormal(): Double {
        val u1 = 1.0 - random.nextDouble() // (0, 1], so the log is finite.
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    private companion object {
        const val MAX_REDRAWS = 5
    }
}
