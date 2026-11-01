package dev.s10e.companion.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerSupervisorTest {
    @Test
    fun backoff_is1_2_4_8_thenCapped30s() {
        val supervisor = ServerSupervisor({ throw Exception() }, {})
        assertEquals(1000L, supervisor.nextBackoffMs(0))
        assertEquals(1000L, supervisor.nextBackoffMs(1))
        assertEquals(2000L, supervisor.nextBackoffMs(2))
        assertEquals(4000L, supervisor.nextBackoffMs(3))
        assertEquals(8000L, supervisor.nextBackoffMs(4))
        assertEquals(16000L, supervisor.nextBackoffMs(5))
        assertEquals(30000L, supervisor.nextBackoffMs(6))
        assertEquals(30000L, supervisor.nextBackoffMs(10))
    }
}
