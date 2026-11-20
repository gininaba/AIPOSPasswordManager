package com.aipos.aipospm.security

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Checks passwords against a local offline asset database of 2000 common passwords.
 */
object PasswordBreachChecker {
    @Volatile
    private var weakPasswords: HashSet<String>? = null

    /**
     * Initializes the weak password list from assets if not already loaded.
     */
    @Synchronized
    fun init(context: Context) {
        if (weakPasswords != null) return
        val set = HashSet<String>()
        try {
            context.assets.open("weak_passwords.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim().lowercase()
                        if (trimmed.isNotEmpty()) {
                            set.add(trimmed)
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("PasswordBreachChecker", "Failed to load weak passwords list", e)
        }
        weakPasswords = set
    }

    /**
     * Checks if a password is considered weak or breached.
     * - Blank/empty passwords (e.g. Secure Notes or username-only entries) are never flagged.
     * - 4-5 digit numeric PINs are only flagged if they appear in the common breached list
     *   (e.g. "1234", "0000"), avoiding false positives on fixed-length card/SIM PINs.
     * - Non-PIN passwords shorter than 6 characters or present in the common passwords list are flagged.
     */
    fun isPasswordBreached(context: Context, password: String): Boolean {
        if (password.isBlank()) return false
        init(context.applicationContext)
        return isPasswordBreachedWithSet(password, weakPasswords)
    }

    internal fun isPasswordBreachedWithSet(password: String, breachedSet: Set<String>?): Boolean {
        if (password.isBlank()) return false
        val cleaned = password.trim().lowercase()
        if (cleaned.isEmpty()) return false
        if (breachedSet?.contains(cleaned) == true) return true
        val isFixedNumericPin = cleaned.length in 4..5 && cleaned.all { it.isDigit() }
        if (isFixedNumericPin) return false
        return cleaned.length < 6
    }
}
