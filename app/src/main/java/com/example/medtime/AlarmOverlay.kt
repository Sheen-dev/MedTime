package com.example.medtime

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

// The pop-up card that appears ON TOP of every app, like an alarm clock.
// It looks like a normal Android notification and uses the colors of the phone.
// It needs the permission "Display over other apps".
object AlarmOverlay {

    private const val TAG = "MedTime"

    private var cardView: View? = null

    // Did the user allow "Display over other apps"?
    fun canShow(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    // Opens the phone page where the user turns the permission ON
    fun openSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open the settings.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    // Shows the card. Returns true when it is on the screen.
    fun show(
        context: Context,
        timeText: String,
        title: String,
        details: String,
        buttonText: String,
        onButton: () -> Unit,
        onCardTap: () -> Unit,
        onSilence: () -> Unit
    ): Boolean {
        hide(context)
        if (!canShow(context)) return false

        return try {
            val themed = ContextThemeWrapper(context, R.style.Theme_MedTime)
            val view = LayoutInflater.from(themed).inflate(R.layout.overlay_alarm, null)

            // The colors follow the theme of the PHONE (light / dark / Material You),
            // so the card looks like the notifications of the other apps
            val colors = SystemColors.of(context)

            val card = view.findViewById<View>(R.id.ovCard)
            val background = ContextCompat.getDrawable(context, R.drawable.bg_overlay_system)!!.mutate()
            (background as GradientDrawable).setColor(colors.background)
            card.background = background

            val tvTime = view.findViewById<TextView>(R.id.ovTime)
            val tvName = view.findViewById<TextView>(R.id.ovName)
            val tvDetails = view.findViewById<TextView>(R.id.ovDetails)
            val tvSilence = view.findViewById<TextView>(R.id.ovSilence)
            val tvTake = view.findViewById<TextView>(R.id.ovTake)
            val ivIcon = view.findViewById<ImageView>(R.id.ovIcon)

            tvTime.text = "MedTime  ·  $timeText"
            tvName.text = title
            tvDetails.text = details
            tvTake.text = buttonText

            tvTime.setTextColor(colors.secondary)
            tvName.setTextColor(colors.primary)
            tvDetails.setTextColor(colors.secondary)
            tvSilence.setTextColor(colors.accent)
            tvTake.setTextColor(colors.accent)
            ivIcon.setColorFilter(colors.accent)

            tvTake.setOnClickListener { onButton() }
            tvSilence.setOnClickListener { onSilence() }
            card.setOnClickListener { onCardTap() }

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP
            params.y = statusBarHeight(context)

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.addView(view, params)
            cardView = view
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not show the pop-up card", e)
            AlarmLog.add(context, "Could not show the pop-up card: ${e.message}")
            false
        }
    }

    // Removes the card from the screen
    fun hide(context: Context) {
        val view = cardView ?: return
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "Could not remove the pop-up card", e)
        }
        cardView = null
    }
}