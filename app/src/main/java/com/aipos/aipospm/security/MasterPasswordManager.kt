package com.aipos.aipospm.security

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
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
        private const val KEY_SCREEN_SECURITY_ENABLED = "screen_security_enabled"
        private const val KEY_RECOVERY_KEY_HASH = "recovery_key_hash"
        private const val KEY_RECOVERY_SALT = "recovery_salt"
        private const val KEY_RECOVERY_KEY_ENCRYPTED = "recovery_key_encrypted"
        private const val KEY_RECOVERY_KEY_IV = "recovery_key_iv"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 32
        private const val DEFAULT_AUTO_LOCK_TIMEOUT_MINUTES = 5
        private const val MAX_FREE_ATTEMPTS = 5

        private val SECURE_RANDOM = SecureRandom()

        @Volatile
        private var instance: MasterPasswordManager? = null

        fun getInstance(context: Context): MasterPasswordManager {
            return instance ?: synchronized(this) {
                instance ?: MasterPasswordManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext = context.applicationContext ?: context
    private val prefs: SharedPreferences
    private val cryptoManager = CryptoManager.shared

    @Volatile
    private var failedAttempts = 0

    @Volatile
    private var lockoutUntilElapsedMs = 0L

    init {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun monotonicNowMs(): Long {
        return try {
            SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            System.nanoTime() / 1_000_000L
        }
    }

    /**
     * Returns remaining brute-force lockout seconds, or 0 if attempts are allowed.
     */
    fun getRemainingLockoutSeconds(): Long {
        val remainingMs = lockoutUntilElapsedMs - monotonicNowMs()
        return if (remainingMs > 0L) (remainingMs + 999L) / 1000L else 0L
    }

    private fun recordFailedAttempt() {
        synchronized(this) {
            failedAttempts++
            if (failedAttempts >= MAX_FREE_ATTEMPTS) {
                val penaltySteps = (failedAttempts - MAX_FREE_ATTEMPTS).coerceAtMost(4)
                val delaySeconds = 5L shl penaltySteps // 5s, 10s, 20s, 40s, 80s
                lockoutUntilElapsedMs = monotonicNowMs() + delaySeconds * 1000L
            }
        }
    }

    private fun resetFailedAttempts() {
        synchronized(this) {
            failedAttempts = 0
            lockoutUntilElapsedMs = 0L
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        return MessageDigest.isEqual(
            a.toByteArray(Charsets.UTF_8),
            b.toByteArray(Charsets.UTF_8)
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
        resetFailedAttempts()
    }

    /**
     * Verify the entered password against the stored hash using constant-time comparison.
     */
    fun verifyMasterPassword(password: String): Boolean {
        if (getRemainingLockoutSeconds() > 0L) return false
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        val saltBase64 = prefs.getString(KEY_SALT, null) ?: return false
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)

        val inputHash = hashPassword(password, salt)
        val matched = constantTimeEquals(storedHash, inputHash)
        if (matched) {
            resetFailedAttempts()
        } else {
            recordFailedAttempt()
        }
        return matched
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
     * -1 represents "Never". Defaults to 5 minutes for safety.
     */
    fun getAutoLockTimeout(): Int {
        return prefs.getInt(KEY_AUTO_LOCK_TIMEOUT, DEFAULT_AUTO_LOCK_TIMEOUT_MINUTES)
    }

    /**
     * Set auto-lock timeout in minutes.
     */
    fun setAutoLockTimeout(timeoutMinutes: Int) {
        prefs.edit().putInt(KEY_AUTO_LOCK_TIMEOUT, timeoutMinutes).apply()
    }

    /**
     * Check if screen security (FLAG_SECURE) is enabled.
     * Defaults to true to protect against screenshots and recent apps switcher leaks.
     */
    fun isScreenSecurityEnabled(): Boolean {
        return prefs.getBoolean(KEY_SCREEN_SECURITY_ENABLED, true)
    }

    /**
     * Enable or disable screen security (FLAG_SECURE).
     */
    fun setScreenSecurityEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCREEN_SECURITY_ENABLED, enabled).apply()
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SECURE_RANDOM.nextBytes(salt)
        return salt
    }

    private fun hashPassword(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * Generate a new recovery key, hash it for lockout recovery,
     * encrypt it for retrieval in settings, and save both.
     * Format: AIPOS-XXXX-XXXX-XXXX-XXXX
     */
    fun generateRecoveryKey(): String {
        val alphabet = "CDEFHJKLMNPRTUVWXY23456789" // 26 unambiguous chars
        val builder = StringBuilder(16)
        
        for (i in 0 until 16) {
            val idx = SECURE_RANDOM.nextInt(alphabet.length)
            builder.append(alphabet[idx])
        }
        
        val key = builder.toString()
        val formattedKey = "AIPOS-${key.substring(0, 4)}-${key.substring(4, 8)}-${key.substring(8, 12)}-${key.substring(12, 16)}"
        
        val cleanKey = cleanRecoveryKey(formattedKey)
        val salt = generateSalt()
        val hash = hashPassword(cleanKey, salt)
        
        val (encrypted, iv) = cryptoManager.encrypt(formattedKey)
        
        prefs.edit()
            .putString(KEY_RECOVERY_KEY_HASH, hash)
            .putString(KEY_RECOVERY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_RECOVERY_KEY_ENCRYPTED, encrypted)
            .putString(KEY_RECOVERY_KEY_IV, iv)
            .apply()
            
        return formattedKey
    }

    /**
     * Retrieve the decrypted recovery key if it exists.
     */
    fun getDecryptedRecoveryKey(): String? {
        val encrypted = prefs.getString(KEY_RECOVERY_KEY_ENCRYPTED, null) ?: return null
        val iv = prefs.getString(KEY_RECOVERY_KEY_IV, null) ?: return null
        return try {
            cryptoManager.decrypt(encrypted, iv)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Verify the entered recovery key against the stored PBKDF2 hash.
     * Supports keys entered with or without the 'AIPOS-' prefix for robust recovery.
     */
    fun verifyRecoveryKey(key: String): Boolean {
        if (getRemainingLockoutSeconds() > 0L) return false
        val storedHash = prefs.getString(KEY_RECOVERY_KEY_HASH, null) ?: return false
        val saltBase64 = prefs.getString(KEY_RECOVERY_SALT, null) ?: return false
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        
        val cleanKey = cleanRecoveryKey(key)
        if (cleanKey.isBlank()) return false

        // 1. Direct hash check
        val inputHash = hashPassword(cleanKey, salt)
        if (constantTimeEquals(storedHash, inputHash)) {
            resetFailedAttempts()
            return true
        }

        // 2. If entered without "AIPOS" prefix, test with "AIPOS" prepended
        if (!cleanKey.startsWith("AIPOS")) {
            val hashWithPrefix = hashPassword("AIPOS$cleanKey", salt)
            if (constantTimeEquals(storedHash, hashWithPrefix)) {
                resetFailedAttempts()
                return true
            }
        }

        // 3. If entered with "AIPOS" prefix, test with "AIPOS" stripped
        if (cleanKey.startsWith("AIPOS")) {
            val stripped = cleanKey.removePrefix("AIPOS")
            val hashStripped = hashPassword(stripped, salt)
            if (constantTimeEquals(storedHash, hashStripped)) {
                resetFailedAttempts()
                return true
            }
        }

        recordFailedAttempt()
        return false
    }

    /**
     * Reset the master password if the recovery key is correct.
     */
    fun resetMasterPasswordWithRecoveryKey(key: String, newPw: String): Boolean {
        if (!verifyRecoveryKey(key)) return false
        setMasterPassword(newPw)
        return true
    }

    internal fun cleanRecoveryKey(key: String): String {
        return key.replace("-", "").filterNot { it.isWhitespace() }.uppercase()
    }
}
