package dev.s10e.companion.presence

import kotlin.math.abs

data class Motion(val centerX: Float, val centerY: Float, val pixels: Int)

class MotionDetector(val pixelDelta: Int = 55, val minPixels: Int = 18) {
    private var prevFrame: ByteArray? = null

    fun onFrame(luma: ByteArray, w: Int, h: Int): Motion? {
        val prev = prevFrame
        prevFrame = luma.clone() // Store a copy of the current frame for next time

        if (prev == null || prev.size != luma.size) {
            return null
        }

        var changedPixels = 0
        var sumX = 0L
        var sumY = 0L

        for (y in 0 until h) {
            for (x in 0 until w) {
                val index = y * w + x
                val currVal = luma[index].toInt() and 0xFF
                val prevVal = prev[index].toInt() and 0xFF
                if (abs(currVal - prevVal) > pixelDelta) {
                    changedPixels++
                    sumX += x
                    sumY += y
                }
            }
        }

        if (changedPixels > minPixels) {
            val avgX = (sumX.toFloat() / changedPixels) / w.toFloat()
            val avgY = (sumY.toFloat() / changedPixels) / h.toFloat()
            // Front camera is mirrored, so we mirror the x axis here. 0 should become 1, 1 becomes 0.
            val mirroredX = 1f - avgX
            return Motion(mirroredX, avgY, changedPixels)
        }

        return null
    }
}
