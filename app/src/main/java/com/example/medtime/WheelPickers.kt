package com.example.medtime

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import java.util.Calendar

// Scrolling pickers (wheels) for the time, the date and the dosage unit.
// You scroll up and down instead of tapping a clock or a calendar.
object WheelPickers {

    private val months = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    // Makes the numbers of a wheel readable in light and dark mode
    private fun paint(context: Context, picker: NumberPicker) {
        val color = ContextCompat.getColor(context, R.color.text_dark)
        for (i in 0 until picker.childCount) {
            val child = picker.getChildAt(i)
            if (child is EditText) child.setTextColor(color)
        }
        picker.wrapSelectorWheel = true
        picker.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    private fun setValues(picker: NumberPicker, values: Array<String>, selected: Int) {
        picker.displayedValues = null
        picker.minValue = 0
        picker.maxValue = values.size - 1
        picker.displayedValues = values
        picker.value = selected
    }

    // Scrolling time. startMinute = minutes of the day (10:00 AM = 600), or null = now.
    fun pickTime(
        context: Context,
        title: String,
        startMinute: Int?,
        onPicked: (Int) -> Unit
    ) {
        val start = startMinute ?: DoseScheduler.minuteNow()
        val hour24 = start / 60
        val minute = start % 60

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_wheel_time, null)
        val npHour = view.findViewById<NumberPicker>(R.id.npHour)
        val npMinute = view.findViewById<NumberPicker>(R.id.npMinute)
        val npAmPm = view.findViewById<NumberPicker>(R.id.npAmPm)
        view.findViewById<TextView>(R.id.tvColon)
            .setTextColor(ContextCompat.getColor(context, R.color.text_dark))

        val hours = Array(12) { (it + 1).toString() }            // 1 .. 12
        val minutes = Array(60) { String.format("%02d", it) }    // 00 .. 59
        val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12

        setValues(npHour, hours, hour12 - 1)
        setValues(npMinute, minutes, minute)
        setValues(npAmPm, arrayOf("AM", "PM"), if (hour24 < 12) 0 else 1)
        npAmPm.wrapSelectorWheel = false

        paint(context, npHour)
        paint(context, npMinute)
        paint(context, npAmPm)
        npAmPm.wrapSelectorWheel = false

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                val h12 = npHour.value + 1            // 1 .. 12
                val pm = npAmPm.value == 1
                val h24 = (h12 % 12) + if (pm) 12 else 0
                onPicked(h24 * 60 + npMinute.value)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Scrolling date. neutralText = an extra button (for example "No end date").
    fun pickDate(
        context: Context,
        title: String,
        start: Calendar,
        onPicked: (year: Int, month: Int, day: Int) -> Unit,
        neutralText: String? = null,
        onNeutral: (() -> Unit)? = null
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_wheel_date, null)
        val npMonth = view.findViewById<NumberPicker>(R.id.npMonth)
        val npDay = view.findViewById<NumberPicker>(R.id.npDay)
        val npYear = view.findViewById<NumberPicker>(R.id.npYear)

        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        val startYear = start.get(Calendar.YEAR)
        val firstYear = minOf(thisYear, startYear)
        val lastYear = maxOf(thisYear + 10, startYear)
        val years = Array(lastYear - firstYear + 1) { (firstYear + it).toString() }

        setValues(npMonth, months, start.get(Calendar.MONTH))
        setValues(npYear, years, startYear - firstYear)

        // The day wheel always has the right number of days for the month and year
        fun updateDays() {
            val check = Calendar.getInstance()
            check.clear()
            check.set(firstYear + npYear.value, npMonth.value, 1)
            val days = check.getActualMaximum(Calendar.DAY_OF_MONTH)
            val keep = npDay.value
            npDay.minValue = 1
            npDay.maxValue = days
            npDay.value = minOf(if (keep == 0) start.get(Calendar.DAY_OF_MONTH) else keep, days)
        }
        npDay.minValue = 1
        npDay.maxValue = 31
        npDay.value = start.get(Calendar.DAY_OF_MONTH)
        updateDays()

        npMonth.setOnValueChangedListener { _, _, _ -> updateDays() }
        npYear.setOnValueChangedListener { _, _, _ -> updateDays() }

        paint(context, npMonth)
        paint(context, npDay)
        paint(context, npYear)
        npYear.wrapSelectorWheel = false

        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                onPicked(firstYear + npYear.value, npMonth.value, npDay.value)
            }
            .setNegativeButton("Cancel", null)

        if (neutralText != null && onNeutral != null) {
            builder.setNeutralButton(neutralText) { _, _ -> onNeutral() }
        }
        builder.show()
    }

    // Scrolling list of text. Returns the index of the chosen item.
    fun pickFromList(
        context: Context,
        title: String,
        items: Array<String>,
        selected: Int,
        onPicked: (Int) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_wheel_single, null)
        val picker = view.findViewById<NumberPicker>(R.id.npSingle)
        setValues(picker, items, selected)
        paint(context, picker)

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("OK") { _, _ -> onPicked(picker.value) }
            .setNegativeButton("Cancel", null)
            .show()
    }
}