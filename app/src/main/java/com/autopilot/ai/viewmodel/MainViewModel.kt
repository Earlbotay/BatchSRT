package com.autopilot.ai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autopilot.ai.App
import com.autopilot.ai.data.model.ConversationMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    private val repository = app.repository
    val orchestrator = app.orchestrator

    val activeKeys = repository.activeKeys
    val isolatedKeys = repository.isolatedKeys
    val allKeys = repository.allKeys

    val messages: StateFlow<List<ConversationMessage>> = orchestrator.messages
    val isRunning = orchestrator.isRunning
    val currentTasks = orchestrator.currentTasks

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    fun updateInput(text: String) {
        _inputText.value = text
    }

    fun sendCommand() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return
        _inputText.value = ""
        viewModelScope.launch {
            orchestrator.processCommand(text)
        }
    }

    fun stopExecution() {
        orchestrator.requestStop()
    }

    fun clearChat() {
        orchestrator.clearMessages()
    }

    fun addApiKeys(keysText: String) {
        val keys = keysText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch {
            repository.addKeys(keys)
        }
    }

    fun deleteApiKey(keyId: Int) {
        viewModelScope.launch {
            repository.deleteKey(keyId)
        }
    }

    fun restoreMonthlyKeys() {
        viewModelScope.launch {
            repository.restoreMonthlyKeys()
        }
    }
}
