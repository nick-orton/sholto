package com.nickorton.sholto.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nickorton.sholto.audio.Cue
import com.nickorton.sholto.engine.RoundConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

/**
 * Timer settings and user presets, stored in Preferences DataStore so they
 * survive force-stop and reboot.
 *
 * Every write goes through a single [DataStore.edit], so it's atomic. Missing
 * keys read as the defaults, and everything read is pulled back inside
 * [TimerLimits], so an old or damaged file can't start an impossible session.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val preferences: Flow<Preferences> =
        dataStore.data.catch { e ->
            // An unreadable file reads as defaults rather than crashing.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

    val settings: Flow<TimerSettings> = preferences.map(::readSettings).distinctUntilChanged()

    /** Your own presets, oldest first. */
    val userPresets: Flow<List<Preset>> = preferences.map(::readPresets).distinctUntilChanged()

    suspend fun updateSettings(transform: (TimerSettings) -> TimerSettings) {
        dataStore.edit { prefs -> writeSettings(prefs, transform(readSettings(prefs)).sanitized()) }
    }

    /** Switches to [preset]'s format, keeping everything else. */
    suspend fun applyPreset(preset: Preset) {
        updateSettings { it.copy(round = preset.applyTo(it.round)) }
    }

    /**
     * Saves the current format as a new preset named [name].
     *
     * @throws IllegalArgumentException if [Presets.nameError] rejects the name.
     */
    suspend fun savePreset(name: String): Preset {
        lateinit var saved: Preset
        dataStore.edit { prefs ->
            val presets = readPresets(prefs)
            requireValidName(name, presets, renamingId = null)
            saved = Preset.from(newId(), Presets.cleanName(name), readSettings(prefs).round)
            prefs[Keys.userPresets] = PresetCodec.encode(presets + saved)
        }
        return saved
    }

    /**
     * Renames your preset [id]. Does nothing if there's no such preset.
     *
     * @throws IllegalArgumentException if [Presets.nameError] rejects the name.
     */
    suspend fun renamePreset(
        id: String,
        name: String,
    ) {
        dataStore.edit { prefs ->
            val presets = readPresets(prefs)
            if (presets.none { it.id == id }) return@edit
            requireValidName(name, presets, renamingId = id)
            val clean = Presets.cleanName(name)
            prefs[Keys.userPresets] = PresetCodec.encode(presets.map { if (it.id == id) it.copy(name = clean) else it })
        }
    }

    /** Deletes your preset [id]. The current settings stay as they are. */
    suspend fun deletePreset(id: String) {
        dataStore.edit { prefs ->
            prefs[Keys.userPresets] = PresetCodec.encode(readPresets(prefs).filter { it.id != id })
        }
    }

    private fun requireValidName(
        name: String,
        presets: List<Preset>,
        renamingId: String?,
    ) {
        val error = Presets.nameError(name, presets, renamingId)
        require(error == null) { "Can't name a preset \"$name\": $error" }
    }

    private fun readSettings(prefs: Preferences): TimerSettings {
        val defaults = TimerSettings()
        val d = defaults.round
        // Lengths are clamped before RoundConfig checks them, and again
        // properly by sanitized().
        val roundLength = (prefs[Keys.roundLength] ?: d.roundLengthMs).coerceAtLeast(1L)
        val round =
            RoundConfig(
                roundLengthMs = roundLength,
                warningLengthMs = (prefs[Keys.warningLength] ?: d.warningLengthMs).coerceIn(0L, roundLength),
                restLengthMs = (prefs[Keys.restLength] ?: d.restLengthMs).coerceAtLeast(0L),
                rounds = prefs[Keys.rounds].let { if (it == null) d.rounds else it.takeIf { n -> n > 0 } },
                getReadyLengthMs = (prefs[Keys.getReadyLength] ?: d.getReadyLengthMs).coerceAtLeast(0L),
            )
        val muted = prefs[Keys.mutedSounds].orEmpty()
        return TimerSettings(
            round = round,
            tapBehavior = TapBehavior.entries.firstOrNull { it.name == prefs[Keys.tapBehavior] } ?: defaults.tapBehavior,
            // Muted cues are stored rather than enabled ones, so a cue added
            // in a later version starts out on.
            sounds = Cue.entries.filterTo(mutableSetOf()) { it.name !in muted },
            vibration = prefs[Keys.vibration] ?: defaults.vibration,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: defaults.keepScreenOn,
        ).sanitized()
    }

    private fun writeSettings(
        prefs: MutablePreferences,
        settings: TimerSettings,
    ) {
        val r = settings.round
        prefs[Keys.roundLength] = r.roundLengthMs
        prefs[Keys.warningLength] = r.warningLengthMs
        prefs[Keys.restLength] = r.restLengthMs
        prefs[Keys.rounds] = r.rounds ?: UNLIMITED
        prefs[Keys.getReadyLength] = r.getReadyLengthMs
        prefs[Keys.tapBehavior] = settings.tapBehavior.name
        prefs[Keys.mutedSounds] = (Cue.entries - settings.sounds).mapTo(mutableSetOf()) { it.name }
        prefs[Keys.vibration] = settings.vibration
        prefs[Keys.keepScreenOn] = settings.keepScreenOn
    }

    private fun readPresets(prefs: Preferences): List<Preset> = PresetCodec.decode(prefs[Keys.userPresets].orEmpty())

    private object Keys {
        val roundLength = longPreferencesKey("round_length_ms")
        val warningLength = longPreferencesKey("warning_length_ms")
        val restLength = longPreferencesKey("rest_length_ms")
        val rounds = intPreferencesKey("rounds")
        val getReadyLength = longPreferencesKey("get_ready_length_ms")
        val tapBehavior = stringPreferencesKey("tap_behavior")
        val mutedSounds = stringSetPreferencesKey("muted_sounds")
        val vibration = booleanPreferencesKey("vibration")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val userPresets = stringPreferencesKey("user_presets")
    }

    private companion object {
        /** Stored for unlimited rounds, since DataStore has no null. */
        const val UNLIMITED = 0
    }
}

