package com.nickorton.sholto.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class ComboTest {
    private fun calls(
        config: PunchConfig,
        n: Int = 10_000,
        seed: Long = 11L,
    ): List<Combo> {
        val engine = PunchEngine(config, Random(seed))
        return List(n) { engine.nextCall() }
    }

    private val combos = PunchConfig(combos = true)

    @Test
    fun `a combo is shown with dots and spoken as one phrase`() {
        val combo = Combo.of(Punch.One, Punch.Two, Punch.ThreeBody)
        assertEquals("1 · 2 · 3B", combo.label)
        assertEquals("one two three body", combo.spokenText(CalloutStyle.Numbers, Stance.Orthodox))
        assertEquals("jab, cross, left hook body", combo.spokenText(CalloutStyle.Names, Stance.Orthodox))
        assertEquals(
            "one two three body, jab, cross, right hook body",
            combo.spokenText(CalloutStyle.Both, Stance.Southpaw),
        )
        assertEquals("Jab, cross, left hook body", combo.fullName(Stance.Orthodox))

        val single = Combo.of(Punch.ThreeBody)
        assertEquals("3B", single.label)
        assertEquals(Punch.ThreeBody.spokenText(CalloutStyle.Both, Stance.Orthodox), single.spokenText(CalloutStyle.Both, Stance.Orthodox))
    }

    @Test
    fun `lengths follow their weights and never pass the max`() {
        val all = calls(combos)
        val counts = all.groupingBy { it.size }.eachCount()
        assertEquals(setOf(1, 2, 3, 4), counts.keys)
        // Default weights 50/30/15/5 for lengths 1-4; 4 standard errors of slack.
        listOf(0.50, 0.30, 0.15, 0.05).forEachIndexed { i, expected ->
            val share = (counts[i + 1] ?: 0) / all.size.toDouble()
            assertTrue("length ${i + 1}: $share", abs(share - expected) < 0.02)
        }

        val upTo2 = calls(combos.copy(maxComboLength = 2))
        assertEquals(setOf(1, 2), upTo2.map { it.size }.toSet())
        val upTo6 = calls(combos.copy(maxComboLength = 6))
        assertEquals(6, upTo6.maxOf { it.size })
    }

    @Test
    fun `a length with weight 0 is never drawn`() {
        val onlyThrees = combos.copy(comboLengthWeights = listOf(0, 0, 1, 0, 0, 0), maxComboLength = 3)
        assertTrue(calls(onlyThrees, n = 1_000).all { it.size == 3 })
    }

    @Test
    fun `combos off gives single punches`() {
        assertTrue(calls(PunchConfig()).all { it.size == 1 })
    }

    @Test
    fun `natural combos alternate hands`() {
        val natural = combos.copy(naturalOnly = true, maxComboLength = 6)
        for (call in calls(natural)) {
            call.punches.zipWithNext().forEach { (a, b) -> assertTrue("$call", a.hand != b.hand) }
        }
        assertTrue(calls(natural).any { it.size >= 4 })
    }

    @Test
    fun `doubling lets the lead hand repeat but never the rear`() {
        val doubling = combos.copy(naturalOnly = true, allowDoubling = true, maxComboLength = 6)
        val all = calls(doubling)
        for (call in all) {
            call.punches.zipWithNext().forEach { (a, b) ->
                assertFalse("$call", a.hand == Hand.Rear && b.hand == Hand.Rear)
            }
        }
        assertTrue(all.any { c -> c.punches.zipWithNext().any { (a, b) -> a.hand == Hand.Lead && b.hand == Hand.Lead } })
        // DESIGN.md's example: 1-1-3 is allowed only with doubling.
        assertTrue(doubling.canFollow(Punch.One, Punch.One) && doubling.canFollow(Punch.One, Punch.Three))
        assertFalse(combos.copy(naturalOnly = true).canFollow(Punch.One, Punch.One))
        assertTrue(combos.canFollow(Punch.Two, Punch.Two))
    }

    @Test
    fun `a natural combo ends early when no punch can follow`() {
        val leadOnly = combos.copy(naturalOnly = true, enabled = setOf(Punch.One, Punch.Three, Punch.Five))
        assertTrue(calls(leadOnly, n = 1_000).all { it.size == 1 })
    }

    @Test
    fun `avoid repeats never gives the same call twice running`() {
        val config = combos.copy(avoidRepeats = true, maxComboLength = 2, comboLengthWeights = listOf(1, 1, 0, 0, 0, 0))
        val all = calls(config)
        all.zipWithNext().forEach { (a, b) -> assertTrue("$a twice", a != b) }
    }

    @Test
    fun `a combo setup with no drawable length is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PunchConfig(combos = true, maxComboLength = 2, comboLengthWeights = listOf(0, 0, 5, 5, 5, 5))
        }
    }

    @Test
    fun `the next wait starts after a combo is spoken and thrown`() {
        val combo = Combo.of(Punch.One, Punch.Two, Punch.Three)
        assertEquals(0L, CallTiming.busyMs(Combo.of(Punch.One), speechMs = 500L))
        assertEquals(700L + 3 * 400L, CallTiming.busyMs(combo, speechMs = 700L))

        // "one two three" is 3 syllables; faster speech is shorter.
        val text = combo.spokenText(CalloutStyle.Numbers, Stance.Orthodox)
        assertEquals(540L, CallTiming.speechMs(text, speechRate = 1.0f))
        assertEquals(270L, CallTiming.speechMs(text, speechRate = 2.0f))
        assertTrue(
            CallTiming.speechMs(combo.spokenText(CalloutStyle.Names, Stance.Orthodox), 1.0f) >
                CallTiming.speechMs(text, 1.0f),
        )
    }
}
