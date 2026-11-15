package com.nickorton.sholto.data

import com.nickorton.sholto.engine.PunchConfig
import com.nickorton.sholto.engine.RoundConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresetsTest {
    @Test
    fun `built-in presets match DESIGN_md and ignore get-ready length`() {
        val pro = Presets.builtIn.first { it.id == "pro" }
        val config = RoundConfig(3 * 60_000L, 30_000L, 60_000L, 12, getReadyLengthMs = 25_000L)
        assertEquals(pro, Presets.active(config, emptyList()))
        assertEquals(25_000L, pro.applyTo(config).getReadyLengthMs)

        val hiit = Presets.builtIn.first { it.id == "hiit" }
        assertEquals(RoundConfig(30_000L, 0L, 30_000L, 10, 25_000L), hiit.applyTo(config))
    }

    @Test
    fun `user preset wins over built-in with the same format`() {
        val proConfig = Presets.builtIn.first { it.id == "pro" }.applyTo(RoundConfig())
        val user = Preset.from("u1", "My 12s", proConfig)
        assertEquals(user, Presets.active(proConfig, listOf(user)))
    }

    @Test
    fun `unmatched format has no active preset`() {
        assertNull(Presets.active(RoundConfig(), emptyList()))
    }

    @Test
    fun `name validation rejects blank, too long, Custom and duplicate names`() {
        val user = listOf(Preset("u1", "Sparring", 120_000L, 30_000L, 60_000L, 6))
        assertEquals(PresetNameError.Blank, Presets.nameError("   ", user))
        assertEquals(PresetNameError.TooLong, Presets.nameError("x".repeat(31), user))
        assertEquals(PresetNameError.Taken, Presets.nameError("custom", user))
        assertEquals(PresetNameError.Taken, Presets.nameError("PRO", user))
        assertEquals(PresetNameError.Taken, Presets.nameError("  sparring ", user))

        // Renaming a preset to its own name (or case variant) is allowed.
        assertNull(Presets.nameError("Sparring", user, renamingId = "u1"))
        assertNull(Presets.nameError("Bag work", user))
    }

    @Test
    fun `combined presets pair existing presets and match only both halves`() {
        val names = CombinedPresets.builtIn.map { it.name }
        assertEquals(listOf("Amateur + Beginner", "Pro + Intermediate", "HIIT + Advanced"), names)
        val hiit = CombinedPresets.builtIn.last()
        val round = hiit.timer.applyTo(RoundConfig())
        val punch = hiit.caller.applyTo(PunchConfig())
        assertEquals(hiit, CombinedPresets.active(round, punch))
        assertNull(CombinedPresets.active(round, PunchConfig(meanGapMs = 5_000L)))
    }

    @Test
    fun `final push factor stays in range and on tenths`() {
        assertEquals(0.3f, FinalPush().withFactor(0.05f).factor)
        assertEquals(0.9f, FinalPush().withFactor(1.5f).factor)
        assertEquals(0.7f, CombinedLimits.stepPushFactorUp(0.6f))
        assertEquals(1.0, FinalPush(enabled = false).meanFactor, 0.0)
        assertEquals(0.6, FinalPush().meanFactor, 1e-6)
    }
}
