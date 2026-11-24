package com.example.medtime

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

// Rings like an alarm clock:
//   1. rings for 5 minutes (the sound repeats, the phone vibrates)
//   2. quiet for 2 minutes
//   3. rings again for 1 minute
//   4. stops (the notification stays until you take the dose)
// It also stops when you take the dose, silence it, or open the app.
//
// How the alarm is shown:
//   SCREEN: MedTime is on the screen -> the full "Take your pill" screen opens on top
//   CARD:   another app, phone unlocked, "Display over other apps" ON -> a pop-up card
//   BANNER: everything else (phone locked, no permission) -> the notification banner,
//           which opens the Take screen over the lock screen
class AlarmService : Service() {

    companion object {
        private const val TAG = "MedTime"

        const val ACTION_START = "com.example.medtime.ALARM_START"
        const val ACTION_SILENCE = "com.example.medtime.ALARM_SILENCE" // stop the sound only
        const val ACTION_DISMISS = "com.example.medtime.ALARM_DISMISS" // stop the sound and remove the notification

        // Change these numbers if you want other times
        private const val FIRST_RING_SECONDS = 300L  // 5 minutes
        private const val PAUSE_SECONDS = 120L       // 2 minutes of silence
        private const val SECOND_RING_SECONDS = 60L  // 1 minute

        // Which dose is ringing now (null = nothing is ringing)
        @Volatile
        private var ringingKey: String? = null

        // Start the alarm of one dose
        fun ring(context: Context, medicineId: Long, doseIndex: Int, dateKey: String) {
            val intent = Intent(context, AlarmService::class.java)
            intent.action = ACTION_START
            intent.putExtra(ReminderIds.EXTRA_MEDICINE_ID, medicineId)
            intent.putExtra(ReminderIds.EXTRA_DOSE_INDEX, doseIndex)
            intent.putExtra(ReminderIds.EXTRA_DATE_KEY, dateKey)
            ContextCompat.startForegroundService(context, intent)
        }

        // A test alarm (from Settings)
        fun ringTest(context: Context) {
            val intent = Intent(context, AlarmService::class.java)
            intent.action = ACTION_START
            intent.putExtra(ReminderIds.EXTRA_TEST, true)
            ContextCompat.startForegroundService(context, intent)
        }

        // Stop the sound, keep the notification
        fun silence(context: Context) {
            if (ringingKey != null) send(context, ACTION_SILENCE)
        }

        // The dose was taken: stop the sound and remove the notification
        fun dismissIfRinging(context: Context, medicineId: Long, doseIndex: Int) {
            if (ringingKey == "$medicineId:$doseIndex") send(context, ACTION_DISMISS)
        }

        private fun send(context: Context, action: String) {
            try {
                val intent = Intent(context, AlarmService::class.java)
                intent.action = action
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not send $action to the alarm service", e)
            }
        }
    }

    private enum class Mode { SCREEN, CARD, BANNER }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    // What is ringing now
    private var currentMedicine: Medicine? = null
    private var currentDoseIndex = -1
    private var currentDateKey = ""
    private var currentIsTest = false
    private var currentNotificationId = 0
    private var currentSoundIndex = 0

    // How the alarm is shown now
    private var mode = Mode.BANNER

