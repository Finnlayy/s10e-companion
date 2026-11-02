package dev.s10e.companion.llm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import dev.s10e.companion.agent.ToolDispatcher
import java.io.File
import java.util.UUID

class LlmServerService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var process: Process? = null
    private var serverJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            "llm_service",
            "Local AI Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val killIntent = PendingIntent.getBroadcast(
            this, 0, Intent(this, KillSwitchReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val killAction = android.app.Notification.Action.Builder(null, "Not-Aus", killIntent).build()

        val notification = android.app.Notification.Builder(this, "llm_service")
            .setContentTitle("S10e Companion")
            .setContentText("AI Server running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(killAction)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelPath = intent?.getStringExtra("MODEL_PATH") ?: return START_NOT_STICKY
        if (serverJob?.isActive == true) return START_STICKY

        serverJob = scope.launch {
            val apiKeyFile = File(filesDir, "api_key.txt")
            if (!apiKeyFile.exists()) {
                apiKeyFile.writeText(UUID.randomUUID().toString())
            }

            val libDir = applicationInfo.nativeLibraryDir
            val llamaServer = File(libDir, "libllama-server.so")

            // Ein Absturz der App kann einen Server hinterlassen, der Port 8080 weiter belegt.
            ProcessBuilder("pkill", "-f", "libllama-server.so").start().waitFor()

            val supervisor = ServerSupervisor(
                launch = {
                    ProcessBuilder(
                        llamaServer.absolutePath,
                        "-m", modelPath,
                        "--host", "127.0.0.1",
                        "--port", "8080",
                        "--api-key-file", apiKeyFile.absolutePath,
                        "--no-webui",
                        "-c", "4096",
                        "-np", "1",
                        "-t", "4",
                        "-tb", "4",
                        "-cram", "256",
                        "-ctxcp", "2",
                        "--no-mmproj",
                        // Ohne das liegen die Gewichte doppelt im RAM, und lmkd beendet beim Laden auch die App im Vordergrund.
                        "--no-repack",
                        "--jinja"
                    ).apply {
                        environment()["LD_LIBRARY_PATH"] = libDir
                        redirectErrorStream(true)
                        redirectOutput(File(filesDir, "llama-server.log"))
                    }.start()
                },
                sleep = { delay(it) }
            )

            var failures = 0
            while (isActive) {
                try {
                    process?.destroy()
                    process = supervisor.launch()
                    val exitCode = process?.waitFor()
                    if (exitCode != 0) {
                        failures++
                        supervisor.sleep(supervisor.nextBackoffMs(failures))
                    } else {
                        failures = 0
                    }
                } catch (e: Exception) {
                    failures++
                    supervisor.sleep(supervisor.nextBackoffMs(failures))
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        process?.destroy()
    }
}

class KillSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ToolDispatcher.killSwitch = true
    }
}
