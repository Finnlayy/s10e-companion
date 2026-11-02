package dev.s10e.companion.llm

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LlmClientTest {
    @Test
    fun client_parsesStreamedTokens() = runTest {
        val events = listOf(LlmEvent.Token("He"), LlmEvent.Token("llo"), LlmEvent.Done)
        val client = LlmClientImpl()
        val result = client.testParseEvents(events).toList()
        assertEquals(events, result)
    }

    @Test
    fun client_parsesToolCall() = runTest {
        val events = listOf(LlmEvent.ToolCall("my_tool", "{}"), LlmEvent.Done)
        val client = LlmClientImpl()
        val result = client.testParseEvents(events).toList()
        assertEquals(events, result)
    }

    @Test
    fun client_stripsThinkBlocks() = runTest {
        val events = listOf(
            LlmEvent.Token("Sure! "),
            LlmEvent.Token("<th"),
            LlmEvent.Token("ink>"),
            LlmEvent.Token(" I am thinking "),
            LlmEvent.Token("</think>"),
            LlmEvent.Token("Here is the answer.")
        )
        val client = LlmClientImpl()
        val result = client.testParseEvents(events).toList()
        assertEquals(
            listOf(LlmEvent.Token("Sure! "), LlmEvent.Token("Here is the answer.")),
            result
        )
    }

    @Test
    fun client_stripsThinkBlocks_sameChunk() = runTest {
        val events = listOf(
            LlmEvent.Token("Sure! <think> I am thinking </think>Here is the answer.")
        )
        val client = LlmClientImpl()
        val result = client.testParseEvents(events).toList()
        assertEquals(
            listOf(LlmEvent.Token("Sure! "), LlmEvent.Token("Here is the answer.")),
            result
        )
    }

    @Test
    fun client_serverDiesMidStream_emitsFailedNotHang() = runTest {
        val events = listOf(LlmEvent.Token("He"), LlmEvent.Failed("Server died"))
        val client = LlmClientImpl()
        val result = client.testParseEvents(events).toList()
        assertEquals(events, result)
    }
}
