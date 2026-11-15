package com.nickorton.sholto.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.nickorton.sholto.engine.CalloutStyle
import com.nickorton.sholto.engine.Combo
import com.nickorton.sholto.engine.Stance
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Speaks punch calls and combos aloud. [TtsAnnouncer] uses Android's [TextToSpeech];
 * unit tests use [None] or a recording implementation.
 */
fun interface CallAnnouncer {
    fun announce(
        call: Combo,
        style: CalloutStyle,
        stance: Stance,
        speechRate: Float,
    )

    companion object {
        val None = CallAnnouncer { _, _, _, _ -> }
    }
}

/**
 * Speaks punch calls through [TextToSpeech] (DESIGN.md §3.3).
 *
 * The synthesizer is created and warmed up with a silent utterance as soon as
 * this object is constructed, so the first real call doesn't pay a cold-start
 * delay. Like [SoundPlayer], speech plays on the media stream (`USAGE_MEDIA`)
 * and asks for transient audio focus with ducking so music from another app
 * dips while a call is spoken and comes back up right after.
 */
class TtsAnnouncer(
    context: Context,
) : CallAnnouncer {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val utteranceCounter = AtomicInteger(0)

    private val attributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    private val focusRequest =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()

    private var ready = false
    private var lastSpeechRate = -1f
    private var activeUtterances = 0
    private val releaseFocusFallback =
        Runnable {
            activeUtterances = 0
            audioManager.abandonAudioFocusRequest(focusRequest)
        }

    private val tts: TextToSpeech =
        TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                onTtsReady()
            }
        }

    private fun onTtsReady() {
        tts.setAudioAttributes(attributes)
        val locale = Locale.US
        val availability = tts.isLanguageAvailable(locale)
        if (availability >= TextToSpeech.LANG_AVAILABLE) {
            tts.language = locale
        }
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) = onUtteranceFinished(utteranceId)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = onUtteranceFinished(utteranceId)

                override fun onStop(
                    utteranceId: String?,
                    interrupted: Boolean,
                ) = onUtteranceFinished(utteranceId)
            },
        )
        // Warm up the synthesizer pipeline so the first call speaks without
        // waiting for the audio track to spin up.
        tts.playSilentUtterance(WARMUP_SILENCE_MS, TextToSpeech.QUEUE_FLUSH, WARMUP_UTTERANCE_ID)
        ready = true
    }

    override fun announce(
        call: Combo,
        style: CalloutStyle,
        stance: Stance,
        speechRate: Float,
    ) {
        if (!ready) return
        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) return

        val rate = speechRate.coerceIn(0.8f, 2.0f)
        if (rate != lastSpeechRate) {
            tts.setSpeechRate(rate)
            lastSpeechRate = rate
        }

        val id = "call-${utteranceCounter.incrementAndGet()}"
        activeUtterances++
        // Safety fallback in case a third-party TTS engine drops a callback.
        handler.removeCallbacks(releaseFocusFallback)
        handler.postAtTime(releaseFocusFallback, SystemClock.uptimeMillis() + FOCUS_TIMEOUT_MS)

        val spoken = call.spokenText(style, stance)
        val result = tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result == TextToSpeech.ERROR) {
            onUtteranceFinished(id)
        }
    }

    private fun onUtteranceFinished(utteranceId: String?) {
        if (utteranceId == WARMUP_UTTERANCE_ID) return
        handler.post {
            activeUtterances = (activeUtterances - 1).coerceAtLeast(0)
            if (activeUtterances == 0) {
                handler.removeCallbacks(releaseFocusFallback)
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
        }
    }

    /** Stops speech and shuts down the engine. */
    fun release() {
        ready = false
        handler.removeCallbacks(releaseFocusFallback)
        audioManager.abandonAudioFocusRequest(focusRequest)
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val WARMUP_UTTERANCE_ID = "warmup"
        const val WARMUP_SILENCE_MS = 10L
        const val FOCUS_TIMEOUT_MS = 3_000L
    }
}
