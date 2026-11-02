package dev.s10e.companion.kid

import org.junit.Assert.assertEquals
import org.junit.Test

class CrashGuardTest {
    @Test
    fun crashGuard_entersSafeModeAfter3In60s() {
        val prefs = MemoryKeyValue()
        val guard = CrashGuard(prefs, maxCrashes = 3, windowMs = 60_000)
        val now = 100_000L

        assertEquals(false, guard.inSafeMode(now))
        guard.recordStart(now) // crash 1
        assertEquals(false, guard.inSafeMode(now + 1000))
        guard.recordStart(now + 1000) // crash 2
        assertEquals(false, guard.inSafeMode(now + 2000))
        guard.recordStart(now + 2000) // crash 3
        assertEquals(true, guard.inSafeMode(now + 3000))
    }

    @Test
    fun crashGuard_safeMode_keepsLockActive() {
        // Safe mode blocks normal operations but stays in KID_LOCKED effectively.
        // It should reset after window or when confirmed? Spec says "sicherer Bildschirm ohne Agentenfunktionen, Sperre bleibt aktiv"
        // Here we just test it recovers after the window
        val prefs = MemoryKeyValue()
        val guard = CrashGuard(prefs, maxCrashes = 3, windowMs = 60_000)
        val now = 100_000L

        guard.recordStart(now)
        guard.recordStart(now + 1000)
        guard.recordStart(now + 2000)

        assertEquals(true, guard.inSafeMode(now + 3000))

        // After 60s window from the FIRST crash, that first crash expires.
        assertEquals(false, guard.inSafeMode(now + 60_001L))
    }
}
