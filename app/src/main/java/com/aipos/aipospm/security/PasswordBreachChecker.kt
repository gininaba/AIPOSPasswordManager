package com.aipos.aipospm.security

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Checks passwords against a local offline asset database of 2000 common passwords.
 */
object PasswordBreachChecker {
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
            e.printStackTrace()
        }
        weakPasswords = set
    }

    /**
     * Checks if a password is considered weak or breached.
     * A password is breached if it is in the common passwords list or has length < 6.
     */
    fun isPasswordBreached(context: Context, password: String): Boolean {
        init(context.applicationContext)
        val cleaned = password.trim().lowercase()
        if (cleaned.length < 6) return true
        return weakPasswords?.contains(cleaned) == true
    }
}
