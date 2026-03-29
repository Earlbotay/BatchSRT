package com.autopilot.ai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autopilot.ai.App
import com.autopilot.ai.data.db.ApiKeyEntity
import com.autopilot.ai.data.model.AgentTask
import com.autopilot.ai.data.model.ConversationMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    private val orchestrator = app.orchestrator
    private val repository = app.repository

    // ── Chat state (from orchestrator) ──
    val messages: StateFlow<List<ConversationMessage>> = orchestrator.messages
    val isRunning: StateFlow<Boolean> = orchestrator.isRunning
    val inputText: StateFlow<String> = orchestrator.inputText
    val currentTasks: StateFlow<List<AgentTask>> = orchestrator.currentTasks

    fun setInputText(text: String) = orchestrator.setInputText(text)

    fun sendCommand() {
        val text = inputText.value.trim()
        if (text.isEmpty()) return

        // Enable overlay on first message
        app.enableOverlay()

        orchestrator.processCommand(text)
    }

    fun stopExecution() = orchestrator.stopExecution()
    fun clearMessages() = orchestrator.clearMessages()

    // ── API Key management ──
    val allKeys: Flow<List<ApiKeyEntity>> = repository.allKeys
    val activeKeys: Flow<List<ApiKeyEntity>> = repository.activeKeys
    val isolatedKeys: Flow<List<ApiKeyEntity>> = repository.isolatedKeys

    /** Add multiple keys from multiline text (one key per line). */
    fun addApiKeys(keysText: String) {
        val keys = keysText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (keys.isEmpty()) return
        viewModelScope.launch {
            repository.addKeys(keys)
        }
    }

    fun deleteApiKey(keyId: Int) {
        viewModelScope.launch {
            repository.deleteKey(keyId)
        }
    }
}
