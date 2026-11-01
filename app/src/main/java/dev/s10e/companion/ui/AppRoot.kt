package dev.s10e.companion.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.s10e.companion.core.AppMode
import dev.s10e.companion.core.ModeEvent
import dev.s10e.companion.core.ModeMachine
import kotlinx.coroutines.delay

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val activity = context as? Activity

    val modeMachine = remember { ModeMachine(AppMode.PASSIVE) }
    var currentMode by remember { mutableStateOf(modeMachine.mode) }

    DisposableEffect(activity) {
        if (activity != null) {
            val window = activity.window
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentMode = modeMachine.onEvent(ModeEvent.Tick(System.currentTimeMillis()))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable {
                currentMode = modeMachine.onEvent(ModeEvent.Interaction(System.currentTimeMillis()))
            },
        contentAlignment = Alignment.Center
    ) {
        when (currentMode) {
            AppMode.PASSIVE -> Text(text = "[Eyes Placeholder]", color = Color.White)
            AppMode.AGENT -> Text(text = "[Terminal Placeholder]", color = Color.White)
            AppMode.KID_LOCKED -> Text(text = "[Kid Mode Placeholder]", color = Color.White)
        }
    }
}
