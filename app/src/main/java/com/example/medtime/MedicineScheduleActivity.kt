package com.example.medtime

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class MedicineScheduleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medicine_schedule)
    }

    // Runs each time this screen appears
    override fun onResume() {
        super.onResume()
        showSchedule()
    }

    private fun showSchedule() {
        val container = findViewById<LinearLayout>(R.id.llSchedule)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)

        container.removeAllViews()

        val today = Calendar.getInstance()
        val dateKey = DoseScheduler.dateKey(today)
        val doses = DoseScheduler.dosesFor(this, today)
        val now = DoseScheduler.minuteNow()

        if (doses.isEmpty()) {
            tvEmpty.text = if (MedicineStorage.getAll(this).isEmpty()) {
                "No medicines yet. Add one from the Home screen."
            } else {
                "No doses for today."
            }
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
        }

        for (dose in doses) {
            val card = layoutInflater.inflate(R.layout.item_medicine_schedule, container, false)

            DoseUi.bindTime(this, card.findViewById<TextView>(R.id.tvTime), dose)
            card.findViewById<TextView>(R.id.tvName).text = dose.medicine.name
            card.findViewById<TextView>(R.id.tvDetails).text = DoseUi.subtitle(dose)
            DoseUi.bindStatus(this, card.findViewById<TextView>(R.id.tvStatus), dose, now)

            // The Take button: save the dose as taken, then draw the list again
            DoseUi.bindTakeButton(this, card.findViewById(R.id.btnTake), dose) {
                MedicineStorage.markDoseTaken(this, dose.medicine.id, dateKey, dose.index)
                Toast.makeText(
                    this,
                    "${dose.medicine.name} (${dose.label()}) marked as taken",
                    Toast.LENGTH_SHORT
                ).show()
                showSchedule()
            }

            // Tap the card (not the button): open the details of this medicine
            card.setOnClickListener {
                val detailIntent = Intent(this, MedicineDetailActivity::class.java)
                detailIntent.putExtra(AddMedicineActivity.EXTRA_MEDICINE_ID, dose.medicine.id)
                startActivity(detailIntent)
            }

            container.addView(card)
        }
    }
}