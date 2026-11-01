package dev.s10e.companion.eyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EyeModelTest {

    @Test
    fun eyeFrame_angry_mirrorsRotation() {
        val frame = eyeFrame(Mood.ANGRY, 0L)
        assertEquals(24f, frame.left.rotationDeg, 0.01f)
        assertEquals(-24f, frame.right.rotationDeg, 0.01f)
    }

    @Test
    fun eyeFrame_isPureFunctionOfTime() {
        val f1 = eyeFrame(Mood.NEUTRAL, 1000L, 0.5f, 0.5f)
        val f2 = eyeFrame(Mood.NEUTRAL, 1000L, 0.5f, 0.5f)
        assertEquals(f1, f2)
    }

    @Test
    fun eyeFrame_blinkLastsAbout130ms() {
        var blinkStart = -1L
        for (i in 0..10000) {
            val t = i * 16_000_000L
            val f = eyeFrame(Mood.NEUTRAL, t)
            if (f.left.height < 148f) {
                blinkStart = t
                break
            }
        }
        assertTrue("Blink should occur eventually", blinkStart != -1L)

        var blinkEnd = -1L
        for (i in 1..20) {
            val t = blinkStart + i * 10_000_000L
            val f = eyeFrame(Mood.NEUTRAL, t)
            if (f.left.height >= 148f) {
                blinkEnd = t
                break
            }
        }
        assertTrue("Blink should end", blinkEnd != -1L)
        val durationMs = (blinkEnd - blinkStart) / 1_000_000L
        assertTrue("Blink duration roughly 130ms. Was $durationMs", durationMs in 100..160)
    }

    @Test
    fun eyeFrame_pixelShiftStaysWithin4dp() {
        var maxShiftX = 0f
        var maxShiftY = 0f
        var minShiftX = 0f
        var minShiftY = 0f

        val step = 1000L * 1000_000_000L
        val h24 = 24L * 60 * 60 * 1000_000_000L
        var t = 0L
        while (t < h24) {
            val frame = eyeFrame(Mood.NEUTRAL, t)
            maxShiftX = maxOf(maxShiftX, frame.offsetX)
            maxShiftY = maxOf(maxShiftY, frame.offsetY)
            minShiftX = minOf(minShiftX, frame.offsetX)
            minShiftY = minOf(minShiftY, frame.offsetY)
            t += step
        }
        assertTrue(maxShiftX <= 4f)
        assertTrue(maxShiftY <= 4f)
        assertTrue(minShiftX >= -4f)
        assertTrue(minShiftY >= -4f)
    }
}
