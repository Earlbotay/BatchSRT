package com.autopilot.ai

import android.app.Application
import com.autopilot.ai.data.db.AppDatabase
import com.autopilot.ai.data.repository.ApiKeyRepository

class App : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { ApiKeyRepository(database.apiKeyDao()) }
}
