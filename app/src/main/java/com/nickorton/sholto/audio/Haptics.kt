package com.nickorton.sholto.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Vibrates on each phase change, with a pattern that echoes its sound: one
 * buzz for a round start, three quick ones for the warning, three long ones
 * for the round end and one long one when the session is done. Countdown
 * beeps don't vibrate.
 *
 * Vibrations are marked as alarms, like a clock app's timer. Unmarked ones
 * count as touch feedback, which many people turn off.
 */
class Haptics(
    context: Context,
) : CuePlayer {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    override fun play(cue: Cue) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect =
            when (cue) {
                Cue.RoundStart -> VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE)
                Cue.Warning -> pulses(on = 80L, off = 120L)
                Cue.RoundEnd -> pulses(on = 250L, off = 150L)
                Cue.SessionDone -> VibrationEffect.createOneShot(1_000L, VibrationEffect.DEFAULT_AMPLITUDE)
                Cue.Countdown -> return
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, ALARM_AUDIO_ATTRIBUTES)
        }
    }

    private companion object {
        val ALARM_AUDIO_ATTRIBUTES: AudioAttributes =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
    }

    private fun pulses(
        on: Long,
        off: Long,
    ): VibrationEffect = VibrationEffect.createWaveform(longArrayOf(0L, on, off, on, off, on), -1)
}
