package dev.s10e.companion

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
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
        lifecycleScope.launch {
            Log.i("S10eCompanion", LlmBootstrap(this@MainActivity).start())
        }
    }
}
