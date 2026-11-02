package dev.s10e.companion.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class LlmClientImpl(
    private val host: String = "127.0.0.1",
    private val port: Int = 8080,
    private val apiKeyProvider: () -> String = { "" }
) : LlmClient {

    private suspend fun kotlinx.coroutines.flow.FlowCollector<LlmEvent>.processStreamLine(
        line: String,
        state: ParseState
    ) {
        if (line.startsWith("data: ")) {
            val data = line.substring(6)
            if (data == "[DONE]") {
                flushThinkBuffer(state)
                emit(LlmEvent.Done)
                return
            }
            val json = JSONObject(data)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val delta = choices.getJSONObject(0).optJSONObject("delta") ?: JSONObject()

                if (delta.has("tool_calls")) {
                    val toolCalls = delta.getJSONArray("tool_calls")
                    if (toolCalls.length() > 0) {
                        val func = toolCalls.getJSONObject(0).getJSONObject("function")
                        val name = func.optString("name")
                        val args = func.optString("arguments")
                        emit(LlmEvent.ToolCall(name, args))
                    }
                } else if (delta.has("content") && !delta.isNull("content")) {
                    val content = delta.getString("content")
                    state.thinkBuffer += content
                    processThinkBuffer(state)
                }
            }
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<LlmEvent>.processThinkBuffer(state: ParseState) {
        while (true) {
            if (!state.inThink) {
                val startIndex = state.thinkBuffer.indexOf("<think>")
                if (startIndex != -1) {
                    state.inThink = true
                    val beforeThink = state.thinkBuffer.substring(0, startIndex)
                    if (beforeThink.isNotEmpty()) emit(LlmEvent.Token(beforeThink))
                    state.thinkBuffer = state.thinkBuffer.substring(startIndex + 7)
                } else {
                    break
                }
            } else {
                val endIndex = state.thinkBuffer.indexOf("</think>")
                if (endIndex != -1) {
                    state.inThink = false
                    state.thinkBuffer = state.thinkBuffer.substring(endIndex + 8)
                } else {
                    break
                }
            }
        }

        val hasPartialStart = state.thinkBuffer.endsWith("<") || state.thinkBuffer.endsWith("<t") || state.thinkBuffer.endsWith("<th") || state.thinkBuffer.endsWith("<thi") || state.thinkBuffer.endsWith("<thin") || state.thinkBuffer.endsWith("<think")
        val hasPartialEnd = state.thinkBuffer.endsWith("</") || state.thinkBuffer.endsWith("</t") || state.thinkBuffer.endsWith("</th") || state.thinkBuffer.endsWith("</thi") || state.thinkBuffer.endsWith("</thin") || state.thinkBuffer.endsWith("</think")

        if (!state.inThink && !hasPartialStart && !hasPartialEnd) {
            if (state.thinkBuffer.isNotEmpty()) {
                emit(LlmEvent.Token(state.thinkBuffer))
                state.thinkBuffer = ""
            }
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<LlmEvent>.flushThinkBuffer(state: ParseState) {
        if (!state.inThink && state.thinkBuffer.isNotEmpty()) {
            emit(LlmEvent.Token(state.thinkBuffer))
            state.thinkBuffer = ""
        }
    }

    override fun chat(messages: List<ChatMessage>, tools: List<ToolSpec>): Flow<LlmEvent> = flow {
        val url = URL("http://$host:$port/v1/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${apiKeyProvider()}")
            conn.doOutput = true

            val msgsArray = JSONArray()
            messages.forEach {
                val obj = JSONObject()
                obj.put("role", it.role)
                obj.put("content", it.content)
                msgsArray.put(obj)
            }

            val payload = JSONObject()
            payload.put("messages", msgsArray)
            payload.put("stream", true)
            payload.put("max_tokens", 256)

            if (tools.isNotEmpty()) {
                val toolsArray = JSONArray()
                tools.forEach {
                    val func = JSONObject()
                    func.put("name", it.name)
                    func.put("description", it.description)

                    val tool = JSONObject()
                    tool.put("type", "function")
                    tool.put("function", func)
                    toolsArray.put(tool)
                }
                payload.put("tools", toolsArray)
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray()) }

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val state = ParseState()

            while (coroutineContext.isActive) {
                val line = reader.readLine() ?: break
                processStreamLine(line, state)
            }
        } catch (e: Exception) {
            emit(LlmEvent.Failed(e.message ?: "Unknown error"))
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    // Internal state for parsing
    private class ParseState {
        var inThink = false
        var thinkBuffer = ""
    }

    // Exposed ONLY for unit tests
    fun testParseEvents(events: List<LlmEvent>): Flow<LlmEvent> = flow {
        val state = ParseState()
        for (event in events) {
            if (event is LlmEvent.Token) {
                state.thinkBuffer += event.text
                processThinkBuffer(state)
            } else if (event is LlmEvent.Done) {
                flushThinkBuffer(state)
                emit(event)
            } else {
                emit(event)
            }
        }
    }
}
