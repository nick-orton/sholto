package com.nickorton.sholto.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PunchTest {
    @Test
    fun defaultPoolMatchesTheDesignDocInOrder() {
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "1B", "2B", "3B", "4B"),
            Punch.entries.map { it.label },
        )
    }

    @Test
    fun oddNumbersAreLeadAndEvenNumbersAreRear() {
        Punch.entries.forEach { punch ->
            val expected = if (punch.number % 2 == 1) Hand.Lead else Hand.Rear
            assertEquals(punch.label, expected, punch.hand)
        }
    }

    @Test
    fun orthodoxNames() {
        assertEquals(
            listOf(
                "Jab",
                "Cross",
                "Left hook",
                "Right hook",
                "Left uppercut",
                "Right uppercut",
                "Jab body",
                "Cross body",
                "Left hook body",
                "Right hook body",
            ),
            Punch.entries.map { it.fullName(Stance.Orthodox) },
        )
    }

    @Test
    fun southpawMirrorsTheSidesButKeepsTheNumbers() {
        assertEquals(
            listOf(
                "Jab",
                "Cross",
                "Right hook",
                "Left hook",
                "Right uppercut",
                "Left uppercut",
                "Jab body",
                "Cross body",
                "Right hook body",
                "Left hook body",
            ),
            Punch.entries.map { it.fullName(Stance.Southpaw) },
        )
        // The lead hook is 3 in both stances.
        assertEquals(Hand.Lead, Punch.Three.hand)
    }
}
