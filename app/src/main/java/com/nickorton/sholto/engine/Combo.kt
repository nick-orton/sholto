package com.nickorton.sholto.engine

import kotlin.math.roundToLong

/**
 * One call: a single punch, or a combo of several thrown in order
 * (DESIGN.md §3.4). Shown as `1 · 2 · 3B` and spoken as one phrase.
 */
data class Combo(
    val punches: List<Punch>,
) {
    init {
        require(punches.isNotEmpty()) { "a combo needs at least one punch" }
    }

    val size: Int get() = punches.size

    /** As shown on screen: `3B`, or `1 · 2 · 3B` for a combo. */
    val label: String get() = punches.joinToString(" · ") { it.label }

    /** Names for [stance], e.g. `Jab, cross, left hook body`. */
    fun fullName(stance: Stance): String =
        punches
            .mapIndexed { i, p -> if (i == 0) p.fullName(stance) else p.fullName(stance).lowercase() }
            .joinToString(", ")

    /**
     * What the announcer says for [style] and [stance], as one phrase:
     * `one two three body`, `jab, cross, left hook body`, or both.
     */
    fun spokenText(
        style: CalloutStyle,
        stance: Stance,
    ): String {
        val numbers = punches.joinToString(" ") { it.spokenNumber }
        val names = punches.joinToString(", ") { it.fullName(stance).lowercase() }
        return when (style) {
            CalloutStyle.Numbers -> numbers
            CalloutStyle.Names -> names
            CalloutStyle.Both -> "$numbers, $names"
        }
    }

    companion object {
        fun of(vararg punches: Punch) = Combo(punches.toList())
    }
}

/**
 * How long calls take, for spacing them out (DESIGN.md §3.4). Pure estimates,
 * so the timing stays the same for a given seed and can be tested without a
 * speech engine.
 */
object CallTiming {
    /** Time to throw each punch of a combo once it has been called. */
    const val THROW_MS_PER_PUNCH = 400L

    /** Roughly one syllable at speech rate 1.0. */
    private const val SYLLABLE_MS = 180.0

    /**
     * About how long [text] takes to say at [speechRate], counting syllables
     * as groups of vowels in each word, less a silent final "e" ("one",
     * "five").
     */
    fun speechMs(
        text: String,
        speechRate: Float,
    ): Long {
        val syllables =
            text
                .lowercase()
                .split(Regex("[^a-z]+"))
                .filter { it.isNotEmpty() }
                .sumOf(::syllables)
        return (syllables * SYLLABLE_MS / speechRate.coerceAtLeast(0.1f)).roundToLong()
    }

    private fun syllables(word: String): Int {
        val groups = VOWEL_GROUP.findAll(word).count()
        val silentE = groups > 1 && word.endsWith("e") && !word.endsWith("ee")
        return (if (silentE) groups - 1 else groups).coerceAtLeast(1)
    }

    private val VOWEL_GROUP = Regex("[aeiouy]+")

    /**
     * How long after a call starts the wait to the next one begins. A single
     * punch is quick, so the wait starts with the call, as before combos. A
     * combo first has to be spoken and then thrown.
     */
    fun busyMs(
        combo: Combo,
        speechMs: Long,
    ): Long = if (combo.size > 1) speechMs + THROW_MS_PER_PUNCH * combo.size else 0L
}

/** Who says the calls (DESIGN.md §3.5). */
enum class Voice {
    /** The phone's text-to-speech engine. Works for any call. */
    SystemTts,

    /** Recorded clips stitched together: faster to start, more natural. */
    Bundled,
}

/** How calls are said: everything the announcer needs besides the call itself. */
data class Callout(
    val style: CalloutStyle = CalloutStyle.Numbers,
    val stance: Stance = Stance.Orthodox,
    val speechRate: Float = 1.2f,
    val voice: Voice = Voice.SystemTts,
)
