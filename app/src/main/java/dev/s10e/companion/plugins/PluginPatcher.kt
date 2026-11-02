package dev.s10e.companion.plugins

import java.io.File
import java.io.IOException

enum class PatchResult {
    SUCCESS, FAIL
}

class PluginPatcher(private val store: PluginStore, private val toolsAllowed: () -> Boolean) {
    // Der Snapshot liegt auf der Platte, damit das Zurückrollen einen Neustart übersteht.
    private val snapshot = File(store.dir, ".snapshot")
    private val snapshotTarget = File(store.dir, ".snapshot-target")

    private fun resolve(file: String): File? {
        val root = store.dir.canonicalFile
        val target = File(root, file).canonicalFile
        return target.takeIf { it.path.startsWith(root.path + File.separator) }
    }

    fun patch(file: String, search: String, replace: String): PatchResult {
        if (!toolsAllowed()) return PatchResult.FAIL

        val target = try {
            resolve(file)
        } catch (e: IOException) {
            null
        } ?: return PatchResult.FAIL

        if (!target.exists()) return PatchResult.FAIL

        val content = target.readText()
        if (!content.contains(search)) return PatchResult.FAIL

        snapshot.writeText(content)
        snapshotTarget.writeText(target.path)

        target.writeText(content.replaceFirst(search, replace))
        return PatchResult.SUCCESS
    }

    fun rollback(): PatchResult {
        if (!toolsAllowed() || !snapshot.exists() || !snapshotTarget.exists()) return PatchResult.FAIL
        File(snapshotTarget.readText()).writeText(snapshot.readText())
        snapshot.delete()
        snapshotTarget.delete()
        return PatchResult.SUCCESS
    }
}
