package com.example.data

import kotlinx.coroutines.flow.Flow

class TicketRepository(private val ticketDao: TicketDao) {
    val allTickets: Flow<List<LotteryTicket>> = ticketDao.getAllTickets()
    val pendingTickets: Flow<List<LotteryTicket>> = ticketDao.getTicketsByStatus(TicketStatus.PENDING)
    val submittedTickets: Flow<List<LotteryTicket>> = ticketDao.getTicketsByStatus(TicketStatus.SUBMITTED)
    val totalCount: Flow<Int> = ticketDao.getTotalCount()
    val pendingCount: Flow<Int> = ticketDao.getCountByStatus(TicketStatus.PENDING)

    fun getTicketsByGame(gameNumber: String): Flow<List<LotteryTicket>> =
        ticketDao.getTicketsByGame(gameNumber)

    suspend fun insertTicket(ticket: LotteryTicket): Long =
        ticketDao.insertTicket(ticket)

    /**
     * Saves [ticket] unless a ticket with the same entry code is already stored.
     * Codes are compared after removing spaces and hyphens, case-insensitively.
     * Returns false when the code is a duplicate and nothing was saved.
     */
    suspend fun insertTicketIfNew(ticket: LotteryTicket): Boolean {
        val code = normalizeEntryCode(ticket.entryCode)
        if (code.isEmpty() || ticketDao.getTicketByNormalizedCode(code) != null) return false
        ticketDao.insertTicket(ticket.copy(entryCode = code))
        return true
    }

    suspend fun insertTickets(tickets: List<LotteryTicket>) =
        ticketDao.insertTickets(tickets)

    suspend fun updateTicket(ticket: LotteryTicket) =
        ticketDao.updateTicket(ticket)

    suspend fun deleteTicket(ticket: LotteryTicket) =
        ticketDao.deleteTicket(ticket)

    suspend fun deleteTicketById(id: Long) =
        ticketDao.deleteTicketById(id)

    suspend fun markSubmitted(id: Long, confirmationCode: String? = null) {
        ticketDao.updateTicketStatus(
            id = id,
            status = TicketStatus.SUBMITTED,
            submittedTime = System.currentTimeMillis(),
            // Only store a receipt the lottery site actually gave us; never invent one.
            confirmation = confirmationCode
        )
    }

    suspend fun markStatus(id: Long, status: TicketStatus, confirmationCode: String? = null) {
        ticketDao.updateTicketStatus(
            id = id,
            status = status,
            submittedTime = if (status == TicketStatus.SUBMITTED) System.currentTimeMillis() else null,
            confirmation = confirmationCode
        )
    }

    suspend fun removeLegacySampleTickets() {
        ticketDao.deleteTicketsByCodes(LEGACY_SAMPLE_CODES)
    }

    suspend fun clearSubmitted() {
        ticketDao.deleteTicketsByStatus(TicketStatus.SUBMITTED)
    }
}

/** Entry codes of the demo tickets that earlier builds inserted on first launch. */
private val LEGACY_SAMPLE_CODES = listOf(
    "84920158291048",
    "84920158291049",
    "77301948271603",
    "91450293817264"
)

/** Strips spaces and hyphens and upper-cases an entry code so equal codes compare equal. */
fun normalizeEntryCode(code: String): String =
    code.filterNot { it == ' ' || it == '-' }.uppercase()
