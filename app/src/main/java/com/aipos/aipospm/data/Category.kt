package com.aipos.aipospm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CategoryType {
    PASSWORD,
    API_KEY
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    @ColumnInfo(defaultValue = "'PASSWORD'")
    val type: String = CategoryType.PASSWORD.name,
    val createdAt: Long = System.currentTimeMillis()
)

