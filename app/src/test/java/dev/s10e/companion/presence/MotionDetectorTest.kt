package dev.s10e.companion.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MotionDetectorTest {

    @Test
    fun motion_firstFrameReturnsNull() {
        val detector = MotionDetector()
        val frame = ByteArray(100) { 100 }
        assertNull(detector.onFrame(frame, 10, 10))
    }

    @Test
    fun motion_belowThreshold_null() {
        val detector = MotionDetector(pixelDelta = 55, minPixels = 18)
        val frame1 = ByteArray(100) { 100 }
        detector.onFrame(frame1, 10, 10)

        // Only change 10 pixels, below minPixels of 18
        val frame2 = ByteArray(100) { 100 }
        for (i in 0 until 10) {
            frame2[i] = (100 + 60).toByte()
        }

        assertNull(detector.onFrame(frame2, 10, 10))
    }

    @Test
    fun motion_centerMirrorsForFrontCamera() {
        val detector = MotionDetector(pixelDelta = 55, minPixels = 18)
        val frame1 = ByteArray(100) { 100 }
        detector.onFrame(frame1, 10, 10)

        val frame2 = ByteArray(100) { 100 }
        // Change left side (x=0, 1) -> width=10
        // (x=0, y=0..9) and (x=1, y=0..9) = 20 pixels > 18 threshold
        for (y in 0 until 10) {
            frame2[y * 10 + 0] = (100 + 60).toByte()
            frame2[y * 10 + 1] = (100 + 60).toByte()
        }

        val motion = detector.onFrame(frame2, 10, 10)!!
        // Original avgX = (0+1)/2 = 0.5. Normalized = 0.5/10 = 0.05
        // Mirrored = 1f - 0.05 = 0.95
        assertEquals(0.95f, motion.centerX, 0.01f)
    }

    @Test
    fun shrinkUpright_keepsCornerAfterRotation() {
        // 4x2-Bild, nur das Pixel oben links ist hell.
        val luma = ByteArray(8)
        luma[0] = 100
        assertEquals(100, shrinkUpright(luma, 4, 2, 0, 4, 2)[0].toInt())
        // 90° im Uhrzeigersinn bringt oben links nach oben rechts.
        assertEquals(100, shrinkUpright(luma, 4, 2, 90, 2, 4)[1].toInt())
        // 180° bringt es nach unten rechts.
        assertEquals(100, shrinkUpright(luma, 4, 2, 180, 4, 2)[7].toInt())
        // 270° bringt es nach unten links.
        assertEquals(100, shrinkUpright(luma, 4, 2, 270, 2, 4)[6].toInt())
    }

    @Test
    fun defaults_detectHandOnGrid() {
        val detector = MotionDetector()
        val before = ByteArray(GRID_WIDTH * GRID_HEIGHT) { 40 }
        val after = before.clone()
        // Eine Hand deckt 10 x 10 Rasterpunkte ab und ist 30 Stufen heller.
        for (y in 20 until 30) for (x in 5 until 15) after[y * GRID_WIDTH + x] = 70
        detector.onFrame(before, GRID_WIDTH, GRID_HEIGHT)
        assertEquals(100, detector.onFrame(after, GRID_WIDTH, GRID_HEIGHT)!!.pixels)
    }
}
