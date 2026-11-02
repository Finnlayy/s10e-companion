package dev.s10e.companion.llm

import kotlinx.coroutines.flow.Flow

data class ChatMessage(val role: String, val content: String)
data class ToolSpec(val name: String, val description: String)

sealed interface LlmEvent {
    data class Token(val text: String) : LlmEvent
    data class ToolCall(val name: String, val argumentsJson: String) : LlmEvent
    data class Failed(val message: String) : LlmEvent
    object Done : LlmEvent
}

interface LlmClient {
    fun chat(messages: List<ChatMessage>, tools: List<ToolSpec> = emptyList()): Flow<LlmEvent>
}
