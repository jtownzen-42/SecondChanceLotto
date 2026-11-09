package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.LotteryTicket
import com.example.data.TicketDao
import com.example.data.TicketStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: AppDatabase
    private lateinit var ticketDao: TicketDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ticketDao = database.ticketDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SecondChance IL", appName)
    }

    @Test
    fun `room database CRUD operations on lottery ticket entity`() = runBlocking {
        // 1. CREATE
        val ticket = LotteryTicket(
            gameName = "${'$'}100,000 Frenzy",
            gameNumber = "852",
            entryCode = "84920158291048",
            status = TicketStatus.PENDING,
            expirationDate = "Nov 15, 2026",
            notes = "Test scratch ticket"
        )
        val id = ticketDao.insertTicket(ticket)
        assertEquals(1L, id)

        // 2. READ
        val fetched = ticketDao.getTicketById(id)
        assertNotNull(fetched)
        assertEquals("${'$'}100,000 Frenzy", fetched?.gameName)
        assertEquals("84920158291048", fetched?.entryCode)
        assertEquals(TicketStatus.PENDING, fetched?.status)
        assertEquals("Nov 15, 2026", fetched?.expirationDate)

        // Read via Flow
        val allTickets = ticketDao.getAllTickets().first()
        assertEquals(1, allTickets.size)

        // 3. UPDATE
        ticketDao.updateTicketStatus(
            id = id,
            status = TicketStatus.SUBMITTED,
            submittedTime = System.currentTimeMillis(),
            confirmation = "IL-CONF-12345"
        )
        val updated = ticketDao.getTicketById(id)
        assertEquals(TicketStatus.SUBMITTED, updated?.status)
        assertEquals("IL-CONF-12345", updated?.confirmationCode)

        // 4. DELETE
        val deleteCount = ticketDao.deleteTicketById(id)
        assertEquals(1, deleteCount)

        val afterDelete = ticketDao.getTicketById(id)
        assertNull(afterDelete)
    }
}
