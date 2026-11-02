package dev.s10e.companion.agent

import dev.s10e.companion.llm.ChatMessage
import dev.s10e.companion.llm.LlmClient
import dev.s10e.companion.llm.LlmEvent
import dev.s10e.companion.llm.ToolSpec
import java.io.File
import kotlinx.coroutines.flow.takeWhile
import org.json.JSONObject

data class Command(val tool: String, val argumentsJson: String)

private const val MAX_STEPS = 8
private const val CHAT_PROMPT = "Du bist Apollo, ein kleiner Schreibtisch-Begleiter. Antworte kurz und auf Deutsch."
private val PHONE_TASK = Regex("""\b(tippe|wische|scrolle|bildschirm|verschiebe|karte|plugin|gib .* ein|schreibe .* ein|was steht)\b""", RegexOption.IGNORE_CASE)

/** Klare Befehle laufen ohne LLM, weil das Modell freien Text entstellt. */
fun parseCommand(text: String): Command? {
    val trimmed = text.trim()
    fun rest(prefix: String) = trimmed.takeIf { it.startsWith("$prefix ", ignoreCase = true) }?.substring(prefix.length + 1)?.trim()
    fun args(key: String, value: String) = JSONObject().put(key, value).toString()
    rest("Aufgabe")?.let { return Command("add_card", args("text", it)) }
    rest("Notiz")?.let { return Command("add_note", args("text", it)) }
    rest("Notiere")?.let { return Command("add_note", args("text", it)) }
    rest("Öffne")?.let { return Command("open_app", args("app_name", it)) }
    return null
}

fun looksLikePhoneTask(text: String): Boolean = PHONE_TASK.containsMatchIn(text)

class AgentLoop(
    private val client: LlmClient,
    private val dispatcher: ToolDispatcher,
    auditLogFile: File
) {
    private val auditLog = AuditLog(auditLogFile)

    private fun toolPrompt(tools: List<ToolSpec>): String =
        "Du steuerst ein Handy. Wähle genau ein Werkzeug und antworte nur als JSON. Werkzeuge:\n" +
            tools.joinToString("\n") { "${it.name}: ${it.description}" } +
            "\nnone: Die Aufgabe ist erledigt."

    private suspend fun dispatch(name: String, argumentsJson: String): ToolResult {
        val result = dispatcher.dispatch(name, argumentsJson)
        auditLog.record(name, argumentsJson, result)
        return result
    }

    suspend fun run(
        initialPrompt: String,
        tools: List<ToolSpec>,
        onReplyText: ((String) -> Unit)? = null
    ): String {
        // Ein neuer Auftrag der Person hebt einen früheren Not-Aus auf.
        ToolDispatcher.killSwitch = false

        parseCommand(initialPrompt)?.let { command ->
            return when (val result = dispatch(command.tool, command.argumentsJson)) {
                is ToolResult.Success -> result.result
                is ToolResult.Error -> "[Fehler: ${result.message}]"
            }
        }

        val history = mutableListOf(ChatMessage("user", initialPrompt))
        var useTools = tools.isNotEmpty() && looksLikePhoneTask(initialPrompt)

        repeat(MAX_STEPS) {
            if (ToolDispatcher.killSwitch) return "[Not-Aus ausgelöst]"

            var toolName: String? = null
            var toolArgs = "{}"
            var text = ""
            var failure: String? = null
            val system = ChatMessage("system", if (useTools) toolPrompt(tools) else CHAT_PROMPT)

            client.chat(listOf(system) + history, if (useTools) tools else emptyList())
                .takeWhile { !ToolDispatcher.killSwitch }
                .collect { event ->
                    when (event) {
                        is LlmEvent.Token -> {
                            text += event.text
                            onReplyText?.invoke(text)
                        }
                        is LlmEvent.ToolCall -> {
                            toolName = event.name
                            toolArgs = event.argumentsJson
                        }
                        is LlmEvent.Failed -> failure = event.message
                        LlmEvent.Done -> {}
                    }
                }

            failure?.let { return "[Fehler: $it]" }
            val name = toolName
            when {
                ToolDispatcher.killSwitch -> return "[Not-Aus ausgelöst]"
                name == null -> return text
                name == "none" -> useTools = false
                else -> {
                    val result = dispatch(name, toolArgs)
                    if (result == ToolResult.Error(ToolDispatcher.DENIED)) return "Abgelehnt, ich führe das nicht aus."
                    val outcome = when (result) {
                        is ToolResult.Success -> result.result
                        is ToolResult.Error -> "Fehler: ${result.message}"
                    }
                    history.add(ChatMessage("assistant", JSONObject().put("tool", name).put("arguments", toolArgs).toString()))
                    history.add(ChatMessage("user", "Ergebnis von $name: $outcome"))
                }
            }
        }
        return "[Schleife nach $MAX_STEPS Schritten abgebrochen]"
    }
}
