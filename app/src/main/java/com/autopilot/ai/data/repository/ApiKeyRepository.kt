package com.autopilot.ai.data.repository

import com.autopilot.ai.data.db.ApiKeyDao
import com.autopilot.ai.data.db.ApiKeyEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class ApiKeyRepository(private val dao: ApiKeyDao) {

    val activeKeys: Flow<List<ApiKeyEntity>> = dao.getActiveKeys()
    val isolatedKeys: Flow<List<ApiKeyEntity>> = dao.getIsolatedKeys()
    val allKeys: Flow<List<ApiKeyEntity>> = dao.getAllKeys()

    suspend fun addKeys(keys: List<String>): Int {
        var added = 0
        for (key in keys) {
            val trimmed = key.trim()
            if (trimmed.isEmpty()) continue
            if (dao.keyExists(trimmed) > 0) continue
            val entity = ApiKeyEntity(keyValue = trimmed)
            val id = dao.insertKey(entity)
            if (id > 0) added++
        }
        return added
    }

    suspend fun getKeyForAgent(excludeIds: List<Int>): ApiKeyEntity? {
        restoreMonthlyKeys()
        val key = dao.getNextAvailableKey(excludeIds)
        if (key != null) {
            dao.incrementUsage(key.id)
        }
        return key
    }

    suspend fun isolateKey(key: ApiKeyEntity) {
        val cal = Calendar.getInstance()
        dao.isolateKey(
            keyId = key.id,
            time = System.currentTimeMillis(),
            month = cal.get(Calendar.MONTH),
            year = cal.get(Calendar.YEAR)
        )
    }

    suspend fun restoreMonthlyKeys() {
        val cal = Calendar.getInstance()
        dao.restoreExpiredIsolatedKeys(
            currentMonth = cal.get(Calendar.MONTH),
            currentYear = cal.get(Calendar.YEAR)
        )
    }

    suspend fun getActiveKeyCount(): Int = dao.getActiveKeyCount()

    suspend fun deleteKey(keyId: Int) = dao.deleteKey(keyId)
}
