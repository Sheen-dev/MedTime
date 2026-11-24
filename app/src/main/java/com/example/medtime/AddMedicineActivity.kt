package com.example.medtime

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import java.util.Calendar
import java.util.Locale

class AddMedicineActivity : AppCompatActivity() {

    companion object {
        // The name of the "note" that carries the medicine id
        const val EXTRA_MEDICINE_ID = "medicine_id"
    }

    // The ONE dose time, as minutes of the day (10:00 AM = 600). -1 = not chosen yet.
    // (Need another time? Add the medicine again with the other time.)
    private var doseMinute = -1

    // The unit of the dosage: index in DosageUnits.units (0 = tablet)
    private var unitIndex = 0

    // The dates, saved as MM/dd/yyyy. An empty End Date means "no end date"
    private var startDate = ""
    private var endDate = ""

    // The chosen alarm sound: 0 = Chime, 1 = Bell, 2 = Beep, 3 = Melody, 4 = Gentle
    private var soundIndex = 0

    // -1 = we are adding a new medicine. Any other number = we are editing that medicine
    private var editingId = -1L

    private lateinit var tvDoseTime: TextView
    private lateinit var tvDosageUnit: TextView
    private lateinit var tvStartDate: TextView
    private lateinit var tvEndDate: TextView
    private lateinit var tvSoundName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_medicine)

        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val etMedicineName = findViewById<EditText>(R.id.etMedicineName)
        val etDosage = findViewById<EditText>(R.id.etDosage)
        tvDoseTime = findViewById(R.id.tvDoseTime)
        tvDosageUnit = findViewById(R.id.tvDosageUnit)
        tvStartDate = findViewById(R.id.tvStartDate)
        tvEndDate = findViewById(R.id.tvEndDate)
        tvSoundName = findViewById(R.id.tvSoundName)
        val switchNotifications = findViewById<SwitchCompat>(R.id.switchNotifications)
        val rowSound = findViewById<View>(R.id.rowSound)
        val btnSaveMedicine = findViewById<AppCompatButton>(R.id.btnSaveMedicine)

        // Edit mode: did the previous screen send a medicine id?
        editingId = intent.getLongExtra(EXTRA_MEDICINE_ID, -1L)
        if (editingId != -1L) {
            val existing = MedicineStorage.getById(this, editingId)
            if (existing == null) {
                finish() // the medicine does not exist anymore
                return
            }

            // Fill every box with the saved values
            tvTitle.text = "Edit Medicine"
            btnSaveMedicine.text = "Save Changes"
            etMedicineName.setText(existing.name)

            // Dosage: split "2 tablets" into the amount (2) and the unit (tablet)
            val parsed = DosageUnits.parse(existing.dosage)
            if (parsed != null) {
                etDosage.setText(parsed.first)
                unitIndex = parsed.second
            } else {
                val number = Regex("[0-9]+(\\.[0-9]+)?").find(existing.dosage)?.value ?: ""
                etDosage.setText(number)
            }

            // One dose time only
            val savedTimes = existing.doseTimes.sorted()
            if (savedTimes.isNotEmpty()) doseMinute = savedTimes.first()
            if (savedTimes.size > 1) {
                Toast.makeText(
                    this,
                    "This medicine had several times. Only the first one is kept.",
                    Toast.LENGTH_LONG
                ).show()
            }

            startDate = existing.startDate
            tvStartDate.text = DoseScheduler.prettyDate(startDate)

            endDate = existing.endDate
            tvEndDate.text = if (endDate.isEmpty()) "" else DoseScheduler.prettyDate(endDate)

            switchNotifications.isChecked = existing.notificationsOn
            soundIndex = existing.soundIndex
        }

        // Show the chosen sound
        tvSoundName.text = AlarmSounds.nameOf(soundIndex)

        // Show the chosen unit and time
        tvDosageUnit.text = DosageUnits.units[unitIndex]
        showDoseTime()

        // Unit: scrolling list (tablet, capsule, ml, ...)
        tvDosageUnit.setOnClickListener {
            WheelPickers.pickFromList(this, "Choose unit", DosageUnits.units, unitIndex) { index ->
                unitIndex = index
                tvDosageUnit.text = DosageUnits.units[unitIndex]
            }
        }

        // Dose time: scrolling clock
        tvDoseTime.setOnClickListener { pickDoseTime() }

        // Start Date and End Date: scrolling dates
        tvStartDate.setOnClickListener { pickStartDate() }
        tvEndDate.setOnClickListener { pickEndDate() }

        // Sound: popup list with a preview
        rowSound.setOnClickListener { pickSound() }

        // Save button: check the boxes, then SAVE (add or update)
        btnSaveMedicine.setOnClickListener {
            val name = etMedicineName.text.toString().trim()
            val amountText = etDosage.text.toString().trim().trimEnd('.')
            val amount = amountText.toDoubleOrNull()

            val start = DoseScheduler.parseDate(startDate)
            val end = DoseScheduler.parseDate(endDate)

            if (name.isEmpty() || amount == null || amount <= 0.0 ||
                doseMinute < 0 || startDate.isEmpty()
            ) {
                Toast.makeText(
                    this,
                    "Please fill in the name, the dosage amount, the time and the start date",
                    Toast.LENGTH_SHORT
                ).show()
            } else if (start != null && end != null && end.timeInMillis < start.timeInMillis) {
                Toast.makeText(
                    this,
                    "The End Date cannot be before the Start Date",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val isEditing = editingId != -1L
                val dosage = DosageUnits.format(amountText, DosageUnits.units[unitIndex])

                val medicine = Medicine(
                    id = if (isEditing) editingId else System.currentTimeMillis(),
                    name = name,
                    dosage = dosage,
                    doseTimes = listOf(doseMinute),
                    startDate = startDate,
                    endDate = endDate,
                    notificationsOn = switchNotifications.isChecked,
                    soundIndex = soundIndex
                )

                if (isEditing) {
                    MedicineStorage.update(this, medicine)
                    Toast.makeText(this, "$name updated!", Toast.LENGTH_SHORT).show()
                } else {
                    MedicineStorage.add(this, medicine)
                    Toast.makeText(this, "$name saved!", Toast.LENGTH_SHORT).show()
                }
                finish() // go back
            }
        }
    }

    // Stop any sound preview when the screen closes
    override fun onDestroy() {
        super.onDestroy()
        SoundPlayer.stop()
    }

    // Popup list of the 5 sounds. Tap a name to hear it, then tap OK.
    private fun pickSound() {
        var choice = soundIndex

        AlertDialog.Builder(this)
            .setTitle("Choose alarm sound")
            .setSingleChoiceItems(AlarmSounds.names, choice) { _, which ->
                choice = which
                SoundPlayer.play(this, which)
            }
            .setPositiveButton("OK") { _, _ ->
                soundIndex = choice
                tvSoundName.text = AlarmSounds.nameOf(soundIndex)
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { SoundPlayer.stop() }
            .show()
    }

    // Writes the chosen time in the box: "10:00 AM"
    private fun showDoseTime() {
        tvDoseTime.text = if (doseMinute < 0) "" else DoseScheduler.formatMinutes(doseMinute)
    }

    // Scrolling clock: hour, minute, AM/PM
    private fun pickDoseTime() {
        WheelPickers.pickTime(
            this,
            "Dose time",
            if (doseMinute < 0) null else doseMinute
        ) { minute ->
            doseMinute = minute
            showDoseTime()
        }
    }

    // Scrolling date for the Start Date
    private fun pickStartDate() {
        val initial = DoseScheduler.parseDate(startDate) ?: Calendar.getInstance()

        WheelPickers.pickDate(this, "Start date", initial, { year, month, day ->
            // month starts at 0 in Android, so we add 1
            startDate = String.format(Locale.US, "%02d/%02d/%04d", month + 1, day, year)
            tvStartDate.text = DoseScheduler.prettyDate(startDate)

            // If the End Date is now before the Start Date, clear it
            val start = DoseScheduler.parseDate(startDate)
            val end = DoseScheduler.parseDate(endDate)
            if (start != null && end != null && end.timeInMillis < start.timeInMillis) {
                endDate = ""
                tvEndDate.text = ""
                Toast.makeText(this, "End Date cleared. Please choose it again.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Scrolling date for the End Date (it has a "No end date" button)
    private fun pickEndDate() {
        val start = DoseScheduler.parseDate(startDate)
        val initial = DoseScheduler.parseDate(endDate) ?: start ?: Calendar.getInstance()

        WheelPickers.pickDate(
            this, "End date", initial,
            { year, month, day ->
                val picked = String.format(Locale.US, "%02d/%02d/%04d", month + 1, day, year)
                val pickedDate = DoseScheduler.parseDate(picked)

                // The End Date cannot be before the Start Date
                if (start != null && pickedDate != null && pickedDate.timeInMillis < start.timeInMillis) {
                    Toast.makeText(
                        this, "The End Date cannot be before the Start Date", Toast.LENGTH_SHORT
                    ).show()
                } else {
                    endDate = picked
                    tvEndDate.text = DoseScheduler.prettyDate(endDate)
                }
            },
            "No end date",
            {
                endDate = ""
                tvEndDate.text = ""
            }
        )
    }
}