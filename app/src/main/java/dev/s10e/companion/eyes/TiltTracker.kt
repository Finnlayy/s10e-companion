package dev.s10e.companion.eyes

import kotlin.math.acos
import kotlin.math.sqrt

private const val GRAVITY = 9.81f
private const val LOOK_LIMIT_X_DP = 42f
private const val LOOK_LIMIT_Y_DP = 22f

data class Tilt(val lookX: Float, val lookY: Float, val pickedUp: Boolean)

/** Vergleicht die Schwerkraft mit einer langsam folgenden Ruhelage, jede Haltung wird so nach Sekunden wieder neutral. */
class TiltTracker(
    val pickupDegrees: Float = 30f,
    val settleMs: Float = 2_000f,
    val cooldownMs: Long = 5_000L
) {
    private var rest: FloatArray? = null
    private var lastMs = 0L
    private var lastPickupMs = Long.MIN_VALUE / 2

    fun onGravity(x: Float, y: Float, z: Float, nowMs: Long): Tilt {
        val base = rest
        if (base == null) {
            rest = floatArrayOf(x, y, z)
            lastMs = nowMs
            return Tilt(0f, 0f, false)
        }

        val dx = x - base[0]
        val dy = y - base[1]
        val pickedUp = angleDegrees(base, x, y, z) > pickupDegrees && nowMs - lastPickupMs > cooldownMs
        if (pickedUp) lastPickupMs = nowMs

        val elapsed = (nowMs - lastMs).toFloat()
        val follow = elapsed / (settleMs + elapsed)
        base[0] += dx * follow
        base[1] += dy * follow
        base[2] += (z - base[2]) * follow
        lastMs = nowMs

        // Die Augen rutschen zur tieferen Seite. Rechts unten macht x negativ, oben unten macht y negativ.
        return Tilt(
            lookX = (-dx / GRAVITY * 2f * LOOK_LIMIT_X_DP).coerceIn(-LOOK_LIMIT_X_DP, LOOK_LIMIT_X_DP),
            lookY = (dy / GRAVITY * 2f * LOOK_LIMIT_Y_DP).coerceIn(-LOOK_LIMIT_Y_DP, LOOK_LIMIT_Y_DP),
            pickedUp = pickedUp
        )
    }

    private fun angleDegrees(a: FloatArray, x: Float, y: Float, z: Float): Float {
        val lengths = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]) * sqrt(x * x + y * y + z * z)
        if (lengths == 0f) return 0f
        val cosine = (a[0] * x + a[1] * y + a[2] * z) / lengths
        return Math.toDegrees(acos(cosine.coerceIn(-1f, 1f)).toDouble()).toFloat()
    }
}
