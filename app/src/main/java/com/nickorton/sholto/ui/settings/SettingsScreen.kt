package com.nickorton.sholto.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nickorton.sholto.R
import com.nickorton.sholto.audio.Cue
import com.nickorton.sholto.data.CallerLimits
import com.nickorton.sholto.data.CallerPreset
import com.nickorton.sholto.data.CallerSettings
import com.nickorton.sholto.data.CombinedLimits
import com.nickorton.sholto.data.CombinedPreset
import com.nickorton.sholto.data.FinalPush
import com.nickorton.sholto.data.LengthRange
import com.nickorton.sholto.data.Preset
import com.nickorton.sholto.data.PresetNameError
import com.nickorton.sholto.data.Presets
import com.nickorton.sholto.data.TapBehavior
import com.nickorton.sholto.data.TimerLimits
import com.nickorton.sholto.data.TimerSettings
import com.nickorton.sholto.engine.CalloutStyle
import com.nickorton.sholto.engine.GapDistribution
import com.nickorton.sholto.engine.Punch
import com.nickorton.sholto.engine.PunchConfig
import com.nickorton.sholto.engine.RoundConfig
import com.nickorton.sholto.engine.Stance
import com.nickorton.sholto.engine.Voice
import com.nickorton.sholto.ui.home.WorkoutMode
import com.nickorton.sholto.ui.home.workoutModes
import com.nickorton.sholto.ui.theme.SholtoTheme
import com.nickorton.sholto.ui.timer.formatCallerSummary
import com.nickorton.sholto.ui.timer.formatDuration
import com.nickorton.sholto.ui.timer.formatPushFactor
import com.nickorton.sholto.ui.timer.formatSecondsTenths
import com.nickorton.sholto.ui.timer.formatSpeechRate
import com.nickorton.sholto.ui.timer.formatSummary
import kotlin.math.roundToInt

/** A preset dialog that's open, if any. */
private sealed interface PresetDialog {
    data object Save : PresetDialog

    data class Rename(
        val preset: Preset,
    ) : PresetDialog

    data class Delete(
        val preset: Preset,
    ) : PresetDialog
}

