package dev.s10e.companion.agent

import dev.s10e.companion.llm.ToolSpec
import org.json.JSONObject

enum class Risk { READ, ACT }

data class Tool(val name: String, val risk: Risk, val spec: ToolSpec, val run: suspend (JSONObject) -> String)

sealed interface ToolResult {
    data class Success(val result: String) : ToolResult
    data class Error(val message: String) : ToolResult
}

class ToolDispatcher(
    private val tools: List<Tool>,
    private val confirm: suspend (String) -> Boolean,
    private val allowed: () -> Boolean
) {
    companion object {
        /** Der Not-Aus liegt bewusst außerhalb der LLM-Logik. */
        @Volatile
        var killSwitch: Boolean = false
    }

    suspend fun dispatch(name: String, argumentsJson: String): ToolResult {
        if (killSwitch) return ToolResult.Error("Not-Aus ausgelöst")
        if (!allowed()) return ToolResult.Error("Im Kindermodus nicht erlaubt")
        val tool = tools.find { it.name == name } ?: return ToolResult.Error("Unbekanntes Werkzeug: $name")

        val jsonArgs = try {
            JSONObject(argumentsJson)
        } catch (e: Exception) {
            return ToolResult.Error("Ungültiges JSON: ${e.message}")
        }

        if (tool.risk == Risk.ACT && !confirm("$name $argumentsJson")) {
            return ToolResult.Error("Abgelehnt")
        }

        return try {
            ToolResult.Success(tool.run(jsonArgs))
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Werkzeug fehlgeschlagen")
        }
    }
}
