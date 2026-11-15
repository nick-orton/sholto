package com.nickorton.sholto.audio

import com.nickorton.sholto.engine.Callout
import com.nickorton.sholto.engine.Combo
import kotlin.math.roundToLong

/**
 * Turns a call into recorded clips and when to start each one, for the
 * bundled voice (PLAN.md M10). Pure Kotlin, so it's tested on the JVM.
 *
 * The clips say exactly the words the system voice would
 * ([Combo.spokenText]), one clip per word. Words of one punch, like "left
 * hook body", run together with a [WORD_GAP_MS] gap; each new punch gets a
 * slightly longer [PUNCH_GAP_MS], so "one two three body" keeps a rhythm.
 * Faster speech rates tighten the gaps; the clips themselves always play at
 * normal speed, since speeding them up would raise the pitch.
 */
object VoiceScript {
    /** Every word a call can contain: the clips a voice pack needs. */
    val WORDS: List<String> =
        listOf("one", "two", "three", "four", "five", "six", "body", "jab", "cross", "left", "right", "hook", "uppercut")

    const val WORD_GAP_MS = 20L
    const val PUNCH_GAP_MS = 80L

    // Words that continue a punch rather than start one.
    private val CONTINUATIONS = setOf("body", "hook", "uppercut")

    /** One clip and when to start it, from the start of the call. */
    data class Clip(
        val word: String,
        val startMs: Long,
    )

    /** The clips for a call, and how long it lasts from the first clip to the end of the last. */
    data class Script(
        val clips: List<Clip>,
        val lengthMs: Long,
    )

    /** The words of [call] as [callout] says them, in order. */
    fun words(
        call: Combo,
        callout: Callout,
    ): List<String> =
        call
            .spokenText(callout.style, callout.stance)
            .lowercase()
            .split(Regex("[^a-z]+"))
            .filter { it.isNotEmpty() }

    /**
     * The script for [call], given each clip's length in [clipMs], or null if
     * a word has no clip.
     */
    fun script(
        call: Combo,
        callout: Callout,
        clipMs: Map<String, Long>,
    ): Script? {
        val rate = callout.speechRate.coerceIn(0.5f, 2.0f)
        var t = 0L
        val clips = mutableListOf<Clip>()
        for ((i, word) in words(call, callout).withIndex()) {
            val length = clipMs[word] ?: return null
            if (i > 0) {
                val gap = if (word in CONTINUATIONS) WORD_GAP_MS else PUNCH_GAP_MS
                t += (gap / rate).roundToLong()
            }
            clips += Clip(word, t)
            t += length
        }
        return Script(clips, t)
    }
}
