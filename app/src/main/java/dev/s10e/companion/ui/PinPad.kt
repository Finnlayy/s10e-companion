package dev.s10e.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

const val MIN_PIN_LENGTH = 4
private val SIDE_BY_SIDE_MIN_WIDTH = 490.dp

@Composable
fun PinPad(title: String, message: String?, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("←", "0", "OK"))

    val info: @Composable () -> Unit = {
        Column(modifier = Modifier.width(220.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White)
            Text("•".repeat(pin.length).ifEmpty { " " }, color = Color.White)
            Text(message ?: " ", color = Color(0xFFFF8A80))
            TextButton(onClick = onCancel) { Text("Abbrechen") }
        }
    }
    val keys: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key ->
                        Button(modifier = Modifier.size(70.dp), contentPadding = PaddingValues(0.dp), onClick = {
                            when (key) {
                                "←" -> pin = pin.dropLast(1)
                                "OK" -> if (pin.length >= MIN_PIN_LENGTH) {
                                    onSubmit(pin)
                                    pin = ""
                                }
                                else -> pin += key
                            }
                        }) { Text(key) }
                    }
                }
            }
        }
    }

    // Nebeneinander brauchen beide Spalten 474 dp. Im Hochformat fielen sonst 3, 6, 9 und OK weg.
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        if (maxWidth < SIDE_BY_SIDE_MIN_WIDTH) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                info()
                keys()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                info()
                keys()
            }
        }
    }
}
