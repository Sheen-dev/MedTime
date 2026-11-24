package com.example.medtime

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import java.util.Locale

// One account
data class User(
    val id: Long,
    val fullName: String,
    val username: String,
    val email: String
)

// Saves and reads the accounts in the database (table "users").
// Call these from the background (AppExecutors.io), not from the screen thread.
object UserStorage {

    private const val TAG = "MedTime"

    enum class RegisterResult { SUCCESS, EMAIL_TAKEN, ERROR }

    enum class UpdateResult { SUCCESS, WRONG_PASSWORD, EMAIL_TAKEN, ERROR }

    // The email is always saved in small letters, so "Ana@Mail.com" = "ana@mail.com"
    fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    fun register(
        context: Context,
        fullName: String,
        email: String,
        password: String
    ): RegisterResult {
        return try {
            val values = ContentValues()
            values.put("full_name", fullName.trim())
            values.put("username", "") // not used anymore (the email is the login)
            values.put("email", normalizeEmail(email))
            values.put("password_hash", PasswordHasher.hash(password))
            values.put("created_at", System.currentTimeMillis())

            MedTimeDatabase.get(context).writableDatabase.insertOrThrow("users", null, values)
            RegisterResult.SUCCESS
        } catch (e: SQLiteConstraintException) {
            // The UNIQUE email already exists
            RegisterResult.EMAIL_TAKEN
        } catch (e: Exception) {
            Log.e(TAG, "Could not save the account", e)
            RegisterResult.ERROR
        }
    }

    // Right email + right password = the User. Anything else = null.
    fun login(context: Context, email: String, password: String): User? {
        return try {
            val db = MedTimeDatabase.get(context).readableDatabase
            db.rawQuery(
                "SELECT id, full_name, username, email, password_hash FROM users WHERE email = ?",
                arrayOf(normalizeEmail(email))
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                val saved = cursor.getString(4)
                if (!PasswordHasher.verify(password, saved)) return null
                User(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login failed", e)
            null
        }
    }

    fun getById(context: Context, id: Long): User? {
        return try {
            val db = MedTimeDatabase.get(context).readableDatabase
            db.rawQuery(
                "SELECT id, full_name, username, email FROM users WHERE id = ?",
                arrayOf(id.toString())
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                User(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read the account", e)
            null
        }
    }

    // Saves the new name / email / password of an account.
    // To change the EMAIL or the PASSWORD, the current password must be right.
    // newPassword = "" means: keep the old password.
    fun updateProfile(
        context: Context,
        userId: Long,
        fullName: String,
        email: String,
        currentPassword: String,
        newPassword: String
    ): UpdateResult {
        return try {
            val db = MedTimeDatabase.get(context).writableDatabase

            // The saved email and password hash of this account
            var savedEmail = ""
            var savedHash = ""
            val found = db.rawQuery(
                "SELECT email, password_hash FROM users WHERE id = ?",
                arrayOf(userId.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    savedEmail = cursor.getString(0)
                    savedHash = cursor.getString(1)
                    true
                } else {
                    false
                }
            }
            if (!found) return UpdateResult.ERROR

            val newEmail = normalizeEmail(email)
            val emailChanged = newEmail != savedEmail
            val passwordChanged = newPassword.isNotEmpty()

            // Email or password: the current password must be right
            if ((emailChanged || passwordChanged) &&
                !PasswordHasher.verify(currentPassword, savedHash)
            ) {
                return UpdateResult.WRONG_PASSWORD
            }

            val values = ContentValues()
            values.put("full_name", fullName.trim())
            if (emailChanged) values.put("email", newEmail)
            if (passwordChanged) values.put("password_hash", PasswordHasher.hash(newPassword))

            db.update("users", values, "id = ?", arrayOf(userId.toString()))
            UpdateResult.SUCCESS
        } catch (e: SQLiteConstraintException) {
            // Another account already has this email
            UpdateResult.EMAIL_TAKEN
        } catch (e: Exception) {
            Log.e(TAG, "Could not update the account", e)
            UpdateResult.ERROR
        }
    }
}