    // The timers of the 3 steps
    private val endFirstRing = Runnable { pauseRinging() }
    private val startSecondRing = Runnable { ringSecondTime() }
    private val endSecondRing = Runnable {
        AlarmLog.add(this@AlarmService, "Alarm finished. The notification stays until you take the dose.")
        stopRinging(removeNotification = false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_SILENCE -> {
                AlarmLog.add(this, "Alarm silenced (sound stopped)")
                stopRinging(removeNotification = false)
            }
            ACTION_DISMISS -> {
                AlarmLog.add(this, "Alarm stopped (dose taken or Stop tapped)")
                stopRinging(removeNotification = true)
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelTimers()
        silenceOutput()
        AlarmOverlay.hide(this)
        ringingKey = null
        super.onDestroy()
    }

    // Is the phone on, with the screen unlocked?
    private fun deviceInUse(): Boolean {
        return try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            pm.isInteractive && !km.isKeyguardLocked
        } catch (e: Exception) {
            false
        }
    }

    private fun handleStart(intent: Intent) {
        val test = intent.getBooleanExtra(ReminderIds.EXTRA_TEST, false)
        val medicineId = intent.getLongExtra(ReminderIds.EXTRA_MEDICINE_ID, -1L)
        val doseIndex = intent.getIntExtra(ReminderIds.EXTRA_DOSE_INDEX, -1)
        val dateKey = intent.getStringExtra(ReminderIds.EXTRA_DATE_KEY) ?: ""
        val key = if (test) "test" else "$medicineId:$doseIndex"
        val medicine = if (test) null else MedicineStorage.getById(this, medicineId)

        // Already ringing for another dose: this dose only gets a normal notification
        if (ringingKey != null && ringingKey != key) {
            AlarmLog.add(this, "Another alarm is ringing. This dose got a normal notification.")
            if (medicine != null) {
                NotificationHelper.showDose(this, medicine, doseIndex, dateKey)
            }
            return
        }

        // Ringing again for the same dose: start clean
        cancelTimers()
        silenceOutput()
        AlarmOverlay.hide(this)

        NotificationHelper.createChannel(this)

        currentIsTest = test
        currentMedicine = medicine
        currentDoseIndex = doseIndex
        currentDateKey = dateKey
        currentSoundIndex = medicine?.soundIndex ?: 0

        // How will we show the alarm?
        val appVisible = MedTimeApp.isInForeground
        val overlayAllowed = AlarmOverlay.canShow(this)
        val inUse = deviceInUse()

        mode = when {
            appVisible && medicine != null -> Mode.SCREEN
            overlayAllowed && inUse -> Mode.CARD
            else -> Mode.BANNER
        }

        AlarmLog.add(
            this,
            "Alarm check: MedTime on screen ${if (appVisible) "yes" else "no"}, " +
                    "pop-up permission ${if (overlayAllowed) "ON" else "OFF"}, " +
                    "phone ${if (inUse) "in use" else "locked or screen off"}, mode $mode"
        )

        // Build the notification FIRST: a foreground service must show one quickly
        val notification = buildCurrentNotification()
        val toShow = notification ?: NotificationHelper.buildTestAlarm(this, mode != Mode.BANNER)

        currentNotificationId = if (notification != null && !test) {
            ReminderIds.notificationId(medicineId, doseIndex)
        } else {
            NotificationHelper.TEST_NOTIFICATION_ID
        }

        if (!startAsForeground(currentNotificationId, toShow)) {
            // The alarm service could not start: use the normal notification instead
            if (medicine != null) {
                NotificationHelper.showDose(this, medicine, doseIndex, dateKey)
            }
            stopSelf()
            return
        }

        // A dose that does not exist anymore: nothing to ring
        if (notification == null) {
            AlarmLog.add(this, "The dose does not exist anymore. Nothing to ring.")
            stopRinging(removeNotification = true)
            return
        }

        ringingKey = key
        AlarmLog.add(this, "Alarm started: rings for 5 minutes")

        startOutput(FIRST_RING_SECONDS)
        handler.postDelayed(endFirstRing, FIRST_RING_SECONDS * 1000L)

        // Show the alarm on the screen
        when (mode) {
            Mode.SCREEN -> {
                if (medicine != null && startTakeScreen(medicine)) {
                    AlarmLog.add(this, "The Take screen opened on top of MedTime")
                } else {
                    fallbackToBanner("the Take screen could not open")
                }
            }
            Mode.CARD -> {
                if (showOverlayCard()) {
                    AlarmLog.add(this, "Pop-up card shown on top of the screen")
                } else {
                    fallbackToBanner("the pop-up card could not open")
                }
            }
            Mode.BANNER -> openTakeScreenIfFullScreenIsBlocked(medicine, overlayAllowed, inUse)
        }
    }

    // The pop-up could not open: use the loud notification banner instead
    private fun fallbackToBanner(reason: String) {
        AlarmLog.add(this, "Using the notification banner, because $reason")
        mode = Mode.BANNER
        val banner = buildCurrentNotification()
        if (banner != null) startAsForeground(currentNotificationId, banner)
    }

    // Android 14 and newer: if the full-screen alarm is blocked, but "Display over other
    // apps" is ON, we open the Take screen ourselves (phone locked)
    private fun openTakeScreenIfFullScreenIsBlocked(
        medicine: Medicine?,
        overlayAllowed: Boolean,
        inUse: Boolean
    ) {
        if (medicine == null || inUse || !overlayAllowed) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val manager = getSystemService(NotificationManager::class.java)
            if (!manager.canUseFullScreenIntent()) {
                if (startTakeScreen(medicine)) {
                    AlarmLog.add(this, "Full-screen alarm is blocked, so the Take screen was opened directly")
                }
            }
        }
    }

    // The notification of what is ringing now
    private fun buildCurrentNotification(): Notification? {
        val quiet = mode != Mode.BANNER
        val medicine = currentMedicine

        if (!currentIsTest && medicine != null) {
            val channel = if (quiet) {
                NotificationHelper.QUIET_CHANNEL_ID
            } else {
                NotificationHelper.ALARM_CHANNEL_ID
            }
            val notification = NotificationHelper.buildDoseNotification(
                this, medicine, currentDoseIndex, currentDateKey, channel, true, quiet
            )
            if (notification != null) return notification
        }
        return if (currentIsTest) NotificationHelper.buildTestAlarm(this, quiet) else null
    }

    private fun showOverlayCard(): Boolean {
        // The test alarm
        if (currentIsTest) {
            return AlarmOverlay.show(
                this,
                DoseScheduler.formatMinutes(DoseScheduler.minuteNow()),
                "Test medicine",
                "1 tablet · This is how your reminder looks",
                "Stop",
                onButton = { stopRinging(removeNotification = true) },
                onCardTap = { },
                onSilence = { stopRinging(removeNotification = false) }
            )
        }

        // A real dose
        val medicine = currentMedicine ?: return false
        val times = medicine.doseTimes.sorted()
        val minute = times.getOrNull(currentDoseIndex) ?: return false
        val details = if (times.size > 1) {
            "${medicine.dosageText()} · Dose ${currentDoseIndex + 1} of ${times.size}"
        } else {
            medicine.dosageText()
        }

        return AlarmOverlay.show(
            this,
            DoseScheduler.formatMinutes(minute),
            medicine.name,
            details,
            "Take",
            onButton = { takeFromCard(medicine) },
            onCardTap = { startTakeScreen(medicine) },
            onSilence = { stopRinging(removeNotification = false) }
        )
    }

