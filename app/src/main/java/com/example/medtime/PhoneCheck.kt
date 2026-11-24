package com.example.medtime

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Tests every phone setting that can stop the alarm from popping up
object PhoneCheck {

    const val KEY_NOTIFICATIONS = "notifications"
    const val KEY_BANNER = "banner"
    const val KEY_OVERLAY = "overlay"
    const val KEY_FULLSCREEN = "fullscreen"
    const val KEY_BATTERY = "battery"
    const val KEY_VOLUME = "volume"
    const val KEY_ALARMS = "alarms"
    const val KEY_TIPS = "tips"

    data class Item(
        val key: String,
        val title: String,
        val ok: Boolean,
        val detail: String,
        val canFix: Boolean   // true = there is a Fix button
    )

    private fun importanceName(importance: Int): String {
        return when (importance) {
            NotificationManager.IMPORTANCE_NONE -> "OFF (blocked)"
            NotificationManager.IMPORTANCE_MIN -> "minimal"
            NotificationManager.IMPORTANCE_LOW -> "low (silent, no pop-up)"
            NotificationManager.IMPORTANCE_DEFAULT -> "default (no pop-up)"
            NotificationManager.IMPORTANCE_HIGH -> "high (pop-up)"
            else -> "unknown ($importance)"
        }
    }

    fun items(context: Context): List<Item> {
        NotificationHelper.createChannel(context)

        val list = mutableListOf<Item>()

        // 1. Notifications allowed?
        val notificationsOk = NotificationHelper.canNotify(context)
        list.add(
            Item(
                KEY_NOTIFICATIONS, "Notifications", notificationsOk,
                if (notificationsOk) "Allowed." else "OFF. MedTime cannot show the reminder.",
                true
            )
        )

        // 2. Pop-up banner allowed? (the importance of the alarm channel)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = manager.getNotificationChannel(NotificationHelper.ALARM_CHANNEL_ID)
            val importance = channel?.importance ?: NotificationManager.IMPORTANCE_NONE
            val bannerOk = importance >= NotificationManager.IMPORTANCE_HIGH
            list.add(
                Item(
                    KEY_BANNER, "Pop-up banner", bannerOk,
                    if (bannerOk) {
                        "ON (high importance)."
                    } else {
                        "Your phone set the banner to ${importanceName(importance)}. " +
                                "Open it and turn ON \"Pop-up\", \"Floating notifications\" or \"Banners\" " +
                                "for \"Dose alarm\", and set the importance to Urgent / High."
                    },
                    true
                )
            )
        }

        // 3. Display over other apps
        val overlayOk = AlarmOverlay.canShow(context)
        list.add(
            Item(
                KEY_OVERLAY, "Display over other apps", overlayOk,
                if (overlayOk) {
                    "ON. The alarm card can appear on top of any app."
                } else {
                    "OFF. Without it, the alarm cannot pop up on top of other apps."
                },
                true
            )
        )

