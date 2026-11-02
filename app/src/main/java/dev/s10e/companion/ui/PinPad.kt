package dev.s10e.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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

@Composable
fun PinPad(title: String, message: String?, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("←", "0", "OK"))

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color.White)
        Text("•".repeat(pin.length).ifEmpty { " " }, color = Color.White)
        if (message != null) Text(message, color = Color(0xFFFF8A80))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Button(modifier = Modifier.size(72.dp), onClick = {
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
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Abbrechen") }
    }
}
