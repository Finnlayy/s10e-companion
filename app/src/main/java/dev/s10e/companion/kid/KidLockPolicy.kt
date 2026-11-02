package dev.s10e.companion.kid

import dev.s10e.companion.core.AppMode

enum class UnlockStep {
    SHOW_PIN, UNLOCK
}

class KidLockPolicy(private val pinRequiredAfterPhrase: Boolean = true) {
    fun onPhrase(): UnlockStep {
        return if (pinRequiredAfterPhrase) {
            UnlockStep.SHOW_PIN
        } else {
            UnlockStep.UNLOCK
        }
    }

    fun toolsAllowed(mode: AppMode): Boolean {
        return mode != AppMode.KID_LOCKED
    }
}
