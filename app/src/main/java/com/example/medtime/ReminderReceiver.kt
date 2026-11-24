package com.example.medtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import java.util.Calendar

// Wakes up when: an alarm time arrives, or the user taps "Take" in the notification
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            // The test alarm of Settings ("Test a real alarm in 1 minute")
            if (intent.action == ReminderIds.ACTION_DOSE &&
                intent.getBooleanExtra(ReminderIds.EXTRA_TEST, false)
            ) {
                AlarmLog.add(context, "Test alarm received. Starting the alarm...")
                try {
                    AlarmService.ringTest(context)
                } catch (e: Exception) {
                    Log.e("MedTime", "Could not start the test alarm", e)
                    AlarmLog.add(context, "The test alarm could not start: ${e.message}")
                }
                return
            }

            val medicineId = intent.getLongExtra(ReminderIds.EXTRA_MEDICINE_ID, -1L)
            val doseIndex = intent.getIntExtra(ReminderIds.EXTRA_DOSE_INDEX, -1)
            if (medicineId == -1L || doseIndex < 0) return

            when (intent.action) {
                ReminderIds.ACTION_DOSE -> onDoseTime(context, medicineId, doseIndex)
                ReminderIds.ACTION_TAKE -> onTake(context, intent, medicineId, doseIndex)
            }
        } catch (e: Exception) {
            Log.e("MedTime", "The reminder receiver failed", e)
            AlarmLog.add(context, "The reminder receiver failed: ${e.message}")
        }
    }

    // The dose time arrived: ring like an alarm clock
    private fun onDoseTime(context: Context, medicineId: Long, doseIndex: Int) {
        val medicine = MedicineStorage.getById(context, medicineId)
        if (medicine == null) {
            AlarmLog.add(context, "Alarm received, but the medicine does not exist anymore")
            return
        }

        AlarmLog.add(context, "Alarm RECEIVED: ${medicine.name}, dose ${doseIndex + 1}")

        // First set tomorrow's alarm
        AlarmScheduler.scheduleDose(context, medicine, doseIndex)

        if (!medicine.notificationsOn) {
            AlarmLog.add(context, "Skipped: notifications are OFF for ${medicine.name}")
            return
        }

        val today = Calendar.getInstance()

        // Outside the Start Date / End Date: no alarm
        if (!DoseScheduler.appliesOn(medicine, today)) {
            AlarmLog.add(context, "Skipped: today is outside the Start Date / End Date")
            return
        }

        // Already taken today: no alarm
        val dateKey = DoseScheduler.dateKey(today)
        val taken = MedicineStorage.getTakenDoses(context, dateKey)
        if (taken.contains("$medicineId:$doseIndex")) {
            AlarmLog.add(context, "Skipped: this dose is already taken")
            return
        }

        try {
            // Sound that repeats, vibration, and a pop-up
            AlarmService.ring(context, medicineId, doseIndex, dateKey)
        } catch (e: Exception) {
            // The alarm service could not start: use the normal notification
            Log.e("MedTime", "Could not start the alarm service", e)
            AlarmLog.add(context, "The alarm service could not start: ${e.message}")
            NotificationHelper.showDose(context, medicine, doseIndex, dateKey)
        }
    }

    // The user tapped "Take" in the notification
    private fun onTake(context: Context, intent: Intent, medicineId: Long, doseIndex: Int) {
        val dateKey = intent.getStringExtra(ReminderIds.EXTRA_DATE_KEY)
            ?: DoseScheduler.dateKey(Calendar.getInstance())

        // This also stops the alarm and removes the notification
        MedicineStorage.markDoseTaken(context, medicineId, dateKey, doseIndex)
        Toast.makeText(context, "Marked as taken", Toast.LENGTH_SHORT).show()
    }
}