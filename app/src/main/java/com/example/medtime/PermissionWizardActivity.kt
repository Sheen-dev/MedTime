package com.example.medtime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat

// 3 setup pages, one for each permission. You can only go to the next page
// after the permission of the current page is allowed.
class PermissionWizardActivity : AppCompatActivity() {

    companion object {
        private const val KEY_STEP = "step"
    }

    private var step = 0

    private lateinit var tvStep: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvBody: TextView
    private lateinit var tvTip: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnAllow: AppCompatButton
    private lateinit var btnNext: AppCompatButton
    private lateinit var tvBack: TextView
    private lateinit var bars: List<View>

    // The popup "Allow MedTime to send you notifications?" (Android 13 and newer)
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                // Denied: the phone may not ask again, so open the notification settings page
                Toast.makeText(
                    this,
                    "Turn ON \"Allow notifications\" for MedTime.",
                    Toast.LENGTH_LONG
                ).show()
                openNotificationSettings()
            }
            render()
        }

    private val titles = arrayOf(
        "Allow notifications",
        "Allow pop-ups",
        "Unrestricted battery"
    )

    private val bodies = arrayOf(
        "MedTime needs notifications to tell you when it is time to take your medicine. " +
                "Tap the button below, then tap Allow.",
        "MedTime shows your reminder on top of any app, like an alarm clock. Tap the button " +
                "below, find MedTime in the list, and turn ON \"Allow display over other apps\". " +
                "Then press Back.",
        "Some phones stop apps in the background and delete their alarms. Tap the button " +
                "below and choose Allow, so your reminder rings exactly on time."
    )

    private val allowButtons = arrayOf(
        "Allow notifications",
        "Open pop-up settings",
        "Allow unrestricted battery"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Everything is allowed already: go straight to Home
        if (PermissionSteps.allGranted(this)) {
            goHome()
            return
        }

        setContentView(R.layout.activity_permission_wizard)

        tvStep = findViewById(R.id.tvStep)
        tvTitle = findViewById(R.id.tvWizardTitle)
        tvBody = findViewById(R.id.tvWizardBody)
        tvTip = findViewById(R.id.tvWizardTip)
        tvStatus = findViewById(R.id.tvWizardStatus)
        btnAllow = findViewById(R.id.btnAllow)
        btnNext = findViewById(R.id.btnNext)
        tvBack = findViewById(R.id.tvBack)
        bars = listOf(
            findViewById(R.id.bar0),
            findViewById(R.id.bar1),
            findViewById(R.id.bar2)
        )

        // Start at the first page that is not allowed yet
        step = savedInstanceState?.getInt(KEY_STEP)
            ?: PermissionSteps.firstMissing(this).coerceAtMost(PermissionSteps.STEP_COUNT - 1)

        btnAllow.setOnClickListener { openCurrentPermission() }

        btnNext.setOnClickListener {
            if (!PermissionSteps.isAllowed(this, step)) {
                Toast.makeText(this, "Please allow this first.", Toast.LENGTH_SHORT).show()
            } else if (step < PermissionSteps.STEP_COUNT - 1) {
                step++
                render()
            } else {
                goHome()
            }
        }

        tvBack.setOnClickListener {
            if (step > 0) {
                step--
                render()
            }
        }

        // The Back button of the phone goes to the page before
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (step > 0) {
                    step--
                    render()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, step)
    }

    // Each time you come back from a settings page, the page is checked again
    override fun onResume() {
        super.onResume()
        if (::tvStep.isInitialized) render()
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    // Draws the current page
    private fun render() {
        val allowed = PermissionSteps.isAllowed(this, step)
        val last = step == PermissionSteps.STEP_COUNT - 1

        tvStep.text = "Step ${step + 1} of ${PermissionSteps.STEP_COUNT}"
        tvTitle.text = titles[step]
        tvBody.text = bodies[step]

        // Page 3: tips for the brand of this phone
        if (last) {
            tvTip.text = "Tip for your ${Build.MANUFACTURER} phone:\n" + PhoneCheck.brandTips()
            tvTip.visibility = View.VISIBLE
        } else {
            tvTip.visibility = View.GONE
        }

        // The 3 bars: done = green, current = blue, next = gray
        bars.forEachIndexed { index, bar ->
            val color = when {
                index < step -> ContextCompat.getColor(this, R.color.check_green)
                index == step -> ContextCompat.getColor(this, R.color.medtime_blue)
                else -> ContextCompat.getColor(this, R.color.border_light)
            }
            bar.setBackgroundColor(color)
        }

        // The status
        if (allowed) {
            tvStatus.text = "Allowed"
            tvStatus.setBackgroundResource(R.drawable.bg_badge)
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.check_green))
        } else {
            tvStatus.text = "Not allowed yet"
            tvStatus.setBackgroundResource(R.drawable.bg_badge_gray)
            tvStatus.setTextColor(Color.parseColor("#DC2626"))
        }

        // The Allow button: blue until allowed, then gray
        if (allowed) {
            btnAllow.text = "Allowed"
            btnAllow.isEnabled = false
            btnAllow.setBackgroundResource(R.drawable.bg_button_taken)
            btnAllow.setTextColor(ContextCompat.getColor(this, R.color.check_green))
        } else {
            btnAllow.text = allowButtons[step]
            btnAllow.isEnabled = true
            btnAllow.setBackgroundResource(R.drawable.bg_button)
            btnAllow.setTextColor(ContextCompat.getColor(this, R.color.white))
        }

        // The Next button: gray and disabled until allowed
        btnNext.text = if (last) "Finish" else "Next"
        if (allowed) {
            btnNext.isEnabled = true
            btnNext.setBackgroundResource(R.drawable.bg_button)
            btnNext.setTextColor(ContextCompat.getColor(this, R.color.white))
        } else {
            btnNext.isEnabled = false
            btnNext.setBackgroundResource(R.drawable.bg_button_taken)
            btnNext.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
        }

        tvBack.visibility = if (step > 0) View.VISIBLE else View.INVISIBLE
    }

    // The Allow button of the current page
    private fun openCurrentPermission() {
        try {
            when (step) {
                0 -> askNotifications()
                1 -> AlarmOverlay.openSettings(this)
                2 -> requestBattery()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open the settings.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askNotifications() {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            // The permission is given, but notifications are turned off in the settings
            openNotificationSettings()
        } else {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openNotificationSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                startActivity(intent)
            } else {
                openAppInfo()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open the settings.", Toast.LENGTH_SHORT).show()
        }
    }

    // The popup "Allow MedTime to always run in the background?"
    private fun requestBattery() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                openAppInfo()
            }
        }
    }

    private fun openAppInfo() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open the settings.", Toast.LENGTH_SHORT).show()
        }
    }
}