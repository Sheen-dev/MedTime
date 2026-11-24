package com.example.medtime

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import java.util.Calendar

class HomeActivity : AppCompatActivity() {

    // Updates the greeting while Home is open (every 30 seconds)
    private val handler = Handler(Looper.getMainLooper())
    private val greetingTick = object : Runnable {
        override fun run() {
            updateGreeting()
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val ivSettings = findViewById<View>(R.id.ivSettings)
        val tvViewSchedule = findViewById<TextView>(R.id.tvViewSchedule)
        val btnAddMedicine = findViewById<AppCompatButton>(R.id.btnAddMedicine)

        // Open the Settings screen (the gear icon)
        ivSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Open the Medicine Schedule screen
        tvViewSchedule.setOnClickListener {
            startActivity(Intent(this, MedicineScheduleActivity::class.java))
        }

        // Open the Add Medicine screen
        btnAddMedicine.setOnClickListener {
            startActivity(Intent(this, AddMedicineActivity::class.java))
        }
    }

    // Runs each time Home appears on the screen
    override fun onResume() {
        super.onResume()

        // The 3 permissions are needed for the alarm. If one was turned off,
        // go back to the setup pages.
        if (!PermissionSteps.allGranted(this)) {
            startActivity(Intent(this, PermissionWizardActivity::class.java))
            finish()
            return
        }

        // You opened the app, so a ringing alarm stops its sound
        AlarmService.silence(this)

        showDoses()

        // Show the greeting now, and keep it fresh while Home is open
        handler.removeCallbacks(greetingTick)
        handler.post(greetingTick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(greetingTick)
    }

    // Good morning / Good afternoon / Good night, from the clock of the phone
    private fun updateGreeting() {
        findViewById<TextView>(R.id.tvGreeting).text = Greeting.forNow()
    }

    private fun showDoses() {
        val container = findViewById<LinearLayout>(R.id.llMedicines)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)

        container.removeAllViews()

        val today = Calendar.getInstance()
        val dateKey = DoseScheduler.dateKey(today)
        val doses = DoseScheduler.dosesFor(this, today)
        val now = DoseScheduler.minuteNow()

        if (doses.isEmpty()) {
            tvEmpty.text = if (MedicineStorage.getAll(this).isEmpty()) {
                "No medicines yet. Tap Add Medicine to start."
            } else {
                "No doses for today."
            }
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
        }

        // The next dose to take = the first dose that is not taken yet
        val next = doses.firstOrNull { !it.taken }

        for (dose in doses) {
            val card = layoutInflater.inflate(R.layout.item_medicine_home, container, false)

            DoseUi.bindTime(this, card.findViewById<TextView>(R.id.tvTime), dose)
            card.findViewById<TextView>(R.id.tvName).text = dose.medicine.name
            card.findViewById<TextView>(R.id.tvDetails).text = DoseUi.subtitle(dose)
            DoseUi.bindStatus(this, card.findViewById<TextView>(R.id.tvStatus), dose, now)

            // Only the next dose shows the "NEXT REMINDER" label
            card.findViewById<TextView>(R.id.tvNextLabel).visibility =
                if (dose === next) View.VISIBLE else View.GONE

            // The Take button: save the dose as taken, then draw the list again
            DoseUi.bindTakeButton(this, card.findViewById(R.id.btnTake), dose) {
                MedicineStorage.markDoseTaken(this, dose.medicine.id, dateKey, dose.index)
                Toast.makeText(
                    this,
                    "${dose.medicine.name} (${dose.label()}) marked as taken",
                    Toast.LENGTH_SHORT
                ).show()
                showDoses()
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