package dev.s10e.companion.llm

class ServerSupervisor(val launch: () -> Process, val sleep: suspend (Long) -> Unit) {
    fun nextBackoffMs(failures: Int): Long {
        if (failures <= 0) return 1000L
        val base = 1000L * (1L shl (failures - 1).coerceAtMost(5))
        return base.coerceAtMost(30_000L)
    }
}
