package dev.s10e.companion.agent

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuditLog(private val logFile: File) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun record(toolName: String, argumentsJson: String, result: ToolResult) {
        val timestamp = dateFormat.format(Date())
        val resultStr = when (result) {
            is ToolResult.Success -> "SUCCESS: ${result.result}"
            is ToolResult.Error -> "ERROR: ${result.message}"
        }
        val entry = "[$timestamp] TOOL: $toolName ARGS: $argumentsJson RESULT: $resultStr\n"
        logFile.appendText(entry)
    }
}
