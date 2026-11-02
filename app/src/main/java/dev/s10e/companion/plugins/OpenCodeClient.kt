package dev.s10e.companion.plugins

import dev.s10e.companion.kid.KeyValue
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class OpenCodeClient(private val prefs: KeyValue) {
    fun fetchServerStatus(): String {
        val host = prefs.getString("opencode_host", "127.0.0.1") ?: "127.0.0.1"
        val port = prefs.getString("opencode_port", "8000") ?: "8000"
        val password = prefs.getString("opencode_password", "") ?: ""

        if (host != "127.0.0.1") return "nicht verbunden" // only talk to loopback

        try {
            val url = URL("http://$host:$port/")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 2000
            connection.readTimeout = 2000

            if (password.isNotEmpty()) {
                val auth = Base64.getEncoder().encodeToString(":$password".toByteArray())
                connection.setRequestProperty("Authorization", "Basic $auth")
            }

            return if (connection.responseCode in 200..299) {
                "verbunden"
            } else {
                "nicht verbunden"
            }
        } catch (e: Exception) {
            return "nicht verbunden"
        }
    }
}
