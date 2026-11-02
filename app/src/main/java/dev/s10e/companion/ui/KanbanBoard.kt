package dev.s10e.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.s10e.companion.vault.Board

@Composable
fun KanbanBoardView(board: Board, modifier: Modifier = Modifier, onInteraction: () -> Unit) {
    val columns = board.filterKeys { it.isNotEmpty() }
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(horizontalScrollState) {
        snapshotFlow { horizontalScrollState.value }
            .collect { onInteraction() }
    }

    Row(
        modifier = modifier
            .horizontalScroll(horizontalScrollState)
            .padding(8.dp)
    ) {
        for ((title, cards) in columns) {
            val verticalScrollState = rememberScrollState()
            LaunchedEffect(verticalScrollState) {
                snapshotFlow { verticalScrollState.value }
                    .collect { onInteraction() }
            }
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .padding(end = 16.dp)
                    .background(Color(0xFF222222))
                    .padding(8.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Column(modifier = Modifier.verticalScroll(verticalScrollState)) {
                    for (card in cards) {
                        if (card.isNotBlank()) {
                            Card(modifier = Modifier.padding(bottom = 8.dp)) {
                                Text(
                                    text = card.removePrefix("- [ ] ").removePrefix("- [x] "),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
