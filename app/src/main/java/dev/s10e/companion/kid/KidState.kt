package dev.s10e.companion.kid

/** Hält die Sperre über Neustarts hinweg, sonst befreit jeder Absturz das Gerät. */
class KidState(private val prefs: KeyValue) {
    var locked: Boolean
        get() = prefs.getLong("kid_locked", 0L) == 1L
        set(value) = prefs.setLong("kid_locked", if (value) 1L else 0L)
}
