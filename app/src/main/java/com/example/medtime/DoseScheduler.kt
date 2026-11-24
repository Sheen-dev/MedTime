package com.example.medtime

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// One dose on one day
data class Dose(
    val medicine: Medicine,
    val index: Int,        // 0 = first dose of that day, 1 = second dose, ...
    val total: Int,        // how many doses that day
    val minuteOfDay: Int,  // 10:00 AM = 600
    val taken: Boolean
) {
    fun timeText(): String = DoseScheduler.formatMinutes(minuteOfDay)

    fun label(): String = "Dose ${index + 1} of $total"

    // Time already passed and not taken
    fun isOverdue(nowMinute: Int): Boolean = !taken && minuteOfDay < nowMinute
}

object DoseScheduler {

    // 840 -> "2:00 PM"
    fun formatMinutes(minuteOfDay: Int): String {
        val hour = minuteOfDay / 60
        val minute = minuteOfDay % 60
        val amPm = if (hour < 12) "AM" else "PM"
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
    }

    // The time right now, as minutes of the day
    fun minuteNow(): Int {
        val now = Calendar.getInstance()
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    }

    // A day as text, for example "2026-11-16"
    fun dateKey(day: Calendar): String {
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            day.get(Calendar.YEAR),
            day.get(Calendar.MONTH) + 1,
            day.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun startOfDay(source: Calendar): Calendar {
        val c = source.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c
    }

    // "11/16/2026" -> a date (null if the text is empty or wrong)
    fun parseDate(text: String): Calendar? {
        return try {
            val format = SimpleDateFormat("MM/dd/yyyy", Locale.US)
            format.isLenient = false
            val date = format.parse(text) ?: return null
            val c = Calendar.getInstance()
            c.time = date
            startOfDay(c)
        } catch (e: Exception) {
            null
        }
    }

    // "11/16/2026" -> "Mon, Nov 16, 2026"
    fun prettyDate(text: String): String {
        val date = parseDate(text) ?: return text
        return SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(date.time)
    }

    // Does this medicine have doses on this day? (from Start Date to End Date)
    fun appliesOn(medicine: Medicine, day: Calendar): Boolean {
        val today = startOfDay(day)

        // Not started yet
        val start = parseDate(medicine.startDate)
        if (start != null && today.timeInMillis < start.timeInMillis) return false

        // Already finished (an empty End Date means no end)
        val end = parseDate(medicine.endDate)
        if (end != null && today.timeInMillis > end.timeInMillis) return false

        return true
    }

    // All doses of one day, sorted by time, with their Taken status
    fun dosesFor(context: Context, day: Calendar): List<Dose> {
        val taken = MedicineStorage.getTakenDoses(context, dateKey(day))
        val result = mutableListOf<Dose>()

        for (medicine in MedicineStorage.getAll(context)) {
            if (!appliesOn(medicine, day)) continue

            val minutes = medicine.doseTimes.sorted()
            minutes.forEachIndexed { index, minute ->
                result.add(
                    Dose(
                        medicine = medicine,
                        index = index,
                        total = minutes.size,
                        minuteOfDay = minute,
                        taken = taken.contains("${medicine.id}:$index")
                    )
                )
            }
        }
        return result.sortedBy { it.minuteOfDay }
    }
}