package com.aipos.aipospm.security

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles encrypted database backup serialization and deserialization.
 * Derives a key from a backup password using PBKDF2 and encrypts/decrypts the JSON payload with AES-256-GCM.
 */
class BackupManager {

    companion object {
        private const val PBKDF2_ITERATIONS = 10_000
        private const val KEY_LENGTH = 256
        private const val GCM_TAG_LENGTH = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ALGORITHM = "AES"
    }

    private val gson = Gson()

    /**
     * Encrypts the given database JSON payload with the provided password.
     * @return A JSON string containing base64-encoded salt, iv, and ciphertext.
     */
    fun encryptBackup(payloadJson: String, password: String): String {
        // Generate random 16-byte salt
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)

        // Derive key from password
        val key = deriveKey(password, salt)

        // Setup AES/GCM Cipher
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val encryptedBytes = cipher.doFinal(payloadJson.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv

        // Create backup object
        val backupObj = JsonObject().apply {
            addProperty("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            addProperty("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            addProperty("ciphertext", Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
        }

        return gson.toJson(backupObj)
    }

    /**
     * Decrypts the given encrypted backup JSON with the provided password.
     * @return The original decrypted JSON payload.
     */
    fun decryptBackup(backupJson: String, password: String): String {
        val backupObj = gson.fromJson(backupJson, JsonObject::class.java)
        
        val saltBase64 = backupObj.get("salt")?.asString ?: throw IllegalArgumentException("Missing salt")
        val ivBase64 = backupObj.get("iv")?.asString ?: throw IllegalArgumentException("Missing iv")
        val ciphertextBase64 = backupObj.get("ciphertext")?.asString ?: throw IllegalArgumentException("Missing ciphertext")

        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)

        // Derive key from password
        val key = deriveKey(password, salt)

        // Setup AES/GCM Cipher
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, ALGORITHM)
    }
}
