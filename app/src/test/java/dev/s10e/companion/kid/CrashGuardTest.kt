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
        val prefs = MemoryKeyValue()
        KidState(prefs).locked = true
        val guard = CrashGuard(prefs, maxCrashes = 3, windowMs = 60_000)
        val now = 100_000L

        guard.recordStart(now)
        guard.recordStart(now + 1000)
        guard.recordStart(now + 2000)

        assertEquals(true, guard.inSafeMode(now + 3000))
        assertEquals(true, KidState(prefs).locked)

        // After 60s window from the FIRST crash, that first crash expires.
        assertEquals(false, guard.inSafeMode(now + 60_001L))
    }
}
