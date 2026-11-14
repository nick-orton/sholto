package com.nickorton.sholto.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickorton.sholto.data.Preset
import com.nickorton.sholto.data.PresetNameError
import com.nickorton.sholto.data.Presets
import com.nickorton.sholto.data.SettingsRepository
import com.nickorton.sholto.data.TimerSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the settings screen and Home show.
 *
 * @property activePreset The preset the settings match, or null for Custom.
 */
data class SettingsUiState(
    val settings: TimerSettings,
    val userPresets: List<Preset>,
    val activePreset: Preset?,
) {
    val builtInPresets: List<Preset> get() = Presets.builtIn

    val activePresetName: String get() = activePreset?.name ?: Presets.CUSTOM_NAME
}

/**
 * Edits the timer settings and presets. Every change is saved right away,
 * so there's no Save button to forget.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {
    /** Null until the saved settings have been read. */
    val uiState: StateFlow<SettingsUiState?> =
        combine(repository.settings, repository.userPresets) { settings, presets ->
            SettingsUiState(settings, presets, Presets.active(settings.round, presets))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun update(transform: (TimerSettings) -> TimerSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }

    fun applyPreset(preset: Preset) {
        viewModelScope.launch { repository.applyPreset(preset) }
    }

    /** Why [name] can't be used, or null if it can. */
    fun nameError(
        name: String,
        renamingId: String? = null,
    ): PresetNameError? = Presets.nameError(name, uiState.value?.userPresets.orEmpty(), renamingId)

    /** Saves the current format as a preset. An invalid name does nothing. */
    fun savePreset(name: String) {
        if (nameError(name) != null) return
        launchIgnoringBadName { repository.savePreset(name) }
    }

    fun renamePreset(
        id: String,
        name: String,
    ) {
        if (nameError(name, renamingId = id) != null) return
        launchIgnoringBadName { repository.renamePreset(id, name) }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch { repository.deletePreset(id) }
    }

    /**
     * The name was checked just before, but another edit could still take it
     * first. Losing that race shouldn't crash the app.
     */
    private fun launchIgnoringBadName(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (_: IllegalArgumentException) {
                // Nothing was saved, and the dialog is already closed.
            }
        }
    }
}
