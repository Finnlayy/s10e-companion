package dev.s10e.companion.eyes

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchReactionsTest {

    @Test
    fun classifyTouch_followsFingerPath() {
        assertEquals(TouchKind.TAP, classifyTouch(0f))
        assertEquals(TouchKind.TAP, classifyTouch(24f))
        assertEquals(TouchKind.NONE, classifyTouch(120f))
        assertEquals(TouchKind.STROKE, classifyTouch(200f))
    }

    @Test
    fun onPoke_surprisesFirst() {
        assertEquals(Mood.SURPRISED, PokeCounter().onPoke(0L))
    }

    @Test
    fun onPoke_getsAngryWhenRepeatedQuickly() {
        val counter = PokeCounter()
        repeat(3) { counter.onPoke(it * 500L) }
        assertEquals(Mood.ANGRY, counter.onPoke(1_500L))
    }

    @Test
    fun onPoke_forgetsOldPokes() {
        val counter = PokeCounter()
        repeat(3) { counter.onPoke(it * 500L) }
        assertEquals(Mood.SURPRISED, counter.onPoke(20_000L))
    }
}
