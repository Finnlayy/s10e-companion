package dev.s10e.companion.llm

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ImportResult {
    data class Ok(val path: String) : ImportResult
    data class Failed(val reason: String) : ImportResult
}

class ModelImporter(private val targetDir: File) {
    suspend fun import(source: InputStream, entry: ModelEntry): ImportResult = withContext(Dispatchers.IO) {
        val targetFile = File(targetDir, entry.file)
        val partFile = File(targetDir, "${entry.file}.part")

        try {
            targetDir.mkdirs()
            if (partFile.exists()) partFile.delete()

            val digest = MessageDigest.getInstance("SHA-256")
            partFile.outputStream().use { out ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (source.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                    digest.update(buffer, 0, bytesRead)
                }
            }

            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash != entry.sha256) {
                partFile.delete()
                return@withContext ImportResult.Failed("SHA-256 mismatch")
            }

            if (partFile.length() != entry.bytes) {
                partFile.delete()
                return@withContext ImportResult.Failed("Size mismatch")
            }

            partFile.renameTo(targetFile)
            ImportResult.Ok(targetFile.absolutePath)
        } catch (e: Exception) {
            partFile.delete()
            ImportResult.Failed(e.message ?: "Unknown error")
        }
    }
}
