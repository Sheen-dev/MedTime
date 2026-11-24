package com.example.medtime

import android.content.Context
import android.net.Uri

// The 5 alarm sounds of the app (the files are in res/raw)
object AlarmSounds {

    data class Sound(val name: String, val rawRes: Int)

    val all = listOf(
        Sound("Chime", R.raw.alarm_chime),
        Sound("Bell", R.raw.alarm_bell),
        Sound("Beep", R.raw.alarm_beep),
        Sound("Melody", R.raw.alarm_melody),
        Sound("Gentle", R.raw.alarm_gentle)
    )

    // The names for the popup list
    val names: Array<String> = all.map { it.name }.toTypedArray()

    // A saved number that is not valid becomes 0 (Chime)
    fun safeIndex(index: Int): Int = if (index in all.indices) index else 0

    fun nameOf(index: Int): String = all[safeIndex(index)].name

    // The address of the sound file (used by the notification channel)
    fun uri(context: Context, index: Int): Uri {
        val res = all[safeIndex(index)].rawRes
        return Uri.parse("android.resource://${context.packageName}/$res")
    }
}