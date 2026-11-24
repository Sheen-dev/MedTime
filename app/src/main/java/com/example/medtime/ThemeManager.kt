package com.example.medtime

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

// Saves the Dark mode choice and applies it
object ThemeManager {

    private const val PREFS_NAME = "medtime_settings"
    private const val KEY_DARK = "dark_mode"

    fun isDark(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK, false)
    }

    // Save the choice, then apply it (the screen redraws by itself)
    fun setDark(context: Context, dark: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK, dark).apply()
        applySaved(context)
    }

    // Read the saved choice and tell Android to use it
    fun applySaved(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            if (isDark(context)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}