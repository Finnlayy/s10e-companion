package dev.s10e.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PinPad(onPinEntered: (String) -> Unit) {
    // Placeholder PIN Pad
    Column {
        Text("PIN eingeben")
        Button(onClick = { onPinEntered("1234") }) {
            Text("Simuliere 1234")
        }
    }
}
