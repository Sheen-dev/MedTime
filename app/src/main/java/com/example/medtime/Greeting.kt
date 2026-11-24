package com.example.medtime

import java.util.Calendar

// The greeting on Home. It reads the clock of the phone.
object Greeting {

    // 12:00 AM to 11:59 AM = Good morning
    // 12:00 PM to 5:59 PM  = Good afternoon
    // 6:00 PM to 11:59 PM  = Good night
    fun forNow(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning!"
            hour < 18 -> "Good afternoon!"
            else -> "Good night!"
        }
    }
}