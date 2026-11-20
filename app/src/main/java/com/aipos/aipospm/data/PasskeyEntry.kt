package com.aipos.aipospm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a WebAuthn / FIDO2 Passkey stored locally in the encrypted AIPOS vault.
 * Private keys are PKCS#8-encoded ECDSA P-256 keys encrypted at rest via [com.aipos.aipospm.security.CryptoManager].
 */
@Entity(tableName = "passkeys")
data class PasskeyEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val credentialId: String, // Base64Url-encoded unique credential ID
    val rpId: String,
    val rpName: String,
    val userHandle: String, // Base64Url-encoded WebAuthn user.id
    val userName: String,
    val userDisplayName: String = "",
    val encryptedPrivateKey: String,
    val privateKeyIv: String,
    val publicKeyCose: String, // Base64Url-encoded COSE EC2 public key
    @ColumnInfo(defaultValue = "0")
    val signCount: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)

