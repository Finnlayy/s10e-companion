package dev.s10e.companion.eyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TiltTrackerTest {

    @Test
    fun lyingStill_staysNeutral() {
        val tracker = TiltTracker()
        tracker.onGravity(0f, 0f, 9.81f, 0L)
        val tilt = tracker.onGravity(0f, 0f, 9.81f, 100L)
        assertEquals(0f, tilt.lookX, 0.01f)
        assertEquals(0f, tilt.lookY, 0.01f)
        assertFalse(tilt.pickedUp)
    }

    @Test
    fun rightEdgeDown_looksRight() {
        val tracker = TiltTracker()
        tracker.onGravity(0f, 0f, 9.81f, 0L)
        assertTrue(tracker.onGravity(-3f, 0f, 9.3f, 100L).lookX > 0f)
    }

    @Test
    fun liftingUpright_countsAsPickupOnce() {
        val tracker = TiltTracker()
        tracker.onGravity(0f, 0f, 9.81f, 0L)
        assertTrue(tracker.onGravity(0f, 8f, 5.6f, 100L).pickedUp)
        assertFalse(tracker.onGravity(0f, 9.81f, 0f, 200L).pickedUp)
    }

    @Test
    fun newPosture_becomesNeutralAgain() {
        val tracker = TiltTracker()
        tracker.onGravity(0f, 0f, 9.81f, 0L)
        var tilt = tracker.onGravity(0f, 9.81f, 0f, 100L)
        for (t in 1..100) tilt = tracker.onGravity(0f, 9.81f, 0f, 100L + t * 100L)
        assertEquals(0f, tilt.lookY, 0.5f)
    }
}
