package com.example.medtime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat

// Shows what blocks the alarm, with a Fix button for each problem
class PhoneCheckActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_check)

        findViewById<AppCompatButton>(R.id.btnCopy).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("MedTime report", PhoneCheck.report(this)))
            Toast.makeText(
                this,
                "Report copied. Paste it in the chat.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Each time you come back from a settings page, the list is checked again
    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val container = findViewById<LinearLayout>(R.id.llChecks)
        container.removeAllViews()

        for (item in PhoneCheck.items(this)) {
            val row = layoutInflater.inflate(R.layout.item_check, container, false)

            val tvStatus = row.findViewById<TextView>(R.id.tvCheckStatus)
            val tvTitle = row.findViewById<TextView>(R.id.tvCheckTitle)
            val tvDetail = row.findViewById<TextView>(R.id.tvCheckDetail)
            val tvFix = row.findViewById<TextView>(R.id.tvCheckFix)

            tvTitle.text = item.title
            tvDetail.text = item.detail

            if (item.ok) {
                tvStatus.text = "OK"
                tvStatus.setBackgroundResource(R.drawable.bg_badge)
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.check_green))
            } else {
                tvStatus.text = if (item.canFix) "FIX" else "CHECK"
                tvStatus.setBackgroundResource(R.drawable.bg_badge_gray)
                tvStatus.setTextColor(Color.parseColor("#DC2626"))
            }

            // The Fix button: only for problems that can be fixed on the phone
            if (item.canFix && !item.ok) {
                tvFix.visibility = TextView.VISIBLE
                tvFix.setOnClickListener { fix(item.key) }
            } else {
                tvFix.visibility = TextView.GONE
            }

            container.addView(row)
        }
    }

    private fun fix(key: String) {
        try {
            when (key) {
                PhoneCheck.KEY_NOTIFICATIONS -> openNotificationSettings()
                PhoneCheck.KEY_BANNER -> openChannelSettings()
                PhoneCheck.KEY_OVERLAY -> AlarmOverlay.openSettings(this)
                PhoneCheck.KEY_FULLSCREEN -> openFullScreenSettings()
                PhoneCheck.KEY_BATTERY -> requestBatteryPermission()
                PhoneCheck.KEY_VOLUME -> startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open the settings.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        } else {
            openAppInfo()
        }
    }

    // The page of the "Dose alarm" channel (importance, pop-up, sound)
    private fun openChannelSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.ALARM_CHANNEL_ID)
            startActivity(intent)
        } else {
            openAppInfo()
        }
    }

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.fromParts("package", packageName, null)
                )
            )
        } else {
            openAppInfo()
        }
    }

    // The popup "Allow MedTime to always run in the background?"
    private fun requestBatteryPermission() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun openAppInfo() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }
}