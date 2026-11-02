package dev.s10e.companion.eyes

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
fun TiltSensor(onTilt: (Float, Float) -> Unit, onPickup: () -> Unit) {
    val context = LocalContext.current
    val currentOnTilt by rememberUpdatedState(onTilt)
    val currentOnPickup by rememberUpdatedState(onPickup)

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val tracker = TiltTracker()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val tilt = tracker.onGravity(event.values[0], event.values[1], event.values[2], SystemClock.elapsedRealtime())
                currentOnTilt(tilt.lookX, tilt.lookY)
                if (tilt.pickedUp) currentOnPickup()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
}
