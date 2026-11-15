package com.nickorton.sholto.ui.theme

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * A segmented-button label on one line. Segments are a third or a quarter of
 * the screen wide, so at large font scales a long word like "Combined" or
 * "Exponential" would break mid-word; instead it shrinks just enough to fit.
 * The smallest size is set in dp, not sp, so it can always shrink far enough
 * however large the font scale.
 */
@Composable
fun SegmentLabel(text: String) {
    val minSize = with(LocalDensity.current) { 9.dp.toSp() }
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = LocalTextStyle.current.fontSize),
    )
}
