package com.nickorton.sholto.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.nickorton.sholto.engine.Callout
import com.nickorton.sholto.engine.Combo
import java.util.concurrent.ConcurrentHashMap

/**
 * Says calls with recorded clips, one per word, from `assets/voice/<word>.ogg`
 * (PLAN.md M10). Clips are loaded into [SoundPool] up front, so a call starts
 * the moment it's made, with none of the speech engine's start-up delay. A
 * combo is stitched from its words by [VoiceScript].
 *
 * The clips aren't in the repository: recording them is a separate step (see
 * `tools/voice/README.md`). Until every word has a clip, [isInstalled] is
 * false and [VoiceRouter] keeps using the system voice.
 *
 * Like the other players it uses the media stream and transient focus with
 * ducking. A new call cuts off one still being said, as the system voice does.
 * Call [announce] from the main thread.
 */
class VoicePackAnnouncer(
    context: Context,
) : CallAnnouncer {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val token = Any()

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

    private val soundPool =
        SoundPool
            .Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(attributes)
            .build()

    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val soundIds = mutableMapOf<String, Int>()
    private val clipMs = mutableMapOf<String, Long>()
    private val openFiles = mutableListOf<AssetFileDescriptor>()
    private val playing = mutableListOf<Int>()
    private val releaseFocus = Runnable { audioManager.abandonAudioFocusRequest(focusRequest) }

    /** True when there is a clip for every word a call can contain. */
    val isInstalled: Boolean

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded += sampleId
        }
        val assets = context.assets
        val present = installedWords(context)
        for (word in present) {
            val fd = runCatching { assets.openFd("$DIR/$word.ogg") }.getOrNull() ?: continue
            val length = durationMs(fd) ?: continue
            openFiles += fd
            clipMs[word] = length
            soundIds[word] = soundPool.load(fd, 1)
        }
        isInstalled = VoiceScript.WORDS.all { it in clipMs }
    }

    /** Whether every clip of [call] is loaded and ready to play. */
    fun canSay(
        call: Combo,
        callout: Callout,
    ): Boolean = VoiceScript.words(call, callout).all { w -> soundIds[w]?.let { it in loaded } == true }

    override fun announce(
        call: Combo,
        callout: Callout,
    ) {
        if (!canSay(call, callout)) return
        val script = VoiceScript.script(call, callout, clipMs) ?: return
        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) return

        // Cut off the call before, if it's still being said.
        handler.removeCallbacksAndMessages(token)
        playing.forEach(soundPool::stop)
        playing.clear()

        val start = SystemClock.uptimeMillis()
        for (clip in script.clips) {
            val id = soundIds.getValue(clip.word)
            if (clip.startMs == 0L) {
                play(id)
            } else {
                handler.postAtTime({ play(id) }, token, start + clip.startMs)
            }
        }
        handler.removeCallbacks(releaseFocus)
        handler.postAtTime(releaseFocus, token, start + script.lengthMs + FOCUS_TAIL_MS)
    }

    /** The exact length of the stitched call, or the estimate if a clip is missing. */
    override fun speechMs(
        call: Combo,
        callout: Callout,
    ): Long = VoiceScript.script(call, callout, clipMs)?.lengthMs ?: super.speechMs(call, callout)

    private fun play(soundId: Int) {
        val stream = soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        if (stream != 0) {
            playing += stream
            if (playing.size > MAX_STREAMS) playing.removeAt(0)
        }
    }

    /** Stops speaking and frees the clips. */
    fun release() {
        handler.removeCallbacksAndMessages(token)
        audioManager.abandonAudioFocusRequest(focusRequest)
        loaded.clear()
        soundPool.release()
        openFiles.forEach { runCatching { it.close() } }
        openFiles.clear()
    }

    private fun durationMs(fd: AssetFileDescriptor): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    companion object {
        private const val DIR = "voice"
        private const val MAX_STREAMS = 6
        private const val FOCUS_TAIL_MS = 250L

        /** The words with a clip in `assets/voice`, without reading the clips. */
        fun installedWords(context: Context): Set<String> =
            context.assets
                .list(DIR)
                .orEmpty()
                .filter { it.endsWith(".ogg") }
                .map { it.removeSuffix(".ogg") }
                .toSet()

        /** True when there is a clip for every word, so the bundled voice can be chosen. */
        fun isInstalled(context: Context): Boolean = installedWords(context).containsAll(VoiceScript.WORDS)
    }
}
