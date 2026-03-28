package com.autopilot.ai.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "api_keys",
    indices = [Index(value = ["keyValue"], unique = true)]
)
data class ApiKeyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val keyValue: String,
    val addedAt: Long = System.currentTimeMillis(),
    val isIsolated: Boolean = false,
    val isolatedAt: Long? = null,
    val isolatedMonth: Int? = null,
    val isolatedYear: Int? = null,
    val totalUsageCount: Int = 0
)
