package com.autopilot.ai

import android.app.Application
import com.autopilot.ai.data.db.AppDatabase
import com.autopilot.ai.data.repository.ApiKeyRepository
import com.autopilot.ai.service.AgentOrchestrator

class App : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { ApiKeyRepository(database.apiKeyDao()) }
    val orchestrator by lazy { AgentOrchestrator(this, repository) }
}
