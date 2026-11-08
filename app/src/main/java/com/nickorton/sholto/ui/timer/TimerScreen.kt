package com.nickorton.sholto.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nickorton.sholto.R
import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.ui.theme.CountdownTextStyle
import com.nickorton.sholto.ui.theme.PhaseGreen
import com.nickorton.sholto.ui.theme.SholtoTheme

/**
 * The running session: phase color, countdown, round counter and progress.
 * Tap anywhere to pause; hold anywhere to end.
 */
@Composable
fun TimerScreen(
    state: TimerUiState.Running,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KeepScreenOn()
    val style = phaseStyle(state.phase)

    // The gesture detector lives across recompositions (one every ~100 ms),
    // so it reads the latest state and callbacks instead of capturing them.
    val isPaused by rememberUpdatedState(state.isPaused)
    val currentOnPause by rememberUpdatedState(onPause)
    val currentOnEnd by rememberUpdatedState(onEnd)
    val pauseLabel = stringResource(R.string.pause)
    val endLabel = stringResource(R.string.end_session)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(style.background)
                .pointerInput(Unit) {
                    detectGloveGestures(
                        onTap = { if (!isPaused) currentOnPause() },
                        onLongPress = { currentOnEnd() },
                    )
                }.semantics {
                    if (!state.isPaused) {
                        onClick(label = pauseLabel) {
                            onPause()
                            true
                        }
                    }
                    onLongClick(label = endLabel) {
                        onEnd()
                        true
                    }
                },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PhaseHeader(state, style)
            Countdown(
                text = formatCountdown(state.phaseTimeLeftMs),
                color = style.content,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            ProgressBar(progress = state.roundProgress, color = style.content)
        }
        if (state.isPaused) {
            PausedOverlay(onResume = onResume, onEnd = onEnd)
        }
    }
}

@Composable
private fun PhaseHeader(
    state: TimerUiState.Running,
    style: PhaseStyle,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (style.icon != PhaseIcon.None) {
            PhaseIconView(style.icon, style.content, Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = phaseLabel(state.phase, state.warningLengthMs),
            color = style.content,
            fontSize = fixedSp(36.dp),
            fontWeight = FontWeight.Black,
        )
    }
    Text(
        text = roundLabel(state.phase, state.round, state.totalRounds),
        color = style.content,
        fontSize = fixedSp(24.dp),
        fontWeight = FontWeight.Bold,
    )
}

/** The countdown, as large as fits: about 40% of the screen height in portrait. */
@Composable
private fun Countdown(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // In a heavy face with tabular figures a digit is about 0.5 em wide
        // and a colon about 0.25 em. Allow a good margin so it never clips.
        val digits = text.count { it.isDigit() }
        val ems = digits * 0.64f + (text.length - digits) * 0.34f
        val size = minOf(maxHeight * 0.8f, maxWidth / ems)
        Text(
            text = text,
            color = color,
            style = CountdownTextStyle,
            fontSize = fixedSp(size),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProgressBar(
    progress: Float,
    color: Color,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.25f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color),
        )
    }
}

/** Big Resume and End buttons, laid out side by side in landscape. */
@Composable
private fun PausedOverlay(
    onResume: () -> Unit,
    onEnd: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        val resume: @Composable (Modifier) -> Unit = { m ->
            Button(
                onClick = onResume,
                modifier = m,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PhaseGreen, contentColor = Color.White),
            ) {
                Text(stringResource(R.string.resume), fontSize = 48.sp, fontWeight = FontWeight.Black)
            }
        }
        val end: @Composable (Modifier) -> Unit = { m ->
            OutlinedButton(onClick = onEnd, modifier = m, shape = RoundedCornerShape(24.dp)) {
                Text(stringResource(R.string.end_session), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                resume(Modifier.weight(2f).fillMaxHeight())
                end(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                resume(Modifier.weight(1f).fillMaxWidth())
                end(Modifier.height(112.dp).fillMaxWidth())
                Text(
                    text = stringResource(R.string.hold_to_end),
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/** Keeps the display on while this is on screen. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * A text size that ignores the system font scale. The running screen is
 * sized to fill the display, so scaling it would only make text overflow.
 */
@Composable
private fun fixedSp(size: Dp) = with(LocalDensity.current) { size.toSp() }

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun TimerScreenWorkPreview() {
    SholtoTheme {
        TimerScreen(
            state = TimerUiState.Running(Phase.Work, 3, 6, 95_000L, 0.4f, 30_000L, isPaused = false),
            onPause = {},
            onResume = {},
            onEnd = {},
        )
    }
}

@Preview(widthDp = 760, heightDp = 360)
@Composable
private fun TimerScreenWarningLandscapePreview() {
    SholtoTheme {
        TimerScreen(
            state = TimerUiState.Running(Phase.Warning, 3, null, 21_000L, 0.9f, 30_000L, isPaused = false),
            onPause = {},
            onResume = {},
            onEnd = {},
        )
    }
}

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun TimerScreenPausedPreview() {
    SholtoTheme {
        TimerScreen(
            state = TimerUiState.Running(Phase.Rest, 3, 6, 42_000L, 1f, 30_000L, isPaused = true),
            onPause = {},
            onResume = {},
            onEnd = {},
        )
    }
}
