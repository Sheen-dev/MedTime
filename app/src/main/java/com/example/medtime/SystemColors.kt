package com.example.medtime

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.core.content.ContextCompat

// The colors of the PHONE (not of the app): light or dark, and on Android 12+ the
// Material You colors of the wallpaper. The pop-up card uses them to look like a
// notification of the system.
class SystemColors(
    val background: Int,
    val primary: Int,
    val secondary: Int,
    val accent: Int
) {
    companion object {
        fun isPhoneDark(context: Context): Boolean {
            val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return mode == Configuration.UI_MODE_NIGHT_YES
        }

        fun of(context: Context): SystemColors {
            val dark = isPhoneDark(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                fun c(id: Int) = ContextCompat.getColor(context, id)
                return if (dark) {
                    SystemColors(
                        background = c(android.R.color.system_neutral1_800),
                        primary = c(android.R.color.system_neutral1_50),
                        secondary = c(android.R.color.system_neutral2_200),
                        accent = c(android.R.color.system_accent1_200)
                    )
                } else {
                    SystemColors(
                        background = c(android.R.color.system_neutral1_50),
                        primary = c(android.R.color.system_neutral1_900),
                        secondary = c(android.R.color.system_neutral2_700),
                        accent = c(android.R.color.system_accent1_600)
                    )
                }
            }

            // Older phones: the usual gray notification colors
            return if (dark) {
                SystemColors(0xFF2B2B2E.toInt(), 0xFFFFFFFF.toInt(), 0xFFBDBDBD.toInt(), 0xFF8AB4F8.toInt())
            } else {
                SystemColors(0xFFFFFFFF.toInt(), 0xFF202124.toInt(), 0xFF5F6368.toInt(), 0xFF1A73E8.toInt())
            }
        }
    }
}