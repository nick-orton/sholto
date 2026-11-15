package com.nickorton.sholto

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.nickorton.sholto.audio.VoicePackAnnouncer
import com.nickorton.sholto.data.SettingsRepository

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Holds the app-wide [SettingsRepository]. The running session and its sound
 * and haptic players live in `WorkoutService`.
 */
class SholtoApplication : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set

    /** Whether recorded clips are bundled for every word, so the bundled voice can be chosen. */
    val voicePackInstalled: Boolean by lazy { VoicePackAnnouncer.isInstalled(this) }

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(dataStore)
    }
}
