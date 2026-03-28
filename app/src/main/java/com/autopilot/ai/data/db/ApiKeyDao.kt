package com.autopilot.ai.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys WHERE isIsolated = 0 ORDER BY totalUsageCount ASC")
    fun getActiveKeys(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys WHERE isIsolated = 1 ORDER BY isolatedAt ASC")
    fun getIsolatedKeys(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys ORDER BY addedAt DESC")
    fun getAllKeys(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys WHERE isIsolated = 0 AND id NOT IN (:excludeIds) ORDER BY totalUsageCount ASC LIMIT 1")
    suspend fun getNextAvailableKey(excludeIds: List<Int>): ApiKeyEntity?

    @Query("SELECT COUNT(*) FROM api_keys WHERE isIsolated = 0")
    suspend fun getActiveKeyCount(): Int

    @Query("SELECT COUNT(*) FROM api_keys WHERE keyValue = :key")
    suspend fun keyExists(key: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKey(key: ApiKeyEntity): Long

    @Update
    suspend fun updateKey(key: ApiKeyEntity)

    @Query("UPDATE api_keys SET isIsolated = 1, isolatedAt = :time, isolatedMonth = :month, isolatedYear = :year WHERE id = :keyId")
    suspend fun isolateKey(keyId: Int, time: Long, month: Int, year: Int)

    @Query("UPDATE api_keys SET isIsolated = 0, isolatedAt = null, isolatedMonth = null, isolatedYear = null WHERE isIsolated = 1 AND (isolatedMonth != :currentMonth OR isolatedYear != :currentYear)")
    suspend fun restoreExpiredIsolatedKeys(currentMonth: Int, currentYear: Int)

    @Query("UPDATE api_keys SET totalUsageCount = totalUsageCount + 1 WHERE id = :keyId")
    suspend fun incrementUsage(keyId: Int)

    @Query("DELETE FROM api_keys WHERE id = :keyId")
    suspend fun deleteKey(keyId: Int)
}
