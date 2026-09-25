package com.aipos.aipospm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "passwords")
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val username: String,
    val encryptedPassword: String,
    val iv: String,
    val url: String = "",
    val notes: String = "",
    val categoryId: Int? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val encryptedTotpSecret: String? = null,
    val totpIv: String? = null,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val customOrder: Int = 0,
    val icon: String? = null
)
