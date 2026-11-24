package com.example.medtime

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import java.util.Calendar

class MedicineDetailActivity : AppCompatActivity() {

    private var medicineId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medicine_detail)

        // The "note" sent by the card: which medicine was tapped
        medicineId = intent.getLongExtra(AddMedicineActivity.EXTRA_MEDICINE_ID, -1L)

        val btnEdit = findViewById<AppCompatButton>(R.id.btnEdit)
        val btnDelete = findViewById<AppCompatButton>(R.id.btnDelete)

        // Edit: open the Add Medicine screen in "edit mode"
        btnEdit.setOnClickListener {
            val editIntent = Intent(this, AddMedicineActivity::class.java)
            editIntent.putExtra(AddMedicineActivity.EXTRA_MEDICINE_ID, medicineId)
            startActivity(editIntent)
        }

        // Delete: ask first
        btnDelete.setOnClickListener {
            confirmDelete()
        }
    }

    // Runs each time this screen appears (also after coming back from Edit)
    override fun onResume() {
        super.onResume()
        showDetails()
    }

    private fun showDetails() {
        val medicine = MedicineStorage.getById(this, medicineId)
        if (medicine == null) {
            finish() // the medicine does not exist anymore
            return
        }

        // Which doses were already taken today?
        val takenToday = MedicineStorage.getTakenDoses(
            this, DoseScheduler.dateKey(Calendar.getInstance())
        )

        val doseLines = medicine.doseTimes.sorted()
            .mapIndexed { index, minute ->
                val status = if (takenToday.contains("${medicine.id}:$index")) " (taken today)" else ""
                "Dose ${index + 1} at ${DoseScheduler.formatMinutes(minute)}$status"
            }
            .joinToString("\n")

        findViewById<TextView>(R.id.tvDetailName).text = medicine.name
        findViewById<TextView>(R.id.tvDetailDosage).text = medicine.dosageText()
        findViewById<TextView>(R.id.tvDetailDoses).text = doseLines
        findViewById<TextView>(R.id.tvDetailStartDate).text =
            DoseScheduler.prettyDate(medicine.startDate)
        findViewById<TextView>(R.id.tvDetailEndDate).text =
            if (medicine.endDate.isEmpty()) "No end date" else DoseScheduler.prettyDate(medicine.endDate)
        findViewById<TextView>(R.id.tvDetailNotifications).text =
            if (medicine.notificationsOn) "On" else "Off"
    }

    private fun confirmDelete() {
        val medicine = MedicineStorage.getById(this, medicineId) ?: return

        AlertDialog.Builder(this)
            .setTitle("Delete medicine?")
            .setMessage("${medicine.name} and its history will be deleted. This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                MedicineStorage.delete(this, medicineId)
                Toast.makeText(this, "${medicine.name} deleted", Toast.LENGTH_SHORT).show()
                finish() // go back to Home
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}