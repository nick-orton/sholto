package com.nickorton.sholto.audio

import com.nickorton.sholto.engine.Callout
import com.nickorton.sholto.engine.Combo
import com.nickorton.sholto.engine.Punch
import com.nickorton.sholto.engine.Voice
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceRouterTest {
    private val said = mutableListOf<String>()
    private val system =
        object : CallAnnouncer {
            override fun announce(
                call: Combo,
                callout: Callout,
            ) {
                said += "system ${call.label}"
            }

            override fun speechMs(
                call: Combo,
                callout: Callout,
            ) = 900L
        }
    private val bundled =
        object : CallAnnouncer {
            override fun announce(
                call: Combo,
                callout: Callout,
            ) {
                said += "bundled ${call.label}"
            }

            override fun speechMs(
                call: Combo,
                callout: Callout,
            ) = 400L
        }

    @Test
    fun `the bundled voice says calls it can, and the system voice the rest`() {
        val router = VoiceRouter(system, bundled, canSayBundled = { call, _ -> Punch.Six !in call.punches })
        val bundledVoice = Callout(voice = Voice.Bundled)

        router.announce(Combo.of(Punch.One, Punch.Two), bundledVoice)
        router.announce(Combo.of(Punch.Six), bundledVoice)
        router.announce(Combo.of(Punch.One), Callout(voice = Voice.SystemTts))

        assertEquals(listOf("bundled 1 · 2", "system 6", "system 1"), said)
        assertEquals(400L, router.speechMs(Combo.of(Punch.One), bundledVoice))
        assertEquals(900L, router.speechMs(Combo.of(Punch.Six), bundledVoice))
    }
}
