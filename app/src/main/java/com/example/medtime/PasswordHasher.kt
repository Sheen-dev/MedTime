package com.example.medtime

import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// Passwords are NEVER saved as text. We save a "hash": a scrambled version that
// cannot be turned back into the password. Every password gets its own random salt.
// Saved format:  algorithm$iterations$salt$hash
object PasswordHasher {

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SEPARATOR = "$"
    private val random = SecureRandom()

    // PBKDF2 with SHA-256 (Android 8+). Android 7 only has SHA-1.
    private fun algorithm(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            "PBKDF2WithHmacSHA256"
        } else {
            "PBKDF2WithHmacSHA1"
        }
    }

    private fun derive(algorithm: String, password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    // This takes a moment on purpose (it slows down guessing). Call it in the background.
    fun hash(password: String): String {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val algorithm = algorithm()
        val hash = derive(algorithm, password, salt, ITERATIONS)
        return listOf(
            algorithm,
            ITERATIONS.toString(),
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(hash, Base64.NO_WRAP)
        ).joinToString(SEPARATOR)
    }

    // Is this the right password for the saved hash?
    fun verify(password: String, saved: String): Boolean {
        return try {
            val parts = saved.split(SEPARATOR)
            if (parts.size != 4) return false
            val iterations = parts[1].toIntOrNull() ?: return false
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expected = Base64.decode(parts[3], Base64.NO_WRAP)
            val actual = derive(parts[0], password, salt, iterations)
            MessageDigest.isEqual(expected, actual)
        } catch (e: Exception) {
            false
        }
    }
}