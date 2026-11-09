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
            confirmation = confirmationCode ?: generateConfirmationCode()
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

    suspend fun clearSubmitted() {
        ticketDao.deleteTicketsByStatus(TicketStatus.SUBMITTED)
    }

    suspend fun prepopulateSampleTicketsIfEmpty(currentCount: Int) {
        if (currentCount > 0) return

        val sampleTickets = listOf(
            LotteryTicket(
                gameNumber = "852",
                gameName = "$100,000 Frenzy",
                packNumber = "04821",
                ticketNumber = "014",
                entryCode = "84920158291048",
                status = TicketStatus.PENDING,
                drawingDeadline = "Nov 15, 2026",
                expirationDate = "Nov 15, 2026",
                notes = "Sample Illinois instant scratch-off ready for submission"
            ),
            LotteryTicket(
                gameNumber = "852",
                gameName = "$100,000 Frenzy",
                packNumber = "04821",
                ticketNumber = "015",
                entryCode = "84920158291049",
                status = TicketStatus.PENDING,
                drawingDeadline = "Nov 15, 2026",
                expirationDate = "Nov 15, 2026",
                notes = "Consecutive ticket non-winner"
            ),
            LotteryTicket(
                gameNumber = "777",
                gameName = "Triple 777 Jackpot",
                packNumber = "12903",
                ticketNumber = "003",
                entryCode = "77301948271603",
                status = TicketStatus.PENDING,
                drawingDeadline = "Nov 22, 2026",
                expirationDate = "Nov 22, 2026",
                notes = "Gas station purchase - second chance draw"
            ),
            LotteryTicket(
                gameNumber = "914",
                gameName = "Holiday Bonus Spectacular",
                packNumber = "00842",
                ticketNumber = "022",
                entryCode = "91450293817264",
                status = TicketStatus.SUBMITTED,
                dateSubmitted = System.currentTimeMillis() - 86400000L * 2,
                confirmationCode = "IL-2ND-914820",
                drawingDeadline = "Dec 05, 2026",
                expirationDate = "Dec 05, 2026",
                notes = "Submitted successfully into Holiday draw"
            )
        )
        ticketDao.insertTickets(sampleTickets)
    }

    private fun generateConfirmationCode(): String {
        val rand = (100000..999999).random()
        return "IL-2ND-$rand"
    }
}
