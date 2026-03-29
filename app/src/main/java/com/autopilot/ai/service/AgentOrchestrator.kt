package com.autopilot.ai.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.autopilot.ai.data.model.AgentTask
import com.autopilot.ai.data.model.ChatMessage
import com.autopilot.ai.data.model.ChatRequest
import com.autopilot.ai.data.model.ConversationMessage
import com.autopilot.ai.data.model.ImageContentBlock
import com.autopilot.ai.data.model.ModelParams
import com.autopilot.ai.data.model.TaskStatus
import com.autopilot.ai.data.model.TextContentBlock
import com.autopilot.ai.data.remote.RetrofitClient
import com.autopilot.ai.data.repository.ApiKeyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Agent orchestrator modeled after ZeroTap's task planning and execution.
 *
 * Flow:
 * 1. User gives a command
 * 2. If it's a simple "open app" command → direct launch, no AI needed
 * 3. Otherwise → Screenshot + screen text → send to Main Agent
 * 4. Main Agent plans subtasks (or does it in one shot)
 * 5. Each subtask executed by a SubAgent loop:
 *    a) Screenshot + read screen
 *    b) Send to AI with subtask context
 *    c) AI returns 1-3 actions
 *    d) Execute actions (wait for gesture completion)
 *    e) Wait for screen to settle
 *    f) Screenshot again → check if subtask is done
 *    g) If not done → repeat from (b)
 *    h) If done → next subtask
 */
