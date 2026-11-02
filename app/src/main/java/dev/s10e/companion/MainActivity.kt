package dev.s10e.companion

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.s10e.companion.kid.CrashGuard
import dev.s10e.companion.kid.KidState
import dev.s10e.companion.kid.LockController
import dev.s10e.companion.kid.SharedPrefsKeyValue
import dev.s10e.companion.eyes.EyesCanvas
import dev.s10e.companion.eyes.Mood
import dev.s10e.companion.llm.LlmBootstrap
import dev.s10e.companion.ui.AppRoot
import kotlinx.coroutines.launch

const val APP_NAME = "S10e Companion"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = SharedPrefsKeyValue(this)
        val crashGuard = CrashGuard(prefs)
        // Ein Neuaufbau der Activity, etwa beim Drehen, ist kein Neustart.
        if (savedInstanceState == null) crashGuard.recordStart(System.currentTimeMillis())

        if (crashGuard.inSafeMode(System.currentTimeMillis())) {
            // Sicherer Bildschirm ohne Sprache und LLM, eine aktive Sperre bleibt bestehen.
            if (KidState(prefs).locked) LockController(this).enterLockMode()
            setContent {
                EyesCanvas(mood = Mood.NEUTRAL)
            }
            return
        }

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
