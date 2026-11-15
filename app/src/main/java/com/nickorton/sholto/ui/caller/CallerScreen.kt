package com.nickorton.sholto.ui.caller

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.nickorton.sholto.engine.Combo
import com.nickorton.sholto.engine.Punch
import com.nickorton.sholto.engine.Stance
import com.nickorton.sholto.ui.theme.ChromeBackground
import com.nickorton.sholto.ui.theme.CountdownTextStyle
import com.nickorton.sholto.ui.theme.OnPhaseLight
import com.nickorton.sholto.ui.theme.PhaseGreen
import com.nickorton.sholto.ui.theme.SholtoTheme
import com.nickorton.sholto.ui.timer.detectGloveGestures
import com.nickorton.sholto.ui.timer.formatCountdown
import com.nickorton.sholto.ui.timer.formatDuration

private const val CALL_FADE_MS = 1_000L
private const val FLASH_MS = 150L
private const val MIN_CALL_ALPHA = 0.25f
private const val MAX_FLASH_ALPHA = 0.35f

/**
 * Opacity of the current call at [callAgeMs] after it was called. Starts at
 * `1.0` and fades over 1 s down to [MIN_CALL_ALPHA], so a fresh call stands
 * out even when the same punch is called twice in a row (DESIGN.md §3.2).
 */
internal fun callAlpha(callAgeMs: Long): Float {
    val progress = (callAgeMs.coerceAtLeast(0L).toFloat() / CALL_FADE_MS).coerceIn(0f, 1f)
    return 1f - (1f - MIN_CALL_ALPHA) * progress
}

/**
 * Opacity of the screen flash at [callAgeMs] after a new call. Gives a short
 * pulse over the first [FLASH_MS] ms.
 */
internal fun flashAlpha(callAgeMs: Long): Float {
    if (callAgeMs < 0L || callAgeMs >= FLASH_MS) return 0f
    return MAX_FLASH_ALPHA * (1f - callAgeMs.toFloat() / FLASH_MS)
}

/**
 * The running punch-caller screen (DESIGN.md §3.2): one large call in the
 * center with a flash and 1 s fade on each new call, a call counter and clock
 * at the top, and the same tap-to-pause / hold-to-end gestures as the timer.
 */
@Composable
fun CallerScreen(
    state: CallerUiState.Running,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.keepScreenOn) KeepScreenOn()

    val isPaused by rememberUpdatedState(state.isPaused)
    val tapEnds by rememberUpdatedState(state.tapEnds)
    val currentOnPause by rememberUpdatedState(onPause)
    val currentOnEnd by rememberUpdatedState(onEnd)
    val pauseLabel = stringResource(R.string.pause)
    val endLabel = stringResource(R.string.end_session)

    val call = state.currentCall
    val flash = if (call != null && !state.isPaused) flashAlpha(state.callAgeMs) else 0f
    val contentAlpha = if (call != null) callAlpha(state.callAgeMs) else 0.6f

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(ChromeBackground)
                .pointerInput(Unit) {
                    detectGloveGestures(
                        onTap = {
                            if (!isPaused) {
                                if (tapEnds) {
                                    currentOnEnd()
                                } else {
                                    currentOnPause()
                                }
                            }
                        },
                        onLongPress = { currentOnEnd() },
                    )
                }.semantics {
                    if (!state.isPaused) {
                        onClick(label = if (state.tapEnds) endLabel else pauseLabel) {
                            if (state.tapEnds) onEnd() else onPause()
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
            Box(Modifier.fillMaxSize().background(PhaseGreen.copy(alpha = flash)))
        }
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallerHeader(state)
            CallDisplay(
                text = call?.label ?: stringResource(R.string.caller_ready),
                subtitle = call?.fullName(state.stance),
                color = OnPhaseLight.copy(alpha = contentAlpha),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        if (state.isPaused) {
            CallerPausedOverlay(onResume = onResume, onEnd = onEnd)
        }
    }
}

@Composable
private fun CallerHeader(state: CallerUiState.Running) {
    val timeText = state.timeLeftMs?.let(::formatCountdown) ?: formatDuration(state.elapsedMs)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.calls_counter, state.callCount),
            color = OnPhaseLight.copy(alpha = 0.8f),
            fontSize = fixedSp(24.dp),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = timeText,
            color = OnPhaseLight.copy(alpha = 0.8f),
            style = CountdownTextStyle,
            fontSize = fixedSp(28.dp),
        )
    }
}

@Composable
internal fun CallDisplay(
    text: String,
    subtitle: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val chars = text.length.coerceAtLeast(2)
        val size = minOf(maxHeight * 0.65f, maxWidth / (chars * 0.68f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                color = color,
                style = CountdownTextStyle,
                fontSize = fixedSp(size),
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle.uppercase(),
                    color = color,
                    fontSize = fixedSp(28.dp),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CallerPausedOverlay(
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

/** Summary screen after a punch-caller session ends. */
@Composable
fun CallerDoneScreen(
    state: CallerUiState.Done,
    onAgain: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(if (state.finished) R.string.done_title else R.string.ended_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
            )
            Column(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${stringResource(R.string.calls_made)}: ${state.callsMade}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "${stringResource(R.string.total_time)}: ${formatDuration(state.totalTimeMs)}",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Button(
                onClick = onAgain,
                modifier = Modifier.fillMaxWidth().height(96.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(stringResource(R.string.again), fontSize = 36.sp, fontWeight = FontWeight.Black)
            }
            OutlinedButton(
                onClick = onHome,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(stringResource(R.string.home), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun fixedSp(size: Dp) = with(LocalDensity.current) { size.toSp() }

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun CallerScreenPreview() {
    SholtoTheme {
        CallerScreen(
            state =
                CallerUiState.Running(
                    currentCall = Combo.of(Punch.One, Punch.Two, Punch.ThreeBody),
                    stance = Stance.Orthodox,
                    callCount = 14,
                    callAgeMs = 100L,
                    elapsedMs = 42_000L,
                    timeLeftMs = null,
                    isPaused = false,
                ),
            onPause = {},
            onResume = {},
            onEnd = {},
        )
    }
}
