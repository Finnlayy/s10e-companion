package dev.s10e.companion.kid

import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.experimental.and

sealed interface PinResult {
    object Ok : PinResult
    object Wrong : PinResult
    data class Locked(val untilMs: Long) : PinResult
}

class PinStore(private val prefs: KeyValue) {
    fun set(pin: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val saltHex = salt.joinToString("") { "%02x".format(it) }

        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        val hash = md.digest(pin.toByteArray(Charsets.UTF_8))
        val hashHex = hash.joinToString("") { "%02x".format(it) }

        prefs.setString("pin_salt", saltHex)
        prefs.setString("pin_hash", hashHex)
        prefs.setLong("pin_failures", 0L)
        prefs.setLong("pin_lockout_until", 0L)
    }

    fun isSet(): Boolean = prefs.getString("pin_hash", null) != null

    fun verify(pin: String, nowMs: Long): PinResult {
        val lockoutUntil = prefs.getLong("pin_lockout_until", 0L)
        if (nowMs < lockoutUntil) {
            return PinResult.Locked(lockoutUntil)
        }

        val saltHex = prefs.getString("pin_salt", null)
        val hashHex = prefs.getString("pin_hash", null)

        if (saltHex == null || hashHex == null) {
            return PinResult.Wrong
        }

        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        val attemptHash = md.digest(pin.toByteArray(Charsets.UTF_8))
        val attemptHashHex = attemptHash.joinToString("") { "%02x".format(it) }

        if (attemptHashHex == hashHex) {
            prefs.setLong("pin_failures", 0L)
            prefs.setLong("pin_lockout_until", 0L)
            return PinResult.Ok
        } else {
            val failures = prefs.getLong("pin_failures", 0L) + 1
            prefs.setLong("pin_failures", failures)
            if (failures >= 5) {
                val until = nowMs + 30_000L
                prefs.setLong("pin_lockout_until", until)
                return PinResult.Locked(until)
            }
            return PinResult.Wrong
        }
    }
}
