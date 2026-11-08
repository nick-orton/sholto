package com.nickorton.sholto.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

val SholtoTypography = Typography()

/**
 * The running countdown: heavy, with tabular figures so digits don't shift
 * as they change. Callers set the size to fit the screen.
 */
val CountdownTextStyle =
    TextStyle(
        fontWeight = FontWeight.Black,
        fontFeatureSettings = "tnum",
    )
