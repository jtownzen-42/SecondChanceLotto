package com.jtownzen.secondchanceil.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jtownzen.secondchanceil.data.GameLearner

/**
 * Adds a game the app doesn't know yet, from the ticket in hand. The game number is the first
 * group of the GAME-PACK-TICKET line printed on the ticket (e.g. 7680 in 7680-04821-014).
 */
@Composable
fun LearnGameDialog(
    initialGameNumber: String,
    initialTitle: String,
    onLearn: (gameNumber: String, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    var number by remember { mutableStateOf(initialGameNumber.filter(Char::isDigit).take(4)) }
    var title by remember { mutableStateOf(initialTitle) }
    val valid = GameLearner.isGameNumber(number)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("learn_game_dialog"),
        title = { Text("Add a new game") },
        text = {
            Column {
                Text(
                    "Use the 4-digit game number printed on the ticket: the first part of the " +
                        "GAME-PACK-TICKET line, like 7680 in 7680-04821-014.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = number,
                    onValueChange = { v -> number = v.filter(Char::isDigit).take(4) },
                    label = { Text("Game number") },
                    isError = number.isNotEmpty() && !valid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("learn_game_number")
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Game name (optional)") },
                    placeholder = { Text("As printed on the ticket") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("learn_game_title")
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "The official name fills in from the 2nd Chance Zone's game list next time you open " +
                        "the Submit tab, and the entry-code letter is learned from this ticket.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onLearn(number, title.trim()) },
                enabled = valid,
                modifier = Modifier.testTag("learn_game_confirm")
            ) { Text("Add game") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
