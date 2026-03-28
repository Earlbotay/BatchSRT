package com.autopilot.ai.data.remote

import com.autopilot.ai.data.model.ChatRequest
import com.autopilot.ai.data.model.ChatResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface BytezApiService {
    @POST("models/v2/anthropic/claude-sonnet-4-20250514")
    suspend fun chat(
        @Header("Authorization") apiKey: String,
        @Body request: ChatRequest
    ): ChatResponse
}
