package com.nickorton.sholto.audio

import com.nickorton.sholto.engine.Callout
import com.nickorton.sholto.engine.CalloutStyle
import com.nickorton.sholto.engine.Combo
import com.nickorton.sholto.engine.Punch
import com.nickorton.sholto.engine.Stance
import com.nickorton.sholto.engine.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceScriptTest {
    private val allClips = VoiceScript.WORDS.associateWith { 300L }
    private val numbers = Callout(CalloutStyle.Numbers, Stance.Orthodox, speechRate = 1.0f)
    private val names = numbers.copy(style = CalloutStyle.Names)

    @Test
    fun `every word a call can say has a clip`() {
        val styles = CalloutStyle.entries
        for (punch in Punch.entries) {
            for (style in styles) {
                for (stance in Stance.entries) {
                    val words = VoiceScript.words(Combo.of(punch), Callout(style, stance))
                    assertTrue("$punch $style $stance: $words", VoiceScript.WORDS.containsAll(words))
                }
            }
        }
    }

    @Test
    fun `a combo is stitched with tight gaps inside a punch and a beat between punches`() {
        val combo = Combo.of(Punch.One, Punch.Two, Punch.ThreeBody)
        val script = VoiceScript.script(combo, numbers, allClips)!!
        assertEquals(listOf("one", "two", "three", "body"), script.clips.map { it.word })
        // one 0-300, gap 80, two 380-680, gap 80, three 760-1060, gap 20, body 1080-1380.
        assertEquals(listOf(0L, 380L, 760L, 1_080L), script.clips.map { it.startMs })
        assertEquals(1_380L, script.lengthMs)
    }

    @Test
    fun `names say the same words as the system voice`() {
        val combo = Combo.of(Punch.One, Punch.ThreeBody)
        val script = VoiceScript.script(combo, names, allClips)!!
        assertEquals(listOf("jab", "left", "hook", "body"), script.clips.map { it.word })
        // jab | left hook body: a beat, then tight.
        assertEquals(listOf(0L, 380L, 700L, 1_020L), script.clips.map { it.startMs })

        val southpaw = VoiceScript.words(Combo.of(Punch.Six), names.copy(stance = Stance.Southpaw))
        assertEquals(listOf("left", "uppercut"), southpaw)
    }

    @Test
    fun `faster speech tightens the gaps but not the clips`() {
        val combo = Combo.of(Punch.One, Punch.Two)
        val script = VoiceScript.script(combo, numbers.copy(speechRate = 2.0f), allClips)!!
        assertEquals(listOf(0L, 340L), script.clips.map { it.startMs })
        assertEquals(640L, script.lengthMs)
    }

    @Test
    fun `a missing clip means no script`() {
        assertNull(VoiceScript.script(Combo.of(Punch.Five), numbers, allClips - "five"))
    }
}
