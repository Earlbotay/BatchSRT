package com.autopilot.ai.data.model

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    val messages: List<ChatMessage>,
    val params: ModelParams? = null
)

data class ChatMessage(
    val role: String,
    val content: Any  // String or List<ContentBlock>
)

data class TextContentBlock(
    val type: String = "text",
    val text: String
)

data class ImageContentBlock(
    val type: String = "image",
    val base64: String  // "data:image/png;base64,{data}"
)

data class ModelParams(
    @SerializedName("max_tokens")
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7
)

data class ChatResponse(
    val error: String?,
    val output: ChatOutput?,
    val provider: ProviderInfo? = null
) {
    fun getOutputText(): String {
        if (error != null) return "Error: $error"
        return output?.content ?: ""
    }
}

data class ChatOutput(
    val role: String?,
    val content: String?
)

data class ProviderInfo(
    val model: String?,
    val usage: UsageInfo?
)

data class UsageInfo(
    @SerializedName("input_tokens")
    val inputTokens: Int?,
    @SerializedName("output_tokens")
    val outputTokens: Int?
)

data class AgentTask(
    val id: Int,
    val description: String,
    var status: TaskStatus = TaskStatus.PENDING,
    var result: String? = null
)

enum class TaskStatus {
    PENDING, RUNNING, COMPLETED, FAILED, STOPPED
}

data class ConversationMessage(
    val role: String,
    val content: String,
    val imageBase64: String? = null,
    val isSubAgent: Boolean = false,
    val subAgentId: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)
