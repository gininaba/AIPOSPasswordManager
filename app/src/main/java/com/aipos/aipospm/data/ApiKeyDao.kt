package com.aipos.aipospm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiKeyDao {

    @Query("SELECT * FROM api_keys WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllApiKeys(): Flow<List<ApiKeyEntry>>

    @Query("SELECT * FROM api_keys WHERE isDeleted = 0 AND serviceName LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchApiKeys(query: String): Flow<List<ApiKeyEntry>>

    @Query("SELECT * FROM api_keys WHERE id = :id")
    fun getApiKeyById(id: Int): Flow<ApiKeyEntry?>

    @Query("SELECT * FROM api_keys WHERE isFavorite = 1 AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getFavoriteApiKeys(): Flow<List<ApiKeyEntry>>

    @Query("SELECT COUNT(*) FROM api_keys WHERE isDeleted = 0")
    fun getApiKeyCount(): Flow<Int>

    // Trash / Soft delete operations
    @Query("SELECT * FROM api_keys WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedApiKeys(): Flow<List<ApiKeyEntry>>

    @Query("SELECT COUNT(*) FROM api_keys WHERE isDeleted = 1")
    fun getDeletedApiKeyCount(): Flow<Int>

    @Query("UPDATE api_keys SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteApiKey(id: Int, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE api_keys SET isDeleted = 0, deletedAt = null WHERE id = :id")
    suspend fun restoreApiKeyById(id: Int)

    @Query("DELETE FROM api_keys WHERE id = :id AND isDeleted = 1")
    suspend fun permanentlyDeleteApiKey(id: Int)

    @Query("DELETE FROM api_keys WHERE isDeleted = 1")
    suspend fun emptyApiKeyTrash()

    @Query("DELETE FROM api_keys WHERE isDeleted = 1 AND deletedAt < :cutoffTimestamp")
    suspend fun purgeOldDeletedApiKeys(cutoffTimestamp: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApiKey(entry: ApiKeyEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApiKeys(entries: List<ApiKeyEntry>)

    @Update
    suspend fun updateApiKey(entry: ApiKeyEntry)

    @Delete
    suspend fun deleteApiKey(entry: ApiKeyEntry)

    @Query("DELETE FROM api_keys WHERE id = :id")
    suspend fun deleteApiKeyById(id: Int)

    @Query("DELETE FROM api_keys")
    suspend fun clearTable()

    @Query("UPDATE api_keys SET categoryId = null WHERE categoryId = :categoryId")
    suspend fun clearCategoryRef(categoryId: Int)
}
