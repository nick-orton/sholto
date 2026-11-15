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
 *
 * Combos come later (M9); for now each call is a single punch.
 */
class PunchEngine(
    private val config: PunchConfig,
    private val random: Random,
) {
    private val pool = config.pool
    private var lastCall: Punch? = null

    /** The wait before the next call, in ms, within [PunchConfig.minGapMs]..[PunchConfig.maxGapMs]. */
    fun nextGap(): Long {
        val mean = config.meanGapMs.toDouble()
        val sample =
            when (config.distribution) {
                // 1 - nextDouble() is in (0, 1], so the log is always finite.
                GapDistribution.Exponential -> {
                    -mean * ln(1.0 - random.nextDouble())
                }

                GapDistribution.Uniform -> {
                    // Symmetric around the mean, so the mean holds exactly.
                    val halfWidth = min(config.meanGapMs - config.minGapMs, config.maxGapMs - config.meanGapMs).toDouble()
                    mean - halfWidth + 2.0 * halfWidth * random.nextDouble()
                }

                GapDistribution.Normal -> {
                    mean + config.stdDevMs * standardNormal()
                }

                GapDistribution.Fixed -> {
                    mean
                }
            }
        return sample.coerceIn(config.minGapMs.toDouble(), config.maxGapMs.toDouble()).roundToLong()
    }

    /**
     * The next punch, picked at random in proportion to its weight. With
     * avoid-repeats on, the last punch is left out of the draw, unless it's
     * the only one that can be called.
     */
    fun nextCall(): Punch {
        val last = lastCall
        val candidates =
            if (config.avoidRepeats && last != null && pool.size > 1) pool.filter { it.first != last } else pool
        var ticket = random.nextInt(candidates.sumOf { it.second })
        for ((punch, weight) in candidates) {
            if (ticket < weight) {
                lastCall = punch
                return punch
            }
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
}
