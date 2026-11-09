package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for [LotteryTicket] CRUD operations.
 */
@Dao
interface TicketDao {
    // --- CREATE ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicket(ticket: LotteryTicket): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTickets(tickets: List<LotteryTicket>): List<Long>

    // --- READ ---
    @Query("SELECT * FROM lottery_tickets ORDER BY dateScanned DESC")
    fun getAllTickets(): Flow<List<LotteryTicket>>

    @Query("SELECT * FROM lottery_tickets WHERE id = :id LIMIT 1")
    suspend fun getTicketById(id: Long): LotteryTicket?

    @Query("SELECT * FROM lottery_tickets WHERE status = :status ORDER BY dateScanned DESC")
    fun getTicketsByStatus(status: TicketStatus): Flow<List<LotteryTicket>>

    @Query("SELECT * FROM lottery_tickets WHERE status = 'PENDING' ORDER BY dateScanned DESC")
    fun getPendingTickets(): Flow<List<LotteryTicket>>

    @Query("SELECT * FROM lottery_tickets WHERE status = 'SUBMITTED' ORDER BY dateSubmitted DESC")
    fun getSubmittedTickets(): Flow<List<LotteryTicket>>

    @Query("SELECT * FROM lottery_tickets WHERE gameNumber = :gameNumber ORDER BY dateScanned DESC")
    fun getTicketsByGame(gameNumber: String): Flow<List<LotteryTicket>>

    @Query("SELECT * FROM lottery_tickets WHERE entryCode = :entryCode LIMIT 1")
    suspend fun getTicketByCode(entryCode: String): LotteryTicket?

    @Query("SELECT COUNT(*) FROM lottery_tickets WHERE status = :status")
    fun getCountByStatus(status: TicketStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM lottery_tickets")
    fun getTotalCount(): Flow<Int>

    // --- UPDATE ---
    @Update
    suspend fun updateTicket(ticket: LotteryTicket): Int

    @Query("UPDATE lottery_tickets SET status = :status, dateSubmitted = :submittedTime, confirmationCode = :confirmation WHERE id = :id")
    suspend fun updateTicketStatus(id: Long, status: TicketStatus, submittedTime: Long?, confirmation: String?): Int

    @Query("UPDATE lottery_tickets SET expirationDate = :expirationDate WHERE id = :id")
    suspend fun updateTicketExpirationDate(id: Long, expirationDate: String): Int

    // --- DELETE ---
    @Delete
    suspend fun deleteTicket(ticket: LotteryTicket): Int

    @Query("DELETE FROM lottery_tickets WHERE id = :id")
    suspend fun deleteTicketById(id: Long): Int

    @Query("DELETE FROM lottery_tickets WHERE status = :status")
    suspend fun deleteTicketsByStatus(status: TicketStatus): Int

    @Query("DELETE FROM lottery_tickets WHERE entryCode IN (:codes)")
    suspend fun deleteTicketsByCodes(codes: List<String>): Int

    @Query("DELETE FROM lottery_tickets")
    suspend fun deleteAllTickets(): Int
}
