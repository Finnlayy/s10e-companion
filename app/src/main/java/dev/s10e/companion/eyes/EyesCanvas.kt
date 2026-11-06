package dev.s10e.companion.eyes

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

@Composable
fun EyesCanvas(mood: Mood, lookX: Float = 0f, lookY: Float = 0f, bored: Boolean = false, zoom: Float = 1f) {
    val timeNanos = remember { mutableLongStateOf(0L) }
    val pose = remember { mutableStateOf(IdlePose()) }
    val director = remember { IdleDirector() }
    val currentBored = rememberUpdatedState(bored)

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                timeNanos.longValue = frameTimeNanos
                pose.value = director.pose(frameTimeNanos / 1_000_000L, currentBored.value)
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val t = timeNanos.longValue
        val frame = eyeFrame(mood, t, lookX, lookY, pose.value)

        val spacing = 58f.dp.toPx()
        val centerX = size.width / 2
        val centerY = size.height / 2

        val offsetX = frame.offsetX.dp.toPx()
        val offsetY = frame.offsetY.dp.toPx()

        scale(zoom) {
            // draw left eye
            translate(
                left = centerX - spacing / 2 - frame.left.width.dp.toPx() + offsetX,
                top = centerY - frame.left.height.dp.toPx() / 2 + offsetY
            ) {
                drawEye(frame.left)
            }

            // draw right eye
            translate(
                left = centerX + spacing / 2 + offsetX,
                top = centerY - frame.right.height.dp.toPx() / 2 + offsetY
            ) {
                drawEye(frame.right)
            }
        }
    }
}

private fun DrawScope.drawEye(eye: EyeShape) {
    val width = eye.width.dp.toPx()
    val height = eye.height.dp.toPx()
    rotate(eye.rotationDeg, pivot = Offset(width / 2, height / 2)) {
        if (eye.heart) {
            drawPath(heartPath(width, height), Color(eye.argb))
        } else {
            drawRoundRect(
                color = Color(eye.argb),
                size = Size(width, height),
                cornerRadius = CornerRadius(eye.corner.dp.toPx(), eye.corner.dp.toPx())
            )
        }
    }
}

private fun heartPath(w: Float, h: Float) = Path().apply {
    moveTo(w / 2, h)
    cubicTo(-w * 0.15f, h * 0.55f, w * 0.05f, -h * 0.1f, w / 2, h * 0.25f)
    cubicTo(w * 0.95f, -h * 0.1f, w * 1.15f, h * 0.55f, w / 2, h)
    close()
}
