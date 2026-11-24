package com.example.medtime

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.View

class MedTimeApp : Application() {

    companion object {
        // How many screens of MedTime are visible right now
        @Volatile
        private var startedActivities = 0

        // true = MedTime is on the screen (the user is inside the app)
        val isInForeground: Boolean
            get() = startedActivities > 0
    }

    override fun onCreate() {
        super.onCreate()

        // Apply the saved Dark / Light choice at app start
        ThemeManager.applySaved(this)

        AlarmLog.add(this, "App process started")

        // Prepare the notification channels and set all the alarms.
        // If this fails, the app still opens (the error goes to the Logcat).
        try {
            NotificationHelper.createChannel(this)
            AlarmScheduler.scheduleAll(this)
        } catch (e: Exception) {
            Log.e("MedTime", "Reminder set-up failed at start-up", e)
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++

                // Every screen that has a back arrow (id: ivBack): the arrow closes the screen
                activity.findViewById<View>(R.id.ivBack)?.setOnClickListener {
                    activity.finish()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                if (startedActivities > 0) startedActivities--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}