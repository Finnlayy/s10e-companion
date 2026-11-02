package dev.s10e.companion.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VaultStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var store: VaultStore

    @Before
    fun setup() {
        rootDir = tempFolder.newFolder("vault")
        store = VaultStore(rootDir)
    }

    @Test
    fun ensureLayout_doesNotOverwriteExisting() {
        val kanban = File(rootDir, "Kanban.md")
        kanban.writeText("Existing Content")

        store.ensureLayout()

        assertEquals("Existing Content", kanban.readText())
    }

    @Test
    fun moveCard_movesLineToColumn() {
        store.ensureLayout()
        store.appendInbox("Milch kaufen", 1L)

        assertTrue(store.moveCard("Milch kaufen", "done"))

        val board = store.load().first
        assertTrue(board["Todo"].orEmpty().isEmpty())
        assertEquals(listOf("- [x] Milch kaufen"), board["Done"])
    }

    @Test
    fun save_writesAtomically() {
        store.ensureLayout()
        val (board, mtime) = store.load()
        board["Todo"] = listOf("- [ ] Atomic test")

        val result = store.save(board, mtime)
        assertTrue(result is SaveResult.Saved)

        val kanban = File(rootDir, "Kanban.md")
        val content = kanban.readText()
        assertTrue(content.contains("Atomic test"))

        // Assert no temp files left
        val tempFiles = rootDir.listFiles { _, name -> name.endsWith(".tmp") }
        assertTrue(tempFiles == null || tempFiles.isEmpty())
    }

    @Test
    fun save_detectsExternalChange_returnsConflictAndKeepsTheirs() {
        store.ensureLayout()
        val (initialBoard, baseMtime) = store.load()

        val kanban = File(rootDir, "Kanban.md")
        // Simulate external change
        kanban.writeText("## Todo\n- [ ] External Task\n")
        // Need to ensure mtime is strictly greater
        kanban.setLastModified(baseMtime + 1000)

        // Try to save our own change based on the old mtime
        initialBoard["Todo"] = listOf("- [ ] Our Task")

        val result = store.save(initialBoard, baseMtime)
        assertTrue(result is SaveResult.Conflict)

        val conflict = result as SaveResult.Conflict
        val mergedLines = conflict.merged["Todo"]!!
        // Theirs is kept, ours is appended
        assertEquals("- [ ] External Task", mergedLines[0])
        assertEquals("- [ ] Our Task", mergedLines[1])

        // The file itself shouldn't be overwritten yet
        val content = kanban.readText()
        assertTrue(content.contains("External Task"))
        assertTrue(!content.contains("Our Task"))
    }
}
