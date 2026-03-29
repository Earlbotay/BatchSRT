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

    /* ═══════════════════════════════════════════════════════
     *  MAIN ENTRY POINT
     * ═══════════════════════════════════════════════════════ */

    suspend fun processCommand(userMessage: String) {
        if (_isRunning.value) return
        _isRunning.value = true
        shouldStop = false
        usedKeyIds.clear()

        addMessage(ConversationMessage(role = "user", content = userMessage))

        try {
            /* ── 1. Direct "open app" command — no screenshot needed ── */
            val appName = extractOpenAppCommand(userMessage)
            if (appName != null) {
                addMessage(ConversationMessage(role = "assistant", content = "Opening $appName..."))
                executeAction("OPEN $appName")
                delay(1000)
                addMessage(ConversationMessage(role = "assistant", content = "✅ $appName opened."))
                _isRunning.value = false
                return
            }

            /* ── 2. All other tasks: screenshot FIRST → analyze ── */
            val mainKey = getNextKey()
            if (mainKey == null) {
                addMessage(ConversationMessage(role = "assistant", content = "No API keys available. Please add API keys in Settings."))
                _isRunning.value = false
                return
            }

            val service = AutoPilotAccessibilityService.instance
            val screenshot = service?.takeScreenshotAsync()
            val screenText = service?.getScreenContent() ?: "Accessibility service not available"

            addMessage(ConversationMessage(role = "system", content = "📸 Screenshot captured, analyzing..."))

            /* ── 3. Main Agent: analyze & plan ── */
            val planMessages = buildMainAgentPrompt(userMessage, screenText, screenshot)
            val planResponse = callApi(mainKey, planMessages)

            if (planResponse == null) {
                addMessage(ConversationMessage(role = "assistant", content = "Failed to get response from AI. Check your API keys."))
                _isRunning.value = false
                return
            }

            val responseText = planResponse.getOutputText()

            /* ── 4. Main Agent decides: direct actions or subtasks ── */
            if (responseText.contains("[SUBTASKS]")) {
                val tasks = parseSubTasks(responseText)
                if (tasks.isNotEmpty()) {
                    _currentTasks.value = tasks
                    addMessage(ConversationMessage(
                        role = "assistant",
                        content = "🧠 Main Agent: Breaking into ${tasks.size} sub-tasks...\n" +
                            tasks.joinToString("\n") { "${it.id}. ${it.description}" }
                    ))
                    executeSubAgentsSequentially(tasks, userMessage)
                } else {
                    executeDirectActions(mainKey, userMessage, responseText)
                }
            } else {
                executeDirectActions(mainKey, userMessage, responseText)
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

    /* ═══════════════════════════════════════════════════════
     *  OPEN APP DETECTION — direct action, no AI needed
     * ═══════════════════════════════════════════════════════ */

    private fun extractOpenAppCommand(message: String): String? {
        val lower = message.lowercase().trim()
        val pattern = Regex(
            "^(?:open|buka|bukak|launch|start|run|jalankan)\\s+(.+)",
            RegexOption.IGNORE_CASE
        )
        val match = pattern.find(lower) ?: return null
        val appName = match.groupValues[1].trim()

        // Only treat as "open app" if it's a simple app name, not a complex instruction
        if (appName.contains(" and ") || appName.contains(" dan ") ||
            appName.contains(" then ") || appName.contains(" pastu ") ||
            appName.contains(" lepas ") || appName.contains(",") ||
            appName.split(" ").size > 4
        ) return null

        // Return original-case app name from the original message
        return message.trim().substringAfter(" ").trim()
    }

    /* ═══════════════════════════════════════════════════════
     *  DIRECT ACTION EXECUTION (simple tasks)
     * ═══════════════════════════════════════════════════════ */

    private suspend fun executeDirectActions(
        key: ApiKeyEntity, userMessage: String, initialResponse: String
    ) {
        addMessage(ConversationMessage(role = "assistant", content = initialResponse))

        val actions = parseActions(initialResponse)
        for (action in actions) {
            if (shouldStop || !coroutineContext.isActive) break
            executeAction(action)
            delay(300)

            if (needsScreenCheck(action)) {
                delay(800)
                val shouldContinue = verifyAndContinue(key, userMessage)
                if (!shouldContinue) break
            }
        }
    }

    private fun needsScreenCheck(action: String): Boolean {
        val cmd = action.trim().split(" ", limit = 2).firstOrNull()?.uppercase() ?: ""
        return cmd in listOf("TAP", "CLICK", "SWIPE", "OPEN", "BACK", "HOME", "TYPE", "LONGPRESS")
    }

    /* ═══════════════════════════════════════════════════════
     *  VERIFY & CONTINUE LOOP (screenshot after each action)
     * ═══════════════════════════════════════════════════════ */

    private suspend fun verifyAndContinue(
        key: ApiKeyEntity, originalCommand: String
    ): Boolean {
        val service = AutoPilotAccessibilityService.instance ?: return false
        val screenshot = service.takeScreenshotAsync()
        val screenText = service.getScreenContent()

        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage(role = "system", content = MAIN_AGENT_PROMPT))

        val promptText = "I just performed an action for: '$originalCommand'\n\n" +
            "Screen text: $screenText\n\n" +
            "Look at the screenshot. Is the task complete?\n" +
            "If more actions needed: respond with [ACTION:...] tags.\n" +
            "If done: respond with [DONE] and a brief summary."

        if (screenshot != null) {
            messages.add(ChatMessage(
                role = "user",
                content = listOf(
                    ImageContentBlock(base64 = bitmapToDataUrl(screenshot)),
                    TextContentBlock(text = promptText)
                )
            ))
        } else {
            messages.add(ChatMessage(role = "user", content = promptText))
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

    /* ═══════════════════════════════════════════════════════
     *  SUBAGENT SEQUENTIAL EXECUTION
     *  Main Agent planned the tasks → SubAgents execute one-by-one
     * ═══════════════════════════════════════════════════════ */

    private suspend fun executeSubAgentsSequentially(
        tasks: List<AgentTask>, originalCommand: String
    ) {
        for (task in tasks) {
            if (shouldStop || !coroutineContext.isActive) {
                task.status = TaskStatus.STOPPED
                _currentTasks.value = tasks.toList()
                addMessage(ConversationMessage(
                    role = "assistant",
                    content = "⏹ Stopped at task ${task.id}: ${task.description}"
                ))
                break
            }

            task.status = TaskStatus.RUNNING
            _currentTasks.value = tasks.toList()

            addMessage(ConversationMessage(
                role = "assistant",
                content = "🤖 SubAgent #${task.id} starting: ${task.description}",
                isSubAgent = true, subAgentId = task.id
            ))

            try {
                executeSubAgentLoop(task, originalCommand)
            } catch (e: Exception) {
                task.status = TaskStatus.FAILED
                task.result = "Error: ${e.message}"
                Log.e(TAG, "SubAgent ${task.id} error", e)
                addMessage(ConversationMessage(
                    role = "assistant",
                    content = "❌ SubAgent #${task.id} failed: ${e.message}",
                    isSubAgent = true, subAgentId = task.id
                ))
            }

            _currentTasks.value = tasks.toList()
        }

        val completed = tasks.count { it.status == TaskStatus.COMPLETED }
        addMessage(ConversationMessage(
            role = "assistant",
            content = "✅ Done. $completed/${tasks.size} sub-tasks completed."
        ))
    }

    /* ═══════════════════════════════════════════════════════
     *  SUBAGENT LOOP: screenshot → analyze → execute → repeat
     *  Each SubAgent independently observes the screen before every cycle
     * ═══════════════════════════════════════════════════════ */

    private suspend fun executeSubAgentLoop(task: AgentTask, originalCommand: String) {
        val maxIterations = 15
        var iteration = 0

        while (iteration < maxIterations && !shouldStop && coroutineContext.isActive) {
            iteration++

            // 1. SubAgent screenshots current screen state FIRST
            val service = AutoPilotAccessibilityService.instance
            if (service == null) {
                task.status = TaskStatus.FAILED
                task.result = "Accessibility service not available"
                break
            }

            val screenshot = service.takeScreenshotAsync()
            val screenText = service.getScreenContent()

            // 2. Get API key for this SubAgent call
            val subKey = getNextKey()
            if (subKey == null) {
                addMessage(ConversationMessage(
                    role = "assistant",
                    content = "⚠ SubAgent #${task.id}: No API keys available",
                    isSubAgent = true, subAgentId = task.id
                ))
                task.status = TaskStatus.FAILED
                break
            }

            // 3. SubAgent AI analyzes screenshot and decides actions
            val subMessages = buildSubAgentPrompt(
                task.description, originalCommand, screenText, screenshot, iteration
            )
            val response = callApi(subKey, subMessages)

            if (response == null) {
                task.status = TaskStatus.FAILED
                task.result = "API call failed at iteration $iteration"
                break
            }

            val output = response.getOutputText()

            // 4. Check if SubAgent says task is done
            if (output.contains("[DONE]")) {
                val summary = output.replace(Regex("\\[DONE]"), "").trim()
                task.status = TaskStatus.COMPLETED
                task.result = summary
                addMessage(ConversationMessage(
                    role = "assistant",
                    content = "✅ SubAgent #${task.id} completed: ${summary.take(200)}",
                    isSubAgent = true, subAgentId = task.id
                ))
                break
            }

            // 5. Execute actions returned by SubAgent
            val actions = parseActions(output)
            if (actions.isEmpty()) {
                // No actions and no [DONE] — SubAgent returned text only
                addMessage(ConversationMessage(
                    role = "assistant",
                    content = "🤖 SubAgent #${task.id}: ${output.take(200)}",
                    isSubAgent = true, subAgentId = task.id
                ))
                task.status = TaskStatus.COMPLETED
                task.result = output
                break
            }

            for (action in actions) {
                if (shouldStop) break
                addMessage(ConversationMessage(
                    role = "system",
                    content = "SubAgent #${task.id} → [ACTION:$action]"
                ))
                executeAction(action)
                delay(300)
            }

            // 6. Wait for screen to update before next screenshot cycle
            delay(800)
        }

        // Max iterations reached
        if (iteration >= maxIterations && task.status != TaskStatus.COMPLETED) {
            task.status = TaskStatus.COMPLETED
            task.result = "Completed after $maxIterations iterations"
            addMessage(ConversationMessage(
                role = "assistant",
                content = "🤖 SubAgent #${task.id}: Max iterations reached, moving on.",
                isSubAgent = true, subAgentId = task.id
            ))
        }
    }

    /* ═══════════════════════════════════════════════════════
     *  API CALL WITH KEY ROTATION
     * ═══════════════════════════════════════════════════════ */

    private suspend fun callApi(
        key: ApiKeyEntity, messages: List<ChatMessage>
    ): ChatResponse? {
        return withContext(Dispatchers.IO) {
            var currentKey = key
            var attempts = 0

            while (attempts < 5) {
                try {
                    val request = ChatRequest(
                        messages = messages,
                        params = ModelParams(maxTokens = 4096, temperature = 0.7)
                    )
                    val response = api.chat("Key ${currentKey.keyValue}", request)

                    if (response.error != null) {
                        val err = response.error.lowercase()
                        if (err.contains("credit") || err.contains("quota") ||
                            err.contains("limit") || err.contains("unauthorized") ||
                            err.contains("invalid") || err.contains("402") ||
                            err.contains("429")
                        ) {
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

    /* ═══════════════════════════════════════════════════════
     *  ACTION EXECUTION
     * ═══════════════════════════════════════════════════════ */

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
                        addMessage(ConversationMessage(
                            role = "system",
                            content = "[Screenshot captured]",
                            imageBase64 = bitmapToDataUrl(bmp)
                        ))
                    }
                }
                "WAIT" -> delay(args.toLongOrNull() ?: 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Action error: $action", e)
        }
    }

    /* ═══════════════════════════════════════════════════════
     *  PARSING HELPERS
     * ═══════════════════════════════════════════════════════ */

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
                AgentTask(
                    id = i + 1,
                    description = line.replace(Regex("^\\d+\\.?\\s*"), "").trim()
                )
            }
            .filter { it.description.isNotEmpty() }
    }

    /* ═══════════════════════════════════════════════════════
     *  MESSAGES
     * ═══════════════════════════════════════════════════════ */

    private fun addMessage(msg: ConversationMessage) {
        _messages.value = _messages.value + msg
    }

    fun clearMessages() {
        _messages.value = emptyList()
        _currentTasks.value = emptyList()
    }

    /* ═══════════════════════════════════════════════════════
     *  PROMPTS — Main Agent & SubAgent
     * ═══════════════════════════════════════════════════════ */

    private fun buildMainAgentPrompt(
        userCommand: String, screenText: String, screenshot: Bitmap?
    ): List<ChatMessage> {
        val msgs = mutableListOf<ChatMessage>()
        msgs.add(ChatMessage(role = "system", content = MAIN_AGENT_PROMPT))

        val text = "Current screen text:\n$screenText\n\n" +
            "User command: $userCommand\n\n" +
            "Analyze the screen and the user's request.\n" +
            "- If simple (1-3 actions): respond with [ACTION:...] tags directly.\n" +
            "- If complex (multiple steps): plan with [SUBTASKS]\\n1. step\\n2. step\\n[/SUBTASKS].\n" +
            "Each subtask should be a clear, achievable goal for a SubAgent."

        if (screenshot != null) {
            msgs.add(ChatMessage(role = "user", content = listOf(
                ImageContentBlock(base64 = bitmapToDataUrl(screenshot)),
                TextContentBlock(text = text)
            )))
        } else {
            msgs.add(ChatMessage(role = "user", content = text))
        }
        return msgs
    }

    private fun buildSubAgentPrompt(
        task: String, originalCommand: String, screenText: String,
        screenshot: Bitmap?, iteration: Int
    ): List<ChatMessage> {
        val msgs = mutableListOf<ChatMessage>()
        msgs.add(ChatMessage(role = "system", content = SUBAGENT_PROMPT))

        val text = "Your assigned task: $task\n" +
            "Original user request: $originalCommand\n" +
            "Iteration: $iteration of $MAX_SUBAGENT_ITERATIONS\n\n" +
            "Current screen text:\n$screenText\n\n" +
            "Analyze the screenshot and screen text carefully.\n" +
            "Determine the NEXT actions to perform for your task.\n" +
            "Respond with [ACTION:...] tags (1-3 actions max per response).\n" +
            "When your task is FULLY COMPLETE, respond with [DONE] and a brief summary."

        if (screenshot != null) {
            msgs.add(ChatMessage(role = "user", content = listOf(
                ImageContentBlock(base64 = bitmapToDataUrl(screenshot)),
                TextContentBlock(text = text)
            )))
        } else {
            msgs.add(ChatMessage(role = "user", content = text))
        }
        return msgs
    }

    companion object {
        private const val TAG = "AgentOrchestrator"
        private const val MAX_SUBAGENT_ITERATIONS = 15

        private const val MAIN_AGENT_PROMPT =
            "You are AutoPilot AI Main Agent. You analyze the user's request and the current screen " +
            "state to plan actions.\n\n" +
            "Available actions:\n" +
            "[ACTION:TAP x,y] - Tap at screen coordinates\n" +
            "[ACTION:LONGPRESS x,y] - Long press (context menus, drag, copy-paste)\n" +
            "[ACTION:SWIPE x1,y1,x2,y2] - Swipe/slide from one point to another\n" +
            "[ACTION:TYPE text] - Type text into the currently focused field\n" +
            "[ACTION:CLICK text] - Click an element by its visible text (preferred over TAP)\n" +
            "[ACTION:BACK] - Press back button\n" +
            "[ACTION:HOME] - Press home button\n" +
            "[ACTION:RECENTS] - Open recent apps\n" +
            "[ACTION:OPEN app] - Open an app by name\n" +
            "[ACTION:SEARCH query] - Search the web\n" +
            "[ACTION:SCRAPE url] - Scrape a webpage\n" +
            "[ACTION:SCREENSHOT] - Take a screenshot for analysis\n" +
            "[ACTION:WAIT ms] - Wait for specified milliseconds\n\n" +
            "Rules:\n" +
            "- Use CLICK over TAP when text is visible on screen\n" +
            "- Use LONGPRESS for context menus, copy-paste, drag operations\n" +
            "- Use SWIPE for scrolling, swiping between pages, pull down notifications\n" +
            "- For complex multi-step tasks: break into subtasks with [SUBTASKS]\\n1. task\\n[/SUBTASKS]\n" +
            "- Each subtask must be a clear single goal that a SubAgent can execute independently\n" +
            "- For simple tasks (1-3 actions): use [ACTION:...] directly\n" +
            "- When complete: [DONE] + brief summary"

        private const val SUBAGENT_PROMPT =
            "You are AutoPilot AI SubAgent. You execute ONE specific task by analyzing the current " +
            "screen and performing actions.\n\n" +
            "You are called in a LOOP. Each iteration:\n" +
            "1. You receive a FRESH screenshot and screen text of the current state\n" +
            "2. You analyze what you see on screen RIGHT NOW\n" +
            "3. You respond with [ACTION:...] tags for the NEXT 1-3 actions\n" +
            "4. Actions are executed, then you get a new screenshot in the next iteration\n\n" +
            "Available actions:\n" +
            "[ACTION:TAP x,y] | [ACTION:LONGPRESS x,y] | [ACTION:SWIPE x1,y1,x2,y2] | " +
            "[ACTION:TYPE text] | [ACTION:CLICK text] | [ACTION:BACK] | [ACTION:HOME] | " +
            "[ACTION:RECENTS] | [ACTION:OPEN app] | [ACTION:SEARCH query] | " +
            "[ACTION:SCRAPE url] | [ACTION:SCREENSHOT] | [ACTION:WAIT ms]\n\n" +
            "Rules:\n" +
            "- ALWAYS look at the screenshot to determine exact coordinates\n" +
            "- Use CLICK over TAP when text is visible\n" +
            "- Only perform 1-3 actions per response — you'll get a new screenshot after\n" +
            "- When your specific task is FULLY COMPLETE: respond with [DONE] and a summary\n" +
            "- Do NOT say [DONE] until you can confirm the task succeeded from the screenshot"
    }
}
