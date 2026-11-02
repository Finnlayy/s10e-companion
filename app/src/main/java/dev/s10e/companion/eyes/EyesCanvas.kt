package dev.s10e.companion.eyes

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

@Composable
fun EyesCanvas(mood: Mood, lookX: Float = 0f, lookY: Float = 0f) {
    val timeNanos = remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                timeNanos.longValue = frameTimeNanos
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val t = timeNanos.longValue
        val frame = eyeFrame(mood, t, lookX, lookY)
        
        val spacing = 58f.dp.toPx()
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        val offsetX = frame.offsetX.dp.toPx()
        val offsetY = frame.offsetY.dp.toPx()

        // draw left eye
        translate(
            left = centerX - spacing / 2 - frame.left.width.dp.toPx() + offsetX,
            top = centerY - frame.left.height.dp.toPx() / 2 + offsetY
        ) {
            rotate(frame.left.rotationDeg, pivot = Offset(frame.left.width.dp.toPx() / 2, frame.left.height.dp.toPx() / 2)) {
                drawRoundRect(
                    color = Color(frame.left.argb),
                    size = Size(frame.left.width.dp.toPx(), frame.left.height.dp.toPx()),
                    cornerRadius = CornerRadius(frame.left.corner.dp.toPx(), frame.left.corner.dp.toPx())
                )
            }
        }
        
        // draw right eye
        translate(
            left = centerX + spacing / 2 + offsetX,
            top = centerY - frame.right.height.dp.toPx() / 2 + offsetY
        ) {
            rotate(frame.right.rotationDeg, pivot = Offset(frame.right.width.dp.toPx() / 2, frame.right.height.dp.toPx() / 2)) {
                drawRoundRect(
                    color = Color(frame.right.argb),
                    size = Size(frame.right.width.dp.toPx(), frame.right.height.dp.toPx()),
                    cornerRadius = CornerRadius(frame.right.corner.dp.toPx(), frame.right.corner.dp.toPx())
                )
            }
        }
    }
}
