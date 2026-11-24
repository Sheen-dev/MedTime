package com.example.medtime

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

// Builds and shows the notifications (normal Android notifications that follow the phone theme)
object NotificationHelper {

    private const val TAG = "MedTime"

    // Old channels of earlier steps (we delete them)
    private const val OLD_CHANNEL_ID = "dose_reminders_v1"
    private const val OLD_ALARM_CHANNEL_ID = "dose_alarm_v1"
    private const val OLD_SOUND_CHANNEL_PREFIX = "dose_reminders_s"

    // The alarm notification: it vibrates (so Android pops it up on the screen),
    // but it has no sound, because the AlarmService plays the sound itself.
    const val ALARM_CHANNEL_ID = "dose_alarm_v2"

    // Used while the pop-up card is on the screen: it stays in the status bar, quietly
    const val QUIET_CHANNEL_ID = "dose_alarm_quiet_v1"

    // The BACKUP, used when Android does not let the alarm service start.
    // It uses the alarm tone of the phone, and it repeats until you dismiss it.
    const val BACKUP_CHANNEL_ID = "dose_backup_v1"

    const val TEST_NOTIFICATION_ID = 2_000_000_000

    private val vibration = longArrayOf(0, 500, 250, 500, 250, 500)

    // The alarm tone of the phone (or the notification tone if there is none)
    private fun phoneAlarmSound(): Uri? {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    // Can the app show notifications right now?
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // Creates the channels
    fun createChannel(context: Context) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.deleteNotificationChannel(OLD_CHANNEL_ID)
            manager.deleteNotificationChannel(OLD_ALARM_CHANNEL_ID)
            for (index in 0 until 5) {
                manager.deleteNotificationChannel(OLD_SOUND_CHANNEL_PREFIX + index)
            }

            // The alarm channel: pops up on the screen
            if (manager.getNotificationChannel(ALARM_CHANNEL_ID) == null) {
                val alarmChannel = NotificationChannel(
                    ALARM_CHANNEL_ID, "Dose alarm", NotificationManager.IMPORTANCE_HIGH
                )
                alarmChannel.description = "Rings like an alarm clock when it is time for your medicine"
                alarmChannel.setSound(null, null)
                alarmChannel.enableVibration(true)
                alarmChannel.vibrationPattern = longArrayOf(0, 300, 200, 300)
                alarmChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                manager.createNotificationChannel(alarmChannel)
            }

            // The quiet channel: no banner (the pop-up card is on the screen instead)
            if (manager.getNotificationChannel(QUIET_CHANNEL_ID) == null) {
                val quietChannel = NotificationChannel(
                    QUIET_CHANNEL_ID,
                    "Dose alarm (while you use the phone)",
                    NotificationManager.IMPORTANCE_LOW
                )
                quietChannel.description = "Stays in the status bar while the pop-up card is on the screen"
                quietChannel.setSound(null, null)
                quietChannel.enableVibration(false)
                quietChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                manager.createNotificationChannel(quietChannel)
            }

            // The backup channel: the alarm tone of the phone
            if (manager.getNotificationChannel(BACKUP_CHANNEL_ID) == null) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val backupChannel = NotificationChannel(
                    BACKUP_CHANNEL_ID,
                    "Dose reminder (backup alarm)",
                    NotificationManager.IMPORTANCE_HIGH
                )
                backupChannel.description = "Rings with the alarm tone of your phone"
                backupChannel.enableVibration(true)
                backupChannel.vibrationPattern = vibration
                backupChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                backupChannel.setSound(phoneAlarmSound(), attributes)
                manager.createNotificationChannel(backupChannel)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not create the notification channels", e)
        }
    }

    // A button of the notification that talks to the AlarmService
    private fun servicePending(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmService::class.java)
        intent.action = action
        return PendingIntent.getService(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // The notification of one dose.
    // alarmStyle = true: part of the alarm (no sound here, the AlarmService plays it).
    //   quiet = false: pops up on the screen and opens the Take screen on the lock screen.
    //   quiet = true: only stays in the status bar (the pop-up card is on the screen).
    // alarmStyle = false: the BACKUP. The notification itself rings with the alarm tone of
    //   the phone, repeating until you dismiss it, and opens the Take screen on the lock screen.
    fun buildDoseNotification(
        context: Context,
        medicine: Medicine,
        doseIndex: Int,
        dateKey: String,
        channel: String,
        alarmStyle: Boolean,
        quiet: Boolean = false
    ): Notification? {
        val times = medicine.doseTimes.sorted()
        if (doseIndex !in times.indices) return null

        val minute = times[doseIndex]
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        // Tap the notification: open the "Take your pill" screen
        val openIntent = Intent(context, TakePillActivity::class.java)
        openIntent.data = Uri.parse("medtime://open/${medicine.id}/$doseIndex")
        openIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        openIntent.putExtra(ReminderIds.EXTRA_MEDICINE_ID, medicine.id)
        openIntent.putExtra(ReminderIds.EXTRA_DOSE_INDEX, doseIndex)
        openIntent.putExtra(ReminderIds.EXTRA_DATE_KEY, dateKey)
        val openPending = PendingIntent.getActivity(context, 0, openIntent, flags)

        // "Take" button
        val takeIntent = Intent(context, ReminderReceiver::class.java)
        takeIntent.action = ReminderIds.ACTION_TAKE
        takeIntent.data = Uri.parse("medtime://take/${medicine.id}/$doseIndex")
        takeIntent.putExtra(ReminderIds.EXTRA_MEDICINE_ID, medicine.id)
        takeIntent.putExtra(ReminderIds.EXTRA_DOSE_INDEX, doseIndex)
        takeIntent.putExtra(ReminderIds.EXTRA_DATE_KEY, dateKey)
        val takePending = PendingIntent.getBroadcast(context, 0, takeIntent, flags)

        val timeText = DoseScheduler.formatMinutes(minute)
        val details = if (times.size > 1) {
            "${medicine.dosageText()} · Dose ${doseIndex + 1} of ${times.size}"
        } else {
            medicine.dosageText()
        }

        // A normal Android notification (no custom card): the phone draws it with
        // ITS OWN theme, colors and fonts, like the notifications of other apps.
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.medtime_blue))
            .setContentTitle("${medicine.name} · $timeText")
            .setContentText("Time to take $details")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Time to take $details"))
            .addAction(0, "Take", takePending)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(openPending)

        if (alarmStyle) {
            // On the lock screen: opens the Take screen by itself, like an alarm clock
            if (!quiet) {
                builder.setFullScreenIntent(openPending, true)
            }
            // Swipe the notification away: the sound stops
            builder.setDeleteIntent(servicePending(context, AlarmService.ACTION_SILENCE, 1))
        } else {
            // BACKUP: opens the Take screen on the lock screen, too
            builder.setFullScreenIntent(openPending, true)

            // Android 7 (no channels): the sound and vibration are set here
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setSound(phoneAlarmSound(), AudioManager.STREAM_ALARM)
                builder.setVibrate(vibration)
            }
        }

        val notification = builder.build()

        // BACKUP: the sound repeats until you take the dose or dismiss the notification
        if (!alarmStyle) {
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
        }

        return notification
    }

    // The notification of the test alarm (from Settings)
    fun buildTestAlarm(context: Context, quiet: Boolean = false): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openPending = PendingIntent.getActivity(
            context, 0, Intent(context, HomeActivity::class.java), flags
        )
        val stopPending = servicePending(context, AlarmService.ACTION_DISMISS, 2)

        val details = "1 tablet · This is how your reminder looks"

        return NotificationCompat.Builder(context, if (quiet) QUIET_CHANNEL_ID else ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.medtime_blue))
            .setContentTitle("Test medicine")
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .addAction(0, "Stop", stopPending)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .setDeleteIntent(servicePending(context, AlarmService.ACTION_SILENCE, 1))
            .build()
    }

    // The backup: a notification that rings with the alarm tone of the phone
    @SuppressLint("MissingPermission")
    fun showDose(context: Context, medicine: Medicine, doseIndex: Int, dateKey: String) {
        try {
            if (!canNotify(context)) {
                AlarmLog.add(context, "Notifications are OFF for MedTime: nothing can be shown")
                return
            }

            createChannel(context)

            val notification = buildDoseNotification(
                context, medicine, doseIndex, dateKey, BACKUP_CHANNEL_ID, false
            ) ?: return

            NotificationManagerCompat.from(context)
                .notify(ReminderIds.notificationId(medicine.id, doseIndex), notification)
            AlarmLog.add(context, "Backup alarm shown for ${medicine.name} (phone alarm tone, repeating)")
        } catch (e: Exception) {
            Log.e(TAG, "Could not show the notification of ${medicine.name}", e)
            AlarmLog.add(context, "Could not show the notification: ${e.message}")
        }
    }

    // Remove the notification of one dose, and stop its alarm if it is ringing
    fun cancel(context: Context, medicineId: Long, doseIndex: Int) {
        try {
            NotificationManagerCompat.from(context)
                .cancel(ReminderIds.notificationId(medicineId, doseIndex))
        } catch (e: Exception) {
            Log.e(TAG, "Could not remove the notification", e)
        }
        AlarmService.dismissIfRinging(context, medicineId, doseIndex)
    }
}