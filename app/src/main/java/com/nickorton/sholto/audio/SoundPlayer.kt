package com.nickorton.sholto.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RawRes
import com.nickorton.sholto.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Plays the timer sounds through [SoundPool]. All sounds are loaded once,
 * when this is created, so playing one has no decode delay.
 *
 * Each sound asks for transient audio focus that allows ducking, so music
 * from another app gets quieter instead of stopping, and gives focus back
 * once the sound has finished. If focus is refused (during a phone call, for
 * example) the sound is skipped.
 *
 * Sounds use the media stream (USAGE_MEDIA). The sonification stream
 * follows the ringer on most phones, so a phone on vibrate would silence the
 * bell. The media volume is the one already turned up for music.
 *
 * Call [play] from the main thread.
 */
class SoundPlayer(
    context: Context,
) : CuePlayer {
    private enum class Sound(
        @param:RawRes val resId: Int,
        val lengthMs: Long,
    ) {
        Bell(R.raw.bell, 2_000L),
        TripleBell(R.raw.bell_triple, 2_400L),
        LongBell(R.raw.bell_long, 4_000L),
        Clacks(R.raw.clack_triple, 520L),
        Beep(R.raw.beep, 150L),
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val attributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private val focusRequest =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            // Cues are short, so there is nothing to do when focus changes.
            .setOnAudioFocusChangeListener { }
            .build()

    private val soundPool =
        SoundPool
            .Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attributes)
            .build()

    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val soundIds: Map<Sound, Int>

    // When focus should be given back: after the last sound still playing.
    private var releaseFocusAtMs = 0L
    private val releaseFocus = Runnable { audioManager.abandonAudioFocusRequest(focusRequest) }

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded += sampleId
        }
        soundIds = Sound.entries.associateWith { soundPool.load(context, it.resId, 1) }
    }

    override fun play(cue: Cue) {
        val sound =
            when (cue) {
                Cue.RoundStart -> Sound.Bell
                Cue.Warning -> Sound.Clacks
                Cue.RoundEnd -> Sound.TripleBell
                Cue.Countdown -> Sound.Beep
                Cue.SessionDone -> Sound.LongBell
            }
        val id = soundIds.getValue(sound)
        if (id !in loaded) return

        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) return
        soundPool.play(id, 1f, 1f, 1, 0, 1f)

        // Hold focus a little past the end, so the ducked music doesn't
        // come back up under the bell's tail.
        val now = SystemClock.uptimeMillis()
        releaseFocusAtMs = maxOf(releaseFocusAtMs, now + sound.lengthMs + 250L)
        handler.removeCallbacks(releaseFocus)
        handler.postAtTime(releaseFocus, releaseFocusAtMs)
    }
}
