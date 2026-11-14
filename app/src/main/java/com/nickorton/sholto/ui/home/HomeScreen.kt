package com.nickorton.sholto.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nickorton.sholto.R
import com.nickorton.sholto.ui.theme.SholtoTheme

/**
 * Home: the app name, active preset and summary of the timer settings, a
 * settings gear in the top right, and a big START button filling the lower
 * half (or the right half in landscape).
 */
@Composable
fun HomeScreen(
    presetName: String,
    summary: String,
    onStart: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
                val title: @Composable (Modifier) -> Unit = { m ->
                    Column(
                        modifier = m,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = presetName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(text = summary, style = MaterialTheme.typography.headlineSmall)
                    }
                }
                val start: @Composable (Modifier) -> Unit = { m ->
                    Button(onClick = onStart, modifier = m, shape = RoundedCornerShape(32.dp)) {
                        Text(stringResource(R.string.start), fontSize = 56.sp, fontWeight = FontWeight.Black)
                    }
                }
                if (maxWidth > maxHeight) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        title(Modifier.weight(1f).fillMaxHeight())
                        start(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        title(Modifier.weight(1f).fillMaxWidth())
                        start(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
            IconButton(
                onClick = onSettings,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.settings),
                )
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    SholtoTheme {
        HomeScreen(
            presetName = "Pro",
            summary = "3:00 × 12 · 1:00 rest",
            onStart = {},
            onSettings = {},
        )
    }
}
