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

    @Query("SELECT * FROM api_keys ORDER BY updatedAt DESC")
    fun getAllApiKeys(): Flow<List<ApiKeyEntry>>

    @Query("SELECT * FROM api_keys WHERE serviceName LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchApiKeys(query: String): Flow<List<ApiKeyEntry>>

    @Query("SELECT * FROM api_keys WHERE id = :id")
    fun getApiKeyById(id: Int): Flow<ApiKeyEntry?>

    @Query("SELECT * FROM api_keys WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteApiKeys(): Flow<List<ApiKeyEntry>>

    @Query("SELECT COUNT(*) FROM api_keys")
    fun getApiKeyCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApiKey(entry: ApiKeyEntry)

    @Update
    suspend fun updateApiKey(entry: ApiKeyEntry)

    @Delete
    suspend fun deleteApiKey(entry: ApiKeyEntry)

    @Query("DELETE FROM api_keys WHERE id = :id")
    suspend fun deleteApiKeyById(id: Int)

    @Query("DELETE FROM api_keys")
    suspend fun clearTable()
}
