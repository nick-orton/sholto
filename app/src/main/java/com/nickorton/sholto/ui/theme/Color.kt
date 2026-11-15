package com.nickorton.sholto.ui.theme

import androidx.compose.ui.graphics.Color

// Phase palette from DESIGN.md §5.5. Each phase also has an icon and a label,
// so color is never the only signal. The green leans teal and the red is a
// little brighter so the two stay apart with red-green colorblindness
// (OKLab ΔE ≥ 10 for every pair under protan, deutan and tritan simulation).
val PhaseGreen = Color(0xFF00796B)
val PhaseYellow = Color(0xFFF2C200)
val PhaseRed = Color(0xFFD32F2F)
val PhaseGrey = Color(0xFF37474F)

/** Text and icons on green, red and grey. */
val OnPhaseLight = Color.White

/** Text and icons on yellow. */
val OnPhaseDark = Color(0xFF1A1A1A)

// Dark app chrome.
val ChromeBackground = Color(0xFF121212)
val ChromeSurface = Color(0xFF1E1E1E)
