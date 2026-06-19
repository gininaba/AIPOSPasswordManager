package com.aipos.aipospm.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Manages master password creation, verification, and change.
 * Uses EncryptedSharedPreferences to store the salted PBKDF2 hash.
 */
class MasterPasswordManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "aipos_master_prefs"
        private const val KEY_PASSWORD_HASH = "master_password_hash"
        private const val KEY_SALT = "master_salt"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 32
    }

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Check if a master password has been set up.
     */
    fun isMasterPasswordSet(): Boolean {
        return prefs.contains(KEY_PASSWORD_HASH)
    }

    /**
     * Set the master password for the first time.
     */
    fun setMasterPassword(password: String) {
        val salt = generateSalt()
        val hash = hashPassword(password, salt)

        prefs.edit()
            .putString(KEY_PASSWORD_HASH, hash)
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .apply()
    }

    /**
     * Verify the entered password against the stored hash.
     */
    fun verifyMasterPassword(password: String): Boolean {
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        val saltBase64 = prefs.getString(KEY_SALT, null) ?: return false
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)

        val inputHash = hashPassword(password, salt)
        return storedHash == inputHash
    }

    /**
     * Change the master password.
     */
    fun changeMasterPassword(oldPassword: String, newPassword: String): Boolean {
        if (!verifyMasterPassword(oldPassword)) return false
        setMasterPassword(newPassword)
        return true
    }

    /**
     * Check if biometric unlock is enabled.
     */
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    /**
     * Enable or disable biometric unlock.
     */
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /**
     * Get auto-lock timeout in minutes.
     * -1 represents "Never".
     */
    fun getAutoLockTimeout(): Int {
        return prefs.getInt(KEY_AUTO_LOCK_TIMEOUT, -1)
    }

    /**
     * Set auto-lock timeout in minutes.
     */
    fun setAutoLockTimeout(timeoutMinutes: Int) {
        prefs.edit().putInt(KEY_AUTO_LOCK_TIMEOUT, timeoutMinutes).apply()
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun hashPassword(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}
