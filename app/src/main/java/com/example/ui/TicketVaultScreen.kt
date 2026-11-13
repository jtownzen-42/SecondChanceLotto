package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.IllinoisLotteryGames
import com.example.data.LotteryTicket
import com.example.data.TicketStatus
import com.example.ui.theme.LotteryEmerald
import com.example.ui.theme.LuckyGold
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusPending
import com.example.ui.theme.StatusSubmitted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketVaultScreen(
    tickets: List<LotteryTicket>,
    onDeleteTicket: (LotteryTicket) -> Unit,
    modifier: Modifier = Modifier,
    /** Puts a submitted/duplicate ticket back in the Submit queue (e.g. it was marked by mistake). */
    onRequeueTicket: (LotteryTicket) -> Unit = {},
    filterGameNumber: String? = null,
    onClearGameFilter: () -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<TicketStatus?>(null) }
    var ticketToDelete by remember { mutableStateOf<LotteryTicket?>(null) }
    var ticketPhotoToView by remember { mutableStateOf<LotteryTicket?>(null) }

    val gameFilteredTickets = if (filterGameNumber.isNullOrBlank()) {
        tickets
    } else {
        tickets.filter { it.gameNumber == filterGameNumber }
    }

    val filteredTickets = gameFilteredTickets.filter { ticket ->
        val matchesFilter = selectedFilter == null || ticket.status == selectedFilter
        val matchesSearch = searchQuery.isBlank() ||
            ticket.entryCode.contains(searchQuery, ignoreCase = true) ||
            ticket.gameName.contains(searchQuery, ignoreCase = true) ||
            ticket.gameNumber.contains(searchQuery, ignoreCase = true) ||
            ticket.packNumber.contains(searchQuery, ignoreCase = true) ||
            ticket.notes.contains(searchQuery, ignoreCase = true)
        matchesFilter && matchesSearch
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar & Export Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by code, game, pack...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("vault_search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LotteryEmerald,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Export Batch Button
            IconButton(
                onClick = {
                    if (filteredTickets.isEmpty()) {
                        Toast.makeText(context, "No tickets to export", Toast.LENGTH_SHORT).show()
                    } else {
                        val exportText = buildString {
                            appendLine("--- ILLINOIS LOTTERY 2ND CHANCE ENTRIES ---")
                            filteredTickets.forEachIndexed { index, t ->
                                appendLine("${index + 1}. Game #${t.gameNumber} (${t.gameName}): ${t.entryCode} [${t.status}]")
                            }
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Lottery Batch Export", exportText))
                        Toast.makeText(context, "Copied ${filteredTickets.size} codes to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(50.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .testTag("export_batch_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = "Export Codes", tint = LotteryEmerald)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!filterGameNumber.isNullOrBlank()) {
                val gameTitle = IllinoisLotteryGames.findGameByNumber(filterGameNumber)?.title ?: "Game #$filterGameNumber"
                FilterChip(
                    selected = true,
                    onClick = onClearGameFilter,
                    label = { Text("$gameTitle (#$filterGameNumber)") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear game filter",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LotteryEmerald,
                        selectedLabelColor = Color.Black,
                        selectedTrailingIconColor = Color.Black
                    ),
                    modifier = Modifier.testTag("vault_game_filter_chip")
                )
            }
            FilterChip(
                selected = selectedFilter == null,
                onClick = { selectedFilter = null },
                label = { Text("All (${gameFilteredTickets.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LotteryEmerald,
                    selectedLabelColor = Color.Black
                )
            )
            FilterChip(
                selected = selectedFilter == TicketStatus.PENDING,
                onClick = { selectedFilter = if (selectedFilter == TicketStatus.PENDING) null else TicketStatus.PENDING },
                label = { Text("Pending (${gameFilteredTickets.count { it.status == TicketStatus.PENDING }})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LuckyGold,
                    selectedLabelColor = Color.Black
                )
            )
            FilterChip(
                selected = selectedFilter == TicketStatus.SUBMITTED,
                onClick = { selectedFilter = if (selectedFilter == TicketStatus.SUBMITTED) null else TicketStatus.SUBMITTED },
                label = { Text("Submitted (${gameFilteredTickets.count { it.status == TicketStatus.SUBMITTED }})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LotteryEmerald,
                    selectedLabelColor = Color.Black
                )
            )
            val duplicateCount = gameFilteredTickets.count { it.status == TicketStatus.DUPLICATE }
            if (duplicateCount > 0 || selectedFilter == TicketStatus.DUPLICATE) {
                FilterChip(
                    selected = selectedFilter == TicketStatus.DUPLICATE,
                    onClick = { selectedFilter = if (selectedFilter == TicketStatus.DUPLICATE) null else TicketStatus.DUPLICATE },
                    label = { Text("Duplicate ($duplicateCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StatusError,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tickets List
        if (filteredTickets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No tickets match your filter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scan or manually add instant tickets to populate your vault.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTickets, key = { it.id }) { ticket ->
                    TicketVaultCard(
                        ticket = ticket,
                        onCopyCode = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Lottery Code", ticket.entryCode))
                            Toast.makeText(context, "Copied ${ticket.entryCode}", Toast.LENGTH_SHORT).show()
                        },
                        onViewPhoto = { ticketPhotoToView = ticket },
                        onDelete = { ticketToDelete = ticket },
                        onRequeue = { onRequeueTicket(ticket) }
                    )
                }
            }
        }
    }

    // Ticket Photo Preview Dialog
    if (ticketPhotoToView != null) {
        val viewedTicket = ticketPhotoToView!!
        AlertDialog(
            onDismissRequest = { ticketPhotoToView = null },
            title = { Text("${viewedTicket.gameName} (#${viewedTicket.gameNumber})") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.DarkGray)
                    ) {
                        AsyncImage(
                            model = viewedTicket.imageUri,
                            contentDescription = "Ticket claim proof photo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Code: ${viewedTicket.formattedCode}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = LotteryEmerald
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { ticketPhotoToView = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (ticketToDelete != null) {
        AlertDialog(
            onDismissRequest = { ticketToDelete = null },
            title = { Text("Delete Ticket Entry?") },
            text = {
                Text("Are you sure you want to delete the entry for ${ticketToDelete?.gameName} (Code: ${ticketToDelete?.entryCode})?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ticketToDelete?.let { onDeleteTicket(it) }
                        ticketToDelete = null
                    }
                ) {
                    Text("Delete", color = StatusError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { ticketToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TicketVaultCard(
    ticket: LotteryTicket,
    onCopyCode: () -> Unit,
    onDelete: () -> Unit,
    onViewPhoto: () -> Unit = {},
    onRequeue: () -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ticket_item_${ticket.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Game Header
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${ticket.gameName} (#${ticket.gameNumber})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scanned: ${dateFormat.format(Date(ticket.dateScanned))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Expiration: ${ticket.displayExpiration}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = LuckyGold
                    )
                }

                // Status Badge
                Surface(
                    color = when (ticket.status) {
                        TicketStatus.SUBMITTED -> StatusSubmitted.copy(alpha = 0.15f)
                        TicketStatus.PENDING -> StatusPending.copy(alpha = 0.15f)
                        TicketStatus.DUPLICATE -> StatusError.copy(alpha = 0.15f)
                        TicketStatus.ERROR -> StatusError.copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (ticket.status) {
                                TicketStatus.SUBMITTED -> Icons.Default.CheckCircle
                                TicketStatus.PENDING -> Icons.Default.HourglassTop
                                else -> Icons.Default.Clear
                            },
                            contentDescription = null,
                            tint = when (ticket.status) {
                                TicketStatus.SUBMITTED -> StatusSubmitted
                                TicketStatus.PENDING -> StatusPending
                                else -> StatusError
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = ticket.status.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = when (ticket.status) {
                                TicketStatus.SUBMITTED -> StatusSubmitted
                                TicketStatus.PENDING -> StatusPending
                                else -> StatusError
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Entry Code Banner with Copy Button
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "2ND CHANCE ENTRY CODE",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = ticket.formattedCode,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (ticket.status == TicketStatus.SUBMITTED) StatusSubmitted else LotteryEmerald
                        )
                    }

                    IconButton(onClick = onCopyCode, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = LuckyGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Ticket Pack/Num metadata and Confirmation receipt
            if (ticket.packNumber.isNotBlank() || ticket.ticketNumber.isNotBlank() || ticket.confirmationCode != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (ticket.packNumber.isNotBlank() || ticket.ticketNumber.isNotBlank()) {
                        Text(
                            text = "Pack: ${ticket.packNumber.ifEmpty { "--" }} • Ticket: ${ticket.ticketNumber.ifEmpty { "--" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (ticket.confirmationCode != null) {
                        Text(
                            text = "Receipt: ${ticket.confirmationCode}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = StatusSubmitted
                        )
                    }
                }
            }

            // Photo preview if available
            if (ticket.imageUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onViewPhoto)
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    AsyncImage(
                        model = ticket.imageUri,
                        contentDescription = "Ticket thumbnail",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.DarkGray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = LotteryEmerald
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ticket photo attached for claim proof (Tap to view)",
                        style = MaterialTheme.typography.labelSmall,
                        color = LotteryEmerald
                    )
                }
            }

            // Actions: send back to the queue (for tickets not pending), delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ticket.status != TicketStatus.PENDING) {
                    TextButton(
                        onClick = onRequeue,
                        modifier = Modifier.testTag("requeue_ticket_${ticket.id}")
                    ) {
                        Text("Back to queue", style = MaterialTheme.typography.labelMedium, color = LuckyGold)
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
