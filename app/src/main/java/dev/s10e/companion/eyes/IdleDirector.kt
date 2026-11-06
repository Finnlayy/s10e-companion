package dev.s10e.companion.eyes

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

enum class IdleAct(val durationMs: Long) {
    DOUBLE_BLINK(320),
    GLANCE(1_000),
    WINK(160),
    SQUINT(500),
    YAWN(1_800)
}

/** Öffnung der Augen von 0 bis 1 und seitlicher Blick in dp. */
data class IdlePose(val leftOpen: Float = 1f, val rightOpen: Float = 1f, val glanceX: Float = 0f)

private const val GAP_MIN_MS = 4_000
private const val GAP_MAX_MS = 10_000
private const val BORED_GAP_MIN_MS = 2_500
private const val BORED_GAP_MAX_MS = 6_000
private const val GLANCE_DP = 26f
private const val SQUINT_DEPTH = 0.7f
private const val YAWN_DEPTH = 0.85f

/** Würfelt kleine Gesten, damit das Gesicht auch ohne Reize lebt. */
class IdleDirector(private val random: Random = Random.Default) {
    private var act: IdleAct? = null
    private var startMs = 0L
    private var side = 1
    private var nextAtMs = -1L

    fun pose(nowMs: Long, bored: Boolean = false): IdlePose {
        if (nextAtMs < 0) nextAtMs = nowMs + gap(bored)

        val running = act
        if (running != null && nowMs - startMs >= running.durationMs) {
            act = null
            nextAtMs = nowMs + gap(bored)
        }
        if (act == null && nowMs >= nextAtMs) {
            act = pick(bored)
            startMs = nowMs
            side = if (random.nextBoolean()) 1 else -1
        }

        val current = act ?: return IdlePose()
        val progress = (nowMs - startMs).toFloat() / current.durationMs
        return poseOf(current, progress)
    }

    private fun gap(bored: Boolean): Int =
        if (bored) random.nextInt(BORED_GAP_MIN_MS, BORED_GAP_MAX_MS) else random.nextInt(GAP_MIN_MS, GAP_MAX_MS)

    private fun pick(bored: Boolean): IdleAct {
        val roll = random.nextInt(100)
        return if (bored) {
            when {
                roll < 30 -> IdleAct.GLANCE
                roll < 60 -> IdleAct.SQUINT
                else -> IdleAct.YAWN
            }
        } else {
            when {
                roll < 42 -> IdleAct.DOUBLE_BLINK
                roll < 76 -> IdleAct.GLANCE
                roll < 90 -> IdleAct.WINK
                else -> IdleAct.SQUINT
            }
        }
    }

    private fun poseOf(act: IdleAct, progress: Float): IdlePose {
        val once = sin(PI * progress).toFloat()
        val twice = abs(sin(2 * PI * progress)).toFloat()
        return when (act) {
            IdleAct.DOUBLE_BLINK -> (1f - twice).let { IdlePose(it, it) }
            IdleAct.GLANCE -> IdlePose(glanceX = side * GLANCE_DP * once)
            IdleAct.WINK -> if (side < 0) IdlePose(leftOpen = 1f - once) else IdlePose(rightOpen = 1f - once)
            IdleAct.SQUINT -> (1f - SQUINT_DEPTH * twice).let { IdlePose(it, it) }
            IdleAct.YAWN -> (1f - YAWN_DEPTH * once).let { IdlePose(it, it) }
        }
    }
}