/**
 * Timer and punch-caller settings and presets (DESIGN.md §2.4, §3.5). Lengths
 * and gaps are set with steppers that stay inside [TimerLimits] and
 * [CallerLimits], so an impossible combination can't be entered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onUpdate: ((TimerSettings) -> TimerSettings) -> Unit,
    onApplyPreset: (Preset) -> Unit,
    onSavePreset: (String) -> Unit,
    onRenamePreset: (id: String, name: String) -> Unit,
    onDeletePreset: (id: String) -> Unit,
    nameError: (name: String, renamingId: String?) -> PresetNameError?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialMode: WorkoutMode = WorkoutMode.Timer,
    onUpdateCaller: ((CallerSettings) -> CallerSettings) -> Unit = {},
    onApplyCallerPreset: (CallerPreset) -> Unit = {},
    onUpdateFinalPush: ((FinalPush) -> FinalPush) -> Unit = {},
    onApplyCombinedPreset: (CombinedPreset) -> Unit = {},
) {
    var dialog by remember { mutableStateOf<PresetDialog?>(null) }
    var mode by rememberSaveable { mutableStateOf(initialMode) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val modes = workoutModes
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                modes.forEachIndexed { index, (item, label) ->
                    SegmentedButton(
                        selected = mode == item,
                        onClick = { mode = item },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) {
                        Text(stringResource(label))
                    }
                }
            }
            when (mode) {
                WorkoutMode.Timer -> {
                    PresetsSection(
                        state = state,
                        onApply = onApplyPreset,
                        onSave = { dialog = PresetDialog.Save },
                        onRename = { dialog = PresetDialog.Rename(it) },
                        onDelete = { dialog = PresetDialog.Delete(it) },
                    )
                    SectionDivider()
                    RoundsSection(state.settings, onUpdate)
                    SectionDivider()
                    SessionSection(state.settings, onUpdate)
                    SectionDivider()
                    SoundsSection(state.settings, onUpdate)
                }

                WorkoutMode.Caller -> {
                    CallerPresetsSection(
                        state = state,
                        onApply = onApplyCallerPreset,
                    )
                    SectionDivider()
                    CallerPacingSection(state.callerSettings, onUpdateCaller)
                    SectionDivider()
                    CallerPunchPoolSection(state.callerSettings, onUpdateCaller)
                    SectionDivider()
                    CallerCombosSection(state.callerSettings, onUpdateCaller)
                    SectionDivider()
                    CallerVoiceSection(state.callerSettings, onUpdateCaller, state.voicePackInstalled)
                    SectionDivider()
                    CallerSessionLengthSection(state.callerSettings, onUpdateCaller)
                    SectionDivider()
                    CallerSessionBehaviorSection(state.callerSettings, onUpdateCaller)
                }

                WorkoutMode.Combined -> {
                    CombinedPresetsSection(state = state, onApply = onApplyCombinedPreset)
                    SectionDivider()
                    FinalPushSection(state.finalPush, onUpdateFinalPush)
                    SectionDivider()
                    Text(
                        text = stringResource(R.string.combined_uses_settings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            Spacer(Modifier.heightIn(min = 24.dp))
        }
    }

    when (val d = dialog) {
        null -> {}

        PresetDialog.Save -> {
            PresetNameDialog(
                title = stringResource(R.string.save_as_preset),
                initialName = "",
                error = { nameError(it, null) },
                onConfirm = {
                    onSavePreset(it)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }

        is PresetDialog.Rename -> {
            PresetNameDialog(
                title = stringResource(R.string.rename_preset),
                initialName = d.preset.name,
                error = { nameError(it, d.preset.id) },
                onConfirm = {
                    onRenamePreset(d.preset.id, it)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }

        is PresetDialog.Delete -> {
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.delete_preset_title, d.preset.name)) },
                text = { Text(stringResource(R.string.delete_preset_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        onDeletePreset(d.preset.id)
                        dialog = null
                    }) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(Modifier.padding(top = 16.dp))
}

@Composable
private fun PresetsSection(
    state: SettingsUiState,
    onApply: (Preset) -> Unit,
    onSave: () -> Unit,
    onRename: (Preset) -> Unit,
    onDelete: (Preset) -> Unit,
) {
    SectionTitle(stringResource(R.string.presets))
    (state.builtInPresets + state.userPresets).forEach { preset ->
        PresetRow(
            name = preset.name,
            summary = formatSummary(preset.applyTo(RoundConfig())),
            selected = preset.id == state.activePreset?.id,
            onClick = { onApply(preset) },
            menu =
                if (preset.builtIn) {
                    null
                } else {
                    { PresetMenu(preset, onRename = { onRename(preset) }, onDelete = { onDelete(preset) }) }
                },
        )
    }
    if (state.activePreset == null) {
        PresetRow(
            name = stringResource(R.string.preset_custom),
            summary = formatSummary(state.settings.round),
            selected = true,
            onClick = {},
            menu = null,
        )
    }
    // Saving only makes sense for settings that aren't a preset already.
    OutlinedButton(
        onClick = onSave,
        enabled = state.activePreset == null,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(stringResource(R.string.save_as_preset))
    }
}

@Composable
private fun PresetRow(
    name: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
    menu: (@Composable () -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        menu?.invoke()
    }
}

@Composable
private fun PresetMenu(
    preset: Preset,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.preset_options, preset.name),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun PresetNameDialog(
    title: String,
    initialName: String,
    error: (String) -> PresetNameError?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var touched by rememberSaveable { mutableStateOf(false) }
    val currentError = error(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    touched = true
                },
                label = { Text(stringResource(R.string.preset_name)) },
                singleLine = true,
                // A blank name isn't worth complaining about before typing.
                isError = touched && currentError != null,
                supportingText = {
                    if (touched && currentError != null) Text(nameErrorText(currentError))
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = currentError == null) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun nameErrorText(error: PresetNameError): String =
    when (error) {
        PresetNameError.Blank -> stringResource(R.string.name_blank)
        PresetNameError.TooLong -> stringResource(R.string.name_too_long, Presets.MAX_NAME_LENGTH)
        PresetNameError.Taken -> stringResource(R.string.name_taken)
    }

@Composable
private fun RoundsSection(
    settings: TimerSettings,
    onUpdate: ((TimerSettings) -> TimerSettings) -> Unit,
) {
    val round = settings.round
    SectionTitle(stringResource(R.string.section_rounds))
    LengthStepper(
        label = stringResource(R.string.round_length),
        valueMs = round.roundLengthMs,
        range = TimerLimits.roundLength,
        onChange = { ms -> onUpdate { it.withRoundLength(ms) } },
    )
    LengthStepper(
        label = stringResource(R.string.warning_length),
        summary = stringResource(R.string.warning_length_summary, formatDuration(round.workLengthMs)),
        valueMs = round.warningLengthMs,
        range = TimerLimits.warningLength(round.roundLengthMs),
        onChange = { ms -> onUpdate { it.withWarningLength(ms) } },
    )
    LengthStepper(
        label = stringResource(R.string.rest_length),
        valueMs = round.restLengthMs,
        range = TimerLimits.restLength,
        onChange = { ms -> onUpdate { it.withRestLength(ms) } },
    )

    // Remembers the count from before Unlimited was switched on.
    var lastFixedRounds by rememberSaveable { mutableStateOf(round.rounds ?: DEFAULT_FIXED_ROUNDS) }
    round.rounds?.let { lastFixedRounds = it }
    SwitchRow(
        label = stringResource(R.string.unlimited_rounds),
        summary = stringResource(R.string.unlimited_rounds_summary),
        checked = round.rounds == null,
        onCheckedChange = { on -> onUpdate { it.withRounds(if (on) null else lastFixedRounds) } },
    )
    val rounds = round.rounds
    Stepper(
        label = stringResource(R.string.rounds),
        value = rounds?.toString() ?: "∞",
        canDecrease = rounds != null && rounds > TimerLimits.MIN_ROUNDS,
        canIncrease = rounds != null && rounds < TimerLimits.MAX_ROUNDS,
        onDecrease = { onUpdate { s -> s.withRounds(s.round.rounds?.minus(1)) } },
        onIncrease = { onUpdate { s -> s.withRounds(s.round.rounds?.plus(1)) } },
    )
    LengthStepper(
        label = stringResource(R.string.get_ready),
        valueMs = round.getReadyLengthMs,
        range = TimerLimits.getReadyLength,
        onChange = { ms -> onUpdate { it.withGetReadyLength(ms) } },
    )
}

private const val DEFAULT_FIXED_ROUNDS = 12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionSection(
    settings: TimerSettings,
    onUpdate: ((TimerSettings) -> TimerSettings) -> Unit,
) {
    SectionTitle(stringResource(R.string.section_session))
    Text(stringResource(R.string.tap_behavior), style = MaterialTheme.typography.bodyLarge)
    val options = listOf(TapBehavior.Pause to R.string.tap_pause, TapBehavior.Stop to R.string.tap_stop)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        options.forEachIndexed { index, (behavior, label) ->
            SegmentedButton(
                selected = settings.tapBehavior == behavior,
                onClick = { onUpdate { it.copy(tapBehavior = behavior) } },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(label))
            }
        }
    }
    SwitchRow(
        label = stringResource(R.string.vibration),
        summary = stringResource(R.string.vibration_summary),
        checked = settings.vibration,
        onCheckedChange = { on -> onUpdate { it.copy(vibration = on) } },
    )
    SwitchRow(
        label = stringResource(R.string.keep_screen_on),
        checked = settings.keepScreenOn,
        onCheckedChange = { on -> onUpdate { it.copy(keepScreenOn = on) } },
    )
}

@Composable
private fun SoundsSection(
    settings: TimerSettings,
    onUpdate: ((TimerSettings) -> TimerSettings) -> Unit,
) {
    SectionTitle(stringResource(R.string.section_sounds))
    Cue.entries.forEach { cue ->
        SwitchRow(
            label = stringResource(soundLabel(cue)),
            checked = cue in settings.sounds,
            onCheckedChange = { on -> onUpdate { it.withSound(cue, on) } },
        )
    }
}

private fun soundLabel(cue: Cue): Int =
    when (cue) {
        Cue.RoundStart -> R.string.sound_round_start
        Cue.Warning -> R.string.sound_warning
        Cue.RoundEnd -> R.string.sound_round_end
        Cue.Countdown -> R.string.sound_countdown
        Cue.SessionDone -> R.string.sound_session_done
    }

@Composable
private fun CallerPresetsSection(
    state: SettingsUiState,
    onApply: (CallerPreset) -> Unit,
) {
    SectionTitle(stringResource(R.string.section_difficulty))
    state.callerPresets.forEach { preset ->
        PresetRow(
            name = preset.name,
            summary = formatCallerSummary(CallerSettings(punch = preset.applyTo(PunchConfig()))),
            selected = preset.id == state.activeCallerPreset?.id,
            onClick = { onApply(preset) },
            menu = null,
        )
    }
    if (state.activeCallerPreset == null) {
        PresetRow(
            name = stringResource(R.string.preset_custom),
            summary = formatCallerSummary(state.callerSettings),
            selected = true,
            onClick = {},
            menu = null,
        )
    }
}

@Composable
private fun CombinedPresetsSection(
    state: SettingsUiState,
    onApply: (CombinedPreset) -> Unit,
) {
    SectionTitle(stringResource(R.string.presets))
    state.combinedPresets.forEach { preset ->
        PresetRow(
            name = preset.name,
            summary = formatSummary(preset.timer.applyTo(RoundConfig())) + " · " + formatSecondsTenths(preset.caller.meanGapMs) + " mean",
            selected = preset.id == state.activeCombinedPreset?.id,
            onClick = { onApply(preset) },
            menu = null,
        )
    }
    if (state.activeCombinedPreset == null) {
        PresetRow(
            name = state.combinedName,
            summary = formatSummary(state.settings.round) + " · " + formatSecondsTenths(state.callerSettings.punch.meanGapMs) + " mean",
            selected = true,
            onClick = {},
            menu = null,
        )
    }
}

@Composable
private fun FinalPushSection(
    push: FinalPush,
    onUpdate: ((FinalPush) -> FinalPush) -> Unit,
) {
    SectionTitle(stringResource(R.string.section_final_push))
    SwitchRow(
        label = stringResource(R.string.final_push),
        summary = stringResource(R.string.final_push_summary),
        checked = push.enabled,
        onCheckedChange = { on -> onUpdate { it.copy(enabled = on) } },
    )
    if (push.enabled) {
        Stepper(
            label = stringResource(R.string.final_push_factor),
            summary = stringResource(R.string.final_push_factor_summary),
            value = formatPushFactor(push.factor),
            canDecrease = CombinedLimits.canStepPushFactorDown(push.factor),
            canIncrease = CombinedLimits.canStepPushFactorUp(push.factor),
            onDecrease = { onUpdate { it.withFactor(CombinedLimits.stepPushFactorDown(it.factor)) } },
            onIncrease = { onUpdate { it.withFactor(CombinedLimits.stepPushFactorUp(it.factor)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallerPacingSection(
    settings: CallerSettings,
    onUpdate: ((CallerSettings) -> CallerSettings) -> Unit,
) {
    val punch = settings.punch
    SectionTitle(stringResource(R.string.section_pacing))
    GapStepper(
        label = stringResource(R.string.mean_gap),
        valueMs = punch.meanGapMs,
        range = CallerLimits.meanGap,
        onChange = { ms -> onUpdate { it.withMeanGap(ms) } },
    )
    Text(stringResource(R.string.distribution), style = MaterialTheme.typography.bodyLarge)
    val distributions =
        listOf(
            GapDistribution.Exponential to R.string.dist_exponential,
            GapDistribution.Uniform to R.string.dist_uniform,
            GapDistribution.Normal to R.string.dist_normal,
            GapDistribution.Fixed to R.string.dist_fixed,
        )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        distributions.forEachIndexed { index, (dist, label) ->
            SegmentedButton(
                selected = punch.distribution == dist,
                onClick = { onUpdate { it.withDistribution(dist) } },
                shape = SegmentedButtonDefaults.itemShape(index, distributions.size),
            ) {
                Text(stringResource(label))
            }
        }
    }
    GapStepper(
        label = stringResource(R.string.min_gap),
        valueMs = punch.minGapMs,
        range = CallerLimits.minGap(punch.meanGapMs),
        onChange = { ms -> onUpdate { it.withMinGap(ms) } },
    )
    GapStepper(
        label = stringResource(R.string.max_gap),
        valueMs = punch.maxGapMs,
        range = CallerLimits.maxGap(punch.meanGapMs),
        onChange = { ms -> onUpdate { it.withMaxGap(ms) } },
    )
    if (punch.distribution == GapDistribution.Normal) {
        GapStepper(
            label = stringResource(R.string.std_dev),
            valueMs = punch.stdDevMs,
            range = CallerLimits.stdDev,
            onChange = { ms -> onUpdate { it.withStdDev(ms) } },
        )
    }
    GapPreviewChart(punch)
}

@Composable
private fun CallerPunchPoolSection(
    settings: CallerSettings,
    onUpdate: ((CallerSettings) -> CallerSettings) -> Unit,
) {
    val punch = settings.punch
    SectionTitle(stringResource(R.string.section_punches))
    SwitchRow(
        label = stringResource(R.string.avoid_repeats),
        summary = stringResource(R.string.avoid_repeats_summary),
        checked = punch.avoidRepeats,
        onCheckedChange = { on -> onUpdate { it.withAvoidRepeats(on) } },
    )
    Punch.entries.forEach { target ->
        SwitchRow(
            label = "${target.label} · ${target.fullName(settings.stance)}",
            checked = target in punch.enabled,
            onCheckedChange = { on -> onUpdate { it.withPunchEnabled(target, on) } },
        )
        if (target in punch.enabled) {
            WeightSlider(
                punchLabel = target.label,
                weight = punch.weightOf(target),
                onChange = { w -> onUpdate { it.withWeight(target, w) } },
            )
        }
    }
}

/** How often a punch is called, from 0 (never) to [PunchConfig.MAX_WEIGHT]. */
@Composable
private fun WeightSlider(
    punchLabel: String,
    weight: Int,
    onChange: (Int) -> Unit,
) {
    val description = stringResource(R.string.weight_of, punchLabel)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.weight),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = weight.toFloat(),
            onValueChange = { v -> if (v.roundToInt() != weight) onChange(v.roundToInt()) },
            valueRange = 0f..PunchConfig.MAX_WEIGHT.toFloat(),
            steps = PunchConfig.MAX_WEIGHT - 1,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp).semantics { contentDescription = description },
        )
        Text(
            text = weight.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 24.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallerCombosSection(
    settings: CallerSettings,
    onUpdate: ((CallerSettings) -> CallerSettings) -> Unit,
) {
    val punch = settings.punch
    SectionTitle(stringResource(R.string.section_combos))
    SwitchRow(
        label = stringResource(R.string.combos),
        summary = stringResource(R.string.combos_summary),
        checked = punch.combos,
        onCheckedChange = { on -> onUpdate { it.withCombos(on) } },
    )
    if (!punch.combos) return
    Stepper(
        label = stringResource(R.string.max_combo_length),
        value = punch.maxComboLength.toString(),
        canDecrease = punch.maxComboLength > 2,
        canIncrease = punch.maxComboLength < PunchConfig.MAX_COMBO_LENGTH,
        onDecrease = { onUpdate { it.withMaxComboLength(it.punch.maxComboLength - 1) } },
        onIncrease = { onUpdate { it.withMaxComboLength(it.punch.maxComboLength + 1) } },
    )
    Text(stringResource(R.string.combo_lengths), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
    Text(
        stringResource(R.string.combo_lengths_summary),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val used = punch.comboLengthWeights.take(punch.maxComboLength)
    val total = used.sum().coerceAtLeast(1)
    used.forEachIndexed { i, w ->
        val length = i + 1
        val percent = (100.0 * w / total).roundToInt()
        Stepper(
            label = pluralStringResource(R.plurals.combo_length, length, length),
            summary = stringResource(R.string.percent_of_calls, percent),
            value = w.toString(),
            canDecrease = w > 0,
            canIncrease = w < CallerLimits.MAX_LENGTH_WEIGHT,
            onDecrease = { onUpdate { it.withComboLengthWeight(length, w - CallerLimits.LENGTH_WEIGHT_STEP) } },
            onIncrease = { onUpdate { it.withComboLengthWeight(length, w + CallerLimits.LENGTH_WEIGHT_STEP) } },
        )
    }
    SwitchRow(
        label = stringResource(R.string.natural_combos),
        summary = stringResource(R.string.natural_combos_summary),
        checked = punch.naturalOnly,
        onCheckedChange = { on -> onUpdate { it.withNaturalOnly(on) } },
    )
    if (punch.naturalOnly) {
        SwitchRow(
            label = stringResource(R.string.allow_doubling),
            summary = stringResource(R.string.allow_doubling_summary),
            checked = punch.allowDoubling,
            onCheckedChange = { on -> onUpdate { it.withDoubling(on) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallerVoiceSection(
    settings: CallerSettings,
    onUpdate: ((CallerSettings) -> CallerSettings) -> Unit,
    voicePackInstalled: Boolean,
) {
    SectionTitle(stringResource(R.string.section_voice))
    Text(stringResource(R.string.voice), style = MaterialTheme.typography.bodyLarge)
    val voices =
        listOf(
            Voice.SystemTts to R.string.voice_system,
            Voice.Bundled to R.string.voice_bundled,
        )
    // Without the clips, calls use the system voice whatever is saved.
    val effectiveVoice = if (voicePackInstalled) settings.voice else Voice.SystemTts
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        voices.forEachIndexed { index, (voice, label) ->
            SegmentedButton(
                selected = effectiveVoice == voice,
                onClick = { onUpdate { it.withVoice(voice) } },
                enabled = voice == Voice.SystemTts || voicePackInstalled,
                shape = SegmentedButtonDefaults.itemShape(index, voices.size),
            ) {
                Text(stringResource(label))
            }
        }
    }
    Text(
        text =
            stringResource(
                when {
                    !voicePackInstalled -> R.string.voice_bundled_missing
                    effectiveVoice == Voice.Bundled -> R.string.voice_bundled_summary
                    else -> R.string.voice_system_summary
                },
            ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )

    Text(stringResource(R.string.stance), style = MaterialTheme.typography.bodyLarge)
    val stances =
        listOf(
            Stance.Orthodox to R.string.stance_orthodox,
            Stance.Southpaw to R.string.stance_southpaw,
        )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        stances.forEachIndexed { index, (stance, label) ->
            SegmentedButton(
                selected = settings.stance == stance,
                onClick = { onUpdate { it.withStance(stance) } },
                shape = SegmentedButtonDefaults.itemShape(index, stances.size),
            ) {
                Text(stringResource(label))
            }
        }
    }

    Text(stringResource(R.string.callout_style), style = MaterialTheme.typography.bodyLarge)
    val styles =
        listOf(
            CalloutStyle.Numbers to R.string.style_numbers,
            CalloutStyle.Names to R.string.style_names,
            CalloutStyle.Both to R.string.style_both,
        )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        styles.forEachIndexed { index, (style, label) ->
            SegmentedButton(
                selected = settings.calloutStyle == style,
                onClick = { onUpdate { it.withCalloutStyle(style) } },
                shape = SegmentedButtonDefaults.itemShape(index, styles.size),
            ) {
                Text(stringResource(label))
            }
        }
    }

    Stepper(
        label = stringResource(R.string.speech_rate),
        value = formatSpeechRate(settings.speechRate),
        canDecrease = CallerLimits.canStepSpeechRateDown(settings.speechRate),
        canIncrease = CallerLimits.canStepSpeechRateUp(settings.speechRate),
        onDecrease = { onUpdate { s -> s.withSpeechRate(CallerLimits.stepSpeechRateDown(s.speechRate)) } },
        onIncrease = { onUpdate { s -> s.withSpeechRate(CallerLimits.stepSpeechRateUp(s.speechRate)) } },
    )
}

@Composable
private fun CallerSessionLengthSection(
    settings: CallerSettings,
    onUpdate: ((CallerSettings) -> CallerSettings) -> Unit,
) {
    SectionTitle(stringResource(R.string.section_caller_session))
    var lastFixedDuration by rememberSaveable { mutableStateOf(settings.sessionLengthMs ?: DEFAULT_CALLER_DURATION_MS) }
    settings.sessionLengthMs?.let { lastFixedDuration = it }
    SwitchRow(
        label = stringResource(R.string.until_stopped),
        summary = stringResource(R.string.until_stopped_summary),
        checked = settings.sessionLengthMs == null,
        onCheckedChange = { on -> onUpdate { it.withSessionLength(if (on) null else lastFixedDuration) } },
    )
    val duration = settings.sessionLengthMs
    Stepper(
        label = stringResource(R.string.session_duration),
        value = duration?.let(::formatDuration) ?: "∞",
        canDecrease = duration != null && CallerLimits.sessionLength.canStepDown(duration),
        canIncrease = duration != null && CallerLimits.sessionLength.canStepUp(duration),
        onDecrease = {
            onUpdate { s ->
                s.sessionLengthMs?.let { s.withSessionLength(CallerLimits.sessionLength.stepDown(it)) } ?: s
            }
        },
        onIncrease = {
            onUpdate { s ->
                s.sessionLengthMs?.let { s.withSessionLength(CallerLimits.sessionLength.stepUp(it)) } ?: s
            }
        },
    )
}

private const val DEFAULT_CALLER_DURATION_MS = 3 * 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallerSessionBehaviorSection(
    settings: CallerSettings,
    onUpdate: ((CallerSettings) -> CallerSettings) -> Unit,
) {
    SectionTitle(stringResource(R.string.section_session))
    Text(stringResource(R.string.tap_behavior), style = MaterialTheme.typography.bodyLarge)
    val options = listOf(TapBehavior.Pause to R.string.tap_pause, TapBehavior.Stop to R.string.tap_stop)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        options.forEachIndexed { index, (behavior, label) ->
            SegmentedButton(
                selected = settings.tapBehavior == behavior,
                onClick = { onUpdate { it.copy(tapBehavior = behavior) } },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(label))
            }
        }
    }
    SwitchRow(
        label = stringResource(R.string.keep_screen_on),
        checked = settings.keepScreenOn,
        onCheckedChange = { on -> onUpdate { it.copy(keepScreenOn = on) } },
    )
}

/** A gap in tenths of a second (`1.5s`), stepped within [range]. */
@Composable
private fun GapStepper(
    label: String,
    valueMs: Long,
    range: LengthRange,
    onChange: (Long) -> Unit,
    summary: String? = null,
) {
    Stepper(
        label = label,
        summary = summary,
        value = formatSecondsTenths(valueMs),
        canDecrease = range.canStepDown(valueMs),
        canIncrease = range.canStepUp(valueMs),
        onDecrease = { onChange(range.stepDown(valueMs)) },
        onIncrease = { onChange(range.stepUp(valueMs)) },
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A length in `M:SS`, stepped within [range]. Zero shows as "Off". */
@Composable
private fun LengthStepper(
    label: String,
    valueMs: Long,
    range: LengthRange,
    onChange: (Long) -> Unit,
    summary: String? = null,
) {
    Stepper(
        label = label,
        summary = summary,
        value = if (valueMs == 0L) stringResource(R.string.off) else formatDuration(valueMs),
        canDecrease = range.canStepDown(valueMs),
        canIncrease = range.canStepUp(valueMs),
        onDecrease = { onChange(range.stepDown(valueMs)) },
        onIncrease = { onChange(range.stepUp(valueMs)) },
    )
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    summary: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FilledTonalIconButton(onClick = onDecrease, enabled = canDecrease, modifier = Modifier.size(48.dp)) {
            Text("−", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        FilledTonalIconButton(onClick = onIncrease, enabled = canIncrease, modifier = Modifier.size(48.dp)) {
            Text("+", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Preview(heightDp = 1800)
@Composable
private fun SettingsScreenPreview() {
    val presets = listOf(Preset("u1", "Sparring", 2 * 60_000L, 15_000L, 45_000L, 8))
    val settings = TimerSettings(round = RoundConfig(roundLengthMs = 150_000L, rounds = 5))
    SholtoTheme {
        SettingsScreen(
            state = SettingsUiState(settings, presets, activePreset = null),
            onUpdate = {},
            onApplyPreset = {},
            onSavePreset = {},
            onRenamePreset = { _, _ -> },
            onDeletePreset = {},
            nameError = { _, _ -> null },
            onBack = {},
        )
    }
}
