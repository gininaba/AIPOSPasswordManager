package com.aipos.aipospm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_keys")
data class ApiKeyEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val serviceName: String,
    val encryptedApiKey: String,
    val iv: String,
    val notes: String = "",
    val categoryId: Int? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val customOrder: Int = 0,
    val icon: String? = null
)
