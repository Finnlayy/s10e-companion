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

    @Test
    fun defaults_triggerOnRealShake() {
        val detector = ShakeDetector()
        // Angenommener Verlauf bei 50 Hz, die Schwelle ist am Gerät noch nicht bestätigt.
        val shaking = List(30) { i -> if (i % 3 == 0) 0f else 15f }
        assertTrue(shaking.any { detector.onSample(it, 0f, 0f) })
    }

    @Test
    fun defaults_ignoreLiftAndSingleBump() {
        val detector = ShakeDetector()
        // Der Testlauf am S10e blieb meist unter 7 m/s², mit einzelnen Spitzen bei 13 m/s².
        val lift = List(50) { 6.5f } + listOf(13f, 13f) + List(50) { 1f }
        assertFalse(lift.any { detector.onSample(it, 0f, 0f) })
    }
}
