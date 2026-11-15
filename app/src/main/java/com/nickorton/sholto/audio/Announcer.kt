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
import com.nickorton.sholto.engine.CallTiming
import com.nickorton.sholto.engine.Callout
import com.nickorton.sholto.engine.Combo
import com.nickorton.sholto.engine.Voice
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Speaks punch calls and combos aloud. [TtsAnnouncer] uses Android's
 * [TextToSpeech] and [VoicePackAnnouncer] recorded clips; unit tests use
 * [None] or a recording implementation.
 */
fun interface CallAnnouncer {
    fun announce(
        call: Combo,
        callout: Callout,
    )

    /**
     * About how long [call] takes to say, for spacing calls and keeping them
     * clear of bells. By default an estimate from the syllables.
     */
    fun speechMs(
        call: Combo,
        callout: Callout,
    ): Long = CallTiming.speechMs(call.spokenText(callout.style, callout.stance), callout.speechRate)

    companion object {
        val None = CallAnnouncer { _, _ -> }
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
        callout: Callout,
    ) {
        if (!ready) return
        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) return

        val rate = callout.speechRate.coerceIn(0.8f, 2.0f)
        if (rate != lastSpeechRate) {
            tts.setSpeechRate(rate)
            lastSpeechRate = rate
        }

        val id = "call-${utteranceCounter.incrementAndGet()}"
        activeUtterances++
        // Safety fallback in case a third-party TTS engine drops a callback.
        handler.removeCallbacks(releaseFocusFallback)
        handler.postAtTime(releaseFocusFallback, SystemClock.uptimeMillis() + FOCUS_TIMEOUT_MS)

        val spoken = call.spokenText(callout.style, callout.stance)
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

/**
 * Says each call with the voice chosen in settings: the bundled voice when
 * [Callout.voice] asks for it and [canSayBundled] can say the call, the
 * system voice otherwise. So a missing or still-loading clip falls back to
 * speech instead of skipping the call.
 */
class VoiceRouter(
    private val system: CallAnnouncer,
    private val bundled: CallAnnouncer,
    private val canSayBundled: (Combo, Callout) -> Boolean,
) : CallAnnouncer {
    override fun announce(
        call: Combo,
        callout: Callout,
    ) = pick(call, callout).announce(call, callout)

    override fun speechMs(
        call: Combo,
        callout: Callout,
    ): Long = pick(call, callout).speechMs(call, callout)

    private fun pick(
        call: Combo,
        callout: Callout,
    ): CallAnnouncer = if (callout.voice == Voice.Bundled && canSayBundled(call, callout)) bundled else system
}
