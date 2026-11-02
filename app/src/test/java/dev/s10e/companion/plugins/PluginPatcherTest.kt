package dev.s10e.companion.plugins

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PluginPatcherTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun patch_missingSearchBlock_fails() {
        val pluginDir = tempFolder.newFolder("plugins")
        val store = PluginStore(pluginDir)
        val patcher = PluginPatcher(store) { true }

        val file = File(pluginDir, "test.txt")
        file.writeText("some existing content")

        val result = patcher.patch("test.txt", "missing", "replace")
        assertEquals(PatchResult.FAIL, result)
        assertEquals("some existing content", file.readText())
    }

    @Test
    fun patch_snapshotsBeforeWrite() {
        val pluginDir = tempFolder.newFolder("plugins")
        val store = PluginStore(pluginDir)
        val patcher = PluginPatcher(store) { true }

        val file = File(pluginDir, "test.txt")
        file.writeText("hello world")

        val result = patcher.patch("test.txt", "world", "universe")
        assertEquals(PatchResult.SUCCESS, result)
        assertEquals("hello universe", file.readText())
    }

    @Test
    fun rollback_restoresPrevious() {
        val pluginDir = tempFolder.newFolder("plugins")
        val store = PluginStore(pluginDir)
        val patcher = PluginPatcher(store) { true }

        val file = File(pluginDir, "test.txt")
        file.writeText("hello world")

        patcher.patch("test.txt", "world", "universe")

        val rollbackResult = patcher.rollback()
        assertEquals(PatchResult.SUCCESS, rollbackResult)
        assertEquals("hello world", file.readText())
    }

    @Test
    fun patch_rejectsPathOutsidePluginDir() {
        val pluginDir = tempFolder.newFolder("plugins")
        val store = PluginStore(pluginDir)
        val patcher = PluginPatcher(store) { true }

        val outsideFile = tempFolder.newFile("outside.txt")
        outsideFile.writeText("hello")

        val result = patcher.patch("../outside.txt", "hello", "world")
        assertEquals(PatchResult.FAIL, result)
    }

    @Test
    fun patch_blockedInKidMode() {
        val pluginDir = tempFolder.newFolder("plugins")
        val store = PluginStore(pluginDir)
        val patcher = PluginPatcher(store) { false } // blocked

        val file = File(pluginDir, "test.txt")
        file.writeText("hello world")

        val result = patcher.patch("test.txt", "world", "universe")
        assertEquals(PatchResult.FAIL, result)
        assertEquals("hello world", file.readText())
    }
}
