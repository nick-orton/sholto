package com.nickorton.sholto.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nickorton.sholto.R
import com.nickorton.sholto.engine.PunchConfig
import com.nickorton.sholto.engine.PunchEngine
import com.nickorton.sholto.ui.timer.formatSecondsTenths
import kotlin.random.Random

/**
 * A histogram of the gaps [PunchEngine] draws for a config, for the preview
 * chart in settings. It samples the real engine, so it shows exactly what a
 * session does, including the pile-ups at min and max where gaps are clamped.
 *
 * @property binMs The width of each bar.
 * @property counts Samples per bar, from 0 ms up to [rangeMs].
 * @property averageMs The mean of the samples.
 * @property lowMs 10% of gaps are shorter than this.
 * @property highMs 10% of gaps are longer than this.
 */
data class GapHistogram(
    val binMs: Long,
    val counts: List<Int>,
    val averageMs: Long,
    val lowMs: Long,
    val highMs: Long,
) {
    val rangeMs: Long get() = binMs * counts.size
}

/** Samples [samples] gaps for [config] with a fixed seed, so the chart doesn't flicker. */
fun gapHistogram(
    config: PunchConfig,
    samples: Int = 4_000,
    bins: Int = 30,
): GapHistogram {
    val engine = PunchEngine(config, Random(PREVIEW_SEED))
    val gaps = LongArray(samples) { engine.nextGap() }.sorted()
    // One bin past max, so the clamped gaps at max get a bar of their own.
    val binMs = (config.maxGapMs / (bins - 1)).coerceAtLeast(1L)
    val counts = IntArray(bins)
    for (g in gaps) counts[(g / binMs).toInt().coerceIn(0, bins - 1)]++
    return GapHistogram(
        binMs = binMs,
        counts = counts.toList(),
        averageMs = gaps.average().toLong(),
        lowMs = gaps[samples / 10],
        highMs = gaps[samples - 1 - samples / 10],
    )
}

private const val PREVIEW_SEED = 3L

/**
 * The gap distribution as a small bar chart that redraws as the pacing
 * settings change (DESIGN.md §3.5). Single series, so no legend: the title
 * names it. The average is marked with a hairline, and screen readers get
 * the same facts as text.
 */
@Composable
fun GapPreviewChart(
    config: PunchConfig,
    modifier: Modifier = Modifier,
) {
    val histogram = remember(config) { gapHistogram(config) }
    val bar = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val summary =
        stringResource(
            R.string.gap_preview_summary,
            formatSecondsTenths(histogram.lowMs),
            formatSecondsTenths(histogram.highMs),
            formatSecondsTenths(histogram.averageMs),
        )

    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.gap_preview), style = MaterialTheme.typography.bodyLarge)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = ink)
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(top = 8.dp)
                .semantics { contentDescription = summary },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val peak = histogram.counts.max().coerceAtLeast(1)
                val slot = size.width / histogram.counts.size
                val gap = 2.dp.toPx()
                val radius = 2.dp.toPx()
                val width = (slot - gap).coerceIn(1f, 24.dp.toPx())
                histogram.counts.forEachIndexed { i, count ->
                    if (count == 0) return@forEachIndexed
                    val h = size.height * count / peak
                    val left = i * slot + (slot - width) / 2
                    // Rounded at the data end, square at the baseline.
                    val path =
                        Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = left,
                                    top = size.height - h,
                                    right = left + width,
                                    bottom = size.height,
                                    topLeftCornerRadius = CornerRadius(radius),
                                    topRightCornerRadius = CornerRadius(radius),
                                    bottomLeftCornerRadius = CornerRadius.Zero,
                                    bottomRightCornerRadius = CornerRadius.Zero,
                                ),
                            )
                        }
                    drawPath(path, bar)
                }
                val hairline = 1.dp.toPx()
                drawRect(axis, topLeft = Offset(0f, size.height - hairline), size = Size(size.width, hairline))
                val avgX = size.width * histogram.averageMs / histogram.rangeMs
                drawRect(ink, topLeft = Offset(avgX - hairline / 2, 0f), size = Size(hairline, size.height))
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("0 s", style = MaterialTheme.typography.labelSmall, color = ink)
            Text(
                text = formatSecondsTenths(config.maxGapMs),
                style = MaterialTheme.typography.labelSmall,
                color = ink,
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd),
            )
        }
    }
}
