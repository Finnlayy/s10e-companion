package dev.s10e.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun appName_isSet() {
        assertEquals("S10e Companion", APP_NAME)
    }
}
