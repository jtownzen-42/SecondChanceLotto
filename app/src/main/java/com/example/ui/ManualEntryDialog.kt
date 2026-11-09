package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IllinoisLotteryGames
import com.example.data.LotteryTicket
import com.example.data.TicketStatus
import com.example.ui.theme.LotteryEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryDialog(
    initialGameNumber: String? = null,
    onDismiss: () -> Unit,
    onSaveTicket: (LotteryTicket) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val defaultGame = initialGameNumber?.let { IllinoisLotteryGames.findGameByNumber(it) }
        ?: IllinoisLotteryGames.activeGames.first()

    var selectedGameNumber by remember { mutableStateOf(defaultGame.gameNumber) }
    var selectedGameTitle by remember { mutableStateOf(defaultGame.title) }
    var drawingDeadline by remember { mutableStateOf(defaultGame.drawingDeadline) }
    var entryCode by remember { mutableStateOf("") }
    var packNumber by remember { mutableStateOf("") }
    var ticketNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var gameDropdownExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Add Instant Ticket",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Enter code directly from non-winning ticket",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Entry Code Input (Required)
            OutlinedTextField(
                value = entryCode,
                onValueChange = { entryCode = it.replace(" ", "").replace("-", "").uppercase() },
                label = { Text("Second Chance Entry Code (10-14 Characters)*") },
                placeholder = { Text("e.g. 84920158291048") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_entry_code_input"),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = LotteryEmerald
                ),
                singleLine = true,
                isError = entryCode.isNotEmpty() && entryCode.length !in 10..14,
                supportingText = {
                    Text(
                        text = when {
                            entryCode.isEmpty() -> "Found under the scratch-off play or validation area"
                            entryCode.length in 10..14 -> "Valid length (${entryCode.length} digits)"
                            else -> "Entry code must be between 10 and 14 characters"
                        },
                        color = if (entryCode.length in 10..14) LotteryEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Game Selection Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = "$selectedGameTitle (#$selectedGameNumber)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Illinois Lottery Instant Game") },
                    trailingIcon = {
                        IconButton(onClick = { gameDropdownExpanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { gameDropdownExpanded = true }
                        .testTag("manual_game_selector")
                )

                DropdownMenu(
                    expanded = gameDropdownExpanded,
                    onDismissRequest = { gameDropdownExpanded = false }
                ) {
                    IllinoisLotteryGames.activeGames.forEach { game ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(game.title, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Game #${game.gameNumber} • ${game.ticketPrice} • Drawing ${game.drawingDeadline}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            onClick = {
                                selectedGameNumber = game.gameNumber
                                selectedGameTitle = game.title
                                drawingDeadline = game.drawingDeadline
                                gameDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pack & Ticket Number Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = packNumber,
                    onValueChange = { packNumber = it },
                    label = { Text("Pack # (Optional)") },
                    placeholder = { Text("e.g. 04821") },
                    modifier = Modifier.weight(1f).testTag("manual_pack_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = ticketNumber,
                    onValueChange = { ticketNumber = it },
                    label = { Text("Ticket # (Optional)") },
                    placeholder = { Text("e.g. 014") },
                    modifier = Modifier.weight(1f).testTag("manual_ticket_input"),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (Optional)") },
                placeholder = { Text("Store bought, batch notes, etc.") },
                modifier = Modifier.fillMaxWidth().testTag("manual_notes_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val ticket = LotteryTicket(
                        gameNumber = selectedGameNumber,
                        gameName = selectedGameTitle,
                        packNumber = packNumber,
                        ticketNumber = ticketNumber,
                        entryCode = entryCode,
                        drawingDeadline = drawingDeadline,
                        expirationDate = drawingDeadline,
                        status = TicketStatus.PENDING,
                        notes = notes
                    )
                    onSaveTicket(ticket)
                    onDismiss()
                },
                enabled = entryCode.length in 10..14,
                colors = ButtonDefaults.buttonColors(containerColor = LotteryEmerald),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_manual_ticket_button")
            ) {
                Text(
                    text = "Save Ticket to Submission Batch",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
