package dev.s10e.companion.llm

import android.content.Context
import android.content.Intent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Verbindet Katalog, Import und Dienst: Ein per adb abgelegtes Modell wird importiert und gestartet. */
class LlmBootstrap(private val context: Context) {
    private val modelsDir = File(context.filesDir, "models")

    suspend fun start(): String {
        val model = loadCatalog().first()
        val target = File(modelsDir, model.file)
        if (!target.exists()) {
            val pushed = File(context.getExternalFilesDir("models"), model.file)
            if (!pushed.exists()) return "Kein Modell gefunden: ${model.file}"
            val result = withContext(Dispatchers.IO) {
                pushed.inputStream().use { ModelImporter(modelsDir).import(it, model) }
            }
            if (result is ImportResult.Failed) return "Import fehlgeschlagen: ${result.reason}"
            pushed.delete()
        }
        context.startForegroundService(
            Intent(context, LlmServerService::class.java).putExtra("MODEL_PATH", target.absolutePath)
        )
        return "Modell ${model.id} gestartet"
    }

    private fun loadCatalog(): List<ModelEntry> {
        val json = context.assets.open("models.json").bufferedReader().use { it.readText() }
        val entries = JSONArray(json)
        return List(entries.length()) { i ->
            val entry = entries.getJSONObject(i)
            ModelEntry(entry.getString("id"), entry.getString("file"), entry.getLong("bytes"), entry.getString("sha256"))
        }
    }
}
