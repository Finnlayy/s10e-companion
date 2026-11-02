package dev.s10e.companion.eyes

import kotlin.math.sqrt

/** Werte für lineare Beschleunigung bei 50 Hz. Die 23/48/5 des Prototyps galten für Differenzen mit Schwerkraft. */
class ShakeDetector(val threshold: Float = 10f, val trigger: Float = 20f, val decay: Float = 1f) {
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
