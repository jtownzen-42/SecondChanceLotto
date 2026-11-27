package com.jtownzen.secondchanceil

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jtownzen.secondchanceil.data.TicketStatus
import com.jtownzen.secondchanceil.scanner.CameraScannerScreen
import com.jtownzen.secondchanceil.submission.BatchSubmissionScreen
import com.jtownzen.secondchanceil.ui.AccountSettingsDialog
import com.jtownzen.secondchanceil.ui.AppTab
import com.jtownzen.secondchanceil.ui.DashboardScreen
import com.jtownzen.secondchanceil.ui.MainViewModel
import com.jtownzen.secondchanceil.ui.ManualEntryDialog
import com.jtownzen.secondchanceil.ui.TicketVaultScreen
import com.jtownzen.secondchanceil.ui.isLandscape
import com.jtownzen.secondchanceil.ui.theme.LotteryEmerald
import com.jtownzen.secondchanceil.ui.theme.LuckyGold
import com.jtownzen.secondchanceil.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SecondChanceApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecondChanceApp(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val allTickets by viewModel.allTickets.collectAsStateWithLifecycle()
    val pendingTickets by viewModel.pendingTickets.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val filterGameNumber by viewModel.filterGameNumber.collectAsStateWithLifecycle()
    val sessionResetCount by viewModel.sessionResetCount.collectAsStateWithLifecycle()

    var showManualEntryDialog by remember { mutableStateOf(false) }
    var showAccountSettingsDialog by remember { mutableStateOf(false) }
    var manualEntryInitialGame by remember { mutableStateOf<String?>(null) }

    // Intercept back button on secondary tabs
    if (currentTab != AppTab.DASHBOARD) {
        BackHandler {
            viewModel.setTab(AppTab.DASHBOARD)
        }
    }

    val landscape = isLandscape()

    val topBarActions: @Composable () -> Unit = {
        // Account Credentials Button
        IconButton(
            onClick = { showAccountSettingsDialog = true },
            modifier = Modifier.testTag("top_bar_account_button")
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "Account Settings",
                    tint = if (userPreferences.accountEmail.isNotBlank()) LotteryEmerald else LuckyGold
                )
                if (userPreferences.accountEmail.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(LotteryEmerald, CircleShape)
                    )
                }
            }
        }

        // Quick Add Ticket
        IconButton(
            onClick = {
                manualEntryInitialGame = if (currentTab == AppTab.VAULT) filterGameNumber else null
                showManualEntryDialog = true
            },
            modifier = Modifier.testTag("top_bar_add_ticket_button")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Ticket Manually",
                tint = LotteryEmerald
            )
        }
    }

    // The tabs, in the order the work is done: scan tickets, then submit them.
    val navTabs = listOf(
        NavTab(AppTab.DASHBOARD, "Dashboard", "nav_tab_dashboard") {
            Icon(Icons.Default.Dashboard, contentDescription = "Dashboard")
        },
        NavTab(AppTab.SCANNER, "Scan", "nav_tab_scanner") {
            Icon(Icons.Default.CameraAlt, contentDescription = "Scanner")
        },
        NavTab(AppTab.SUBMISSION, "Submit", "nav_tab_submission") {
            BadgedBox(
                badge = {
                    if (pendingTickets.isNotEmpty()) {
                        Badge(containerColor = LuckyGold, contentColor = Color.Black) {
                            Text(pendingTickets.size.toString())
                        }
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Submit Batch")
            }
        },
        NavTab(AppTab.VAULT, "Vault", "nav_tab_vault") {
            BadgedBox(
                badge = {
                    if (allTickets.isNotEmpty()) {
                        Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(allTickets.size.toString())
                        }
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Vault")
            }
        }
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            // In landscape the title bar would eat a third of the height; its buttons move to the side rail.
            if (!landscape) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentTab) {
                                AppTab.DASHBOARD -> "SecondChance IL"
                                AppTab.SCANNER -> "Scan Instant Ticket"
                                AppTab.SUBMISSION -> "Batch Submitter"
                                AppTab.VAULT -> "Ticket Vault"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    },
                    actions = { topBarActions() },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            if (!landscape) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    navTabs.forEach { nav ->
                        NavigationBarItem(
                            selected = currentTab == nav.tab,
                            onClick = { viewModel.setTab(nav.tab) },
                            icon = nav.icon,
                            label = { Text(nav.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = LotteryEmerald,
                                indicatorColor = LotteryEmerald
                            ),
                            modifier = Modifier.testTag(nav.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (landscape) {
                // Landscape: tabs down the left edge, leaving the full height for the screen.
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { topBarActions() }
                    },
                    modifier = Modifier.testTag("side_nav_rail")
                ) {
                    navTabs.forEach { nav ->
                        NavigationRailItem(
                            selected = currentTab == nav.tab,
                            onClick = { viewModel.setTab(nav.tab) },
                            icon = nav.icon,
                            label = { Text(nav.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = LotteryEmerald,
                                indicatorColor = LotteryEmerald
                            ),
                            modifier = Modifier.testTag(nav.testTag)
                        )
                    }
                }
            }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            when (currentTab) {
                AppTab.DASHBOARD -> {
                    DashboardScreen(
                        tickets = allTickets,
                        onScanClicked = { viewModel.setTab(AppTab.SCANNER) },
                        onManualEntryClicked = {
                            manualEntryInitialGame = null
                            showManualEntryDialog = true
                        },
                        onSubmitBatchClicked = { viewModel.setTab(AppTab.SUBMISSION) },
                        onViewGameTicketsClicked = { gameNum ->
                            viewModel.setFilterGame(gameNum)
                        }
                    )
                }

                AppTab.SCANNER -> {
                    CameraScannerScreen(
                        onTicketSaved = { ticket ->
                            viewModel.saveTicket(ticket) { saved ->
                                if (saved) {
                                    // Stay on the scanner so the next ticket can be scanned right away.
                                    Toast.makeText(context, "Added to the Submit queue. Scan the next ticket.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "That entry code is already in your vault", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onManualEntryRequested = {
                            showManualEntryDialog = true
                        },
                        onLearnGame = { number, title, code ->
                            viewModel.learnGame(number, title, code)
                            Toast.makeText(context, "Game #$number added. Its official name fills in from the 2nd Chance Zone.", Toast.LENGTH_LONG).show()
                        }
                    )
                }

                AppTab.SUBMISSION -> {
                    BatchSubmissionScreen(
                        pendingTickets = pendingTickets,
                        userPreferences = userPreferences,
                        onTicketSubmitted = { ticketId, conf ->
                            viewModel.markTicketSubmitted(ticketId, conf)
                        },
                        onTicketDuplicate = { ticketId ->
                            viewModel.markTicketStatus(ticketId, TicketStatus.DUPLICATE)
                        },
                        onConfigureCredentials = {
                            showAccountSettingsDialog = true
                        },
                        sessionResetCount = sessionResetCount,
                        onPortalGameList = viewModel::onPortalGameList,
                        onRemoveLearnedGame = viewModel::removeLearnedGame
                    )
                }

                AppTab.VAULT -> {
                    TicketVaultScreen(
                        tickets = allTickets,
                        filterGameNumber = filterGameNumber,
                        onClearGameFilter = { viewModel.clearFilterGame() },
                        onDeleteTicket = { ticket ->
                            viewModel.deleteTicket(ticket)
                            Toast.makeText(context, "Ticket deleted", Toast.LENGTH_SHORT).show()
                        },
                        onRequeueTicket = { ticket ->
                            viewModel.markTicketStatus(ticket.id, TicketStatus.PENDING)
                            Toast.makeText(context, "Back in the Submit queue", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
        }

        // Manual Entry Dialog
        if (showManualEntryDialog) {
            ManualEntryDialog(
                initialGameNumber = manualEntryInitialGame,
                onDismiss = { showManualEntryDialog = false },
                onSaveTicket = { ticket ->
                    viewModel.saveTicket(ticket) { saved ->
                        val message = if (saved) "Saved to submission batch!" else "That entry code is already in your vault"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Account Settings Dialog
        if (showAccountSettingsDialog) {
            AccountSettingsDialog(
                userPreferences = userPreferences,
                onSaveCredentials = { email, pass ->
                    val saved = viewModel.saveAccountCredentials(email, pass)
                    if (saved) {
                        Toast.makeText(context, "Illinois Lottery login saved!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Secure Keystore unavailable — login not saved.", Toast.LENGTH_LONG).show()
                    }
                    saved
                },
                onClearCredentials = {
                    viewModel.clearAccountCredentials()
                    Toast.makeText(context, "Credentials and browser session cleared", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showAccountSettingsDialog = false }
            )
        }
    }
}

/** One tab of the app's navigation (bottom bar in portrait, side rail in landscape). */
private class NavTab(
    val tab: AppTab,
    val label: String,
    val testTag: String,
    val icon: @Composable () -> Unit
)
