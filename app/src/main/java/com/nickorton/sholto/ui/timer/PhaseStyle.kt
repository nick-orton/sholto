package com.nickorton.sholto.ui.timer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.ui.theme.ChromeBackground
import com.nickorton.sholto.ui.theme.OnPhaseDark
import com.nickorton.sholto.ui.theme.OnPhaseLight
import com.nickorton.sholto.ui.theme.PhaseGreen
import com.nickorton.sholto.ui.theme.PhaseGrey
import com.nickorton.sholto.ui.theme.PhaseRed
import com.nickorton.sholto.ui.theme.PhaseYellow

enum class PhaseIcon { Play, Warning, Pause, None }

/** How a phase looks on the running screen (DESIGN.md §5.5). */
data class PhaseStyle(
    val background: Color,
    val content: Color,
    val icon: PhaseIcon,
)

fun phaseStyle(phase: Phase): PhaseStyle =
    when (phase) {
        Phase.GetReady -> PhaseStyle(PhaseGrey, OnPhaseLight, PhaseIcon.None)
        Phase.Work -> PhaseStyle(PhaseGreen, OnPhaseLight, PhaseIcon.Play)
        Phase.Warning -> PhaseStyle(PhaseYellow, OnPhaseDark, PhaseIcon.Warning)
        Phase.Rest -> PhaseStyle(PhaseRed, OnPhaseLight, PhaseIcon.Pause)
        Phase.Done -> PhaseStyle(ChromeBackground, OnPhaseLight, PhaseIcon.None)
    }

/**
 * Draws a phase icon. These are drawn rather than taken from a font, so the
 * warning sign can't turn into a color emoji.
 */
@Composable
fun PhaseIconView(
    icon: PhaseIcon,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (icon == PhaseIcon.None) return
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (icon) {
            PhaseIcon.Play -> {
                val path =
                    Path().apply {
                        moveTo(w * 0.2f, h * 0.1f)
                        lineTo(w * 0.9f, h * 0.5f)
                        lineTo(w * 0.2f, h * 0.9f)
                        close()
                    }
                drawPath(path, color)
            }

            PhaseIcon.Pause -> {
                val bar = Size(w * 0.25f, h * 0.8f)
                val radius = CornerRadius(w * 0.04f)
                drawRoundRect(color, Offset(w * 0.15f, h * 0.1f), bar, radius)
                drawRoundRect(color, Offset(w * 0.6f, h * 0.1f), bar, radius)
            }

            PhaseIcon.Warning -> {
                val stroke = w * 0.09f
                val path =
                    Path().apply {
                        moveTo(w * 0.5f, h * 0.08f)
                        lineTo(w * 0.95f, h * 0.9f)
                        lineTo(w * 0.05f, h * 0.9f)
                        close()
                    }
                drawPath(path, color, style = Stroke(width = stroke, join = StrokeJoin.Round))
                drawLine(color, Offset(w * 0.5f, h * 0.38f), Offset(w * 0.5f, h * 0.6f), stroke, StrokeCap.Round)
                drawCircle(color, stroke * 0.6f, Offset(w * 0.5f, h * 0.75f))
            }

            PhaseIcon.None -> {
                Unit
            }
        }
    }
}
