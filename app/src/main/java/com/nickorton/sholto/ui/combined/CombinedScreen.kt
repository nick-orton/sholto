package com.nickorton.sholto.ui.combined

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nickorton.sholto.R
import com.nickorton.sholto.engine.Combo
import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.engine.Punch
import com.nickorton.sholto.engine.Stance
import com.nickorton.sholto.ui.caller.CallDisplay
import com.nickorton.sholto.ui.caller.callAlpha
import com.nickorton.sholto.ui.caller.flashAlpha
import com.nickorton.sholto.ui.theme.SholtoTheme
import com.nickorton.sholto.ui.timer.Countdown
import com.nickorton.sholto.ui.timer.KeepScreenOn
import com.nickorton.sholto.ui.timer.PausedOverlay
import com.nickorton.sholto.ui.timer.PhaseHeader
import com.nickorton.sholto.ui.timer.PhaseStyle
import com.nickorton.sholto.ui.timer.ProgressBar
import com.nickorton.sholto.ui.timer.TimerUiState
import com.nickorton.sholto.ui.timer.detectGloveGestures
import com.nickorton.sholto.ui.timer.fixedSp
import com.nickorton.sholto.ui.timer.formatCountdown
import com.nickorton.sholto.ui.timer.phaseStyle

/**
 * The running combined session (DESIGN.md §4): the phase color fills the
 * background, the countdown sits at the top and the punch call is large in
 * the center, with the caller's flash and fade on each new call. During Get
 * ready and Rest there are no calls, so the countdown takes the center.
 * Tap to pause and hold to end, as on the timer.
 */
@Composable
fun CombinedScreen(
    state: CombinedUiState.Running,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timer = state.timer
    if (timer.keepScreenOn) KeepScreenOn()
    val style = phaseStyle(timer.phase)

    // The gesture detector lives across recompositions, so it reads the
    // latest state and callbacks instead of capturing them.
    val isPaused by rememberUpdatedState(timer.isPaused)
    val tapEnds by rememberUpdatedState(timer.tapEnds)
    val currentOnPause by rememberUpdatedState(onPause)
    val currentOnEnd by rememberUpdatedState(onEnd)
    val pauseLabel = stringResource(R.string.pause)
    val endLabel = stringResource(R.string.end_session)

    val call = state.currentCall
    val flash = if (call != null && !timer.isPaused) flashAlpha(state.callAgeMs) else 0f

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(style.background)
                .pointerInput(Unit) {
                    detectGloveGestures(
                        onTap = {
                            // While paused, the overlay's buttons do the work.
                            if (!isPaused) {
                                if (tapEnds) currentOnEnd() else currentOnPause()
                            }
                        },
                        onLongPress = { currentOnEnd() },
                    )
                }.semantics {
                    if (!timer.isPaused) {
                        onClick(label = if (timer.tapEnds) endLabel else pauseLabel) {
                            if (timer.tapEnds) onEnd() else onPause()
                            true
                        }
                    }
                    onLongClick(label = endLabel) {
                        onEnd()
                        true
                    }
                },
    ) {
        if (flash > 0f) {
            Box(Modifier.fillMaxSize().background(style.content.copy(alpha = flash)))
        }
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            val landscape = maxWidth > maxHeight
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!timer.phase.hasCalls) {
                    PhaseHeader(timer, style)
                    Countdown(
                        text = formatCountdown(timer.phaseTimeLeftMs),
                        color = style.content,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else if (landscape) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Column(
                            Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PhaseHeader(timer, style)
                            Countdown(
                                text = formatCountdown(timer.phaseTimeLeftMs),
                                color = style.content,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Call(state, style, Modifier.weight(1.4f).fillMaxHeight())
                    }
                } else {
                    PhaseHeader(timer, style)
                    Countdown(
                        text = formatCountdown(timer.phaseTimeLeftMs),
                        color = style.content,
                        modifier = Modifier.weight(0.45f).fillMaxWidth(),
                    )
                    Call(state, style, Modifier.weight(1f).fillMaxWidth())
                }
                Text(
                    text = stringResource(R.string.calls_counter, state.callCount),
                    color = style.content.copy(alpha = 0.8f),
                    fontSize = fixedSp(20.dp),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start),
                )
                Spacer(Modifier.height(8.dp))
                ProgressBar(progress = timer.roundProgress, color = style.content)
            }
        }
        if (timer.isPaused) {
            PausedOverlay(onResume = onResume, onEnd = onEnd)
        }
    }
}

/** The current call, fading over a second. */
@Composable
private fun Call(
    state: CombinedUiState.Running,
    style: PhaseStyle,
    modifier: Modifier,
) {
    val call = state.currentCall
    if (call == null) {
        // Empty until the round's first call: the bell has just rung.
        Box(modifier)
        return
    }
    CallDisplay(
        text = call.label,
        subtitle = call.fullName(state.stance),
        color = style.content.copy(alpha = callAlpha(state.callAgeMs)),
        modifier = modifier,
    )
}

private val Phase.hasCalls: Boolean get() = this == Phase.Work || this == Phase.Warning

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun CombinedScreenWorkPreview() {
    SholtoTheme {
        CombinedScreen(
            state =
                CombinedUiState.Running(
                    timer = TimerUiState.Running(Phase.Work, 3, 6, 95_000L, 0.4f, 30_000L, isPaused = false),
                    currentCall = Combo.of(Punch.ThreeBody),
                    stance = Stance.Orthodox,
                    callCount = 42,
                    callAgeMs = 200L,
                ),
            onPause = {},
            onResume = {},
            onEnd = {},
        )
    }
}

@Preview(widthDp = 760, heightDp = 360)
@Composable
private fun CombinedScreenWarningLandscapePreview() {
    SholtoTheme {
        CombinedScreen(
            state =
                CombinedUiState.Running(
                    timer = TimerUiState.Running(Phase.Warning, 3, null, 21_000L, 0.9f, 30_000L, isPaused = false),
                    currentCall = Combo.of(Punch.One, Punch.Two),
                    stance = Stance.Orthodox,
                    callCount = 57,
                    callAgeMs = 600L,
                ),
            onPause = {},
            onResume = {},
            onEnd = {},
        )
    }
}
