package com.example.medtime

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.widget.doAfterTextChanged

class RegisterActivity : AppCompatActivity() {

    companion object {
        // The "note" that sends the new email back to the Login screen
        const val EXTRA_EMAIL = "email"

        // The password rules: at least 8 characters, and at least 1 special character
        private const val MIN_LENGTH = 8

        // A special character is anything that is not a letter, a number or a space
        private fun hasSpecialCharacter(password: String): Boolean {
            return password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        }

        // The list of rules the password still breaks (empty = the password is good)
        fun passwordProblems(password: String): List<String> {
            val problems = mutableListOf<String>()
            if (password.length < MIN_LENGTH) {
                problems.add("Password must be at least 8 characters long")
            }
            if (!hasSpecialCharacter(password)) {
                problems.add("Password needs a special character (for example ! @ # % &)")
            }
            return problems
        }
    }

    private var isPasswordVisible = false
    private var isConfirmVisible = false

    // After the first try, the red text updates while the user types
    private var liveCheck = false

    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var tvPasswordError: TextView
    private lateinit var tvEmailError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etFullName = findViewById<EditText>(R.id.etFullName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        tvPasswordError = findViewById(R.id.tvPasswordError)
        tvEmailError = findViewById(R.id.tvEmailError)
        val ivTogglePassword = findViewById<ImageView>(R.id.ivTogglePassword)
        val ivToggleConfirm = findViewById<ImageView>(R.id.ivToggleConfirm)
        val cbTerms = findViewById<CheckBox>(R.id.cbTerms)
        val btnCreateAccount = findViewById<AppCompatButton>(R.id.btnCreateAccount)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)

        // Remove any theme tint, so our green check keeps its own colors
        cbTerms.buttonTintList = null

        // Eye icon: show or hide the password
        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            setPasswordVisible(etPassword, ivTogglePassword, isPasswordVisible)
        }
        ivToggleConfirm.setOnClickListener {
            isConfirmVisible = !isConfirmVisible
            setPasswordVisible(etConfirmPassword, ivToggleConfirm, isConfirmVisible)
        }

        // The red text updates while typing (after the first try)
        etPassword.doAfterTextChanged { if (liveCheck) showPasswordErrors() }
        etConfirmPassword.doAfterTextChanged { if (liveCheck) showPasswordErrors() }
        etEmail.doAfterTextChanged { tvEmailError.visibility = View.GONE }

        // Create Account button
        btnCreateAccount.setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val confirm = etConfirmPassword.text.toString()

            if (fullName.isEmpty() || email.isEmpty() ||
                password.isEmpty() || confirm.isEmpty()
            ) {
                showMessage("Please fill in all fields")
                return@setOnClickListener
            }

            // Check everything, and show the red texts under the boxes
            var allGood = true

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tvEmailError.text = "Please enter a valid email"
                tvEmailError.visibility = View.VISIBLE
                allGood = false
            }

            liveCheck = true
            if (!showPasswordErrors()) allGood = false

            if (!allGood) return@setOnClickListener

            if (!cbTerms.isChecked) {
                showMessage("Please agree to the Terms and Privacy Policy")
                return@setOnClickListener
            }

            saveAccount(btnCreateAccount, fullName, email, password)
        }

        // "Login" link: go back to the Login screen
        tvLogin.setOnClickListener {
            finish()
        }
    }

    // Writes the red text under the Confirm password box. Returns true if all is good.
    private fun showPasswordErrors(): Boolean {
        val password = etPassword.text.toString()
        val confirm = etConfirmPassword.text.toString()

        val errors = passwordProblems(password).toMutableList()
        if (password != confirm) {
            errors.add("Passwords do not match")
        }

        return if (errors.isEmpty()) {
            tvPasswordError.visibility = View.GONE
            true
        } else {
            tvPasswordError.text = errors.joinToString("\n")
            tvPasswordError.visibility = View.VISIBLE
            false
        }
    }

    // Saves the account in the database (in the background, the screen stays smooth)
    private fun saveAccount(
        button: AppCompatButton,
        fullName: String,
        email: String,
        password: String
    ) {
        button.isEnabled = false
        button.text = "Creating account..."
        val appContext = applicationContext

        AppExecutors.io.execute {
            val result = UserStorage.register(appContext, fullName, email, password)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                when (result) {
                    UserStorage.RegisterResult.SUCCESS -> {
                        showMessage("Account created! Please login.")
                        val data = Intent().putExtra(EXTRA_EMAIL, UserStorage.normalizeEmail(email))
                        setResult(RESULT_OK, data)
                        finish() // back to Login
                    }
                    UserStorage.RegisterResult.EMAIL_TAKEN -> {
                        button.isEnabled = true
                        button.text = "Create Account"
                        tvEmailError.text = "This email is already registered. Please login."
                        tvEmailError.visibility = View.VISIBLE
                    }
                    UserStorage.RegisterResult.ERROR -> {
                        button.isEnabled = true
                        button.text = "Create Account"
                        showMessage("Could not create the account. Please try again.")
                    }
                }
            }
        }
    }

    private fun setPasswordVisible(editText: EditText, icon: ImageView, visible: Boolean) {
        if (visible) {
            editText.transformationMethod = HideReturnsTransformationMethod.getInstance()
            icon.setImageResource(R.drawable.ic_eye_off)
        } else {
            editText.transformationMethod = PasswordTransformationMethod.getInstance()
            icon.setImageResource(R.drawable.ic_eye)
        }
        editText.setSelection(editText.text.length)
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}