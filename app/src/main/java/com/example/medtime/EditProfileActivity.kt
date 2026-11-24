package com.example.medtime

import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.widget.doAfterTextChanged

// Edit the name, the email and the password. "Save Changes" writes them in the database.
class EditProfileActivity : AppCompatActivity() {

    // The account as it is saved now (null until it is read from the database)
    private var user: User? = null

    // After the first try, the red password text updates while the user types
    private var liveCheck = false

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etCurrentPassword: EditText
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var tvNameError: TextView
    private lateinit var tvEmailError: TextView
    private lateinit var tvCurrentError: TextView
    private lateinit var tvNewPasswordError: TextView
    private lateinit var btnSave: AppCompatButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etCurrentPassword = findViewById(R.id.etCurrentPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        tvNameError = findViewById(R.id.tvNameError)
        tvEmailError = findViewById(R.id.tvEmailError)
        tvCurrentError = findViewById(R.id.tvCurrentError)
        tvNewPasswordError = findViewById(R.id.tvNewPasswordError)
        btnSave = findViewById(R.id.btnSaveProfile)

        // Eye icons: show or hide each password
        setupEye(etCurrentPassword, findViewById(R.id.ivToggleCurrent))
        setupEye(etNewPassword, findViewById(R.id.ivToggleNew))
        setupEye(etConfirmPassword, findViewById(R.id.ivToggleConfirm))

        // The red texts go away when the user types again
        etName.doAfterTextChanged { tvNameError.visibility = View.GONE }
        etEmail.doAfterTextChanged { tvEmailError.visibility = View.GONE }
        etCurrentPassword.doAfterTextChanged { tvCurrentError.visibility = View.GONE }
        etNewPassword.doAfterTextChanged { if (liveCheck) showPasswordErrors() }
        etConfirmPassword.doAfterTextChanged { if (liveCheck) showPasswordErrors() }

        // Fill the boxes with the saved name and email (read in the background)
        val appContext = applicationContext
        AppExecutors.io.execute {
            val saved = UserStorage.getById(appContext, SessionManager.currentUserId(appContext))
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (saved == null) {
                    Toast.makeText(this, "Could not load your account", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    user = saved
                    etName.setText(saved.fullName)
                    etEmail.setText(saved.email)
                }
            }
        }

        btnSave.setOnClickListener { trySave() }
    }

    private fun trySave() {
        val saved = user ?: return // the account is not loaded yet

        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val currentPassword = etCurrentPassword.text.toString()
        val newPassword = etNewPassword.text.toString()
        val confirm = etConfirmPassword.text.toString()

        tvNameError.visibility = View.GONE
        tvEmailError.visibility = View.GONE
        tvCurrentError.visibility = View.GONE

        var allGood = true

        if (name.isEmpty()) {
            showError(tvNameError, "Please enter your name")
            allGood = false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(tvEmailError, "Please enter a valid email")
            allGood = false
        }

        val nameChanged = name != saved.fullName
        val emailChanged = UserStorage.normalizeEmail(email) != saved.email
        val passwordChanged = newPassword.isNotEmpty() || confirm.isNotEmpty()

        if (passwordChanged) {
            liveCheck = true
            if (!showPasswordErrors()) allGood = false
        }

        // A new email or a new password needs the current password
        if ((emailChanged || passwordChanged) && currentPassword.isEmpty()) {
            showError(tvCurrentError, "Enter your current password to change your email or password")
            allGood = false
        }

        if (!allGood) return

        if (!nameChanged && !emailChanged && !passwordChanged) {
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show()
            return
        }

        // Save in the database (in the background, the screen stays smooth)
        btnSave.isEnabled = false
        btnSave.text = "Saving..."
        val appContext = applicationContext

        AppExecutors.io.execute {
            val result = UserStorage.updateProfile(
                appContext, saved.id, name, email, currentPassword, newPassword
            )

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                when (result) {
                    UserStorage.UpdateResult.SUCCESS -> {
                        Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                        finish() // back to Settings, which shows the new info
                    }
                    UserStorage.UpdateResult.WRONG_PASSWORD -> {
                        resetButton()
                        showError(tvCurrentError, "Current password is incorrect")
                    }
                    UserStorage.UpdateResult.EMAIL_TAKEN -> {
                        resetButton()
                        showError(tvEmailError, "This email is already used by another account")
                    }
                    UserStorage.UpdateResult.ERROR -> {
                        resetButton()
                        Toast.makeText(
                            this, "Could not save your changes. Please try again.", Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    // The red text under the Confirm box: 8 characters, special character, same password
    private fun showPasswordErrors(): Boolean {
        val errors = RegisterActivity.passwordProblems(etNewPassword.text.toString()).toMutableList()
        if (etNewPassword.text.toString() != etConfirmPassword.text.toString()) {
            errors.add("Passwords do not match")
        }

        return if (errors.isEmpty()) {
            tvNewPasswordError.visibility = View.GONE
            true
        } else {
            tvNewPasswordError.text = errors.joinToString("\n")
            tvNewPasswordError.visibility = View.VISIBLE
            false
        }
    }

    private fun showError(view: TextView, message: String) {
        view.text = message
        view.visibility = View.VISIBLE
    }

    private fun resetButton() {
        btnSave.isEnabled = true
        btnSave.text = "Save Changes"
    }

    private fun setupEye(editText: EditText, icon: ImageView) {
        var visible = false
        icon.setOnClickListener {
            visible = !visible
            if (visible) {
                editText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                icon.setImageResource(R.drawable.ic_eye_off)
            } else {
                editText.transformationMethod = PasswordTransformationMethod.getInstance()
                icon.setImageResource(R.drawable.ic_eye)
            }
            editText.setSelection(editText.text.length)
        }
    }
}