package com.example.medtime

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast

// Plays a sound for a preview (when you choose a sound in Add Medicine)
object SoundPlayer {

    private var player: MediaPlayer? = null

    fun play(context: Context, soundIndex: Int) {
        stop()
        try {
            // The preview uses the ALARM volume, like the real alarm
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                Toast.makeText(
                    context,
                    "Your alarm volume is 0. Turn up the alarm volume to hear the sound.",
                    Toast.LENGTH_LONG
                ).show()
            }

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val newPlayer = MediaPlayer.create(
                context.applicationContext,
                AlarmSounds.all[AlarmSounds.safeIndex(soundIndex)].rawRes,
                attributes,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            if (newPlayer == null) {
                Toast.makeText(context, "This sound could not be played.", Toast.LENGTH_SHORT).show()
                return
            }

            newPlayer.setOnCompletionListener { stop() }
            newPlayer.start()
            player = newPlayer
        } catch (e: Exception) {
            Log.e("MedTime", "Could not play the sound", e)
        }
    }

    fun stop() {
        try {
            player?.release()
        } catch (e: Exception) {
            Log.e("MedTime", "Could not stop the sound", e)
        }
        player = null
    }
}