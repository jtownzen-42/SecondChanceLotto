package com.jtownzen.secondchanceil.ui

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtownzen.secondchanceil.data.AppDatabase
import com.jtownzen.secondchanceil.data.GameLearner
import com.jtownzen.secondchanceil.data.GameSyncReport
import com.jtownzen.secondchanceil.data.IllinoisLotteryGames
import com.jtownzen.secondchanceil.data.LearnedGamesStore
import com.jtownzen.secondchanceil.data.PortalGameList
import com.jtownzen.secondchanceil.data.LotteryTicket
import com.jtownzen.secondchanceil.data.TicketRepository
import com.jtownzen.secondchanceil.data.TicketStatus
import com.jtownzen.secondchanceil.data.UserPreferences
import com.jtownzen.secondchanceil.data.UserPreferencesRepository
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
    private val learnedGamesStore = LearnedGamesStore(application)

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

    private val _sessionResetCount = MutableStateFlow(0)
    val sessionResetCount: StateFlow<Int> = _sessionResetCount.asStateFlow()

    init {
        IllinoisLotteryGames.applyLearned(learnedGamesStore.load())
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
            val saved = ticketRepository.insertTicketIfNew(ticket)
            if (saved) learnLetterFromTicket(ticket)
            onResult(saved)
        }
    }

    /** A saved ticket confirms its game's entry-code letter, so later scans can find the game by code. */
    private fun learnLetterFromTicket(ticket: LotteryTicket) {
        val next = GameLearner.learnLetterFromTicket(
            IllinoisLotteryGames.learnedState.value,
            ticket.gameNumber,
            ticket.entryCode,
            IllinoisLotteryGames.activeGames
        ) ?: return
        storeLearned(next)
    }

    /** Merges the portal's Select game list; returns what changed. */
    fun onPortalGameList(list: PortalGameList): GameSyncReport {
        val (next, report) = GameLearner.mergePortalList(
            IllinoisLotteryGames.learnedState.value,
            IllinoisLotteryGames.builtInActiveGames,
            list,
            System.currentTimeMillis()
        )
        if (next != IllinoisLotteryGames.learnedState.value) storeLearned(next)
        return report
    }

    /** "Learn this game" from the scan review sheet. */
    fun learnGame(gameNumber: String, title: String, entryCode: String?) {
        storeLearned(
            GameLearner.learnGame(
                IllinoisLotteryGames.learnedState.value,
                gameNumber,
                title,
                entryCode,
                IllinoisLotteryGames.builtInActiveGames,
                System.currentTimeMillis()
            )
        )
    }

    fun removeLearnedGame(gameNumber: String) {
        storeLearned(GameLearner.remove(IllinoisLotteryGames.learnedState.value, gameNumber))
    }

    private fun storeLearned(state: com.jtownzen.secondchanceil.data.LearnedGamesState) {
        IllinoisLotteryGames.applyLearned(state)
        learnedGamesStore.save(state)
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

    fun saveAccountCredentials(email: String, pass: String): Boolean {
        return userPreferencesRepository.saveAccountCredentials(email, pass)
    }

    fun clearAccountCredentials() {
        userPreferencesRepository.clearCredentials()
        try {
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
            WebStorage.getInstance().deleteAllData()
        } catch (_: Exception) {
        }
        _sessionResetCount.value++
    }

    fun setFilterGame(gameNumber: String?) {
        _filterGameNumber.value = gameNumber
        _currentTab.value = AppTab.VAULT
    }

    fun clearFilterGame() {
        _filterGameNumber.value = null
    }
}
