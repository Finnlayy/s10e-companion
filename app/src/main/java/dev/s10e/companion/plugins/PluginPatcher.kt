package dev.s10e.companion.plugins

import java.io.File
import java.io.IOException

enum class PatchResult {
    SUCCESS, FAIL
}

class PluginPatcher(private val store: PluginStore, private val toolsAllowed: () -> Boolean) {

    private var snapshotFile: File? = null
    private var snapshotContent: String? = null

    fun patch(file: String, search: String, replace: String): PatchResult {
        if (!toolsAllowed()) return PatchResult.FAIL

        val target = File(store.dir, file)

        // Ensure path doesn't escape plugin dir
        try {
            if (!target.canonicalPath.startsWith(store.dir.canonicalPath)) {
                return PatchResult.FAIL
            }
        } catch (e: IOException) {
            return PatchResult.FAIL
        }

        if (!target.exists()) return PatchResult.FAIL

        val content = target.readText()
        if (!content.contains(search)) return PatchResult.FAIL

        // Create snapshot before write
        snapshotFile = target
        snapshotContent = content

        val newContent = content.replaceFirst(search, replace)
        target.writeText(newContent)
        return PatchResult.SUCCESS
    }

    fun rollback(): PatchResult {
        val file = snapshotFile
        val content = snapshotContent
        if (file != null && content != null) {
            file.writeText(content)
            snapshotFile = null
            snapshotContent = null
            return PatchResult.SUCCESS
        }
        return PatchResult.FAIL
    }
}
