package dev.s10e.companion.core

enum class AppMode { PASSIVE, AGENT, KID_LOCKED }

sealed interface ModeEvent {
    data class Interaction(val nowMs: Long) : ModeEvent
    data class Tick(val nowMs: Long) : ModeEvent
    object EnterAgent : ModeEvent
    object EnterKidMode : ModeEvent
    object UnlockConfirmed : ModeEvent
}

class ModeMachine(initial: AppMode, private val idleTimeoutMs: Long = 30_000) {
    var mode: AppMode = initial
        private set

    private var lastInteractionMs: Long? = null

    private var agentStartTimeMs: Long? = null

    fun onEvent(event: ModeEvent): AppMode {
        when (event) {
            is ModeEvent.Interaction -> {
                lastInteractionMs = event.nowMs
            }
            is ModeEvent.Tick -> {
                if (mode == AppMode.AGENT) {
                    if (agentStartTimeMs == null) {
                        agentStartTimeMs = event.nowMs
                    }

                    val referenceTime = lastInteractionMs ?: agentStartTimeMs!!
                    val idleTime = event.nowMs - referenceTime

                    if (idleTime >= idleTimeoutMs) {
                        mode = AppMode.PASSIVE
                    }
                }
            }
            ModeEvent.EnterAgent -> {
                if (mode != AppMode.KID_LOCKED) {
                    mode = AppMode.AGENT
                    lastInteractionMs = null
                    agentStartTimeMs = null
                }
            }
            ModeEvent.EnterKidMode -> {
                mode = AppMode.KID_LOCKED
            }
            ModeEvent.UnlockConfirmed -> {
                if (mode == AppMode.KID_LOCKED) {
                    mode = AppMode.PASSIVE
                }
            }
        }
        return mode
    }
}
