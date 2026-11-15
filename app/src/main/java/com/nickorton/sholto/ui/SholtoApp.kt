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
import com.nickorton.sholto.ui.caller.CallerDoneScreen
import com.nickorton.sholto.ui.caller.CallerScreen
import com.nickorton.sholto.ui.caller.CallerUiState
import com.nickorton.sholto.ui.caller.CallerViewModel
import com.nickorton.sholto.ui.home.HomeScreen
import com.nickorton.sholto.ui.home.WorkoutMode
import com.nickorton.sholto.ui.settings.SettingsScreen
import com.nickorton.sholto.ui.settings.SettingsViewModel
import com.nickorton.sholto.ui.timer.DoneScreen
import com.nickorton.sholto.ui.timer.TimerScreen
import com.nickorton.sholto.ui.timer.TimerUiState
import com.nickorton.sholto.ui.timer.TimerViewModel
import com.nickorton.sholto.ui.timer.formatCallerSummary
import com.nickorton.sholto.ui.timer.formatSummary

/**
 * The app's top level. While a timer or punch-caller session is running or on
 * its summary screen, that screen takes over; otherwise Home and Settings let
 * the user pick between Timer and Punch Caller modes.
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
    callerViewModel: CallerViewModel =
        viewModel {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SholtoApplication
            val connection = WorkoutServiceConnection(app).also { it.bind() }
            CallerViewModel(
                controllers = connection.callerController,
                onClose = connection::unbind,
            )
        },
    settingsViewModel: SettingsViewModel =
        viewModel {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SholtoApplication
            SettingsViewModel(app.settingsRepository)
        },
) {
    val timerState by timerViewModel.uiState.collectAsStateWithLifecycle()
    val callerState by callerViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(WorkoutMode.Timer) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val tState = timerState
    val cState = callerState
    when {
        tState is TimerUiState.Running -> {
            // Back pauses first, so it can't end a session by accident.
            BackHandler { if (tState.isPaused) timerViewModel.end() else timerViewModel.pause() }
            TimerScreen(
                state = tState,
                onPause = timerViewModel::pause,
                onResume = timerViewModel::resume,
                onEnd = timerViewModel::end,
            )
        }

        tState is TimerUiState.Done -> {
            BackHandler(onBack = timerViewModel::home)
            DoneScreen(state = tState, onAgain = timerViewModel::start, onHome = timerViewModel::home)
        }

        cState is CallerUiState.Running -> {
            BackHandler { if (cState.isPaused) callerViewModel.end() else callerViewModel.pause() }
            CallerScreen(
                state = cState,
                onPause = callerViewModel::pause,
                onResume = callerViewModel::resume,
                onEnd = callerViewModel::end,
            )
        }

        cState is CallerUiState.Done -> {
            BackHandler(onBack = callerViewModel::home)
            CallerDoneScreen(state = cState, onAgain = callerViewModel::start, onHome = callerViewModel::home)
        }

        tState is TimerUiState.Idle && cState is CallerUiState.Idle -> {
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
                    initialMode = mode,
                    onUpdateCaller = settingsViewModel::updateCaller,
                    onApplyCallerPreset = settingsViewModel::applyCallerPreset,
                )
            } else {
                when (mode) {
                    WorkoutMode.Timer -> {
                        HomeScreen(
                            presetName = currentSettings?.activePresetName ?: Presets.CUSTOM_NAME,
                            summary = formatSummary(tState.config),
                            onStart = timerViewModel::start,
                            onSettings = { showSettings = true },
                            mode = mode,
                            onModeChange = { mode = it },
                        )
                    }

                    WorkoutMode.Caller -> {
                        HomeScreen(
                            presetName = currentSettings?.activeCallerPresetName ?: Presets.CUSTOM_NAME,
                            summary = formatCallerSummary(cState.settings),
                            onStart = callerViewModel::start,
                            onSettings = { showSettings = true },
                            mode = mode,
                            onModeChange = { mode = it },
                        )
                    }
                }
            }
        }

        else -> {
            Surface(Modifier.fillMaxSize()) {}
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
