package dev.s10e.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.s10e.companion.ui.AppRoot
import dev.s10e.companion.kid.CrashGuard
import dev.s10e.companion.kid.SharedPrefsKeyValue
import dev.s10e.companion.eyes.EyesCanvas
import dev.s10e.companion.eyes.Mood

const val APP_NAME = "S10e Companion"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashGuard = CrashGuard(SharedPrefsKeyValue(this))
        crashGuard.recordStart(System.currentTimeMillis())

        if (crashGuard.inSafeMode(System.currentTimeMillis())) {
            setContent {
                // Safe mode: UI only, no speech/agent loops
                EyesCanvas(mood = Mood.NEUTRAL)
            }
        } else {
            setContent {
                AppRoot()
            }
        }
    }
}
