package com.nickorton.sholto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nickorton.sholto.ui.home.HomeScreen
import com.nickorton.sholto.ui.timer.DoneScreen
import com.nickorton.sholto.ui.timer.TimerScreen
import com.nickorton.sholto.ui.timer.TimerUiState
import com.nickorton.sholto.ui.timer.TimerViewModel
import com.nickorton.sholto.ui.timer.formatSummary

/**
 * The app's top level. With only the timer so far, the screen follows the
 * session state directly. Compose Navigation comes in when more modes do.
 */
@Composable
fun SholtoApp(viewModel: TimerViewModel = viewModel { TimerViewModel() }) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        is TimerUiState.Idle -> {
            HomeScreen(summary = formatSummary(s.config), onStart = viewModel::start)
        }

        is TimerUiState.Running -> {
            // Back pauses first, so it can't end a session by accident.
            BackHandler { if (s.isPaused) viewModel.end() else viewModel.pause() }
            TimerScreen(
                state = s,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onEnd = viewModel::end,
            )
        }

        is TimerUiState.Done -> {
            BackHandler(onBack = viewModel::home)
            DoneScreen(state = s, onAgain = viewModel::start, onHome = viewModel::home)
        }
    }
}
