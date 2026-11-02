package dev.s10e.companion.speech

import android.content.Context
import android.util.Log
import dev.s10e.companion.llm.ImportResult
import dev.s10e.companion.llm.ModelEntry
import dev.s10e.companion.llm.ModelImporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.vosk.Model

class SpeechStack(val recognizer: Recognizer, val controller: SpeechController)

/** Importiert per adb abgelegte Sprachmodelle, entpackt sie und lädt sie abseits des Main-Threads. */
class SpeechBootstrap(private val context: Context) {
    private val modelsDir = File(context.filesDir, "models")

    suspend fun load(): SpeechStack? = withContext(Dispatchers.IO) {
        try {
            val dirs = loadCatalog().map { prepare(it) ?: return@withContext null }
            val recognizer = VoskRecognizer(Model(dirs[0].absolutePath))
            SpeechStack(recognizer, SpeechController(recognizer, PiperSpeaker(dirs[1].absolutePath)))
        } catch (e: Exception) {
            Log.w(TAG, "Sprache nicht verfügbar: ${e.message}")
            null
        }
    }

    private suspend fun prepare(entry: ModelEntry): File? {
        val target = File(modelsDir, entry.id)
        if (target.isDirectory) return target
        val pushed = File(context.getExternalFilesDir("models"), entry.file)
        if (!pushed.exists()) {
            Log.w(TAG, "Sprachmodell fehlt: ${entry.file}")
            return null
        }
        val result = pushed.inputStream().use { ModelImporter(modelsDir).import(it, entry) }
        if (result is ImportResult.Failed) {
            Log.w(TAG, "Import fehlgeschlagen: ${result.reason}")
            return null
        }
        val archive = File(modelsDir, entry.file)
        val workDir = File(modelsDir, ".extract-${entry.id}")
        workDir.deleteRecursively()
        ArchiveExtractor.extract(archive, workDir)
        File(workDir, entry.id).renameTo(target)
        workDir.deleteRecursively()
        archive.delete()
        pushed.delete()
        return target.takeIf { it.isDirectory }
    }

    private fun loadCatalog(): List<ModelEntry> {
        val json = context.assets.open("speech.json").bufferedReader().use { it.readText() }
        val entries = JSONArray(json)
        return List(entries.length()) { i ->
            val entry = entries.getJSONObject(i)
            ModelEntry(entry.getString("id"), entry.getString("file"), entry.getLong("bytes"), entry.getString("sha256"))
        }
    }

    private companion object {
        const val TAG = "S10eCompanion"
    }
}
