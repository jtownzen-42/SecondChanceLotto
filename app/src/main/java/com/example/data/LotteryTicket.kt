package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TicketStatus {
    PENDING,
    SUBMITTED,
    DUPLICATE,
    ERROR
}

@Entity(tableName = "lottery_tickets")
data class LotteryTicket(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameNumber: String = "",
    val gameName: String,
    val packNumber: String = "",
    val ticketNumber: String = "",
    val entryCode: String,
    val imageUri: String? = null,
    val status: TicketStatus = TicketStatus.PENDING,
    val dateScanned: Long = System.currentTimeMillis(),
    val dateSubmitted: Long? = null,
    val confirmationCode: String? = null,
    val drawingDeadline: String = "",
    val expirationDate: String = "",
    val notes: String = ""
) {
    val formattedTicketReference: String
        get() = buildString {
            if (gameNumber.isNotBlank()) append("Game #$gameNumber")
            if (packNumber.isNotBlank()) append(" • Pack $packNumber")
            if (ticketNumber.isNotBlank()) append(" • Ticket $ticketNumber")
        }

    val formattedCode: String
        get() {
            // Group code into 4-character blocks for readability
            val clean = entryCode.replace(" ", "").replace("-", "")
            return clean.chunked(4).joinToString(" ")
        }

    val displayExpiration: String
        get() = expirationDate.ifBlank { drawingDeadline.ifBlank { "Check Official Site" } }
}
