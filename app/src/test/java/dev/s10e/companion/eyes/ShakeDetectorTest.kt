package dev.s10e.companion.eyes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectorTest {

    @Test
    fun shake_triggersOnlyAboveAccumulator() {
        val detector = ShakeDetector(threshold = 23f, trigger = 48f, decay = 5f)
        
        assertFalse(detector.onSample(10f, 10f, 10f)) 
        assertFalse(detector.onSample(10f, 10f, 10f)) 

        assertFalse(detector.onSample(20f, 20f, 20f)) 
        assertFalse(detector.onSample(30f, 30f, 30f))  
        assertTrue(detector.onSample(30f, 30f, 30f))
    }

    @Test
    fun shake_decaysBetweenBumps() {
        val detector = ShakeDetector(threshold = 23f, trigger = 48f, decay = 5f)
        
        assertFalse(detector.onSample(20f, 20f, 20f)) 
        assertFalse(detector.onSample(0f, 0f, 0f))    
        assertFalse(detector.onSample(0f, 0f, 0f))    
        assertFalse(detector.onSample(0f, 0f, 0f))    
        
        assertFalse(detector.onSample(20f, 20f, 20f)) 
        assertFalse(detector.onSample(30f, 30f, 30f)) 
        assertFalse(detector.onSample(0f, 0f, 0f))    
    }
}
