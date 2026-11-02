package dev.s10e.companion.vault

typealias Board = LinkedHashMap<String, List<String>>

object KanbanCodec {
    fun parse(md: String): Board {
        val board = Board()
        var currentColumn = ""
        var currentLines = mutableListOf<String>()

        for (line in md.lines()) {
            if (line.startsWith("## ")) {
                board[currentColumn] = currentLines.toList()
                currentColumn = line.substring(3).trim()
                currentLines = mutableListOf()
            } else {
                currentLines.add(line)
            }
        }
        board[currentColumn] = currentLines.toList()

        // Trim trailing blank lines from each section to normalize spacing
        val normalizedBoard = Board()
        board.forEach { (column, lines) ->
            var lastNonBlank = lines.indexOfLast { it.isNotBlank() }
            if (lastNonBlank == -1) {
                normalizedBoard[column] = emptyList()
            } else {
                normalizedBoard[column] = lines.take(lastNonBlank + 1)
            }
        }
        return normalizedBoard
    }

    fun serialize(board: Board): String {
        val sb = StringBuilder()
        val entries = board.entries.toList()

        for (i in entries.indices) {
            val column = entries[i].key
            val lines = entries[i].value

            if (column.isNotEmpty()) {
                sb.append("## ").append(column).append("\n")
            }
            val isDone = column.equals("done", ignoreCase = true) ||
                         column.equals("completed", ignoreCase = true) ||
                         column.equals("erledigt", ignoreCase = true)
            for (line in lines) {
                var outLine = line
                if (isDone && outLine.startsWith("- [ ] ")) {
                    outLine = "- [x] " + outLine.substring(6)
                }
                sb.append(outLine).append("\n")
            }

            // Add a blank line after each section, EXCEPT the preamble
            if (i < entries.size - 1 && column.isNotEmpty()) {
                sb.append("\n")
            }
        }
        return sb.toString()
    }
}
