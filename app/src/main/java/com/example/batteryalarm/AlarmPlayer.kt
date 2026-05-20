package com.example.batteryalarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class AlarmPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null

    // Saves the stream volume that was set before the alarm fired, so we can
    // restore it cleanly when the alarm is stopped.
    private var savedAlarmStreamVolume: Int = -1

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun play(ringtoneUriString: String?, enableVibration: Boolean, volumePercent: Int = 80) {
        if (mediaPlayer?.isPlaying == true) return

        val uri = if (ringtoneUriString.isNullOrEmpty()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        } else {
            Uri.parse(ringtoneUriString)
        }

        val clampedVolume = volumePercent.coerceIn(0, 100)

        // ── Control the ALARM audio stream volume ─────────────────────────────
        // MediaPlayer.setVolume() works on a *relative* scale — 1.0f just means
        // "match the current stream level".  To guarantee true maximum output
        // when the user picks 100%, we must set the STREAM_ALARM level directly
        // via AudioManager.  We save the old level so we can restore it after.
        audioManager?.let { am ->
            val maxStreamVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            savedAlarmStreamVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val targetStreamVol = ((clampedVolume / 100f) * maxStreamVol).toInt()
                .coerceIn(0, maxStreamVol)
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                targetStreamVol,
                0 // no UI feedback while alarm is playing
            )
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                // Set relative volume to 1.0f — stream volume handles the level.
                setVolume(1.0f, 1.0f)
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If playback fails, restore stream volume immediately.
            restoreStreamVolume()
        }

        if (enableVibration) {
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        vibrator?.cancel()
        restoreStreamVolume()
    }

    private fun restoreStreamVolume() {
        if (savedAlarmStreamVolume >= 0) {
            audioManager?.setStreamVolume(
                AudioManager.STREAM_ALARM,
                savedAlarmStreamVolume,
                0
            )
            savedAlarmStreamVolume = -1
        }
    }
}
