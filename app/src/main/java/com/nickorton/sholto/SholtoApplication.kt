package com.nickorton.sholto

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.nickorton.sholto.audio.CuePlayer
import com.nickorton.sholto.audio.Haptics
import com.nickorton.sholto.audio.SoundPlayer
import com.nickorton.sholto.data.SettingsRepository

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Holds the app-wide objects. The app is small, so DI is by hand. */
class SholtoApplication : Application() {
    /** Sounds, created at startup so the samples load early. */
    lateinit var soundPlayer: CuePlayer
        private set

    lateinit var hapticPlayer: CuePlayer
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        soundPlayer = SoundPlayer(this)
        hapticPlayer = Haptics(this)
        settingsRepository = SettingsRepository(dataStore)
    }
}
