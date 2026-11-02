package dev.s10e.companion.speech

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/** Entpackt ein Modellarchiv (.zip oder .tar.bz2) und bricht bei Pfaden außerhalb des Ziels ab. */
object ArchiveExtractor {
    fun extract(archive: File, targetDir: File) {
        targetDir.mkdirs()
        archive.inputStream().buffered().use { input ->
            when {
                archive.name.endsWith(".zip") -> extractZip(input, targetDir)
                archive.name.endsWith(".tar.bz2") -> extractTarBz2(input, targetDir)
                else -> throw IOException("Unbekanntes Archiv: ${archive.name}")
            }
        }
    }

    private fun extractZip(input: InputStream, targetDir: File) {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                write(zip, resolve(targetDir, entry.name), entry.isDirectory)
            }
        }
    }

    private fun extractTarBz2(input: InputStream, targetDir: File) {
        TarArchiveInputStream(BZip2CompressorInputStream(input)).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                write(tar, resolve(targetDir, entry.name), entry.isDirectory)
            }
        }
    }

    private fun resolve(targetDir: File, name: String): File {
        val target = File(targetDir, name).canonicalFile
        val root = targetDir.canonicalFile
        if (target != root && !target.path.startsWith(root.path + File.separator)) {
            throw IOException("Pfad außerhalb des Ziels: $name")
        }
        return target
    }

    private fun write(input: InputStream, target: File, isDirectory: Boolean) {
        if (isDirectory) {
            target.mkdirs()
            return
        }
        target.parentFile?.mkdirs()
        target.outputStream().use { input.copyTo(it) }
    }
}
