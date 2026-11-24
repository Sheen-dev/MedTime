package com.example.medtime

import android.content.Context
import android.os.PowerManager

// The 3 things the app needs, in this order:
//   step 0 = notifications, step 1 = pop-up (display over other apps), step 2 = unrestricted battery
object PermissionSteps {

    const val STEP_COUNT = 3

    // Is this step allowed?
    fun isAllowed(context: Context, step: Int): Boolean {
        return when (step) {
            0 -> NotificationHelper.canNotify(context)
            1 -> AlarmOverlay.canShow(context)
            2 -> {
                val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                power.isIgnoringBatteryOptimizations(context.packageName)
            }
            else -> true
        }
    }

    // Are all 3 steps allowed?
    fun allGranted(context: Context): Boolean {
        return (0 until STEP_COUNT).all { isAllowed(context, it) }
    }

    // The first step that is not allowed yet (STEP_COUNT means: all allowed)
    fun firstMissing(context: Context): Int {
        for (step in 0 until STEP_COUNT) {
            if (!isAllowed(context, step)) return step
        }
        return STEP_COUNT
    }
}