    // "Take" on the pop-up card
    private fun takeFromCard(medicine: Medicine) {
        ringingKey = null // so the dose does not send a second stop message
        MedicineStorage.markDoseTaken(this, medicine.id, currentDateKey, currentDoseIndex)
        Toast.makeText(this, "${medicine.name} marked as taken", Toast.LENGTH_SHORT).show()
        AlarmLog.add(this, "Dose taken from the pop-up card")
        stopRinging(removeNotification = true)
    }

    // Opens the full "Take your pill" screen
    private fun startTakeScreen(medicine: Medicine): Boolean {
        return try {
            val intent = Intent(this, TakePillActivity::class.java)
            intent.data = Uri.parse("medtime://open/${medicine.id}/$currentDoseIndex")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            intent.putExtra(ReminderIds.EXTRA_MEDICINE_ID, medicine.id)
            intent.putExtra(ReminderIds.EXTRA_DOSE_INDEX, currentDoseIndex)
            intent.putExtra(ReminderIds.EXTRA_DATE_KEY, currentDateKey)
            startActivity(intent)
            AlarmOverlay.hide(this)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not open the Take screen", e)
            AlarmLog.add(this, "Could not open the Take screen: ${e.message}")
            false
        }
    }

    private fun startAsForeground(id: Int, notification: Notification): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(id, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not start the alarm service as a foreground service", e)
            AlarmLog.add(this, "ALARM SERVICE FAILED to start: ${e.message}. Using a normal notification.")
            false
        }
    }

    // Step 2: after 5 minutes, be quiet for a while
    private fun pauseRinging() {
        AlarmLog.add(this, "First ring finished. Quiet for ${PAUSE_SECONDS / 60} minutes.")
        silenceOutput()
        handler.postDelayed(startSecondRing, PAUSE_SECONDS * 1000L)
    }

    // Step 3: ring again for 1 minute, and show the banner again
    private fun ringSecondTime() {
        AlarmLog.add(this, "Ringing again for 1 minute")

        val notification = buildCurrentNotification()
        if (notification != null) {
            startAsForeground(currentNotificationId, notification)
        }

        startOutput(SECOND_RING_SECONDS)
        handler.postDelayed(endSecondRing, SECOND_RING_SECONDS * 1000L)
    }

    // Sound + vibration + keep the phone awake
    private fun startOutput(seconds: Long) {
        startSound(currentSoundIndex)
        startVibration()
        acquireWakeLock(seconds)
    }

    // The alarm sound, repeating, on the ALARM volume
    private fun startSound(soundIndex: Int) {
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val newPlayer = MediaPlayer.create(
                this,
                AlarmSounds.all[AlarmSounds.safeIndex(soundIndex)].rawRes,
                attributes,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            if (newPlayer == null) {
                Log.e(TAG, "The alarm sound could not be loaded")
                AlarmLog.add(this, "The alarm sound could not be loaded")
                return
            }

            newPlayer.isLooping = true
            newPlayer.start()
            player = newPlayer
        } catch (e: Exception) {
            Log.e(TAG, "Could not play the alarm sound", e)
            AlarmLog.add(this, "Could not play the alarm sound: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        try {
            val v = getSystemService(Vibrator::class.java) ?: return
            val pattern = longArrayOf(0, 600, 400, 600, 400, 1000, 1200)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                v.vibrate(VibrationEffect.createWaveform(pattern, 0), attributes)
            } else {
                v.vibrate(pattern, 0)
            }
            vibrator = v
        } catch (e: Exception) {
            Log.e(TAG, "Could not vibrate", e)
        }
    }

    // Keeps the phone awake while the alarm rings
    private fun acquireWakeLock(seconds: Long) {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MedTime:alarm")
            lock.acquire((seconds + 10) * 1000L)
            wakeLock = lock
        } catch (e: Exception) {
            Log.e(TAG, "Could not keep the phone awake", e)
        }
    }

    private fun cancelTimers() {
        handler.removeCallbacks(endFirstRing)
        handler.removeCallbacks(startSecondRing)
        handler.removeCallbacks(endSecondRing)
    }

    // Stops the sound, the vibration and the wake lock
    private fun silenceOutput() {
        try {
            player?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Could not stop the player", e)
        }
        try {
            player?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Could not release the player", e)
        }
        player = null

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Could not stop the vibration", e)
        }
        vibrator = null

        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Could not release the wake lock", e)
        }
        wakeLock = null
    }

    private fun stopRinging(removeNotification: Boolean) {
        cancelTimers()
        silenceOutput()
        AlarmOverlay.hide(this)
        ringingKey = null
        stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        stopSelf()
    }
}