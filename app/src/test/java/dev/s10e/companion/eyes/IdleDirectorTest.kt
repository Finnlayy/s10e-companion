package dev.s10e.companion.eyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class IdleDirectorTest {

    private fun poses(bored: Boolean, seconds: Int): List<IdlePose> {
        val director = IdleDirector(Random(7))
        return (0 until seconds * 60).map { director.pose(it * 1_000L / 60, bored) }
    }

    @Test
    fun pose_staysCalmUntilFirstGesture() {
        val director = IdleDirector(Random(7))
        assertEquals(IdlePose(), director.pose(0L))
        assertEquals(IdlePose(), director.pose(3_999L))
    }

    @Test
    fun pose_showsGesturesWithinAFewMinutes() {
        val all = poses(bored = false, seconds = 180)
        assertTrue(all.any { it.leftOpen < 0.5f || it.rightOpen < 0.5f })
        assertTrue(all.any { abs(it.glanceX) > 10f })
    }

    @Test
    fun pose_returnsToRestBetweenGestures() {
        assertTrue(poses(bored = false, seconds = 60).count { it == IdlePose() } > 60 * 30)
    }

    @Test
    fun pose_keepsValuesInRange() {
        poses(bored = true, seconds = 180).forEach {
            assertTrue(it.leftOpen in 0f..1f)
            assertTrue(it.rightOpen in 0f..1f)
            assertTrue(abs(it.glanceX) <= 26f)
        }
    }

    @Test
    fun pose_boredYawnsLongerThanABlink() {
        val closedFrames = poses(bored = true, seconds = 180).count { it.leftOpen < 0.5f }
        val awakeFrames = poses(bored = false, seconds = 180).count { it.leftOpen < 0.5f }
        assertTrue("gelangweilt $closedFrames, wach $awakeFrames", closedFrames > awakeFrames)
    }
}
