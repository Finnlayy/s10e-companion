package dev.s10e.companion.kid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryKeyValue : KeyValue {
    val store = mutableMapOf<String, String>()
    override fun getString(key: String, defValue: String?): String? = store[key] ?: defValue
    override fun setString(key: String, value: String) { store[key] = value }
    override fun getLong(key: String, defValue: Long): Long = store[key]?.toLongOrNull() ?: defValue
    override fun setLong(key: String, value: Long) { store[key] = value.toString() }
}

class PinStoreTest {
    @Test
    fun pin_storedAsSaltedHash() {
        val prefs = MemoryKeyValue()
        val pinStore = PinStore(prefs)
        pinStore.set("1234")

        val storedHash = prefs.getString("pin_hash", null)
        val storedSalt = prefs.getString("pin_salt", null)
        assertTrue(storedHash != null && storedHash != "1234")
        assertTrue(storedSalt != null)
    }

    @Test
    fun pin_lockoutAfter5Wrong_30s() {
        val prefs = MemoryKeyValue()
        val pinStore = PinStore(prefs)
        pinStore.set("1234")
        val nowMs = 1000L

        assertEquals(PinResult.Wrong, pinStore.verify("1111", nowMs))
        assertEquals(PinResult.Wrong, pinStore.verify("1111", nowMs))
        assertEquals(PinResult.Wrong, pinStore.verify("1111", nowMs))
        assertEquals(PinResult.Wrong, pinStore.verify("1111", nowMs))

        val lockoutResult = pinStore.verify("1111", nowMs)
        assertTrue(lockoutResult is PinResult.Locked)
        assertEquals(nowMs + 30_000L, (lockoutResult as PinResult.Locked).untilMs)

        // Still locked out even with correct PIN
        assertEquals(PinResult.Locked(nowMs + 30_000L), pinStore.verify("1234", nowMs + 1000L))

        // Correct pin works after lockout
        assertEquals(PinResult.Ok, pinStore.verify("1234", nowMs + 30_001L))
    }
}
