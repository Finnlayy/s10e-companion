package dev.s10e.companion.presence

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

private const val ANALYSIS_INTERVAL_MS = 220L

/** Kopiert die Y-Ebene ohne Zeilenabstand. Bilder bleiben im Speicher und verlassen das Gerät nie. */
private fun ImageProxy.lumaBytes(): ByteArray {
    val plane = planes[0]
    val buffer = plane.buffer
    val luma = ByteArray(width * height)
    for (row in 0 until height) {
        buffer.position(row * plane.rowStride)
        buffer.get(luma, row * width, width)
    }
    return luma
}

@Composable
fun CameraPresence(onMotion: (Motion) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    val currentOnMotion by rememberUpdatedState(onMotion)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCameraPermission) return

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val detector = MotionDetector()
        var lastAnalysisMs = 0L
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(160, 120))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(executor) { image ->
            image.use {
                val now = SystemClock.elapsedRealtime()
                if (now - lastAnalysisMs >= ANALYSIS_INTERVAL_MS) {
                    lastAnalysisMs = now
                    val grid = shrinkUpright(it.lumaBytes(), it.width, it.height, it.imageInfo.rotationDegrees)
                    detector.onFrame(grid, GRID_WIDTH, GRID_HEIGHT)?.let { motion ->
                        Log.d("S10eCompanion", "Bewegung x=${motion.centerX} y=${motion.centerY} px=${motion.pixels}")
                        currentOnMotion(motion)
                    }
                }
            }
        }

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                }
            } catch (e: Exception) {
                Log.w("S10eCompanion", "Kamera nicht verfügbar: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            future.addListener({ future.get().unbind(analysis) }, ContextCompat.getMainExecutor(context))
            executor.shutdown()
        }
    }
}
