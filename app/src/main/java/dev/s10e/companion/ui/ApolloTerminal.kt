package dev.s10e.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.s10e.companion.eyes.Mood
import dev.s10e.companion.llm.ChatMessage
import dev.s10e.companion.llm.LlmClientImpl
import dev.s10e.companion.llm.LlmEvent
import dev.s10e.companion.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

import android.content.Context
import kotlinx.coroutines.CoroutineScope

private const val HEALTH_WAIT_S = 60

fun handleTerminalInput(
    text: String,
    context: Context,
    scope: CoroutineScope,
    vaultStore: VaultStore,
    onMoodChange: (Mood) -> Unit,
    onInteraction: () -> Unit,
    isServerHealthy: () -> Boolean,
    updateReply: (String) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        if (!isServerHealthy()) {
            updateReply("Modell lädt…")
            var waited = 0
            while (!isServerHealthy() && waited < HEALTH_WAIT_S) {
                delay(1000)
                onInteraction()
                waited++
            }
            if (!isServerHealthy()) {
                updateReply("[Fehler: Server antwortet nicht rechtzeitig.]")
                return@launch
            }
        }
        var currentReply = ""
        updateReply(currentReply)
        onMoodChange(Mood.THINKING)
        try {
            val apiKey = File(context.filesDir, "api_key.txt").readText().trim()
            val client = LlmClientImpl(apiKeyProvider = { apiKey })
            client.chat(listOf(
                ChatMessage("system", "Du bist Apollo, ein kleiner Schreibtisch-Begleiter. Antworte kurz und auf Deutsch."),
                ChatMessage("user", text)
            )).collect { event ->
                onInteraction() // keep awake during stream
                when (event) {
                    is LlmEvent.Token -> {
                        currentReply += event.text
                        updateReply(currentReply)
                    }
                    is LlmEvent.Failed -> {
                        currentReply += "\n[Fehler: ${event.message}]"
                        updateReply(currentReply)
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            currentReply += "\n[Fehler: ${e.message}]"
            updateReply(currentReply)
        }
        onMoodChange(Mood.NEUTRAL)
    }
}

@Composable
fun ApolloTerminal(
    onMoodChange: (Mood) -> Unit,
    onEnterKidMode: () -> Unit,
    onInteraction: () -> Unit,
    mood: Mood,
    reply: String,
    onReplyChange: (String) -> Unit,
    onNote: suspend (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Manage external storage permission
    // Die Freigabe geschieht in den Systemeinstellungen, deshalb bei jeder Rückkehr neu prüfen.
    val lifecycleState by (context as LifecycleOwner).lifecycle.currentStateFlow.collectAsState()
    val hasStoragePermission = remember(lifecycleState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
    }

    val vaultStore = remember { VaultStore(File("/sdcard/Documents/ObsidianVault/")) }
    var boardState by remember { mutableStateOf(dev.s10e.companion.vault.Board()) }

    var inputText by remember { mutableStateOf("") }

    // Health check state
    var isServerHealthy by remember { mutableStateOf(false) }

    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission) {
            scope.launch(Dispatchers.IO) {
                try {
                    boardState = vaultStore.load().first
                } catch (e: IOException) {
                    onReplyChange("[Vault nicht lesbar: ${e.message}]")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Poll for health check
        scope.launch(Dispatchers.IO) {
            while (!isServerHealthy) {
                try {
                    val url = URL("http://127.0.0.1:8080/health")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 1000
                    connection.readTimeout = 1000
                    if (connection.responseCode == 200) {
                        isServerHealthy = true
                    }
                    connection.disconnect()
                } catch (e: Exception) {
                    // Ignore, keep polling
                }
                if (!isServerHealthy) {
                    delay(2000)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { onEnterKidMode() }) {
                Text("Kindermodus")
            }
            if (!hasStoragePermission) {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Speicher freigeben")
                }
            }
        }

        // Body: Kanban Board
        Box(modifier = Modifier.weight(1f)) {
            if (hasStoragePermission) {
                KanbanBoardView(
                    board = boardState,
                    modifier = Modifier.fillMaxSize(),
                    onInteraction = onInteraction
                )
            } else {
                Text(
                    "Tippe oben, um den Vault zu erlauben.",
                    color = Color.LightGray,
                    modifier = Modifier.padding(16.dp).align(Alignment.Center)
                )
            }
        }

        // LLM Reply text
        val status = when (mood) {
            Mood.LISTENING -> "Ich höre zu…"
            Mood.THINKING -> "Ich denke nach…"
            else -> null
        }
        if (status != null) Text(status, color = Color(0xFFB84DFF), modifier = Modifier.padding(horizontal = 16.dp))
        if (reply.isNotEmpty()) {
            Text(
                text = reply,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(Color.Black)
                    .padding(8.dp)
            )
        }

        // Input footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    onInteraction()
                },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        val text = inputText.trim()
                        if (text.isNotEmpty()) {
                            onInteraction()
                            if (text.startsWith("Notiz", ignoreCase = true) || text.startsWith("Aufgabe", ignoreCase = true)) {
                                scope.launch {
                                    onNote(text)
                                    if (hasStoragePermission) {
                                        boardState = withContext(Dispatchers.IO) { vaultStore.load().first }
                                    }
                                }
                            } else {
                                handleTerminalInput(text, context, scope, vaultStore, onMoodChange, onInteraction, { isServerHealthy }, onReplyChange)
                            }
                            inputText = ""
                        }
                    }
                )
            )

            Button(
                onClick = {
                    onInteraction()
                    onMoodChange(Mood.LISTENING)
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("🎤")
            }
        }
    }
}
