package dev.s10e.companion.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ModeMachineTest {

    @Test
    fun idle30s_switchesAgentToPassive() {
        val machine = ModeMachine(AppMode.AGENT, 30_000)

        machine.onEvent(ModeEvent.Tick(0)) // start time initialized to 0

        // At 29.999s, remains AGENT
        val mode1 = machine.onEvent(ModeEvent.Tick(29_999))
        assertEquals(AppMode.AGENT, mode1)

        // At 30.000s, switches to PASSIVE
        val mode2 = machine.onEvent(ModeEvent.Tick(30_000))
        assertEquals(AppMode.PASSIVE, mode2)
    }

    @Test
    fun interactionResetsIdle() {
        val machine = ModeMachine(AppMode.AGENT, 30_000)

        machine.onEvent(ModeEvent.Tick(0)) // start time initialized to 0

        // Interaction at 15s
        val mode1 = machine.onEvent(ModeEvent.Interaction(15_000))
        assertEquals(AppMode.AGENT, mode1)

        // At 30s it's only been 15s since interaction
        val mode2 = machine.onEvent(ModeEvent.Tick(30_000))
        assertEquals(AppMode.AGENT, mode2)

        // At 45s it switches
        val mode3 = machine.onEvent(ModeEvent.Tick(45_000))
        assertEquals(AppMode.PASSIVE, mode3)
    }

    @Test
    fun kidLocked_ignoresEnterAgent() {
        val machine = ModeMachine(AppMode.KID_LOCKED)

        val mode = machine.onEvent(ModeEvent.EnterAgent)
        assertEquals(AppMode.KID_LOCKED, mode)
    }

    @Test
    fun kidLocked_leavesOnlyViaUnlockConfirmed() {
        val machine = ModeMachine(AppMode.KID_LOCKED)

        val mode = machine.onEvent(ModeEvent.UnlockConfirmed)
        assertEquals(AppMode.PASSIVE, mode)
    }
}
