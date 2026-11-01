package dev.s10e.companion.llm

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NetworkConfigTest {
    @Test
    fun networkConfig_allowsOnlyLoopback() {
        val file = File("src/main/res/xml/network_security_config.xml")
        val content = file.readText()
        assertTrue(content.contains("<domain includeSubdomains=\"true\">127.0.0.1</domain>"))
        assertTrue(content.contains("<base-config cleartextTrafficPermitted=\"false\" />"))
    }
}
