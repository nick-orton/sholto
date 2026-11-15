package com.nickorton.sholto.ui.settings

import com.nickorton.sholto.engine.GapDistribution
import com.nickorton.sholto.engine.PunchConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GapPreviewTest {
    @Test
    fun `every sample lands in a bar and the range covers max`() {
        val h = gapHistogram(PunchConfig(), samples = 4_000, bins = 30)
        assertEquals(4_000, h.counts.sum())
        assertEquals(30, h.counts.size)
        assertTrue(h.rangeMs > 8_000L)
        // Exponential, clamped to 1-8 s: nothing under the min.
        assertEquals(0, h.counts.take((1_000L / h.binMs).toInt()).sum())
        assertTrue(h.lowMs in 1_000L..h.highMs && h.highMs <= 8_000L)
    }

    @Test
    fun `a fixed gap is one bar at the mean`() {
        val fixed = PunchConfig(meanGapMs = 3_000L, distribution = GapDistribution.Fixed)
        val h = gapHistogram(fixed)
        assertEquals(1, h.counts.count { it > 0 })
        assertEquals(3_000L, h.averageMs)
        assertEquals((3_000L / h.binMs).toInt(), h.counts.indexOfFirst { it > 0 })
    }

    @Test
    fun `the preview follows the mean`() {
        val slow = gapHistogram(PunchConfig(meanGapMs = 6_000L, maxGapMs = 20_000L))
        val fast = gapHistogram(PunchConfig(meanGapMs = 1_500L, minGapMs = 500L, maxGapMs = 20_000L))
        assertTrue(slow.averageMs > 3 * fast.averageMs)
        assertTrue(abs(slow.averageMs - 6_000L) < 400L)
    }
}
