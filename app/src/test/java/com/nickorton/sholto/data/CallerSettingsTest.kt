package com.nickorton.sholto.data

import com.nickorton.sholto.engine.Punch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallerSettingsTest {
    @Test
    fun `withMeanGap pulls minGap down and maxGap up so min le mean le max holds`() {
        val initial = CallerSettings() // mean = 3000, min = 1000, max = 8000
        val shortened = initial.withMeanGap(500L)
        assertEquals(500L, shortened.punch.meanGapMs)
        assertEquals(500L, shortened.punch.minGapMs)
        assertEquals(8_000L, shortened.punch.maxGapMs)

        val lengthened = initial.withMeanGap(10_000L)
        assertEquals(10_000L, lengthened.punch.meanGapMs)
        assertEquals(1_000L, lengthened.punch.minGapMs)
        assertEquals(10_000L, lengthened.punch.maxGapMs)
    }

    @Test
    fun `withMinGap and withMaxGap clamp against meanGap`() {
        val initial = CallerSettings() // mean = 3000
        assertEquals(3_000L, initial.withMinGap(9_000L).punch.minGapMs)
        assertEquals(300L, initial.withMinGap(100L).punch.minGapMs)
        assertEquals(3_000L, initial.withMaxGap(1_000L).punch.maxGapMs)
        assertEquals(30_000L, initial.withMaxGap(99_000L).punch.maxGapMs)
    }

    @Test
    fun `withPunchEnabled refuses to disable the last callable punch`() {
        var settings = CallerSettings()
        val allPunches = Punch.entries
        allPunches.drop(1).forEach { punch ->
            settings = settings.withPunchEnabled(punch, on = false)
        }
        assertEquals(setOf(allPunches.first()), settings.punch.enabled)

        // Trying to turn off the last one leaves it enabled.
        val afterLastOff = settings.withPunchEnabled(allPunches.first(), on = false)
        assertEquals(setOf(allPunches.first()), afterLastOff.punch.enabled)
    }

    @Test
    fun `speech rate steps by tenths within 0_8 to 2_0`() {
        assertEquals(0.8f, CallerLimits.clampSpeechRate(0.1f), 1e-4f)
        assertEquals(2.0f, CallerLimits.clampSpeechRate(5.0f), 1e-4f)
        assertEquals(1.3f, CallerLimits.stepSpeechRateUp(1.2f), 1e-4f)
        assertEquals(1.1f, CallerLimits.stepSpeechRateDown(1.2f), 1e-4f)
    }

    @Test
    fun `difficulty presets match DESIGN_md 3_5`() {
        val defaults = CallerSettings()
        assertEquals("Intermediate", CallerPresets.active(defaults.punch)?.name)

        val beginner = CallerSettings(punch = CallerPresets.builtIn.first { it.id == "beginner" }.applyTo(defaults.punch))
        assertEquals("Beginner", CallerPresets.active(beginner.punch)?.name)
        assertEquals(4_000L, beginner.punch.meanGapMs)
        assertEquals(setOf(Punch.One, Punch.Two, Punch.Three, Punch.Four), beginner.punch.enabled)

        val advanced = CallerSettings(punch = CallerPresets.builtIn.first { it.id == "advanced" }.applyTo(defaults.punch))
        assertEquals("Advanced", CallerPresets.active(advanced.punch)?.name)
        assertEquals(1_800L, advanced.punch.meanGapMs)
        assertEquals(Punch.entries.toSet(), advanced.punch.enabled)

        val custom = advanced.withMeanGap(2_000L)
        assertNull(CallerPresets.active(custom.punch))
    }
}
