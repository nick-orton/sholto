package com.nickorton.sholto

import android.app.Application
import com.nickorton.sholto.audio.CompositeCuePlayer
import com.nickorton.sholto.audio.CuePlayer
import com.nickorton.sholto.audio.Haptics
import com.nickorton.sholto.audio.SoundPlayer

/** Holds the app-wide objects. The app is small, so DI is by hand. */
class SholtoApplication : Application() {
    /** Sounds and vibration, created at startup so the sounds load early. */
    lateinit var cuePlayer: CuePlayer
        private set

    override fun onCreate() {
        super.onCreate()
        cuePlayer = CompositeCuePlayer(SoundPlayer(this), Haptics(this))
    }
}
