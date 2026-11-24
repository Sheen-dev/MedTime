package com.example.medtime

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// A small notebook that remembers what the alarms did (for testing).
// You can read it in Settings > Alarm tools > View the alarm log.
object AlarmLog {

    private const val PREFS_NAME = "medtime_alarm_log"
    private const val KEY_LINES = "lines"
    private const val MAX_LINES = 150

    fun add(context: Context, message: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val time = SimpleDateFormat("MMM d, h:mm:ss a", Locale.US).format(Date())
            val old = prefs.getString(KEY_LINES, "") ?: ""

            val lines = (old.split("\n").filter { it.isNotBlank() } + "$time  $message")
                .takeLast(MAX_LINES)

            prefs.edit().putString(KEY_LINES, lines.joinToString("\n")).apply()
        } catch (e: Exception) {
            Log.e("MedTime", "Could not write the alarm log", e)
        }
    }

    // The lines, newest first
    fun read(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val text = prefs.getString(KEY_LINES, "") ?: ""
        return text.split("\n").filter { it.isNotBlank() }.reversed().joinToString("\n\n")
    }

    // Only the newest lines (for the report)
    fun recent(context: Context, count: Int): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val text = prefs.getString(KEY_LINES, "") ?: ""
        val lines = text.split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return "(the log is empty)"
        return lines.takeLast(count).reversed().joinToString("\n")
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_LINES).apply()
    }
}