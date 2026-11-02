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
import androidx.compose.runtime.mutableIntStateOf
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
import dev.s10e.companion.eyes.EyesCanvas
import dev.s10e.companion.eyes.Mood
import dev.s10e.companion.eyes.ShakeSensor

import kotlinx.coroutines.delay
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import dev.s10e.companion.llm.ChatMessage
import dev.s10e.companion.llm.LlmClientImpl
import dev.s10e.companion.llm.LlmEvent
import dev.s10e.companion.speech.PiperSpeaker
import dev.s10e.companion.speech.SpeechController
import dev.s10e.companion.speech.VoiceIntent
import dev.s10e.companion.speech.VoskRecognizer
import dev.s10e.companion.speech.classify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.io.File

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val activity = context as? Activity

    val modeMachine = remember { ModeMachine(AppMode.PASSIVE) }
    var currentMode by remember { mutableStateOf(modeMachine.mode) }
    var mood by remember { mutableStateOf(Mood.NEUTRAL) }
    var shakes by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    var speechController by remember { mutableStateOf<SpeechController?>(null) }
    var voskRecognizer by remember { mutableStateOf<VoskRecognizer?>(null) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Ignored
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        withContext(Dispatchers.IO) {
            try {
                val modelsDir = File(context.filesDir, "models")
                val voskModelDir = File(modelsDir, "vosk-model-small-de-0.15")
                val piperModelDir = File(modelsDir, "vits-piper-de_DE-thorsten-medium")

                if (voskModelDir.exists() && piperModelDir.exists()) {
                    val model = Model(voskModelDir.absolutePath)
                    val vRecognizer = VoskRecognizer(model)
                    voskRecognizer = vRecognizer
                    val pSpeaker = PiperSpeaker(context.assets, piperModelDir.absolutePath)
                    speechController = SpeechController(vRecognizer, pSpeaker)
                } else {
                    Log.w("AppRoot", "Speech models missing. Continuing without speech.")
                }
            } catch (e: Exception) {
                Log.w("AppRoot", "Error loading speech models: ${e.message}")
            }
        }
    }

    LaunchedEffect(voskRecognizer, currentMode) {
        val rec = voskRecognizer ?: return@LaunchedEffect
        val grammar = if (currentMode == AppMode.KID_LOCKED) {
            listOf("agentenmodus aktivieren", "agenten modus aktivieren", "[unk]")
        } else {
            null
        }
        speechController?.setGrammar(grammar) ?: rec.start(grammar)

        try {
            rec.results.collectLatest { transcript ->
                val intent = classify(transcript, currentMode == AppMode.KID_LOCKED)
                when (intent) {
                    VoiceIntent.UnlockPhrase -> {
                        currentMode = modeMachine.onEvent(ModeEvent.EnterAgent)
                    }
                    is VoiceIntent.Free -> {
                        if (mood == Mood.LISTENING) {
                            mood = Mood.THINKING
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val client = LlmClientImpl()
                                    var fullReply = ""
                                    client.chat(listOf(ChatMessage("user", intent.text))).collect { event ->
                                        when (event) {
                                            is LlmEvent.Token -> fullReply += event.text
                                            is LlmEvent.Done -> {
                                                speechController?.say(fullReply)
                                                mood = Mood.NEUTRAL
                                            }
                                            is LlmEvent.Failed -> mood = Mood.NEUTRAL
                                            else -> {}
                                        }
                                    }
                                } catch (e: Exception) {
                                    mood = Mood.NEUTRAL
                                }
                            }
                        }
                    }
                    VoiceIntent.Ignored -> {}
                    VoiceIntent.EnterKidMode -> {}
                }
            }
        } finally {
            rec.stop()
        }
    }

    ShakeSensor { shakes++ }

    LaunchedEffect(shakes) {
        if (shakes > 0) {
            mood = Mood.DIZZY
            delay(3600)
            mood = Mood.ANGRY
            delay(2200)
            mood = Mood.NEUTRAL
        }
    }

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
                mood = Mood.LISTENING
                val event = if (currentMode == AppMode.PASSIVE) ModeEvent.EnterAgent else ModeEvent.Interaction(System.currentTimeMillis())
                currentMode = modeMachine.onEvent(event)
            },
        contentAlignment = Alignment.Center
    ) {
        when (currentMode) {
            AppMode.PASSIVE -> EyesCanvas(mood)
            AppMode.AGENT -> Text(text = "[Terminal Placeholder]", color = Color.White)
            AppMode.KID_LOCKED -> EyesCanvas(mood)
        }
    }
}
