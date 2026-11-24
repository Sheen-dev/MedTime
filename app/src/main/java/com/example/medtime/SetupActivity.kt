package com.example.medtime

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SetupActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout

    private val prefs by lazy {
        getSharedPreferences("medtime_setup", MODE_PRIVATE)
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "Please enable notifications so medicine reminders can appear.",
                Toast.LENGTH_LONG
            ).show()
        }
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (prefs.getBoolean("complete", false)) {
            openLogin()
            return
        }

        NotificationHelper.createChannel(this)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()

        if (::content.isInitialized) {
            render()
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(246, 248, 252))
        }

        val scroll = ScrollView(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(root)

        render()
    }

    private fun render() {
        content.removeAllViews()

        val brand = text(
            "MEDTIME",
            13,
            Color.rgb(37, 99, 235),
            true
        )

        brand.letterSpacing = 0.14f
        content.addView(brand)

        content.addView(
            text(
                "Let's get your reminders ready",
                29,
                Color.rgb(15, 23, 42),
                true
            ).apply {
                setPadding(0, dp(12), 0, dp(8))
            }
        )

        content.addView(
            text(
                "A few quick settings help MedTime remind you on time, even when your phone is locked or you're using another app.",
                15,
                Color.rgb(100, 116, 139),
                false
            )
        )

        val steps = listOf(
            Step(
                "01",
                "Allow notifications",
                "Let MedTime show medicine reminders in your notification shade.",
                notificationOk(),
                "Allow notifications",
                ::openNotifications
            ),

            Step(
                "02",
                "Enable pop-up banners",
                "Allow the Dose alarm notification channel to use High / Urgent importance and show banners.",
                bannerOk(),
                "Set up banners",
                ::openBannerSettings
            ),

            Step(
                "03",
                "Set battery to Unrestricted",
                "Prevent Android from putting MedTime to sleep and delaying scheduled reminders.",
                batteryOk(),
                "Set battery access",
                ::openBatterySettings
            ),

            Step(
                "04",
                "Allow display over other apps",
                "This lets an active reminder card appear above another app when your phone is unlocked.",
                AlarmOverlay.canShow(this),
                "Allow pop-up display",
                { AlarmOverlay.openSettings(this) }
            ),

            Step(
                "05",
                "Allow full-screen alarms",
                "On supported Android versions, let urgent reminders open the take-medicine screen over the lock screen.",
                fullScreenOk(),
                if (Build.VERSION.SDK_INT >= 34)
                    "Allow full-screen alarms"
                else
                    "Not required",
                ::openFullScreenSettings
            )
        )

        steps.forEach { step ->
            addStep(step)
        }

        val ready =
            notificationOk() &&
                    bannerOk() &&
                    batteryOk() &&
                    AlarmOverlay.canShow(this) &&
                    fullScreenOk()

        val button = TextView(this).apply {
            text = if (ready) {
                "Continue to MedTime  →"
            } else {
                "Complete the steps above to continue"
            }

            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)

            setPadding(
                dp(18),
                dp(17),
                dp(18),
                dp(17)
            )

            background = rounded(
                if (ready) Color.rgb(37, 99, 235)
                else Color.rgb(148, 163, 184),
                16
            )

            isEnabled = ready

            setOnClickListener {
                prefs.edit()
                    .putBoolean("complete", true)
                    .apply()

                openLogin()
            }
        }

        val params = LinearLayout.LayoutParams(-1, -2)
        params.topMargin = dp(20)

        content.addView(button, params)

        content.addView(
            text(
                "You can review these permissions later in Settings → Phone check.",
                12,
                Color.rgb(100, 116, 139),
                false
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(14), 0, dp(4))
            }
        )
    }

    private data class Step(
        val number: String,
        val title: String,
        val description: String,
        val done: Boolean,
        val action: String,
        val onAction: () -> Unit
    )

    private fun addStep(step: Step) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(16),
                dp(16),
                dp(16),
                dp(16)
            )

            background = rounded(Color.WHITE, 18)
            elevation = dp(2).toFloat()
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val num = TextView(this).apply {
            text = step.number
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER

            setTextColor(Color.rgb(37, 99, 235))
            background = rounded(
                Color.rgb(239, 246, 255),
                12
            )

            setPadding(
                dp(11),
                dp(8),
                dp(11),
                dp(8)
            )
        }

        top.addView(num)

        val title = text(
            step.title,
            16,
            Color.rgb(15, 23, 42),
            true
        )

        val titleParams = LinearLayout.LayoutParams(
            0,
            -2,
            1f
        ).apply {
            leftMargin = dp(12)
        }

        top.addView(title, titleParams)

        val status = text(
            if (step.done) "DONE" else "NEEDS SETUP",
            10,
            if (step.done)
                Color.rgb(22, 163, 74)
            else
                Color.rgb(217, 119, 6),
            true
        )

        top.addView(status)
        card.addView(top)

        card.addView(
            text(
                step.description,
                13,
                Color.rgb(100, 116, 139),
                false
            ).apply {
                setPadding(
                    0,
                    dp(12),
                    0,
                    dp(12)
                )
            }
        )

        val action = TextView(this).apply {
            text = if (step.done) {
                "Settings enabled ✓"
            } else {
                step.action
            }

            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD

            setTextColor(
                if (step.done)
                    Color.rgb(22, 163, 74)
                else
                    Color.rgb(37, 99, 235)
            )

            setPadding(0, dp(4), 0, dp(2))

            if (!step.done) {
                setOnClickListener {
                    step.onAction()
                }
            }
        }

        card.addView(action)

        val params = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(16)
        }

        content.addView(card, params)
    }

    private fun notificationOk(): Boolean {
        val runtime =
            Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

        return runtime && NotificationHelper.canNotify(this)
    }

    private fun bannerOk(): Boolean {
        if (Build.VERSION.SDK_INT < 26) return true

        val channel = getSystemService(
            NotificationManager::class.java
        ).getNotificationChannel(
            NotificationHelper.ALARM_CHANNEL_ID
        )

        return channel != null &&
                channel.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    private fun batteryOk(): Boolean {
        val power = getSystemService(POWER_SERVICE) as PowerManager

        return power.isIgnoringBatteryOptimizations(packageName)
    }

    private fun fullScreenOk(): Boolean {
        return Build.VERSION.SDK_INT < 34 ||
                getSystemService(NotificationManager::class.java)
                    .canUseFullScreenIntent()
    }

    private fun openNotifications() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            openAppNotificationSettings()
        }
    }

    private fun openAppNotificationSettings() {
        val intent = Intent(
            Settings.ACTION_APP_NOTIFICATION_SETTINGS
        ).putExtra(
            Settings.EXTRA_APP_PACKAGE,
            packageName
        )

        startActivity(intent)
    }

    private fun openBannerSettings() {
        if (Build.VERSION.SDK_INT >= 26) {
            val intent = Intent(
                Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
            )
                .putExtra(
                    Settings.EXTRA_APP_PACKAGE,
                    packageName
                )
                .putExtra(
                    Settings.EXTRA_CHANNEL_ID,
                    NotificationHelper.ALARM_CHANNEL_ID
                )

            startActivity(intent)
        } else {
            openAppNotificationSettings()
        }
    }

    private fun openBatterySettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        }
    }

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.fromParts("package", packageName, null)
                )
            )
        } else {
            Toast.makeText(
                this,
                "This permission is not needed on your Android version.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun text(
        value: String,
        size: Int,
        color: Int,
        bold: Boolean
    ) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)

        if (bold) {
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun rounded(
        color: Int,
        radius: Int
    ) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun openLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        )

        finish()
    }
}