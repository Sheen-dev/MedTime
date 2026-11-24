package com.example.medtime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Sets and cancels the alarms. One alarm for each dose of each medicine.
// Every function has a safety net: if something fails, the app writes a note in
// the alarm log and the Logcat (tag "MedTime") and keeps running.
object AlarmScheduler {

    private const val TAG = "MedTime"

    // A medicine can have up to 8 doses, so we check 10 places
    private const val MAX_SLOTS = 10

    private fun alarmManager(context: Context): AlarmManager {
        return context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    private fun formatTrigger(millis: Long): String {
        return SimpleDateFormat("EEE MMM d, h:mm a", Locale.US).format(Date(millis))
    }

    // The "envelope" that Android sends to our receiver when the alarm rings.
    // Its address (the data) is different for each dose, so alarms never mix.
    private fun buildOperation(
        context: Context,
        medicineId: Long,
        doseIndex: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        intent.action = ReminderIds.ACTION_DOSE
        intent.data = Uri.parse("medtime://dose/$medicineId/$doseIndex")
        intent.putExtra(ReminderIds.EXTRA_MEDICINE_ID, medicineId)
        intent.putExtra(ReminderIds.EXTRA_DOSE_INDEX, doseIndex)

        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // The next moment when this dose must ring (null = never again)
    private fun nextTrigger(medicine: Medicine, minuteOfDay: Int): Long? {
        val earliest = System.currentTimeMillis() + 2_000L

        // Look at today and the next days, until we find a day that is allowed
        // (not before the Start Date, not after the End Date)
        for (offset in 0..400) {
            val day = Calendar.getInstance()
            day.add(Calendar.DAY_OF_YEAR, offset)
            day.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            day.set(Calendar.MINUTE, minuteOfDay % 60)
            day.set(Calendar.SECOND, 0)
            day.set(Calendar.MILLISECOND, 0)

            if (day.timeInMillis > earliest && DoseScheduler.appliesOn(medicine, day)) {
                return day.timeInMillis
            }
        }
        return null
    }

    // setAlarmClock rings exactly on time, even when the phone sleeps
    private fun setClockAlarm(context: Context, triggerAt: Long, operation: PendingIntent) {
        // What opens when the user taps the little alarm icon of the phone
        val showIntent = PendingIntent.getActivity(
            context, 0, Intent(context, HomeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager(context).setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showIntent),
            operation
        )
    }

    // Set the next alarm of one dose
    fun scheduleDose(context: Context, medicine: Medicine, doseIndex: Int) {
        try {
            val times = medicine.doseTimes.sorted()
            if (doseIndex !in times.indices) return

            val trigger = nextTrigger(medicine, times[doseIndex])
            if (trigger == null) {
                AlarmLog.add(
                    context,
                    "No alarm for ${medicine.name}, dose ${doseIndex + 1}: no day left (End Date passed?)"
                )
                return
            }

            setClockAlarm(context, trigger, buildOperation(context, medicine.id, doseIndex))
            AlarmLog.add(
                context,
                "Alarm set: ${medicine.name}, dose ${doseIndex + 1}, for ${formatTrigger(trigger)}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not set the alarm of ${medicine.name}, dose $doseIndex", e)
            AlarmLog.add(context, "COULD NOT SET the alarm of ${medicine.name}: ${e.message}")
        }
    }

    // A real alarm that rings after some seconds (for testing, from Settings)
    fun scheduleTestIn(context: Context, seconds: Int) {
        try {
            val intent = Intent(context, ReminderReceiver::class.java)
            intent.action = ReminderIds.ACTION_DOSE
            intent.data = Uri.parse("medtime://test")
            intent.putExtra(ReminderIds.EXTRA_TEST, true)

            val operation = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val trigger = System.currentTimeMillis() + seconds * 1000L
            setClockAlarm(context, trigger, operation)
            AlarmLog.add(context, "Test alarm set for ${formatTrigger(trigger)}")
        } catch (e: Exception) {
            Log.e(TAG, "Could not set the test alarm", e)
            AlarmLog.add(context, "COULD NOT SET the test alarm: ${e.message}")
        }
    }

    // Cancel the alarms of one medicine
    private fun cancelDaily(context: Context, medicineId: Long) {
        for (index in 0 until MAX_SLOTS) {
            val operation = buildOperation(context, medicineId, index)
            alarmManager(context).cancel(operation)
            operation.cancel()
        }
    }

    // Set all the alarms of one medicine (first cancel the old ones)
    fun scheduleMedicine(context: Context, medicine: Medicine) {
        try {
            cancelDaily(context, medicine.id)

            if (!medicine.notificationsOn) {
                AlarmLog.add(context, "Notifications are OFF for ${medicine.name}: no alarms set")
                return
            }

            for (index in medicine.doseTimes.indices) {
                scheduleDose(context, medicine, index)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not set the alarms of ${medicine.name}", e)
            AlarmLog.add(context, "COULD NOT SET the alarms of ${medicine.name}: ${e.message}")
        }
    }

    // Cancel everything of one medicine (used when it is deleted)
    fun cancelMedicine(context: Context, medicineId: Long) {
        try {
            cancelDaily(context, medicineId)

            for (index in 0 until MAX_SLOTS) {
                NotificationHelper.cancel(context, medicineId, index)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not cancel the alarms of medicine $medicineId", e)
        }
    }

    // Set the alarms of all medicines (after reboot, update or app start)
    fun scheduleAll(context: Context) {
        try {
            for (medicine in MedicineStorage.getAll(context)) {
                scheduleMedicine(context, medicine)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not set the alarms at start-up", e)
            AlarmLog.add(context, "COULD NOT SET the alarms at start-up: ${e.message}")
        }
    }
}