package com.example.medtime

// Names and numbers shared by the alarm, the notification and the Take screen
object ReminderIds {

    // What the receiver must do
    const val ACTION_DOSE = "com.example.medtime.ACTION_DOSE" // it is time to take a dose
    const val ACTION_TAKE = "com.example.medtime.ACTION_TAKE" // "Take" button

    // The "notes" we send inside intents
    const val EXTRA_MEDICINE_ID = "medicine_id"
    const val EXTRA_DOSE_INDEX = "dose_index"
    const val EXTRA_DATE_KEY = "date_key"
    const val EXTRA_TEST = "test" // true = a test alarm from Settings

    // One number for each dose: used as the notification id
    fun notificationId(medicineId: Long, doseIndex: Int): Int {
        return (medicineId % 100_000_000L).toInt() * 10 + doseIndex
    }
}