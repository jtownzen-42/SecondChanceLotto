package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.example.data.TicketStatus
import com.example.scanner.CameraScannerScreen
import com.example.submission.BatchSubmissionScreen
import com.example.ui.AccountSettingsDialog
import com.example.ui.AppTab
import com.example.ui.DashboardScreen
import com.example.ui.MainViewModel
import com.example.ui.ManualEntryDialog
import com.example.ui.TicketVaultScreen
import com.example.ui.theme.LotteryEmerald
import com.example.ui.theme.LuckyGold
import com.example.ui.theme.MyApplicationTheme

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

    var showManualEntryDialog by remember { mutableStateOf(false) }
    var showAccountSettingsDialog by remember { mutableStateOf(false) }
    var manualEntryInitialGame by remember { mutableStateOf<String?>(null) }

    // Intercept back button on secondary tabs
    if (currentTab != AppTab.DASHBOARD) {
        BackHandler {
            viewModel.setTab(AppTab.DASHBOARD)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
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
                actions = {
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
                            manualEntryInitialGame = null
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
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                // Dashboard Tab
                NavigationBarItem(
                    selected = currentTab == AppTab.DASHBOARD,
                    onClick = { viewModel.setTab(AppTab.DASHBOARD) },
                    icon = {
                        Icon(Icons.Default.Dashboard, contentDescription = "Dashboard")
                    },
                    label = { Text("Dashboard") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = LotteryEmerald,
                        indicatorColor = LotteryEmerald
                    ),
                    modifier = Modifier.testTag("nav_tab_dashboard")
                )

                // Scanner Tab
                NavigationBarItem(
                    selected = currentTab == AppTab.SCANNER,
                    onClick = { viewModel.setTab(AppTab.SCANNER) },
                    icon = {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Scanner")
                    },
                    label = { Text("Scan") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = LotteryEmerald,
                        indicatorColor = LotteryEmerald
                    ),
                    modifier = Modifier.testTag("nav_tab_scanner")
                )

                // Submit Batch Tab
                NavigationBarItem(
                    selected = currentTab == AppTab.SUBMISSION,
                    onClick = { viewModel.setTab(AppTab.SUBMISSION) },
                    icon = {
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
                    label = { Text("Submit") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = LotteryEmerald,
                        indicatorColor = LotteryEmerald
                    ),
                    modifier = Modifier.testTag("nav_tab_submission")
                )

                // Ticket Vault Tab
                NavigationBarItem(
                    selected = currentTab == AppTab.VAULT,
                    onClick = { viewModel.setTab(AppTab.VAULT) },
                    icon = {
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
                    },
                    label = { Text("Vault") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = LotteryEmerald,
                        indicatorColor = LotteryEmerald
                    ),
                    modifier = Modifier.testTag("nav_tab_vault")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                                    Toast.makeText(context, "Ticket added to batch queue!", Toast.LENGTH_SHORT).show()
                                    viewModel.setTab(AppTab.DASHBOARD)
                                } else {
                                    Toast.makeText(context, "That entry code is already in your vault", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onManualEntryRequested = {
                            showManualEntryDialog = true
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
                        }
                    )
                }

                AppTab.VAULT -> {
                    TicketVaultScreen(
                        tickets = allTickets,
                        onDeleteTicket = { ticket ->
                            viewModel.deleteTicket(ticket)
                            Toast.makeText(context, "Ticket deleted", Toast.LENGTH_SHORT).show()
                        }
                    )
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
                    viewModel.saveAccountCredentials(email, pass)
                    Toast.makeText(context, "Illinois Lottery login saved!", Toast.LENGTH_SHORT).show()
                },
                onClearCredentials = {
                    viewModel.clearAccountCredentials()
                    Toast.makeText(context, "Credentials cleared", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showAccountSettingsDialog = false }
            )
        }
    }
}
