package com.aipos.aipospm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {

    @Query("SELECT * FROM passwords WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllPasswords(): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM passwords WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    suspend fun getActivePasswordsList(): List<PasswordEntry>

    @Query("SELECT * FROM passwords WHERE isDeleted = 0 AND (title LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    fun searchPasswords(query: String): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM passwords WHERE id = :id")
    fun getPasswordById(id: Int): Flow<PasswordEntry?>

    @Query("SELECT * FROM passwords WHERE isFavorite = 1 AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getFavoritePasswords(): Flow<List<PasswordEntry>>

    @Query("SELECT COUNT(*) FROM passwords WHERE isDeleted = 0")
    fun getPasswordCount(): Flow<Int>

    // Trash / Soft delete operations
    @Query("SELECT * FROM passwords WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedPasswords(): Flow<List<PasswordEntry>>

    @Query("SELECT COUNT(*) FROM passwords WHERE isDeleted = 1")
    fun getDeletedPasswordCount(): Flow<Int>

    @Query("UPDATE passwords SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeletePassword(id: Int, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE passwords SET isDeleted = 0, deletedAt = null WHERE id = :id")
    suspend fun restorePasswordById(id: Int)

    @Query("DELETE FROM passwords WHERE id = :id AND isDeleted = 1")
    suspend fun permanentlyDeletePassword(id: Int)

    @Query("DELETE FROM passwords WHERE isDeleted = 1")
    suspend fun emptyPasswordTrash()

    @Query("DELETE FROM passwords WHERE isDeleted = 1 AND deletedAt < :cutoffTimestamp")
    suspend fun purgeOldDeletedPasswords(cutoffTimestamp: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(entry: PasswordEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasswords(entries: List<PasswordEntry>)

    @Update
    suspend fun updatePassword(entry: PasswordEntry)

    @Delete
    suspend fun deletePassword(entry: PasswordEntry)

    @Query("DELETE FROM passwords WHERE id = :id")
    suspend fun deletePasswordById(id: Int)

    @Query("DELETE FROM passwords")
    suspend fun clearTable()

    @Query("UPDATE passwords SET categoryId = null WHERE categoryId = :categoryId")
    suspend fun clearCategoryRef(categoryId: Int)
}
