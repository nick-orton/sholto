package com.nickorton.sholto.data

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
