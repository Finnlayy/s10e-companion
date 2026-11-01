package dev.s10e.companion.llm

import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelImporterTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun import_writesPartThenRenames() = runTest {
        val targetDir = tempFolder.newFolder("models")
        val importer = ModelImporter(targetDir)
        val data = "test data".toByteArray()
        val entry = ModelEntry("test", "test.gguf", data.size.toLong(), "916f0027a575074ce72a331777c3478d6513f786a591bd892da1a577bf2335f9")

        val result = importer.import(ByteArrayInputStream(data), entry)

        assertTrue(result is ImportResult.Ok)
        val path = (result as ImportResult.Ok).path
        assertTrue(File(path).exists())
        assertEquals("test data", File(path).readText())
    }

    @Test
    fun import_shaMismatch_deletesPartAndFails() = runTest {
        val targetDir = tempFolder.newFolder("models")
        val importer = ModelImporter(targetDir)
        val data = "test data".toByteArray()
        val entry = ModelEntry("test", "test.gguf", data.size.toLong(), "wronghash")

        val result = importer.import(ByteArrayInputStream(data), entry)

        assertTrue(result is ImportResult.Failed)
        assertEquals(0, targetDir.listFiles()?.size)
    }

    @Test
    fun import_truncatedStream_leavesNoTarget() = runTest {
        val targetDir = tempFolder.newFolder("models")
        val importer = ModelImporter(targetDir)
        val data = "test data".toByteArray()
        val entry = ModelEntry("test", "test.gguf", (data.size + 1).toLong(), "916f0027a575074ce72a331777c3478d6513f786a591bd892da1a577bf2335f9")

        val result = importer.import(ByteArrayInputStream(data), entry)

        assertTrue(result is ImportResult.Failed)
        assertEquals(0, targetDir.listFiles()?.size)
    }

    @Test
    fun import_retryAfterFailure_succeeds() = runTest {
        val targetDir = tempFolder.newFolder("models")
        val importer = ModelImporter(targetDir)
        val data = "test data".toByteArray()
        val entryWrong = ModelEntry("test", "test.gguf", data.size.toLong(), "wronghash")
        val entryRight = ModelEntry("test", "test.gguf", data.size.toLong(), "916f0027a575074ce72a331777c3478d6513f786a591bd892da1a577bf2335f9")

        importer.import(ByteArrayInputStream(data), entryWrong)
        val result = importer.import(ByteArrayInputStream(data), entryRight)

        assertTrue(result is ImportResult.Ok)
        assertTrue(File((result as ImportResult.Ok).path).exists())
    }
}
