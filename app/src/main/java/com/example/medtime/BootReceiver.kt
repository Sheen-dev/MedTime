package com.example.medtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Wakes up when the phone restarts or the app is updated, and sets the alarms again
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            AlarmScheduler.scheduleAll(context)
        }
    }
}