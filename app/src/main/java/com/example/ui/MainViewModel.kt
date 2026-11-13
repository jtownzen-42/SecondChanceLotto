package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.LotteryTicket
import com.example.data.TicketRepository
import com.example.data.TicketStatus
import com.example.data.UserPreferences
import com.example.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab(val title: String) {
    DASHBOARD("Dashboard"),
    SCANNER("Scanner"),
    SUBMISSION("Submit Batch"),
    VAULT("Ticket Vault")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val ticketRepository = TicketRepository(database.ticketDao())
    private val userPreferencesRepository = UserPreferencesRepository(application)

    val allTickets: StateFlow<List<LotteryTicket>> = ticketRepository.allTickets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pendingTickets: StateFlow<List<LotteryTicket>> = ticketRepository.pendingTickets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val submittedTickets: StateFlow<List<LotteryTicket>> = ticketRepository.submittedTickets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferences

    private val _currentTab = MutableStateFlow(AppTab.DASHBOARD)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _filterGameNumber = MutableStateFlow<String?>(null)
    val filterGameNumber: StateFlow<String?> = _filterGameNumber.asStateFlow()

    init {
        // Earlier builds seeded fake demo tickets into the database. Remove them so they
        // can never be submitted as if they were real entries.
        viewModelScope.launch {
            ticketRepository.removeLegacySampleTickets()
        }
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    /**
     * Saves [ticket] if its entry code is new. [onResult] is called on the main thread
     * with true when saved, or false when the code was already in the vault.
     */
    fun saveTicket(ticket: LotteryTicket, onResult: (saved: Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(ticketRepository.insertTicketIfNew(ticket))
        }
    }

    fun deleteTicket(ticket: LotteryTicket) {
        viewModelScope.launch {
            ticketRepository.deleteTicket(ticket)
            val path = ticket.imageUri
            if (!path.isNullOrBlank()) {
                try {
                    val file = java.io.File(path)
                    if (file.exists() && file.parentFile?.canonicalPath == getApplication<Application>().filesDir.canonicalPath) {
                        file.delete()
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun markTicketSubmitted(id: Long, confirmationCode: String? = null) {
        viewModelScope.launch {
            ticketRepository.markSubmitted(id, confirmationCode)
        }
    }

    fun markTicketStatus(id: Long, status: TicketStatus) {
        viewModelScope.launch {
            ticketRepository.markStatus(id, status)
        }
    }

    fun saveAccountCredentials(email: String, pass: String) {
        userPreferencesRepository.saveAccountCredentials(email, pass)
    }

    fun clearAccountCredentials() {
        userPreferencesRepository.clearCredentials()
    }

    fun setFilterGame(gameNumber: String?) {
        _filterGameNumber.value = gameNumber
        _currentTab.value = AppTab.VAULT
    }

    fun clearFilterGame() {
        _filterGameNumber.value = null
    }
}
