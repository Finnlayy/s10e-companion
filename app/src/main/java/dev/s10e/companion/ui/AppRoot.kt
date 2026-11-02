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
import dev.s10e.companion.kid.LockController

import kotlinx.coroutines.delay
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import dev.s10e.companion.llm.ChatMessage
import dev.s10e.companion.llm.LlmClientImpl
import dev.s10e.companion.llm.LlmEvent
import dev.s10e.companion.speech.SpeechBootstrap
import dev.s10e.companion.speech.SpeechStack
import dev.s10e.companion.speech.UNLOCK_GRAMMAR
import dev.s10e.companion.speech.VoiceIntent
import dev.s10e.companion.speech.classify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.pointer.pointerInput
import dev.s10e.companion.kid.KidLockPolicy
import dev.s10e.companion.kid.KidState
import dev.s10e.companion.kid.UnlockStep
import dev.s10e.companion.kid.PinStore
import dev.s10e.companion.kid.SharedPrefsKeyValue
import dev.s10e.companion.kid.PinResult
import dev.s10e.companion.vault.VaultStore

private enum class PinScreen { SET_FOR_KID_MODE, UNLOCK, DEVICE_OWNER }

private const val LISTEN_TIMEOUT_MS = 8_000L
private const val PIN_GESTURE_MS = 5_000L
private const val SYSTEM_PROMPT = "Du bist Apollo, ein kleiner Schreibtisch-Begleiter. Antworte kurz und auf Deutsch."

