package com.nickorton.sholto.data

import com.nickorton.sholto.engine.GapDistribution
import com.nickorton.sholto.engine.Punch
import com.nickorton.sholto.engine.PunchConfig
import com.nickorton.sholto.engine.RoundConfig

/**
 * A named round format: round length, warning, rest and number of rounds.
 *
 * The get-ready countdown isn't part of a preset. It's about how long *you*
 * need to get to the bag, not about the format, so it stays as you set it.
 *
 * @property id Stable across renames. Built-in ids never change.
 * @property rounds How many rounds, or null for unlimited.
 */
data class Preset(
    val id: String,
    val name: String,
    val roundLengthMs: Long,
    val warningLengthMs: Long,
    val restLengthMs: Long,
    val rounds: Int?,
    val builtIn: Boolean = false,
) {
    /** True if [config] has this preset's format, whatever its get-ready. */
    fun matches(config: RoundConfig): Boolean =
        config.roundLengthMs == roundLengthMs &&
            config.warningLengthMs == warningLengthMs &&
            config.restLengthMs == restLengthMs &&
            config.rounds == rounds

    /** [config] with this preset's format, keeping its get-ready. */
    fun applyTo(config: RoundConfig): RoundConfig =
        config.copy(
            roundLengthMs = roundLengthMs,
            warningLengthMs = warningLengthMs,
            restLengthMs = restLengthMs,
            rounds = rounds,
        )

    companion object {
        /** A user preset with the format of [config]. */
        fun from(
            id: String,
            name: String,
            config: RoundConfig,
        ) = Preset(
            id = id,
            name = name,
            roundLengthMs = config.roundLengthMs,
            warningLengthMs = config.warningLengthMs,
            restLengthMs = config.restLengthMs,
            rounds = config.rounds,
        )
    }
}

/** The built-in presets and the rules for naming your own (DESIGN.md §2.4). */
object Presets {
    val builtIn: List<Preset> =
        listOf(
            Preset("pro", "Pro", 3 * 60_000L, 30_000L, 60_000L, 12, builtIn = true),
            Preset("amateur", "Amateur", 3 * 60_000L, 30_000L, 60_000L, 3, builtIn = true),
            Preset("womens_pro", "Women's pro", 2 * 60_000L, 30_000L, 60_000L, 10, builtIn = true),
            Preset("hiit", "HIIT", 30_000L, 0L, 30_000L, 10, builtIn = true),
        )

    /** What Home calls settings that match no preset. */
    const val CUSTOM_NAME = "Custom"

    const val MAX_NAME_LENGTH = 30

    /**
     * The preset that [config] matches, or null for Custom. Your own presets
     * win over built-in ones with the same format, since you named them.
     */
    fun active(
        config: RoundConfig,
        userPresets: List<Preset>,
    ): Preset? = userPresets.firstOrNull { it.matches(config) } ?: builtIn.firstOrNull { it.matches(config) }

    /** A name as it will be stored: one line, trimmed. */
    fun cleanName(name: String): String = name.replace(Regex("\\s+"), " ").trim()

    /**
     * Why [name] can't be used for a preset, or null if it can. Names must be
     * unique, ignoring case, across built-in and user presets, and can't be
     * "Custom".
     *
     * @param renamingId The preset being renamed, which may keep its name.
     */
    fun nameError(
        name: String,
        userPresets: List<Preset>,
        renamingId: String? = null,
    ): PresetNameError? {
        val clean = cleanName(name)
        val taken =
            clean.equals(CUSTOM_NAME, ignoreCase = true) ||
                (builtIn + userPresets).any { it.id != renamingId && it.name.equals(clean, ignoreCase = true) }
        return when {
            clean.isEmpty() -> PresetNameError.Blank
            clean.length > MAX_NAME_LENGTH -> PresetNameError.TooLong
            taken -> PresetNameError.Taken
            else -> null
        }
    }
}

enum class PresetNameError { Blank, TooLong, Taken }

/**
 * A difficulty preset for the punch caller (DESIGN.md §3.5). Stance, callout
 * style, speech rate and session length stay as you set them.
 */
data class CallerPreset(
    val id: String,
    val name: String,
    val meanGapMs: Long,
    val minGapMs: Long,
    val maxGapMs: Long = 8_000L,
    val distribution: GapDistribution,
    val enabled: Set<Punch>,
) {
    fun matches(config: PunchConfig): Boolean =
        config.meanGapMs == meanGapMs &&
            config.minGapMs == minGapMs &&
            config.maxGapMs == maxGapMs &&
            config.distribution == distribution &&
            config.enabled == enabled

    fun applyTo(config: PunchConfig): PunchConfig =
        config.copy(
            meanGapMs = meanGapMs,
            minGapMs = minGapMs,
            maxGapMs = maxGapMs,
            distribution = distribution,
            enabled = enabled,
        )
}

/** The built-in difficulty presets for the punch caller (DESIGN.md §3.5). */
object CallerPresets {
    val builtIn: List<CallerPreset> =
        listOf(
            CallerPreset(
                id = "beginner",
                name = "Beginner",
                meanGapMs = 4_000L,
                minGapMs = 2_000L,
                distribution = GapDistribution.Uniform,
                enabled = setOf(Punch.One, Punch.Two, Punch.Three, Punch.Four),
            ),
            CallerPreset(
                id = "intermediate",
                name = "Intermediate",
                meanGapMs = 3_000L,
                minGapMs = 1_000L,
                distribution = GapDistribution.Exponential,
                enabled = Punch.entries.toSet(),
            ),
            CallerPreset(
                id = "advanced",
                name = "Advanced",
                meanGapMs = 1_800L,
                minGapMs = 600L,
                distribution = GapDistribution.Exponential,
                enabled = Punch.entries.toSet(),
            ),
        )

    fun active(config: PunchConfig): CallerPreset? = builtIn.firstOrNull { it.matches(config) }
}

/**
 * A combined-mode preset: a timer preset paired with a caller difficulty
 * preset (PLAN.md M8). Applying it applies both.
 */
data class CombinedPreset(
    val id: String,
    val timer: Preset,
    val caller: CallerPreset,
) {
    val name: String get() = combinedName(timer.name, caller.name)

    fun matches(
        round: RoundConfig,
        punch: PunchConfig,
    ): Boolean = timer.matches(round) && caller.matches(punch)
}

/** How Home and settings name a timer and caller setup together, e.g. `Pro + Advanced`. */
fun combinedName(
    timerName: String,
    callerName: String,
): String = "$timerName + $callerName"

/** The built-in combined presets. */
object CombinedPresets {
    val builtIn: List<CombinedPreset> =
        listOf(
            pair("amateur", "beginner"),
            pair("pro", "intermediate"),
            pair("hiit", "advanced"),
        )

    fun active(
        round: RoundConfig,
        punch: PunchConfig,
    ): CombinedPreset? = builtIn.firstOrNull { it.matches(round, punch) }

    private fun pair(
        timerId: String,
        callerId: String,
    ) = CombinedPreset(
        id = "${timerId}_$callerId",
        timer = Presets.builtIn.single { it.id == timerId },
        caller = CallerPresets.builtIn.single { it.id == callerId },
    )
}
