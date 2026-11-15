package com.nickorton.sholto.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nickorton.sholto.SholtoApplication
import com.nickorton.sholto.data.Presets
import com.nickorton.sholto.service.WorkoutServiceConnection
import com.nickorton.sholto.ui.home.HomeScreen
import com.nickorton.sholto.ui.settings.SettingsScreen
import com.nickorton.sholto.ui.settings.SettingsViewModel
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
fun SholtoApp(
    timerViewModel: TimerViewModel =
        viewModel {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SholtoApplication
            val connection = WorkoutServiceConnection(app).also { it.bind() }
            TimerViewModel(
                controllers = connection.controller,
                onClose = connection::unbind,
            )
        },
    settingsViewModel: SettingsViewModel =
        viewModel {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SholtoApplication
            SettingsViewModel(app.settingsRepository)
        },
) {
    val state by timerViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    when (val s = state) {
        TimerUiState.Loading -> {
            Surface(Modifier.fillMaxSize()) {}
        }

        is TimerUiState.Idle -> {
            RequestNotificationPermission()
            val currentSettings = settingsState
            if (showSettings && currentSettings != null) {
                BackHandler { showSettings = false }
                SettingsScreen(
                    state = currentSettings,
                    onUpdate = settingsViewModel::update,
                    onApplyPreset = settingsViewModel::applyPreset,
                    onSavePreset = settingsViewModel::savePreset,
                    onRenamePreset = settingsViewModel::renamePreset,
                    onDeletePreset = settingsViewModel::deletePreset,
                    nameError = settingsViewModel::nameError,
                    onBack = { showSettings = false },
                )
            } else {
                HomeScreen(
                    presetName = currentSettings?.activePresetName ?: Presets.CUSTOM_NAME,
                    summary = formatSummary(s.config),
                    onStart = timerViewModel::start,
                    onSettings = { showSettings = true },
                )
            }
        }

        is TimerUiState.Running -> {
            // Back pauses first, so it can't end a session by accident.
            BackHandler { if (s.isPaused) timerViewModel.end() else timerViewModel.pause() }
            TimerScreen(
                state = s,
                onPause = timerViewModel::pause,
                onResume = timerViewModel::resume,
                onEnd = timerViewModel::end,
            )
        }

        is TimerUiState.Done -> {
            BackHandler(onBack = timerViewModel::home)
            DoneScreen(state = s, onAgain = timerViewModel::start, onHome = timerViewModel::home)
        }
    }
}

/**
 * Asks once for `POST_NOTIFICATIONS` on Android 13+ while on Home, before
 * gloves go on. If it's refused, sessions still run in the foreground with a
 * wake lock and full audio cues, just without the visible notification.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Nothing to do: WorkoutService checks permission when posting.
        }
    LaunchedEffect(Unit) {
        if (asked) return@LaunchedEffect
        asked = true
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
