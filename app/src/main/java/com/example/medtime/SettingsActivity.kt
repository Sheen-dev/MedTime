package com.example.medtime

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileEmail: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val cardProfile = findViewById<View>(R.id.cardProfile)
        val switchDarkMode = findViewById<SwitchCompat>(R.id.switchDarkMode)
        val tvLogout = findViewById<TextView>(R.id.tvLogout)
        tvProfileName = findViewById(R.id.tvProfileName)
        tvProfileEmail = findViewById(R.id.tvProfileEmail)

        // Tap the profile: edit the name, the email and the password
        cardProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        switchDarkMode.isChecked = ThemeManager.isDark(this)

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            ThemeManager.setDark(this, isChecked)
        }

        tvLogout.setOnClickListener {
            // Forget the account: next time the app opens on the Login screen
            SessionManager.logOut(this)

            val intent = Intent(this, LoginActivity::class.java)

            intent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK

            startActivity(intent)
        }
    }

    // Each time Settings appears (also after Edit Profile): show the saved name and email
    override fun onResume() {
        super.onResume()

        val appContext = applicationContext
        AppExecutors.io.execute {
            val user = UserStorage.getById(appContext, SessionManager.currentUserId(appContext))
            runOnUiThread {
                if (user == null || isFinishing || isDestroyed) return@runOnUiThread
                tvProfileName.text = user.fullName
                tvProfileEmail.text = user.email
            }
        }
    }
}