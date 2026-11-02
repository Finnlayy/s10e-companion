package dev.s10e.companion.agent

import dev.s10e.companion.core.AppMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTests {
    private fun tempLog() = java.io.File.createTempFile("audit", ".log").apply { deleteOnExit() }

    @Test
    fun dispatch_unknownTool_rejected() = runTest {
        val dispatcher = ToolDispatcher(emptyList(), confirm = { true }, allowed = { true })
        val result = dispatcher.dispatch("unknown", "{}")
        assertTrue(result is ToolResult.Error)
        assertEquals("Unbekanntes Werkzeug: unknown", (result as ToolResult.Error).message)
    }

    @Test
    fun dispatch_kidMode_rejectsEverything() = runTest {
        val dispatcher = ToolDispatcher(
            listOf(Tool("read_screen", Risk.READ, dev.s10e.companion.llm.ToolSpec("read_screen", ""), { "ok" })),
            confirm = { true },
            allowed = { false }
        )
        val result = dispatcher.dispatch("read_screen", "{}")
        assertTrue(result is ToolResult.Error)
        assertEquals("Im Kindermodus nicht erlaubt", (result as ToolResult.Error).message)
    }

    @Test
    fun dispatch_actNeedsConfirmation() = runTest {
        var confirmed = false
        val dispatcher = ToolDispatcher(
            listOf(Tool("tap", Risk.ACT, dev.s10e.companion.llm.ToolSpec("tap", ""), { "ok" })),
            confirm = { confirmed = true; true },
            allowed = { true }
        )
        val result = dispatcher.dispatch("tap", "{}")
        assertTrue(confirmed)
        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun dispatch_badJson_returnsErrorToModel() = runTest {
        val dispatcher = ToolDispatcher(
            listOf(Tool("read_screen", Risk.READ, dev.s10e.companion.llm.ToolSpec("read_screen", ""), { "ok" })),
            confirm = { true },
            allowed = { true }
        )
        val result = dispatcher.dispatch("read_screen", "invalid json")
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("JSON"))
    }

    @Test
    fun dispatch_killSwitch_stopsLoop() = runTest {
        ToolDispatcher.killSwitch = true
        val dispatcher = ToolDispatcher(
            listOf(Tool("read_screen", Risk.READ, dev.s10e.companion.llm.ToolSpec("read_screen", ""), { "ok" })),
            confirm = { true },
            allowed = { true }
        )
        val result = dispatcher.dispatch("read_screen", "{}")
        assertTrue(result is ToolResult.Error)
        assertEquals("Not-Aus ausgelöst", (result as ToolResult.Error).message)
        ToolDispatcher.killSwitch = false
    }

    @Test
    fun agentLoop_killSwitch_endsLoopAtNextStep() = runTest {
        var calls = 0
        val client = object : dev.s10e.companion.llm.LlmClient {
            override fun chat(
                messages: List<dev.s10e.companion.llm.ChatMessage>,
                tools: List<dev.s10e.companion.llm.ToolSpec>
            ): kotlinx.coroutines.flow.Flow<dev.s10e.companion.llm.LlmEvent> = kotlinx.coroutines.flow.flow {
                calls++
                emit(dev.s10e.companion.llm.LlmEvent.ToolCall("read_screen", "{}"))
            }
        }
        val spec = dev.s10e.companion.llm.ToolSpec("read_screen", "")
        val dispatcher = ToolDispatcher(
            listOf(Tool("read_screen", Risk.READ, spec) { ToolDispatcher.killSwitch = true; "ok" }),
            confirm = { true },
            allowed = { true }
        )
        val reply = AgentLoop(client, dispatcher, tempLog()).run("Was steht auf dem Bildschirm", listOf(spec))
        assertEquals("[Not-Aus ausgelöst]", reply)
        assertEquals(1, calls)
        ToolDispatcher.killSwitch = false
    }

    @Test
    fun agentLoop_denied_stopsWithoutAskingAgain() = runTest {
        var asked = 0
        val client = object : dev.s10e.companion.llm.LlmClient {
            override fun chat(
                messages: List<dev.s10e.companion.llm.ChatMessage>,
                tools: List<dev.s10e.companion.llm.ToolSpec>
            ): kotlinx.coroutines.flow.Flow<dev.s10e.companion.llm.LlmEvent> = kotlinx.coroutines.flow.flow {
                emit(dev.s10e.companion.llm.LlmEvent.ToolCall("tap", "{}"))
            }
        }
        val spec = dev.s10e.companion.llm.ToolSpec("tap", "")
        val dispatcher = ToolDispatcher(
            listOf(Tool("tap", Risk.ACT, spec) { "ok" }),
            confirm = { asked++; false },
            allowed = { true }
        )
        AgentLoop(client, dispatcher, tempLog()).run("Tippe auf Start", listOf(spec))
        assertEquals(1, asked)
    }

    @Test
    fun agentLoop_plainQuestion_sendsNoTools() = runTest {
        var sentTools = -1
        val client = object : dev.s10e.companion.llm.LlmClient {
            override fun chat(
                messages: List<dev.s10e.companion.llm.ChatMessage>,
                tools: List<dev.s10e.companion.llm.ToolSpec>
            ): kotlinx.coroutines.flow.Flow<dev.s10e.companion.llm.LlmEvent> = kotlinx.coroutines.flow.flow {
                sentTools = tools.size
                emit(dev.s10e.companion.llm.LlmEvent.Token("Paris"))
            }
        }
        val spec = dev.s10e.companion.llm.ToolSpec("read_screen", "")
        val dispatcher = ToolDispatcher(emptyList(), confirm = { true }, allowed = { true })
        val reply = AgentLoop(client, dispatcher, tempLog()).run("Wie heißt die Hauptstadt?", listOf(spec))
        assertEquals("Paris", reply)
        assertEquals(0, sentTools)
    }


    @Test
    fun agentLoop_stopsAfter8Steps() = runTest {
        var callCount = 0
        val client = object : dev.s10e.companion.llm.LlmClient {
            override fun chat(
                messages: List<dev.s10e.companion.llm.ChatMessage>,
                tools: List<dev.s10e.companion.llm.ToolSpec>
            ): kotlinx.coroutines.flow.Flow<dev.s10e.companion.llm.LlmEvent> = kotlinx.coroutines.flow.flow {
                callCount++
                emit(dev.s10e.companion.llm.LlmEvent.ToolCall("read_screen", "{}"))
                emit(dev.s10e.companion.llm.LlmEvent.Done)
            }
        }
        val dispatcher = ToolDispatcher(
            listOf(Tool("read_screen", Risk.READ, dev.s10e.companion.llm.ToolSpec("read_screen", ""), { "ok" })),
            confirm = { true },
            allowed = { true }
        )
        val loop = AgentLoop(client, dispatcher, tempLog())

        loop.run("Was steht auf dem Bildschirm", listOf(dev.s10e.companion.llm.ToolSpec("read_screen", "")))
        assertEquals(8, callCount)
    }

    @Test
    fun audit_recordsEveryCall() = runTest {
        val auditFile = java.io.File.createTempFile("audit", ".log")
        auditFile.deleteOnExit()
        val logger = AuditLog(auditFile)
        logger.record("test_tool", "{}", ToolResult.Success("ok"))
        logger.record("bad_tool", "[]", ToolResult.Error("bad json"))
        
        val content = auditFile.readText()
        assertTrue(content.contains("test_tool"))
        assertTrue(content.contains("bad_tool"))
        assertTrue(content.contains("bad json"))
    }
    
    @Test
    fun command_aufgabe_keepsWordsVerbatim() = runTest {
        val client = object : dev.s10e.companion.llm.LlmClient {
            override fun chat(
                messages: List<dev.s10e.companion.llm.ChatMessage>,
                tools: List<dev.s10e.companion.llm.ToolSpec>
            ): kotlinx.coroutines.flow.Flow<dev.s10e.companion.llm.LlmEvent> = kotlinx.coroutines.flow.flow {
                throw AssertionError("Should not call LLM for deterministic commands")
            }
        }
        var toolCalled = false
        var args = ""
        val dispatcher = ToolDispatcher(
            listOf(Tool("add_card", Risk.READ, dev.s10e.companion.llm.ToolSpec("add_card", ""), { json -> 
                toolCalled = true
                args = json.toString()
                "ok" 
            })),
            confirm = { true },
            allowed = { true }
        )
        val loop = AgentLoop(client, dispatcher, tempLog())
        loop.run("Aufgabe Waschmaschine ausräumen", emptyList())
        assertTrue(toolCalled)
        assertEquals("Waschmaschine ausräumen", org.json.JSONObject(args).getString("text"))
    }

    @Test
    fun command_oeffne_mapsToOpenApp() = runTest {
        val client = object : dev.s10e.companion.llm.LlmClient {
            override fun chat(
                messages: List<dev.s10e.companion.llm.ChatMessage>,
                tools: List<dev.s10e.companion.llm.ToolSpec>
            ): kotlinx.coroutines.flow.Flow<dev.s10e.companion.llm.LlmEvent> = kotlinx.coroutines.flow.flow {
                throw AssertionError("Should not call LLM for deterministic commands")
            }
        }
        var toolCalled = false
        var args = ""
        val dispatcher = ToolDispatcher(
            listOf(Tool("open_app", Risk.READ, dev.s10e.companion.llm.ToolSpec("open_app", ""), { json -> 
                toolCalled = true
                args = json.toString()
                "ok" 
            })),
            confirm = { true },
            allowed = { true }
        )
        val loop = AgentLoop(client, dispatcher, tempLog())
        loop.run("Öffne Kalender", emptyList())
        assertTrue(toolCalled)
        assertTrue(args.contains("Kalender"))
    }

}
