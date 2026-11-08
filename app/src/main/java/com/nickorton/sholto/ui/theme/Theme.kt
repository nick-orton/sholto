package com.nickorton.sholto.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SholtoColors =
    darkColorScheme(
        primary = PhaseGreen,
        onPrimary = OnPhaseLight,
        background = ChromeBackground,
        surface = ChromeBackground,
        surfaceContainer = ChromeSurface,
    )

/** The app chrome is always dark (DESIGN.md §5.5). */
@Composable
fun SholtoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SholtoColors,
        typography = SholtoTypography,
        content = content,
    )
}
