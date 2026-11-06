package dev.s10e.companion.eyes

enum class TouchKind { TAP, STROKE, NONE }

private const val TAP_MAX_PATH_DP = 24f
private const val STROKE_MIN_PATH_DP = 200f
private const val POKE_WINDOW_MS = 5_000L
private const val POKES_UNTIL_ANGRY = 4

/** Unterscheidet nach dem zurückgelegten Weg des Fingers, ein kurzes Wischen zählt als nichts. */
fun classifyTouch(pathDp: Float): TouchKind = when {
    pathDp <= TAP_MAX_PATH_DP -> TouchKind.TAP
    pathDp >= STROKE_MIN_PATH_DP -> TouchKind.STROKE
    else -> TouchKind.NONE
}

/** Einmal Anstupsen überrascht, ständiges Anstupsen nervt. */
class PokeCounter {
    private val pokes = ArrayDeque<Long>()

    fun onPoke(nowMs: Long): Mood {
        pokes.addLast(nowMs)
        while (nowMs - pokes.first() > POKE_WINDOW_MS) pokes.removeFirst()
        return if (pokes.size >= POKES_UNTIL_ANGRY) Mood.ANGRY else Mood.SURPRISED
    }
}