        // 4. Full-screen alarm on the lock screen (Android 14 and newer)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val fullOk = manager.canUseFullScreenIntent()
            list.add(
                Item(
                    KEY_FULLSCREEN, "Full-screen alarm on the lock screen", fullOk,
                    if (fullOk) "ON." else "OFF. The alarm cannot open over the lock screen.",
                    true
                )
            )
        } else {
            list.add(
                Item(
                    KEY_FULLSCREEN, "Full-screen alarm on the lock screen", true,
                    "Not needed on your Android version.",
                    false
                )
            )
        }

        // 5. Battery
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOk = power.isIgnoringBatteryOptimizations(context.packageName)
        list.add(
            Item(
                KEY_BATTERY, "Battery: run without limits", batteryOk,
                if (batteryOk) {
                    "Allowed."
                } else {
                    "Your phone may stop MedTime in the background and delete its alarms. " +
                            "Tap Fix and choose Allow."
                },
                true
            )
        )

        // 6. Alarm volume
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val volume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        list.add(
            Item(
                KEY_VOLUME, "Alarm volume", volume > 0,
                if (volume > 0) {
                    "$volume of $maxVolume."
                } else {
                    "0. The alarm will be silent. Turn up the Alarm volume."
                },
                true
            )
        )

        // 7. Is the next alarm really registered with the phone?
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = alarmManager.nextAlarmClock
        val format = SimpleDateFormat("EEE MMM d, h:mm a", Locale.US)
        list.add(
            Item(
                KEY_ALARMS, "Next alarm registered with the phone", next != null,
                if (next == null) {
                    "None. If you saved a medicine, the phone deleted the alarm (some brands do " +
                            "this when the app is closed). Open MedTime once to set it again, and " +
                            "follow the tips below."
                } else {
                    format.format(Date(next.triggerTime)) +
                            " (set by ${next.showIntent?.creatorPackage ?: "an app"})"
                },
                false
            )
        )

        // 8. Tips for the brand of this phone
        list.add(
            Item(
                KEY_TIPS,
                "Tips for ${Build.MANUFACTURER} ${Build.MODEL}",
                true,
                brandTips(),
                false
            )
        )

        return list
    }

    // True when every item with a Fix button is OK
    fun allGood(context: Context): Boolean {
        return items(context).none { it.canFix && !it.ok }
    }

    // Tips for the brand of the phone
    fun brandTips(): String {
        val brand = Build.MANUFACTURER.lowercase(Locale.US)
        return when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ->
                "Xiaomi / Redmi / Poco: open Settings > Apps > Manage apps > MedTime. Turn ON " +
                        "Autostart. In Other permissions, turn ON \"Display pop-up windows while " +
                        "running in the background\" and \"Show on lock screen\". In Battery saver, " +
                        "choose No restrictions. Then open Recent apps, long-press MedTime and tap the lock icon."

            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") ->
                "Oppo / Realme / OnePlus: open Settings > Apps > MedTime. Turn ON Auto launch " +
                        "(or Allow auto-start), and in Battery choose Allow background activity. " +
                        "Then open Recent apps, and lock MedTime (pull down on the card, or tap the lock icon)."

            brand.contains("vivo") || brand.contains("iqoo") ->
                "Vivo / iQOO: open Settings > Apps > MedTime. Turn ON Autostart, and in Battery " +
                        "choose High background power consumption (or Allow). Then lock MedTime in Recent apps."

            brand.contains("samsung") ->
                "Samsung: open Settings > Battery > Background usage limits. Remove MedTime from " +
                        "Sleeping apps and Deep sleeping apps, and add it to Never sleeping apps. " +
                        "In the app's Battery page, choose Unrestricted."

            brand.contains("infinix") || brand.contains("tecno") || brand.contains("itel") ->
                "Infinix / Tecno / itel: open the Phone Master (or Settings > Apps > MedTime). " +
                        "Turn ON Auto-start, and in Battery choose No restrictions. Then lock MedTime in Recent apps."

            brand.contains("huawei") || brand.contains("honor") ->
                "Huawei / Honor: open Settings > Apps > App launch > MedTime. Choose Manage " +
                        "manually, and turn ON Auto-launch, Secondary launch and Run in background."

            else ->
                "Open Settings > Apps > MedTime. Allow notifications, set Battery to " +
                        "Unrestricted, and allow Display over other apps."
        }
    }

    // A text with all the results and the alarm log (to send in the chat)
    fun report(context: Context): String {
        val builder = StringBuilder()
        builder.append("MedTime phone check\n")
        builder.append(
            "Phone: ${Build.MANUFACTURER} ${Build.MODEL}, " +
                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n"
        )

        for (item in items(context)) {
            if (item.key == KEY_TIPS) continue
            builder.append("[${if (item.ok) "OK" else "FIX"}] ${item.title}: ${item.detail}\n")
        }

        builder.append("\nAlarm log (newest first):\n")
        builder.append(AlarmLog.recent(context, 25))
        return builder.toString()
    }
}