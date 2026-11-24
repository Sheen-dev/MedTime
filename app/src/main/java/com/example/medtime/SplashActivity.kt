package com.example.medtime

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity

// The first screen: the logo. Meanwhile it checks if someone is already logged in:
//   logged in      -> Home (or the permission pages if something is not allowed)
//   not logged in  -> Login
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    // The logo stays at least this long (milliseconds)
    private val showTime = 1200L

    private var goNext: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val startTime = SystemClock.elapsedRealtime()
        val appContext = applicationContext

        // The database check runs in the background, so the logo never freezes
        AppExecutors.io.execute {
            var loggedIn = false
            val userId = SessionManager.currentUserId(appContext)
            if (userId != -1L) {
                if (UserStorage.getById(appContext, userId) != null) {
                    loggedIn = true
                } else {
                    // The account does not exist anymore: logout
                    SessionManager.logOut(appContext)
                }
            }

            val wait = maxOf(0L, showTime - (SystemClock.elapsedRealtime() - startTime))
            val next = Runnable { openNext(loggedIn) }
            goNext = next
            handler.postDelayed(next, wait)
        }
    }

    private fun openNext(loggedIn: Boolean) {
        if (isFinishing || isDestroyed) return

        val target = when {
            !loggedIn -> LoginActivity::class.java
            PermissionSteps.allGranted(this) -> HomeActivity::class.java
            else -> PermissionWizardActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        goNext?.let { handler.removeCallbacks(it) }
    }
}