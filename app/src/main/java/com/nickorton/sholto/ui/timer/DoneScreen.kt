package com.nickorton.sholto.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nickorton.sholto.R
import com.nickorton.sholto.ui.theme.SholtoTheme

/** The session summary, with Again and Home. */
@Composable
fun DoneScreen(
    state: TimerUiState.Done,
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
            Stat(stringResource(R.string.rounds_completed), state.roundsCompleted.toString())
            Stat(stringResource(R.string.total_time), formatDuration(state.totalTimeMs))
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
private fun Stat(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
    }
}

@Preview
@Composable
private fun DoneScreenPreview() {
    SholtoTheme { DoneScreen(TimerUiState.Done(3, 610_000L, finished = true), onAgain = {}, onHome = {}) }
}
