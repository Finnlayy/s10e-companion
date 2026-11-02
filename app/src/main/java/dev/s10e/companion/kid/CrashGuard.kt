package dev.s10e.companion.kid

class CrashGuard(
    private val prefs: KeyValue,
    val maxCrashes: Int = 3,
    val windowMs: Long = 60_000
) {
    fun recordStart(nowMs: Long) {
        val history = getCrashHistory()
        val recent = history.filter { nowMs - it <= windowMs }.toMutableList()
        recent.add(nowMs)
        saveCrashHistory(recent)
    }

    fun inSafeMode(nowMs: Long): Boolean {
        val history = getCrashHistory()
        val recent = history.filter { nowMs - it <= windowMs }
        return recent.size >= maxCrashes
    }

    private fun getCrashHistory(): List<Long> {
        val str = prefs.getString("crash_history", "") ?: ""
        return if (str.isEmpty()) emptyList() else str.split(",").mapNotNull { it.toLongOrNull() }
    }

    private fun saveCrashHistory(history: List<Long>) {
        prefs.setString("crash_history", history.joinToString(","))
    }
}