class AgentOrchestrator(
    private val context: Context,
    private val repository: ApiKeyRepository
) {
    companion object {
        private const val TAG = "AgentOrch"
        private const val MAX_SUBAGENT_ITERATIONS = 20
        private const val SCREEN_SETTLE_MS = 1000L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentJob: Job? = null

    // ── Observable state ──
    private val _messages = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _currentTasks = MutableStateFlow<List<AgentTask>>(emptyList())
    val currentTasks = _currentTasks.asStateFlow()

    private var _inputText = MutableStateFlow("")
    val inputText = _inputText.asStateFlow()

    fun setInputText(text: String) { _inputText.value = text }

    /* ════════════════════════════════════════════════════════
     *  MAIN ENTRY POINT
     * ════════════════════════════════════════════════════════ */

    fun processCommand(command: String) {
        currentJob?.cancel()
        currentJob = scope.launch(Dispatchers.IO) {
            _isRunning.value = true
            _inputText.value = ""
            addMessage("user", command)

            try {
                // Check if simple open-app command
                val appName = extractOpenAppCommand(command)
                if (appName != null) {
                    handleOpenApp(appName)
                } else {
                    // All other tasks → screenshot first, then AI decides
                    handleSmartTask(command)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Task error", e)
                addMessage("system", "❌ Error: ${e.message}")
            } finally {
                _isRunning.value = false
                _currentTasks.value = emptyList()
            }
        }
    }

    fun stopExecution() {
        currentJob?.cancel()
        currentJob = null
        _isRunning.value = false
        _currentTasks.value = emptyList()
        addMessage("system", "⏹ Stopped.")
    }

    fun clearMessages() {
        _messages.value = emptyList()
        _currentTasks.value = emptyList()
    }

    /* ════════════════════════════════════════════════════════
     *  OPEN APP — Direct, no AI
     * ════════════════════════════════════════════════════════ */

    private val OPEN_PATTERNS = listOf(
        "^(?:open|buka|bukak|launch|start|run|jalankan|lancarkan)\\s+(.+)$"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private fun extractOpenAppCommand(cmd: String): String? {
        val trimmed = cmd.trim()
        for (pattern in OPEN_PATTERNS) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val rest = match.groupValues[1].trim()
                // Make sure it's not a complex sentence
                if (rest.split(" ").size <= 4 &&
                    !rest.contains(" and ", ignoreCase = true) &&
                    !rest.contains(" then ", ignoreCase = true) &&
                    !rest.contains(" dan ", ignoreCase = true)
                ) {
                    return rest
                }
            }
        }
        return null
    }

    private suspend fun handleOpenApp(appName: String) {
        addMessage("assistant", "🚀 Opening $appName...")
        val launched = AppLauncher.launchAppByName(context, appName)
        if (launched) {
            addMessage("system", "✅ $appName opened.")
        } else {
            addMessage("system", "⚠️ Could not find \"$appName\". Trying via AI...")
            handleSmartTask("Open the app called $appName")
        }
    }

    /* ════════════════════════════════════════════════════════
     *  SMART TASK — Screenshot first, AI decides
     * ════════════════════════════════════════════════════════ */

    private suspend fun handleSmartTask(command: String) {
        addMessage("assistant", "📸 Analyzing screen...")

        // Step 1: Take screenshot + read screen
        val screenData = captureScreenState()

        // Step 2: Ask Main Agent to plan
        val planPrompt = buildMainAgentPrompt(command, screenData)
        val planResponse = callAI(planPrompt, screenData.screenshotBase64)

        if (planResponse == null) {
            addMessage("system", "❌ No API key available or AI unreachable.")
            return
        }

        // Step 3: Parse plan — might be subtasks or direct actions
        val subtasks = parsePlan(planResponse)

        if (subtasks.isEmpty()) {
            // AI responded conversationally (no actions/subtasks)
            addMessage("assistant", cleanResponse(planResponse))
            return
        }

        // Step 4: Execute each subtask
        _currentTasks.value = subtasks
        for ((index, task) in subtasks.withIndex()) {
            val updatedTask = task.copy(status = TaskStatus.RUNNING)
            updateTask(index, updatedTask)
            addMessage("assistant", "▶ Task ${index + 1}/${subtasks.size}: ${task.description}",
                isSubAgent = true, subAgentId = index + 1)

            val success = executeSubAgentLoop(task.description, index + 1)

            updateTask(index, updatedTask.copy(
                status = if (success) TaskStatus.COMPLETED else TaskStatus.FAILED
            ))

            if (!success) {
                addMessage("system", "⚠️ Task ${index + 1} did not complete fully. Continuing...")
            }
        }

        addMessage("system", "✅ All tasks completed.")
    }

    /* ════════════════════════════════════════════════════════
     *  SUBAGENT EXECUTION LOOP — Screenshot → AI → Actions → Repeat
     * ════════════════════════════════════════════════════════ */

    private suspend fun executeSubAgentLoop(taskDescription: String, subAgentId: Int): Boolean {
        var iteration = 0

        while (iteration < MAX_SUBAGENT_ITERATIONS) {
            iteration++
            Log.d(TAG, "SubAgent #$subAgentId iteration $iteration")

            // 1. Wait for screen to settle
            delay(SCREEN_SETTLE_MS)

            // 2. Screenshot + read screen
            val screenData = captureScreenState()

            // 3. Ask SubAgent AI what to do
            val prompt = buildSubAgentPrompt(taskDescription, screenData, iteration)
            val response = callAI(prompt, screenData.screenshotBase64) ?: continue

            // 4. Parse actions from response
            val actions = parseActions(response)

            // 5. Check for [DONE]
            if (response.contains("[DONE]")) {
                val cleanMsg = cleanResponse(response)
                if (cleanMsg.isNotEmpty()) {
                    addMessage("assistant", cleanMsg, isSubAgent = true, subAgentId = subAgentId)
                }
                return true
            }

            // 6. No actions? AI might be confused
            if (actions.isEmpty()) {
                val cleanMsg = cleanResponse(response)
                if (cleanMsg.isNotEmpty()) {
                    addMessage("assistant", cleanMsg, isSubAgent = true, subAgentId = subAgentId)
                }
                // Give it one more try
                continue
            }

            // 7. Execute each action sequentially
            for (action in actions) {
                addMessage("assistant", "⚡ ${action.type}: ${action.params}",
                    isSubAgent = true, subAgentId = subAgentId)
                executeAction(action)
                delay(400) // Small delay between actions
            }
        }

        Log.w(TAG, "SubAgent #$subAgentId hit max iterations")
        return false
    }

    /* ════════════════════════════════════════════════════════
     *  SCREEN CAPTURE
     * ════════════════════════════════════════════════════════ */

    data class ScreenState(
        val screenText: String,
        val screenshotBase64: String?
    )

    private suspend fun captureScreenState(): ScreenState {
        val a11y = AutoPilotAccessibilityService.instance
        val screenText = a11y?.getScreenContent() ?: "Accessibility service not connected"

        var base64: String? = null
        val bitmap = a11y?.takeScreenshotAsync()
        if (bitmap != null) {
            base64 = bitmapToBase64(bitmap)
            bitmap.recycle()
        }

        return ScreenState(screenText, base64)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Resize to max 720p for faster AI processing
        val maxDim = 1280
        val scale = if (bitmap.width > bitmap.height) {
            maxDim.toFloat() / bitmap.width
        } else {
            maxDim.toFloat() / bitmap.height
        }.coerceAtMost(1f)

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true
            )
        } else bitmap

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    /* ════════════════════════════════════════════════════════
     *  AI CALL (with API key rotation + isolation on failure)
     * ════════════════════════════════════════════════════════ */

    private suspend fun callAI(prompt: String, imageBase64: String?): String? {
        val excludeIds = mutableListOf<Int>()
        val api = RetrofitClient.apiService

        // Try up to 3 different keys
        repeat(3) {
            val key = repository.getKeyForAgent(excludeIds) ?: return null

            try {
                // Build content parts
                val contentParts = mutableListOf<Any>()
                contentParts.add(TextContentBlock(text = prompt))

                if (imageBase64 != null) {
                    contentParts.add(ImageContentBlock(
                        base64 = "data:image/jpeg;base64,$imageBase64"
                    ))
                }

                val request = ChatRequest(
                    messages = listOf(
                        ChatMessage(role = "user", content = contentParts)
                    ),
                    params = ModelParams(maxTokens = 2048, temperature = 0.3)
                )

                val response = api.chat("Key ${key.keyValue}", request)
                val text = response.getOutputText()

                if (text.startsWith("Error:")) {
                    Log.w(TAG, "API error with key #${key.id}: $text")
                    excludeIds.add(key.id)

                    // Isolate key if it's a quota/auth error
                    if (text.contains("quota", ignoreCase = true) ||
                        text.contains("rate", ignoreCase = true) ||
                        text.contains("invalid", ignoreCase = true)
                    ) {
                        repository.isolateKey(key)
                    }
                } else {
                    return text
                }
            } catch (e: Exception) {
                Log.w(TAG, "API exception with key #${key.id}: ${e.message}")
                excludeIds.add(key.id)
            }
        }
        return null
    }

    /* ════════════════════════════════════════════════════════
     *  ACTION PARSING & EXECUTION
     * ════════════════════════════════════════════════════════ */

    data class AgentAction(val type: String, val params: String)

    private val ACTION_REGEX = Regex("""\[ACTION:(\w+)\s*([^\]]*)]""")

    private fun parseActions(response: String): List<AgentAction> {
        return ACTION_REGEX.findAll(response).map {
            AgentAction(it.groupValues[1].uppercase(), it.groupValues[2].trim())
        }.toList()
    }

    private suspend fun executeAction(action: AgentAction) {
        val a11y = AutoPilotAccessibilityService.instance
        if (a11y == null) {
            addMessage("system", "⚠️ Accessibility service not connected!")
            return
        }

        try {
            when (action.type) {
                "TAP" -> {
                    val (x, y) = parseCoords(action.params)
                    a11y.performTap(x, y)
                    delay(SCREEN_SETTLE_MS)
                }
                "LONGPRESS" -> {
                    val parts = action.params.split(",", " ").filter { it.isNotBlank() }
                    val x = parts[0].trim().toFloat()
                    val y = parts[1].trim().toFloat()
                    val dur = parts.getOrNull(2)?.trim()?.toLongOrNull() ?: 1500L
                    a11y.performLongPress(x, y, dur)
                    delay(SCREEN_SETTLE_MS)
                }
                "SWIPE" -> {
                    val nums = action.params.split(",", " ")
                        .filter { it.isNotBlank() }
                        .map { it.trim().toFloat() }
                    if (nums.size >= 4) {
                        a11y.performSwipe(nums[0], nums[1], nums[2], nums[3])
                        delay(SCREEN_SETTLE_MS)
                    }
                }
                "CLICK" -> {
                    val text = action.params.trim().removeSurrounding("\"")
                    val clicked = a11y.findAndClickText(text)
                    if (!clicked) {
                        addMessage("system", "⚠️ Could not find \"$text\" on screen")
                    }
                    delay(SCREEN_SETTLE_MS)
                }
                "LONGCLICK" -> {
                    val text = action.params.trim().removeSurrounding("\"")
                    a11y.findAndLongClickText(text)
                    delay(SCREEN_SETTLE_MS)
                }
                "TYPE" -> {
                    val text = action.params.trim().removeSurrounding("\"")
                    a11y.setTextInField(text)
                    delay(500)
                }
                "SUBMIT" -> {
                    a11y.submitText()
                    delay(SCREEN_SETTLE_MS)
                }
                "BACK" -> {
                    a11y.performBack()
                    delay(SCREEN_SETTLE_MS)
                }
                "HOME" -> {
                    a11y.performHome()
                    delay(SCREEN_SETTLE_MS)
                }
                "RECENTS" -> {
                    a11y.performRecents()
                    delay(SCREEN_SETTLE_MS)
                }
                "ALLAPPS" -> {
                    a11y.showAllApps()
                    delay(SCREEN_SETTLE_MS)
                }
                "OPEN" -> {
                    val appName = action.params.trim().removeSurrounding("\"")
                    val launched = AppLauncher.launchAppByName(context, appName)
                    if (!launched) addMessage("system", "⚠️ Could not find app: $appName")
                    delay(1500) // Give app time to launch
                }
                "SCREENSHOT" -> {
                    addMessage("system", "📸 Taking screenshot for analysis...")
                }
                "WAIT" -> {
                    val ms = action.params.trim().toLongOrNull() ?: 2000L
                    delay(ms.coerceIn(500, 10000))
                }
                else -> {
                    addMessage("system", "⚠️ Unknown action: ${action.type}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Action execution error: ${action.type}", e)
            addMessage("system", "⚠️ Action failed: ${e.message}")
        }
    }

    private fun parseCoords(params: String): Pair<Float, Float> {
        val nums = params.split(",", " ").filter { it.isNotBlank() }.map { it.trim().toFloat() }
        return Pair(nums[0], nums[1])
    }

    /* ════════════════════════════════════════════════════════
     *  PLAN PARSING
     * ════════════════════════════════════════════════════════ */

    private fun parsePlan(response: String): List<AgentTask> {
        val tasks = mutableListOf<AgentTask>()

        // Check for [SUBTASK:n] pattern
        val subtaskRegex = Regex("""\[SUBTASK:(\d+)]\s*(.+)""")
        subtaskRegex.findAll(response).forEach { match ->
            tasks.add(AgentTask(
                id = match.groupValues[1].toIntOrNull() ?: (tasks.size + 1),
                description = match.groupValues[2].trim(),
                status = TaskStatus.PENDING
            ))
        }

        // If no subtasks but has actions → single-task execution
        if (tasks.isEmpty() && ACTION_REGEX.containsMatchIn(response)) {
            tasks.add(AgentTask(
                id = 1,
                description = cleanResponse(response).take(80).ifEmpty { "Execute task" },
                status = TaskStatus.PENDING
            ))
        }

        return tasks
    }

    /* ════════════════════════════════════════════════════════
     *  PROMPTS
     * ════════════════════════════════════════════════════════ */

    private fun buildMainAgentPrompt(command: String, screen: ScreenState): String {
        return """
You are AutoPilot AI, a device control agent. The user wants you to perform a task on their Android phone.

## Your Role (Main Agent)
Analyze the user's request and the current screen state. Then either:
1. **Execute directly** — If the task is simple (1-3 steps), output [ACTION:...] commands directly, ending with [DONE] when complete.
2. **Plan subtasks** — If the task is complex, break it into numbered subtasks using [SUBTASK:N] format. Each subtask should be a clear, atomic instruction.

## Available Actions
- [ACTION:TAP x,y] — Tap at coordinates
- [ACTION:LONGPRESS x,y] — Long press at coordinates (for context menus, drag, etc.)
- [ACTION:SWIPE x1,y1,x2,y2] — Swipe/slide between two points
- [ACTION:CLICK "text"] — Click an element by its visible text
- [ACTION:LONGCLICK "text"] — Long click an element by its visible text
- [ACTION:TYPE "text"] — Type text into the focused field
- [ACTION:SUBMIT] — Press Enter/Send/Go on the keyboard
- [ACTION:BACK] — Press back button
- [ACTION:HOME] — Press home button
- [ACTION:RECENTS] — Open recent apps
- [ACTION:ALLAPPS] — Open app drawer
- [ACTION:OPEN "app name"] — Launch an app by name
- [ACTION:WAIT ms] — Wait for specified milliseconds
- [DONE] — Task is complete

## Current Screen
```
${screen.screenText}
```

## User Command
$command

Respond with either direct actions or a subtask plan. Be precise with coordinates from the screen data.
""".trimIndent()
    }

    private fun buildSubAgentPrompt(taskDescription: String, screen: ScreenState, iteration: Int): String {
        return """
You are a SubAgent executing a specific task on an Android device. You can see the current screen.

## Your Task
$taskDescription

## Rules
1. Look at the screen content and screenshot carefully
2. Output 1-3 [ACTION:...] commands to make progress on the task
3. After your actions, the system will take a new screenshot for you to verify
4. When the task is CONFIRMED complete (you can see the result on screen), output [DONE]
5. Do NOT output [DONE] unless you are certain the task is finished
6. If you're stuck, try alternative approaches (e.g., use coordinates instead of text click)
7. This is iteration $iteration of max $MAX_SUBAGENT_ITERATIONS

## Available Actions
- [ACTION:TAP x,y] — Tap at coordinates
- [ACTION:LONGPRESS x,y] — Long press
- [ACTION:SWIPE x1,y1,x2,y2] — Swipe/slide
- [ACTION:CLICK "text"] — Click by text
- [ACTION:LONGCLICK "text"] — Long click by text
- [ACTION:TYPE "text"] — Type into focused field
- [ACTION:SUBMIT] — Press Enter/Send
- [ACTION:BACK] — Back button
- [ACTION:HOME] — Home button
- [ACTION:OPEN "app name"] — Launch app
- [ACTION:WAIT ms] — Wait
- [DONE] — Task complete

## Current Screen Content
```
${screen.screenText}
```

What actions should be performed next?
""".trimIndent()
    }

    /* ════════════════════════════════════════════════════════
     *  MESSAGE HELPERS
     * ════════════════════════════════════════════════════════ */

    private fun addMessage(
        role: String,
        content: String,
        isSubAgent: Boolean = false,
        subAgentId: Int? = null
    ) {
        val msg = ConversationMessage(
            role = role,
            content = content,
            isSubAgent = isSubAgent,
            subAgentId = subAgentId
        )
        _messages.value = _messages.value + msg
    }

    private fun updateTask(index: Int, task: AgentTask) {
        val list = _currentTasks.value.toMutableList()
        if (index in list.indices) {
            list[index] = task
            _currentTasks.value = list
        }
    }

    private fun cleanResponse(response: String): String {
        return response
            .replace(ACTION_REGEX, "")
            .replace(Regex("""\[SUBTASK:\d+].*"""), "")
            .replace("[DONE]", "")
            .trim()
    }
}
