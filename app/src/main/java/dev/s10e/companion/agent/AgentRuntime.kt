package dev.s10e.companion.agent

import android.content.Context
import android.content.Intent
import dev.s10e.companion.llm.LlmClientImpl
import dev.s10e.companion.llm.ToolSpec
import dev.s10e.companion.plugins.PatchResult
import dev.s10e.companion.plugins.PluginPatcher
import dev.s10e.companion.plugins.PluginStore
import dev.s10e.companion.vault.VaultStore
import java.io.File
import org.json.JSONObject

/** Gemeinsamer Weg für getippte und gesprochene Aufträge. */
class AgentRuntime(
    private val context: Context,
    private val vault: VaultStore,
    allowed: () -> Boolean,
    confirm: suspend (String) -> Boolean
) {
    private val patcher = PluginPatcher(PluginStore(File(context.filesDir, "plugins").apply { mkdirs() }), allowed)

    private val tools = listOf(
        Tool("read_screen", Risk.READ, ToolSpec("read_screen", "Liest den Bildschirm. Keine Argumente.")) { service().readScreen() },
        Tool("tap", Risk.ACT, ToolSpec("tap", "Tippt auf x und y.")) { args ->
            service().tap(args.getDouble("x").toFloat(), args.getDouble("y").toFloat())
        },
        Tool("swipe", Risk.ACT, ToolSpec("swipe", "Wischt von x1, y1 nach x2, y2.")) { args ->
            service().swipe(
                args.getDouble("x1").toFloat(), args.getDouble("y1").toFloat(),
                args.getDouble("x2").toFloat(), args.getDouble("y2").toFloat(),
                SWIPE_MS
            )
        },
        Tool("type_text", Risk.ACT, ToolSpec("type_text", "Schreibt text ins aktive Feld.")) { args -> service().typeText(args.getString("text")) },
        Tool("open_app", Risk.ACT, ToolSpec("open_app", "Öffnet die App app_name.")) { args -> openApp(args.getString("app_name")) },
        Tool("patch_plugin", Risk.ACT, ToolSpec("patch_plugin", "Ersetzt search durch replace in der Plugin-Datei file.")) { args ->
            val result = patcher.patch(args.getString("file"), args.getString("search"), args.getString("replace"))
            if (result == PatchResult.SUCCESS) "Plugin geändert" else throw IllegalArgumentException("Patch nicht anwendbar")
        },
        Tool("rollback_plugin", Risk.ACT, ToolSpec("rollback_plugin", "Macht die letzte Plugin-Änderung rückgängig.")) {
            if (patcher.rollback() == PatchResult.SUCCESS) "Zurückgerollt" else throw IllegalStateException("Nichts zurückzurollen")
        },
        Tool("add_card", Risk.READ, ToolSpec("add_card", "Legt die Aufgabe text an.")) { args -> addToVault(args) },
        Tool("add_note", Risk.READ, ToolSpec("add_note", "Speichert die Notiz text.")) { args -> addToVault(args) },
        Tool("move_card", Risk.READ, ToolSpec("move_card", "Verschiebt die Karte text in die Spalte column.")) { args ->
            val moved = vault.moveCard(args.getString("text"), args.getString("column"))
            if (moved) "Verschoben" else throw IllegalArgumentException("Karte oder Spalte nicht gefunden")
        }
    )

    private val dispatcher = ToolDispatcher(tools, confirm, allowed)
    private val client = LlmClientImpl(apiKeyProvider = { File(context.filesDir, "api_key.txt").readText().trim() })
    private val loop = AgentLoop(client, dispatcher, File(context.filesDir, "audit.log"))

    suspend fun handle(text: String, onReply: (String) -> Unit): String =
        loop.run(text, tools.map { it.spec }, onReply).also(onReply)

    private fun service(): PhoneControlService =
        PhoneControlService.instance ?: throw IllegalStateException("Bedienungshilfe ist nicht aktiviert")

    private fun addToVault(args: JSONObject): String {
        val text = args.getString("text")
        vault.appendInbox(text, System.currentTimeMillis())
        return "Gespeichert: $text"
    }

    private fun openApp(name: String): String {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = context.packageManager
        val match = pm.queryIntentActivities(launcher, 0).firstOrNull {
            it.loadLabel(pm).toString().equals(name, ignoreCase = true)
        } ?: throw IllegalArgumentException("App nicht gefunden: $name")
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: throw IllegalArgumentException("App nicht startbar: $name")
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Geöffnet: $name"
    }

    private companion object {
        const val SWIPE_MS = 500L
    }
}
