package dev.s10e.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import dev.s10e.companion.agent.AgentRuntime
import androidx.compose.foundation.layout.fillMaxHeight
import dev.s10e.companion.agent.PhoneControlService
import dev.s10e.companion.agent.ToolDispatcher
import dev.s10e.companion.agent.parseCommand
import dev.s10e.companion.kid.SharedPrefsKeyValue
import dev.s10e.companion.plugins.OpenCodeClient
import androidx.compose.material3.AlertDialog
import kotlinx.coroutines.withContext
import dev.s10e.companion.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

import kotlinx.coroutines.CoroutineScope

const val VAULT_PATH = "/sdcard/Documents/ObsidianVault/"
private const val HEALTH_WAIT_S = 60

fun handleTerminalInput(
    text: String,
    scope: CoroutineScope,
    agent: AgentRuntime,
    onMoodChange: (Mood) -> Unit,
    onInteraction: () -> Unit,
    isServerHealthy: () -> Boolean,
    updateReply: (String) -> Unit,
    onBoardChanged: () -> Unit
) {
    scope.launch(Dispatchers.IO) {
        val isCommand = parseCommand(text) != null
        if (!isCommand && !isServerHealthy()) {
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
        updateReply("")
        onMoodChange(Mood.THINKING)
        try {
            agent.handle(text) { partial ->
                updateReply(partial)
                onInteraction()
            }
        } catch (e: Exception) {
            updateReply("[Fehler: ${e.message}]")
        }
        onMoodChange(Mood.NEUTRAL)
        onBoardChanged()
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
    agent: AgentRuntime,
    agentScope: CoroutineScope,
    vaultVersion: Int,
    onBoardChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Manage external storage permission
    // Die Freigabe geschieht in den Systemeinstellungen, deshalb bei jeder Rückkehr neu prüfen.
    val lifecycleState by (context as LifecycleOwner).lifecycle.currentStateFlow.collectAsState()
    val hasStoragePermission = remember(lifecycleState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
    }

    val accessibilityEnabled = remember(lifecycleState) { PhoneControlService.instance != null }

    val vaultStore = remember { VaultStore(File(VAULT_PATH)) }
    var boardState by remember { mutableStateOf(dev.s10e.companion.vault.Board()) }

    var inputText by remember { mutableStateOf("") }
    var showPlugins by remember { mutableStateOf(false) }

    // Health check state
    var isServerHealthy by remember { mutableStateOf(false) }

    LaunchedEffect(hasStoragePermission, vaultVersion) {
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
            Button(
                onClick = {
                    showPlugins = true
                    onInteraction()
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Plugins")
            }
            if (showPlugins) {
                var openCodeStatus by remember { mutableStateOf("…") }
                LaunchedEffect(Unit) {
                    openCodeStatus = withContext(Dispatchers.IO) { OpenCodeClient(SharedPrefsKeyValue(context)).fetchServerStatus() }
                }
                AlertDialog(
                    onDismissRequest = { showPlugins = false },
                    title = { Text("Plugins, OpenCode: $openCodeStatus") },
                    text = {
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
                            PluginPanel(File(context.filesDir, "plugins/index.html"))
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showPlugins = false }) { Text("Schließen") }
                    }
                )
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
            if (!accessibilityEnabled) {
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Agent erlauben")
                }
            }
            Button(
                onClick = { ToolDispatcher.killSwitch = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Not-Aus")
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
                    .heightIn(max = 110.dp)
                    .background(Color.Black)
                    .verticalScroll(rememberScrollState(), reverseScrolling = true)
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
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        val text = inputText.trim()
                        if (text.isNotEmpty()) {
                            onInteraction()
                            handleTerminalInput(text, agentScope, agent, onMoodChange, onInteraction, { isServerHealthy }, onReplyChange, onBoardChanged)
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
