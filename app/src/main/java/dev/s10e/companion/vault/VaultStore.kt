package dev.s10e.companion.vault

import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

sealed interface SaveResult {
    object Saved : SaveResult
    data class Conflict(val merged: Board) : SaveResult
}

class VaultStore(private val root: File) {
    private val lock = ReentrantReadWriteLock()
    private val kanbanFile = File(root, "Kanban.md")
    private val inboxFile = File(root, "Inbox.md")
    private val dailyDir = File(root, "Daily")

    fun ensureLayout() {
        lock.write {
            if (!root.exists()) root.mkdirs()
            if (!dailyDir.exists()) dailyDir.mkdirs()
            if (!kanbanFile.exists()) {
                val initialBoard = Board().apply {
                    this[""] = listOf(
                        "---",
                        "kanban-plugin: basic",
                        "---"
                    )
                    this["Todo"] = emptyList()
                    this["In Progress"] = emptyList()
                    this["Done"] = emptyList()
                }
                kanbanFile.writeText(KanbanCodec.serialize(initialBoard))
            }
            if (!inboxFile.exists()) {
                inboxFile.writeText("")
            }
        }
    }

    fun load(): Pair<Board, Long> {
        return lock.read {
            if (!kanbanFile.exists()) {
                ensureLayout()
            }
            val content = kanbanFile.readText()
            val mtime = kanbanFile.lastModified()
            Pair(KanbanCodec.parse(content), mtime)
        }
    }

    fun save(board: Board, baseMtimeMs: Long): SaveResult {
        return lock.write {
            if (!kanbanFile.exists()) {
                ensureLayout()
            }
            val currentMtime = kanbanFile.lastModified()
            if (currentMtime != baseMtimeMs) {
                // Conflict detected: external change
                val currentBoard = KanbanCodec.parse(kanbanFile.readText())
                val merged = mergeBoards(currentBoard, board)
                return SaveResult.Conflict(merged)
            }

            // Write atomically to a temp file and rename
            val tempFile = File(kanbanFile.parentFile, kanbanFile.name + ".tmp")
            tempFile.writeText(KanbanCodec.serialize(board))
            if (!tempFile.renameTo(kanbanFile)) throw IOException("Kanban.md nicht ersetzbar")
            SaveResult.Saved
        }
    }

    fun appendInbox(text: String, nowMs: Long) {
        lock.write {
            if (!inboxFile.exists()) {
                ensureLayout()
            }
            inboxFile.appendText("- [ ] $text\n")

            // Also append to the first column of the Kanban board
            val (board, mtime) = load()
            val columns = board.keys.filter { it.isNotEmpty() }
            if (columns.isNotEmpty()) {
                val firstCol = columns.first()
                val currentLines = board[firstCol]?.toMutableList() ?: mutableListOf()
                currentLines.add("- [ ] $text")
                board[firstCol] = currentLines
                var res = save(board, mtime)
                // If conflict during inbox append, retry once
                if (res is SaveResult.Conflict) {
                     val retryMtime = kanbanFile.lastModified()
                     val retryCol = res.merged.keys.filter { it.isNotEmpty() }.firstOrNull()
                     if (retryCol != null) {
                         val retryLines = res.merged[retryCol]?.toMutableList() ?: mutableListOf()
                         retryLines.add("- [ ] $text")
                         res.merged[retryCol] = retryLines
                         save(res.merged, retryMtime)
                     }
                }
            }
        }
    }

    fun moveCard(text: String, column: String): Boolean {
        return lock.write {
            val (board, mtime) = load()
            val target = board.keys.firstOrNull { it.equals(column, ignoreCase = true) && it.isNotEmpty() } ?: return false
            val source = board.keys.firstOrNull { key -> key.isNotEmpty() && board[key].orEmpty().any { it.endsWith(" $text") } } ?: return false
            val line = board[source].orEmpty().first { it.endsWith(" $text") }
            board[source] = board[source].orEmpty() - line
            board[target] = board[target].orEmpty() + line
            save(board, mtime) is SaveResult.Saved
        }
    }

    private fun mergeBoards(theirs: Board, ours: Board): Board {
        // Keep theirs as base, append only *new* items from ours.
        val merged = Board()

        // Preamble from theirs
        merged[""] = theirs[""]?.toList() ?: emptyList()

        for (col in theirs.keys.filter { it.isNotEmpty() }) {
            val theirLines = theirs[col] ?: emptyList()
            val ourLines = ours[col] ?: emptyList()

            val mergedLines = theirLines.toMutableList()
            for (line in ourLines) {
                if (!theirLines.contains(line) && line.startsWith("- [ ] ")) {
                     mergedLines.add(line)
                }
            }
            merged[col] = mergedLines
        }

        // Add any columns from ours that aren't in theirs
        for (col in ours.keys.filter { it.isNotEmpty() }) {
            if (!theirs.containsKey(col)) {
                merged[col] = ours[col]?.toList() ?: emptyList()
            }
        }

        return merged
    }
}
