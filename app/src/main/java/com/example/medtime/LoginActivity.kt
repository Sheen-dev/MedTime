package com.example.medtime

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

// Login with the EMAIL and the password of an account saved in the database
class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvLoginError: TextView

    // After Sign Up, the new email is filled in here
    private val signUpLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val email = result.data?.getStringExtra(RegisterActivity.EXTRA_EMAIL)
        if (result.resultCode == RESULT_OK && !email.isNullOrEmpty()) {
            etEmail.setText(email)
            etPassword.requestFocus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvLoginError = findViewById(R.id.tvLoginError)
        val tvSignUp = findViewById<TextView>(R.id.tvSignUp)

        btnLogin.setOnClickListener { tryLogin() }

        // "Sign Up" link: open the Registration screen
        tvSignUp.setOnClickListener {
            signUpLauncher.launch(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun tryLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        tvLoginError.visibility = View.GONE

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter your email and password")
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email")
            return
        }

        setBusy(true)
        val appContext = applicationContext

        // The database and the password check run in the background (the screen stays smooth)
        AppExecutors.io.execute {
            val user = UserStorage.login(appContext, email, password)
            if (user != null) {
                SessionManager.logIn(appContext, user.id) // so we do not login again next time
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                if (user == null) {
                    setBusy(false)
                    showError("Incorrect email or password")
                } else {
                    // Login first, THEN the permissions: if something is not allowed yet,
                    // open the setup pages. Otherwise open Home.
                    val next = if (PermissionSteps.allGranted(this)) {
                        HomeActivity::class.java
                    } else {
                        PermissionWizardActivity::class.java
                    }
                    startActivity(Intent(this, next))
                    finish()
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        btnLogin.isEnabled = !busy
        btnLogin.text = if (busy) "Logging in..." else "Login"
    }

    private fun showError(message: String) {
        tvLoginError.text = message
        tvLoginError.visibility = View.VISIBLE
    }
}