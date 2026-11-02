package dev.s10e.companion.presence

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.concurrent.Executors

@Composable
fun CameraPresence(onMotion: (Motion) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        val executor = remember { Executors.newSingleThreadExecutor() }
        val detector = remember { MotionDetector() }
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }



        DisposableEffect(lifecycleOwner) {
            val cameraProvider = cameraProviderFuture.get()
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(160, 120))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(executor) { image ->
                val plane = image.planes[0]
                val buffer: ByteBuffer = plane.buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val w = image.width
                val h = image.height
                image.close()
                val motion = detector.onFrame(bytes, w, h)
                if (motion != null) {
                    onMotion(motion)
                }
            }

            cameraProviderFuture.addListener({
                try {
                    if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            analysis
                        )
                    }
                } catch (e: Exception) {
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                cameraProvider.unbindAll()
                executor.shutdown()
            }
        }


    }
}
