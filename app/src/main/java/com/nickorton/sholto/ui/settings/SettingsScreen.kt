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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nickorton.sholto.R
import com.nickorton.sholto.audio.Cue
import com.nickorton.sholto.data.LengthRange
import com.nickorton.sholto.data.Preset
import com.nickorton.sholto.data.PresetNameError
import com.nickorton.sholto.data.Presets
import com.nickorton.sholto.data.TapBehavior
import com.nickorton.sholto.data.TimerLimits
import com.nickorton.sholto.data.TimerSettings
import com.nickorton.sholto.engine.RoundConfig
import com.nickorton.sholto.ui.theme.SholtoTheme
import com.nickorton.sholto.ui.timer.formatDuration
import com.nickorton.sholto.ui.timer.formatSummary

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
 * Timer settings and presets (DESIGN.md §2.4). Lengths are set with steppers
 * that stay inside [TimerLimits], so an impossible combination can't be
 * entered: shortening the round below the warning shortens the warning too.
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
) {
    var dialog by remember { mutableStateOf<PresetDialog?>(null) }

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
