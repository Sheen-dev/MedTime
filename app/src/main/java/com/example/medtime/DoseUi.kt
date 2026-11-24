package com.example.medtime

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat

// Small helpers that draw a dose on the screen
object DoseUi {

    // The big time: blue. After the dose is taken, it turns gray.
    fun bindTime(context: Context, timeView: TextView, dose: Dose) {
        timeView.text = dose.timeText()
        timeView.setTextColor(
            ContextCompat.getColor(
                context,
                if (dose.taken) R.color.text_gray else R.color.medtime_blue
            )
        )
    }

    // The status badge: "Overdue" or "Upcoming". When the dose is taken, the
    // badge is hidden, because the button already says "Taken".
    fun bindStatus(context: Context, badge: TextView, dose: Dose, nowMinute: Int) {
        when {
            dose.taken -> {
                badge.visibility = View.GONE
            }
            dose.isOverdue(nowMinute) -> {
                badge.visibility = View.VISIBLE
                badge.text = "Overdue"
                badge.setBackgroundResource(R.drawable.bg_badge_gray)
                badge.setTextColor(Color.parseColor("#DC2626"))
            }
            else -> {
                badge.visibility = View.VISIBLE
                badge.text = "Upcoming"
                badge.setBackgroundResource(R.drawable.bg_badge)
                badge.setTextColor(ContextCompat.getColor(context, R.color.medtime_blue))
            }
        }
    }

    // The Take button. Not taken: blue "Take". Taken: gray "Taken", and it does nothing.
    fun bindTakeButton(
        context: Context,
        button: AppCompatButton,
        dose: Dose,
        onTake: () -> Unit
    ) {
        if (dose.taken) {
            button.text = "Taken"
            button.isEnabled = false
            button.setOnClickListener(null)
            button.setBackgroundResource(R.drawable.bg_button_taken)
            button.setTextColor(ContextCompat.getColor(context, R.color.check_green))
        } else {
            button.text = "Take"
            button.isEnabled = true
            button.setBackgroundResource(R.drawable.bg_button)
            button.setTextColor(ContextCompat.getColor(context, R.color.white))
            button.setOnClickListener { onTake() }
        }
    }

    // The small line under the name. Example: "1 tablet · Dose 1 of 2"
    fun subtitle(dose: Dose): String {
        return if (dose.total > 1) "${dose.medicine.dosageText()} · ${dose.label()}" else dose.medicine.dosageText()
    }
}