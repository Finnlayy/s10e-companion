package dev.s10e.companion.speech

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ArchiveExtractorTest {
    private val dir = Files.createTempDirectory("extract").toFile()

    private fun zip(name: String): File {
        val file = File(dir, "a.zip")
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry(name))
            it.write("hallo".toByteArray())
            it.closeEntry()
        }
        return file
    }

    @Test
    fun extract_zip_writesNestedFile() {
        val target = File(dir, "out")
        ArchiveExtractor.extract(zip("model/am/final.mdl"), target)
        assertEquals("hallo", File(target, "model/am/final.mdl").readText())
    }

    @Test
    fun extract_tarBz2_writesNestedFile() {
        val file = File(dir, "a.tar.bz2")
        TarArchiveOutputStream(BZip2CompressorOutputStream(file.outputStream())).use {
            val entry = TarArchiveEntry("model/tokens.txt")
            entry.size = 5
            it.putArchiveEntry(entry)
            it.write("hallo".toByteArray())
            it.closeArchiveEntry()
        }
        val target = File(dir, "out")
        ArchiveExtractor.extract(file, target)
        assertEquals("hallo", File(target, "model/tokens.txt").readText())
    }

    @Test
    fun extract_rejectsPathOutsideTarget() {
        val target = File(dir, "out")
        assertThrows(IOException::class.java) { ArchiveExtractor.extract(zip("../boese.txt"), target) }
        assertFalse(File(dir, "boese.txt").exists())
    }
}
