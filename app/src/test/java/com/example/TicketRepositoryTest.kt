package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.LotteryTicket
import com.example.data.TicketRepository
import com.example.data.TicketStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TicketRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: TicketRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TicketRepository(database.ticketDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun ticket(code: String) = LotteryTicket(gameName = "Test Game", entryCode = code)

    @Test
    fun `same entry code is only saved once, ignoring spaces, hyphens and case`() = runBlocking {
        assertTrue(repository.insertTicketIfNew(ticket("1234-5678 90ab")))
        assertFalse(repository.insertTicketIfNew(ticket("1234567890AB")))

        val all = repository.allTickets.first()
        assertEquals(1, all.size)
        assertEquals("1234567890AB", all.single().entryCode)
    }

    @Test
    fun `duplicate is caught against a code stored with hyphens by an earlier build`() = runBlocking {
        repository.insertTicket(ticket("1234-5678-90ab"))
        assertFalse(repository.insertTicketIfNew(ticket("1234567890AB")))
        assertEquals(1, repository.allTickets.first().size)
    }

    @Test
    fun `blank entry code is not saved`() = runBlocking {
        assertFalse(repository.insertTicketIfNew(ticket("  ")))
        assertTrue(repository.allTickets.first().isEmpty())
    }

    @Test
    fun `marking submitted without a receipt stores no confirmation code`() = runBlocking {
        repository.insertTicketIfNew(ticket("1234567890AB"))
        val id = repository.allTickets.first().single().id

        repository.markSubmitted(id)

        val saved = database.ticketDao().getTicketById(id)!!
        assertEquals(TicketStatus.SUBMITTED, saved.status)
        assertNull(saved.confirmationCode)
    }

    @Test
    fun `legacy demo tickets are removed and real tickets are kept`() = runBlocking {
        repository.insertTicket(ticket("84920158291048"))
        repository.insertTicket(ticket("91450293817264"))
        repository.insertTicketIfNew(ticket("1234567890AB"))

        repository.removeLegacySampleTickets()

        val codes = repository.allTickets.first().map { it.entryCode }
        assertEquals(listOf("1234567890AB"), codes)
    }

}
