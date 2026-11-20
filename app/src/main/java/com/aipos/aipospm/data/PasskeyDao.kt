package com.aipos.aipospm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PasskeyDao {

    @Query("SELECT * FROM passkeys WHERE isDeleted = 0 ORDER BY lastUsedAt DESC, createdAt DESC")
    fun getAllPasskeys(): Flow<List<PasskeyEntry>>

    @Query("SELECT * FROM passkeys WHERE isDeleted = 0 ORDER BY lastUsedAt DESC, createdAt DESC")
    suspend fun getActivePasskeysList(): List<PasskeyEntry>

    @Query("SELECT COUNT(*) FROM passkeys WHERE isDeleted = 0")
    fun getPasskeyCount(): Flow<Int>

    @Query("SELECT * FROM passkeys WHERE isDeleted = 0 AND LOWER(rpId) = LOWER(:rpId) ORDER BY lastUsedAt DESC")
    suspend fun getActivePasskeysByRpId(rpId: String): List<PasskeyEntry>

    @Query("SELECT * FROM passkeys WHERE isDeleted = 0 AND credentialId = :credentialId LIMIT 1")
    suspend fun getActivePasskeyByCredentialId(credentialId: String): PasskeyEntry?

    @Query("SELECT * FROM passkeys WHERE id = :id LIMIT 1")
    suspend fun getPasskeyByIdSync(id: Int): PasskeyEntry?

    @Query("UPDATE passkeys SET signCount = signCount + 1, lastUsedAt = :usedAt WHERE id = :id")
    suspend fun recordPasskeyUsage(id: Int, usedAt: Long = System.currentTimeMillis())

    // Trash / Soft delete operations
    @Query("SELECT * FROM passkeys WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedPasskeys(): Flow<List<PasskeyEntry>>

    @Query("SELECT COUNT(*) FROM passkeys WHERE isDeleted = 1")
    fun getDeletedPasskeyCount(): Flow<Int>

    @Query("UPDATE passkeys SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeletePasskey(id: Int, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE passkeys SET isDeleted = 0, deletedAt = null WHERE id = :id")
    suspend fun restorePasskeyById(id: Int)

    @Query("DELETE FROM passkeys WHERE id = :id AND isDeleted = 1")
    suspend fun permanentlyDeletePasskey(id: Int)

    @Query("DELETE FROM passkeys WHERE isDeleted = 1")
    suspend fun emptyPasskeyTrash()

    @Query("DELETE FROM passkeys WHERE isDeleted = 1 AND deletedAt < :cutoffTimestamp")
    suspend fun purgeOldDeletedPasskeys(cutoffTimestamp: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasskey(entry: PasskeyEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasskeys(entries: List<PasskeyEntry>)

    @Query("DELETE FROM passkeys")
    suspend fun clearTable()
}

