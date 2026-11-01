package dev.s10e.companion.eyes

import kotlin.math.sqrt

class ShakeDetector(val threshold: Float = 23f, val trigger: Float = 48f, val decay: Float = 5f) {
    private var accumulator = 0f

    fun onSample(x: Float, y: Float, z: Float): Boolean {
        val len = sqrt(x * x + y * y + z * z)

        if (len > threshold) {
            accumulator += (len - threshold)
        } else {
            accumulator -= decay
            if (accumulator < 0f) {
                accumulator = 0f
            }
        }

        if (accumulator > trigger) {
            accumulator = 0f
            return true
        }

        return false
    }
}