/**
 * Stores user presets as text, one per line:
 * `id <TAB> round ms <TAB> warning ms <TAB> rest ms <TAB> rounds <TAB> name`,
 * with rounds 0 for unlimited. Names are cleaned to one line, and tabs are
 * whitespace, so they can't break the format. Lines that don't parse, or
 * describe an impossible format, are skipped.
 */
internal object PresetCodec {
    private const val FIELDS = 6

    fun encode(presets: List<Preset>): String =
        presets.joinToString("\n") { p ->
            listOf(p.id, p.roundLengthMs, p.warningLengthMs, p.restLengthMs, p.rounds ?: 0, Presets.cleanName(p.name))
                .joinToString("\t")
        }

    fun decode(text: String): List<Preset> =
        text
            .lineSequence()
            .mapNotNull(::decodeLine)
            .distinctBy { it.id }
            .toList()

    private fun decodeLine(line: String): Preset? {
        val f = line.split('\t', limit = FIELDS)
        if (f.size != FIELDS || f[0].isEmpty() || f[5].isBlank()) return null
        val roundLength = f[1].toLongOrNull() ?: return null
        val warningLength = f[2].toLongOrNull() ?: return null
        val restLength = f[3].toLongOrNull() ?: return null
        val rounds = f[4].toIntOrNull() ?: return null
        val config =
            runCatching { RoundConfig(roundLength, warningLength, restLength, rounds.takeIf { it > 0 }) }
                .getOrNull()
                ?.let { TimerSettings(round = it).sanitized().round }
                ?: return null
        return Preset.from(id = f[0], name = Presets.cleanName(f[5]), config = config)
    }
}
