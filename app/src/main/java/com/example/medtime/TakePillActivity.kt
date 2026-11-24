package com.example.medtime

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import java.util.Calendar

// The "Take your pill" screen. When the alarm rings, it opens over the lock screen.
class TakePillActivity : AppCompatActivity() {

    private var medicineId = -1L
    private var doseIndex = -1
    private var dateKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContentView(R.layout.activity_take_pill)

        medicineId = intent.getLongExtra(ReminderIds.EXTRA_MEDICINE_ID, -1L)
        doseIndex = intent.getIntExtra(ReminderIds.EXTRA_DOSE_INDEX, -1)
        dateKey = intent.getStringExtra(ReminderIds.EXTRA_DATE_KEY)
            ?: DoseScheduler.dateKey(Calendar.getInstance())

        val btnTake = findViewById<AppCompatButton>(R.id.btnTake)
        val tvNotNow = findViewById<TextView>(R.id.tvNotNow)

        // Take: save that this dose is taken (this also stops the alarm)
        btnTake.setOnClickListener {
            MedicineStorage.markDoseTaken(this, medicineId, dateKey, doseIndex)
            Toast.makeText(this, "Marked as taken. Well done!", Toast.LENGTH_SHORT).show()
            closeScreen()
        }

        // Not now: stop the sound, the dose stays pending
        tvNotNow.setOnClickListener {
            AlarmService.silence(this)
            closeScreen()
        }

        // The Back button also stops the sound
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlarmService.silence(this@TakePillActivity)
                closeScreen()
            }
        })
    }

    // Runs each time this screen appears
    override fun onResume() {
        super.onResume()
        showDose()
    }

    // Show this screen above the lock screen and turn the screen on
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun showDose() {
        val medicine = MedicineStorage.getById(this, medicineId)
        val times = medicine?.doseTimes?.sorted() ?: emptyList()

        // The medicine or the dose does not exist anymore
        if (medicine == null || doseIndex !in times.indices) {
            closeScreen()
            return
        }

        findViewById<TextView>(R.id.tvPillName).text = medicine.name
        findViewById<TextView>(R.id.tvPillDosage).text = medicine.dosageText()
        findViewById<TextView>(R.id.tvPillTime).text = DoseScheduler.formatMinutes(times[doseIndex])
        findViewById<TextView>(R.id.tvPillDose).text = "Dose ${doseIndex + 1} of ${times.size}"

        // Already taken: hide the Take button
        val taken = MedicineStorage.getTakenDoses(this, dateKey).contains("$medicineId:$doseIndex")
        findViewById<TextView>(R.id.tvPillStatus).visibility = if (taken) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnTake).visibility = if (taken) View.GONE else View.VISIBLE
    }

    // If the app was opened from the notification, go to Home when we close
    // (when the phone is locked, we only close this screen)
    private fun closeScreen() {
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        if (isTaskRoot && !locked) {
            startActivity(Intent(this, HomeActivity::class.java))
        }
        finish()
    }
}