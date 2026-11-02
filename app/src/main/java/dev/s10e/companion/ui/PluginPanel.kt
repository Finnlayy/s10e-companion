package dev.s10e.companion.ui

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
fun PluginPanel(pluginFile: File) {
    val context = LocalContext.current
    val pluginsDir = File(context.filesDir, "plugins")

    if (!pluginFile.exists()) {
        Text("Noch kein Plugin vorhanden.")
        return
    }

    val webView = remember {
        WebView(context).apply {
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.javaScriptEnabled = true

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: ""

                    // Only allow file:// urls inside the plugins directory
                    if (url.startsWith("file://")) {
                        val requestedPath = url.removePrefix("file://")
                        val requestedFile = File(requestedPath)
                        try {
                            if (!requestedFile.canonicalPath.startsWith(pluginsDir.canonicalPath + File.separator)) {
                                return WebResourceResponse("text/plain", "UTF-8", null)
                            }
                        } catch (e: Exception) {
                            return WebResourceResponse("text/plain", "UTF-8", null)
                        }
                    } else {
                        // Block all non-file requests
                        return WebResourceResponse("text/plain", "UTF-8", null)
                    }

                    return super.shouldInterceptRequest(view, request)
                }
            }
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    ) { view ->
        try {
            if (pluginFile.canonicalPath.startsWith(pluginsDir.canonicalPath + File.separator)) {
                view.loadUrl("file://${pluginFile.absolutePath}")
            }
        } catch (e: Exception) {
            // Ignore escaping attempts
        }
    }
}
