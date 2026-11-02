package dev.s10e.companion

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.s10e.companion.llm.LlmBootstrap
import dev.s10e.companion.ui.AppRoot
import kotlinx.coroutines.launch

val APP_NAME = "S10e Companion"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppRoot()
        }
        // Android verbietet den Dienststart aus dem Hintergrund, der Import kann aber länger dauern als der Besuch.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.i("S10eCompanion", LlmBootstrap(this@MainActivity).start())
            }
        }
    }
}
