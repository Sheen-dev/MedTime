package com.example.medtime

import android.content.Context

// Remembers who is logged in, so the user does not login again next time.
// Only the account number is saved here (never the password).
object SessionManager {

    private const val PREFS_NAME = "medtime_session"
    private const val KEY_USER_ID = "user_id"

    fun currentUserId(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_USER_ID, -1L)
    }

    fun isLoggedIn(context: Context): Boolean = currentUserId(context) != -1L

    fun logIn(context: Context, userId: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_USER_ID, userId).apply()
    }

    fun logOut(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_USER_ID).apply()
    }
}