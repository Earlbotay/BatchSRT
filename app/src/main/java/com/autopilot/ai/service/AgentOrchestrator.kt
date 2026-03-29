package com.autopilot.ai.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.autopilot.ai.data.db.ApiKeyEntity
import com.autopilot.ai.data.model.AgentTask
import com.autopilot.ai.data.model.ChatMessage
import com.autopilot.ai.data.model.ChatRequest
import com.autopilot.ai.data.model.ChatResponse
import com.autopilot.ai.data.model.ConversationMessage
import com.autopilot.ai.data.model.ImageContentBlock
import com.autopilot.ai.data.model.ModelParams
import com.autopilot.ai.data.model.TaskStatus
import com.autopilot.ai.data.model.TextContentBlock
import com.autopilot.ai.data.remote.RetrofitClient
import com.autopilot.ai.data.repository.ApiKeyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

class AgentOrchestrator(
    private val context: Context,
    private val repository: ApiKeyRepository
) {
    private val api = RetrofitClient.apiService

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _messages = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val messages: StateFlow<List<ConversationMessage>> = _messages.asStateFlow()

    private val _currentTasks = MutableStateFlow<List<AgentTask>>(emptyList())
    val currentTasks: StateFlow<List<AgentTask>> = _currentTasks.asStateFlow()

    private val usedKeyIds = mutableListOf<Int>()

    @Volatile
    var shouldStop = false
        private set

    fun requestStop() {
        shouldStop = true
    }

    private fun bitmapToDataUrl(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 85, stream)
        val bytes = stream.toByteArray()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/png;base64,$b64"
    }

    suspend fun processCommand(userMessage: String) {
        if (_isRunning.value) return
        _isRunning.value = true
        shouldStop = false
        usedKeyIds.clear()

        addMessage(ConversationMessage(role = "user", content = userMessage))

        try {
            val mainKey = getNextKey()
            if (mainKey == null) {
                addMessage(ConversationMessage(role = "assistant", content = "No API keys available. Please add API keys in Settings."))
                _isRunning.value = false
                return
            }

            val service = AutoPilotAccessibilityService.instance
            val screenshot = service?.takeScreenshotAsync()
            val screenText = service?.getScreenContent() ?: "Accessibility service not available"

            val analysisMessages = buildAnalysisPrompt(userMessage, screenText, screenshot)
            val analysisResponse = callApi(mainKey, analysisMessages)

            if (analysisResponse == null) {
                addMessage(ConversationMessage(role = "assistant", content = "Failed to get response from AI. Check your API keys."))
                _isRunning.value = false
                return
            }

            val responseText = analysisResponse.getOutputText()

            if (responseText.contains("[SUBTASKS]")) {
                val tasks = parseSubTasks(responseText)
                if (tasks.isNotEmpty()) {
                    _currentTasks.value = tasks
                    addMessage(ConversationMessage(
                        role = "assistant",
                        content = "Breaking this into ${tasks.size} sub-tasks..."
                    ))
                    executeSubTasks(tasks, userMessage)
                } else {
                    executeDirectly(mainKey, userMessage, responseText)
                }
            } else {
                executeDirectly(mainKey, userMessage, responseText)
            }
        } catch (e: CancellationException) {
            addMessage(ConversationMessage(role = "assistant", content = "Task cancelled."))
        } catch (e: Exception) {
            Log.e(TAG, "Error processing command", e)
            addMessage(ConversationMessage(role = "assistant", content = "Error: ${e.message}"))
        } finally {
            _isRunning.value = false
            shouldStop = false
        }
    }

    private suspend fun executeDirectly(key: ApiKeyEntity, userMessage: String, initialResponse: String) {
        addMessage(ConversationMessage(role = "assistant", content = initialResponse))

        val actions = parseActions(initialResponse)
        for (action in actions) {
            if (shouldStop || !coroutineContext.isActive) break
            executeAction(action)
            delay(300)

            if (needsScreenCheck(action)) {
                delay(800)
                val shouldContinue = checkScreenAndContinue(key, userMessage)
                if (!shouldContinue) break
            }
        }
    }

    private fun needsScreenCheck(action: String): Boolean {
        val cmd = action.trim().split(" ", limit = 2).firstOrNull()?.uppercase() ?: ""
        return cmd in listOf("TAP", "CLICK", "SWIPE", "OPEN", "BACK", "HOME", "TYPE")
    }

    private suspend fun checkScreenAndContinue(key: ApiKeyEntity, originalCommand: String): Boolean {
        val service = AutoPilotAccessibilityService.instance ?: return false
        val screenshot = service.takeScreenshotAsync()
        val screenText = service.getScreenContent()

        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage(role = "system", content = SYSTEM_PROMPT))

        if (screenshot != null) {
            val dataUrl = bitmapToDataUrl(screenshot)
            messages.add(ChatMessage(
                role = "user",
                content = listOf(
                    ImageContentBlock(base64 = dataUrl),
                    TextContentBlock(text = "I just performed an action for: '$originalCommand'\n\n" +
                        "Screen text: $screenText\n\n" +
                        "Look at the screenshot. Is the task complete?\n" +
                        "If more actions needed: respond with [ACTION:...] tags.\n" +
                        "If done: respond with [DONE] and a brief summary.")
                )
            ))
        } else {
            messages.add(ChatMessage(
                role = "user",
                content = "Action done for: '$originalCommand'\nScreen: $screenText\n" +
                    "Complete? If not, use [ACTION:...]. If done, say [DONE]."
            ))
        }

        val response = callApi(key, messages) ?: return false
        val output = response.getOutputText()

        if (output.contains("[DONE]")) {
            val summary = output.replace("[DONE]", "").trim()
            if (summary.isNotEmpty()) {
                addMessage(ConversationMessage(role = "assistant", content = summary))
            }
            return false
        }

        val newActions = parseActions(output)
        if (newActions.isEmpty()) return false

        for (action in newActions) {
            if (shouldStop) break
            executeAction(action)
            delay(300)
        }
        return true
    }

    private suspend fun executeSubTasks(tasks: List<AgentTask>, originalCommand: String) {
        for (task in tasks) {
            if (shouldStop || !coroutineContext.isActive) {
                task.status = TaskStatus.STOPPED
                _currentTasks.value = tasks.toList()
                addMessage(ConversationMessage(role = "assistant", content = "Stopped at task ${task.id}: ${task.description}"))
                break
            }

            task.status = TaskStatus.RUNNING
            _currentTasks.value = tasks.toList()

            val subKey = getNextKey()
            if (subKey == null) {
                addMessage(ConversationMessage(role = "assistant", content = "No API keys for sub-task ${task.id}.", isSubAgent = true, subAgentId = task.id))
                task.status = TaskStatus.FAILED
                _currentTasks.value = tasks.toList()
                continue
            }

            addMessage(ConversationMessage(role = "assistant", content = "SubAgent #${task.id}: ${task.description}", isSubAgent = true, subAgentId = task.id))

            try {
                val service = AutoPilotAccessibilityService.instance
                val screenshot = service?.takeScreenshotAsync()
                val screenText = service?.getScreenContent() ?: "No accessibility"

                val subMessages = buildSubAgentPrompt(task.description, originalCommand, screenText, screenshot)
                val response = callApi(subKey, subMessages)

                if (response != null) {
                    val output = response.getOutputText()
                    task.result = output
                    task.status = TaskStatus.COMPLETED

                    addMessage(ConversationMessage(role = "assistant", content = "SubAgent #${task.id} done: ${output.take(200)}", isSubAgent = true, subAgentId = task.id))

                    val actions = parseActions(output)
                    for (action in actions) {
                        if (shouldStop) break
                        executeAction(action)
                        delay(300)
                    }
                    if (actions.isNotEmpty()) delay(800)
                } else {
                    task.status = TaskStatus.FAILED
                    addMessage(ConversationMessage(role = "assistant", content = "SubAgent #${task.id} failed.", isSubAgent = true, subAgentId = task.id))
                }
            } catch (e: Exception) {
                task.status = TaskStatus.FAILED
                task.result = "Error: ${e.message}"
                Log.e(TAG, "SubAgent ${task.id} error", e)
            }
            _currentTasks.value = tasks.toList()
        }

        val completed = tasks.count { it.status == TaskStatus.COMPLETED }
        addMessage(ConversationMessage(role = "assistant", content = "Done. $completed/${tasks.size} sub-tasks completed."))
    }

    private suspend fun callApi(key: ApiKeyEntity, messages: List<ChatMessage>): ChatResponse? {
        return withContext(Dispatchers.IO) {
            var currentKey = key
            var attempts = 0

            while (attempts < 5) {
                try {
                    val request = ChatRequest(messages = messages, params = ModelParams(maxTokens = 4096, temperature = 0.7))
                    val response = api.chat("Key ${currentKey.keyValue}", request)

                    if (response.error != null) {
                        val err = response.error.lowercase()
                        if (err.contains("credit") || err.contains("quota") || err.contains("limit") ||
                            err.contains("unauthorized") || err.contains("invalid") ||
                            err.contains("402") || err.contains("429")) {
                            repository.isolateKey(currentKey)
                            val next = getNextKey() ?: return@withContext null
                            currentKey = next
                            attempts++
                            continue
                        }
                    }
                    return@withContext response
                } catch (e: retrofit2.HttpException) {
                    val code = e.code()
                    if (code in listOf(401, 402, 403, 429)) {
                        repository.isolateKey(currentKey)
                        val next = getNextKey() ?: return@withContext null
                        currentKey = next
                        attempts++
                    } else {
                        Log.e(TAG, "HTTP $code", e)
                        delay(1000)
                        attempts++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "API error", e)
                    delay(1000)
                    attempts++
                }
            }
            null
        }
    }

    private suspend fun getNextKey(): ApiKeyEntity? {
        val key = repository.getKeyForAgent(usedKeyIds)
        if (key != null) usedKeyIds.add(key.id)
        return key
    }

    private suspend fun executeAction(action: String) {
        val service = AutoPilotAccessibilityService.instance ?: return
        val parts = action.trim().split(" ", limit = 2)
        val cmd = parts.firstOrNull()?.uppercase() ?: return
        val args = if (parts.size > 1) parts[1] else ""

        try {
            when (cmd) {
                "TAP" -> {
                    val c = args.split(",").map { it.trim().toFloat() }
                    if (c.size == 2) service.performTap(c[0], c[1])
                }
                "LONGPRESS" -> {
                    val c = args.split(",").map { it.trim().toFloat() }
                    if (c.size >= 2) {
                        val duration = if (c.size >= 3) c[2].toLong() else 1500L
                        service.performLongPress(c[0], c[1], duration)
                    }
                }
                "SWIPE" -> {
                    val c = args.split(",").map { it.trim().toFloat() }
                    if (c.size == 4) service.performSwipe(c[0], c[1], c[2], c[3])
                }
                "TYPE" -> service.setTextInField(args)
                "CLICK" -> service.findAndClickText(args)
                "BACK" -> service.performBack()
                "HOME" -> service.performHome()
                "RECENTS" -> service.performRecents()
                "OPEN" -> AppLauncher.launchAppByName(context, args)
                "SEARCH" -> {
                    val result = WebScraper.searchWeb(args)
                    addMessage(ConversationMessage(role = "system", content = "Search:\n$result"))
                }
                "SCRAPE" -> {
                    val result = WebScraper.scrapeUrl(args)
                    addMessage(ConversationMessage(role = "system", content = "Scraped:\n$result"))
                }
                "SCREENSHOT" -> {
                    val bmp = service.takeScreenshotAsync()
                    if (bmp != null) {
                        addMessage(ConversationMessage(role = "system", content = "[Screenshot captured]", imageBase64 = bitmapToDataUrl(bmp)))
                    }
                }
                "WAIT" -> delay(args.toLongOrNull() ?: 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Action error: $action", e)
        }
    }

    private fun parseActions(response: String): List<String> {
        val actions = mutableListOf<String>()
        Regex("\\[ACTION:(.*?)]", RegexOption.DOT_MATCHES_ALL).findAll(response).forEach {
            actions.add(it.groupValues[1].trim())
        }
        return actions
    }

    private fun parseSubTasks(response: String): List<AgentTask> {
        val section = response.substringAfter("[SUBTASKS]").substringBefore("[/SUBTASKS]")
        return section.lines()
            .filter { it.trim().isNotEmpty() }
            .mapIndexed { i, line ->
                AgentTask(id = i + 1, description = line.replace(Regex("^\\d+\\.?\\s*"), "").trim())
            }
            .filter { it.description.isNotEmpty() }
    }

    private fun addMessage(msg: ConversationMessage) {
        _messages.value = _messages.value + msg
    }

    fun clearMessages() {
        _messages.value = emptyList()
        _currentTasks.value = emptyList()
    }

    private fun buildAnalysisPrompt(userCommand: String, screenText: String, screenshot: Bitmap?): List<ChatMessage> {
        val msgs = mutableListOf<ChatMessage>()
        msgs.add(ChatMessage(role = "system", content = SYSTEM_PROMPT))

        if (screenshot != null) {
            msgs.add(ChatMessage(role = "user", content = listOf(
                ImageContentBlock(base64 = bitmapToDataUrl(screenshot)),
                TextContentBlock(text = "Screen text: $screenText\n\nUser: $userCommand\n\n" +
                    "Analyze: simple? Use [ACTION:...]. Complex? Use [SUBTASKS]...[/SUBTASKS].\n" +
                    "Actions: TAP x,y | LONGPRESS x,y | SWIPE x1,y1,x2,y2 | TYPE text | CLICK text | BACK | HOME | RECENTS | OPEN app | SEARCH query | SCRAPE url | SCREENSHOT | WAIT ms")
            )))
        } else {
            msgs.add(ChatMessage(role = "user", content = "Screen: $screenText\n\nUser: $userCommand\n\n" +
                "Simple? [ACTION:...]. Complex? [SUBTASKS]...[/SUBTASKS].\n" +
                "Actions: TAP x,y | SWIPE x1,y1,x2,y2 | TYPE text | CLICK text | BACK | HOME | RECENTS | OPEN app | SEARCH query | SCRAPE url | SCREENSHOT | WAIT ms"))
        }
        return msgs
    }

    private fun buildSubAgentPrompt(task: String, original: String, screenText: String, screenshot: Bitmap?): List<ChatMessage> {
        val msgs = mutableListOf<ChatMessage>()
        msgs.add(ChatMessage(role = "system", content = SYSTEM_PROMPT))

        if (screenshot != null) {
            msgs.add(ChatMessage(role = "user", content = listOf(
                ImageContentBlock(base64 = bitmapToDataUrl(screenshot)),
                TextContentBlock(text = "SubAgent task: $task\nOriginal: $original\nScreen: $screenText\n\nRespond with [ACTION:...] tags.")
            )))
        } else {
            msgs.add(ChatMessage(role = "user", content = "SubAgent task: $task\nOriginal: $original\nScreen: $screenText\n\nRespond with [ACTION:...] tags."))
        }
        return msgs
    }

    companion object {
        private const val TAG = "AgentOrchestrator"
        private const val SYSTEM_PROMPT = "You are AutoPilot AI, a phone automation agent. You see screenshots and control the screen.\n\n" +
            "Actions: [ACTION:TAP x,y] [ACTION:LONGPRESS x,y] [ACTION:SWIPE x1,y1,x2,y2] [ACTION:TYPE text] [ACTION:CLICK text] " +
            "[ACTION:BACK] [ACTION:HOME] [ACTION:RECENTS] [ACTION:OPEN app] [ACTION:SEARCH query] " +
            "[ACTION:SCRAPE url] [ACTION:SCREENSHOT] [ACTION:WAIT ms]\n\n" +
            "Rules: Use CLICK over TAP when text visible. Use LONGPRESS for long-touch actions (e.g. copy-paste menus, drag). Use SCREENSHOT if unsure. " +
            "Complex tasks: [SUBTASKS]\\n1. task\\n[/SUBTASKS]. Done: [DONE] + summary."
    }
}