private suspend fun answer(context: Context, stack: SpeechStack, question: String) {
    val apiKey = File(context.filesDir, "api_key.txt")
    val client = LlmClientImpl(apiKeyProvider = { apiKey.readText().trim() })
    val reply = StringBuilder()
    client.chat(listOf(ChatMessage("system", SYSTEM_PROMPT), ChatMessage("user", question))).collect { event ->
        when (event) {
            is LlmEvent.Token -> reply.append(event.text)
            is LlmEvent.Failed -> Log.w("S10eCompanion", "LLM-Fehler: ${event.message}")
            else -> {}
        }
    }
    try {
        stack.controller.say(reply.toString())
    } catch (e: Exception) {
        Log.w("S10eCompanion", "Sprachausgabe fehlgeschlagen: ${e.message}")
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val activity = context as? Activity

    val kidState = remember { KidState(SharedPrefsKeyValue(context)) }
    val modeMachine = remember { ModeMachine(if (kidState.locked) AppMode.KID_LOCKED else AppMode.PASSIVE) }
    var currentMode by remember { mutableStateOf(modeMachine.mode) }
    var mood by remember { mutableStateOf(Mood.NEUTRAL) }
    var shakes by remember { mutableIntStateOf(0) }
    var pinScreen by remember { mutableStateOf<PinScreen?>(null) }
    var pinMessage by remember { mutableStateOf<String?>(null) }

    val lockController = remember { activity?.let { LockController(it) } }
    val pinStore = remember { PinStore(SharedPrefsKeyValue(context)) }
    val policy = remember { KidLockPolicy() }

    fun openPin(screen: PinScreen) {
        pinMessage = null
        pinScreen = screen
    }

    fun enterKidMode() {
        if (pinStore.isSet()) currentMode = modeMachine.onEvent(ModeEvent.EnterKidMode) else openPin(PinScreen.SET_FOR_KID_MODE)
    }

    val scope = rememberCoroutineScope()
    var speech by remember { mutableStateOf<SpeechStack?>(null) }
    var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val lifecycleState by (context as LifecycleOwner).lifecycle.currentStateFlow.collectAsState()
    val visible = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        micGranted = isGranted
    }

    LaunchedEffect(Unit) {
        if (!micGranted) requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        speech = SpeechBootstrap(context).load()
    }

    LaunchedEffect(currentMode) {
        kidState.locked = currentMode == AppMode.KID_LOCKED
        if (currentMode == AppMode.KID_LOCKED) {
            lockController?.enterLockMode()
        } else {
            lockController?.exitLockMode()
        }
    }

    LaunchedEffect(speech, currentMode, micGranted, visible) {
        val stack = speech ?: return@LaunchedEffect
        if (!micGranted || !visible) return@LaunchedEffect
        stack.controller.setGrammar(if (currentMode == AppMode.AGENT) null else UNLOCK_GRAMMAR)

        val vaultStore = VaultStore(File("/sdcard/Documents/ObsidianVault/"))

        try {
            stack.recognizer.results.collect { transcript ->
                when (val intent = classify(transcript, currentMode == AppMode.KID_LOCKED)) {
                    VoiceIntent.UnlockPhrase -> {
                        if (currentMode == AppMode.KID_LOCKED) {
                            if (policy.onPhrase() == UnlockStep.SHOW_PIN) {
                                openPin(PinScreen.UNLOCK)
                            } else {
                                currentMode = modeMachine.onEvent(ModeEvent.UnlockConfirmed)
                            }
                        } else {
                            currentMode = modeMachine.onEvent(ModeEvent.EnterAgent)
                        }
                    }
                    is VoiceIntent.Free -> if (mood == Mood.LISTENING) {
                        currentMode = modeMachine.onEvent(ModeEvent.Interaction(System.currentTimeMillis()))
                        val text = intent.text
                        if (text.startsWith("Notiz", ignoreCase = true) || text.startsWith("Aufgabe", ignoreCase = true)) {
                            scope.launch(Dispatchers.IO) {
                                vaultStore.appendInbox(text, System.currentTimeMillis())
                            }
                            mood = Mood.NEUTRAL
                        } else {
                            mood = Mood.THINKING
                            scope.launch(Dispatchers.IO) {
                                answer(context, stack, text)
                                mood = Mood.NEUTRAL
                            }
                        }
                    }
                    VoiceIntent.Ignored -> {}
                    VoiceIntent.EnterKidMode -> enterKidMode()
                }
            }
        } finally {
            stack.controller.stopListening()
        }
    }

    LaunchedEffect(mood) {
        if (mood == Mood.LISTENING) {
            delay(LISTEN_TIMEOUT_MS)
            mood = Mood.NEUTRAL
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

    val screen = pinScreen
    if (screen != null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (screen == PinScreen.DEVICE_OWNER) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = {
                        lockController?.clearDeviceOwnerApp()
                        pinScreen = null
                    }) { Text("Gerätebesitz aufheben") }
                    TextButton(onClick = { pinScreen = null }) { Text("Fertig") }
                }
                return@Box
            }
            PinPad(
                title = if (screen == PinScreen.UNLOCK) "PIN eingeben" else "Neue PIN festlegen",
                message = pinMessage,
                onSubmit = { pin ->
                    if (screen == PinScreen.SET_FOR_KID_MODE) {
                        pinStore.set(pin)
                        pinScreen = null
                        currentMode = modeMachine.onEvent(ModeEvent.EnterKidMode)
                    } else {
                        when (val result = pinStore.verify(pin, System.currentTimeMillis())) {
                            PinResult.Ok -> {
                                currentMode = modeMachine.onEvent(ModeEvent.UnlockConfirmed)
                                pinScreen = if (lockController?.isDeviceOwner() == true) PinScreen.DEVICE_OWNER else null
                            }
                            PinResult.Wrong -> pinMessage = "Falsche PIN"
                            is PinResult.Locked -> pinMessage = "Gesperrt für ${(result.untilMs - System.currentTimeMillis()) / 1000 + 1} s"
                        }
                    }
                },
                onCancel = { pinScreen = null }
            )
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable {
                if (currentMode != AppMode.KID_LOCKED) mood = Mood.LISTENING
                val event = if (currentMode == AppMode.PASSIVE) ModeEvent.EnterAgent else ModeEvent.Interaction(System.currentTimeMillis())
                currentMode = modeMachine.onEvent(event)
            },
        contentAlignment = Alignment.Center
    ) {
        when (currentMode) {
            AppMode.PASSIVE -> EyesCanvas(mood)
            AppMode.AGENT -> ApolloTerminal(
                onMoodChange = { mood = it },
                onEnterKidMode = { enterKidMode() },
                onInteraction = {
                    currentMode = modeMachine.onEvent(ModeEvent.Interaction(System.currentTimeMillis()))
                }
            )
            AppMode.KID_LOCKED -> EyesCanvas(mood)
        }
        // Versteckter Ausweg, er funktioniert auch ohne Mikrofon und Erkennung.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxSize(0.2f)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        val released = withTimeoutOrNull(PIN_GESTURE_MS) { waitForUpOrCancellation() ?: Unit }
                        if (released == null) openPin(if (pinStore.isSet()) PinScreen.UNLOCK else PinScreen.SET_FOR_KID_MODE)
                    }
                }
        )
    }
}
