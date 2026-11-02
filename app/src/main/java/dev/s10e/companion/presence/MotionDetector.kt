package dev.s10e.companion.presence

import kotlin.math.abs

data class Motion(val centerX: Float, val centerY: Float, val pixels: Int)

const val GRID_WIDTH = 48
const val GRID_HEIGHT = 64

/** Dreht das Kamerabild aufrecht und verkleinert es auf das Raster, für das die Schwellen gelten. */
fun shrinkUpright(luma: ByteArray, w: Int, h: Int, rotationDegrees: Int, outW: Int = GRID_WIDTH, outH: Int = GRID_HEIGHT): ByteArray {
    val uprightW = if (rotationDegrees % 180 == 0) w else h
    val uprightH = if (rotationDegrees % 180 == 0) h else w
    val out = ByteArray(outW * outH)
    for (dy in 0 until outH) {
        val uy = (dy * 2 + 1) * uprightH / (2 * outH)
        for (dx in 0 until outW) {
            val ux = (dx * 2 + 1) * uprightW / (2 * outW)
            val index = when (rotationDegrees) {
                90 -> (h - 1 - ux) * w + uy
                180 -> (h - 1 - uy) * w + (w - 1 - ux)
                270 -> ux * w + (w - 1 - uy)
                else -> uy * w + ux
            }
            out[dy * outW + dx] = luma[index]
        }
    }
    return out
}

/** Der Prototyp summierte Rot und Grün gegen 55, auf einem Kanal entspricht das 28. 72 sind 2,3 % des Rasters wie dort. */
class MotionDetector(val pixelDelta: Int = 28, val minPixels: Int = 72) {
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
