package com.example.submission

import com.example.data.IllinoisLotteryGames
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.LotteryTicket
import com.example.data.TicketStatus
import com.example.data.UserPreferences
import com.example.ui.theme.LotteryEmerald
import com.example.ui.theme.LuckyGold
import com.example.ui.theme.StatusSubmitted
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BatchSubmissionScreen(
    pendingTickets: List<LotteryTicket>,
    userPreferences: UserPreferences,
    onTicketSubmitted: (ticketId: Long, confirmation: String?) -> Unit,
    onTicketDuplicate: (ticketId: Long) -> Unit,
    onConfigureCredentials: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var currentQueueIndex by remember { mutableIntStateOf(0) }
    var completedInSessionCount by remember { mutableIntStateOf(0) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageLoadProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(false) }
    var currentWebUrl by remember { mutableStateOf(IllinoisLotteryGames.SECOND_CHANCE_ZONE_URL) }
    var lastAutomationStatus by remember { mutableStateOf<String?>(null) }
    var isHudExpanded by remember { mutableStateOf(true) }

    // The ticket whose code was last injected into the portal and whose result we are
    // waiting to see. The WebView callbacks are created once, so they must read this
    // state (always current) instead of capturing `currentTicket` (frozen at creation).
    var awaitingResultForTicketId by remember { mutableStateOf<Long?>(null) }
    val latestOnTicketSubmitted by rememberUpdatedState(onTicketSubmitted)
    val latestOnTicketDuplicate by rememberUpdatedState(onTicketDuplicate)

    // Reset index if tickets list changes
    LaunchedEffect(pendingTickets.size) {
        if (currentQueueIndex >= pendingTickets.size) {
            currentQueueIndex = 0
        }
        if (pendingTickets.isEmpty()) {
            awaitingResultForTicketId = null
        }
    }

    // Poll DOM for SPA / XHR submission results that don't trigger a full page reload
    LaunchedEffect(awaitingResultForTicketId) {
        val targetId = awaitingResultForTicketId ?: return@LaunchedEffect
        for (waitMs in listOf(900L, 900L, 1200L)) {
            delay(waitMs)
            if (awaitingResultForTicketId != targetId) return@LaunchedEffect
            val wv = webViewInstance ?: break
            wv.evaluateJavascript(SubmissionAutomationHelper.generateCheckSuccessScript()) { result ->
                if (awaitingResultForTicketId != targetId) return@evaluateJavascript
                when (SubmissionAutomationHelper.parsePortalResult(result)) {
                    PortalResult.SUBMITTED -> {
                        awaitingResultForTicketId = null
                        completedInSessionCount++
                        latestOnTicketSubmitted(targetId, null)
                    }
                    PortalResult.DUPLICATE -> {
                        awaitingResultForTicketId = null
                        completedInSessionCount++
                        latestOnTicketDuplicate(targetId)
                    }
                    PortalResult.UNKNOWN -> Unit
                }
            }
        }
        if (awaitingResultForTicketId == targetId) {
            awaitingResultForTicketId = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
        }
    }

    val currentTicket = pendingTickets.getOrNull(currentQueueIndex)
    val currentPromotion = remember(currentTicket?.gameNumber) {
        currentTicket?.let { IllinoisLotteryGames.findGameByNumber(it.gameNumber)?.promotion }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top Automation Control HUD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .testTag("batch_automation_hud"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Header Status Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(if (pendingTickets.isNotEmpty()) LotteryEmerald else LuckyGold, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (pendingTickets.isNotEmpty()) {
                                    "Queue: Ticket ${currentQueueIndex + 1} of ${pendingTickets.size}"
                                } else {
                                    "Batch Submission Complete"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (pendingTickets.size > 1) {
                                IconButton(
                                    onClick = {
                                        awaitingResultForTicketId = null
                                        currentQueueIndex = (currentQueueIndex + 1) % pendingTickets.size
                                    },
                                    modifier = Modifier.testTag("skip_next_ticket_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Skip to next ticket in queue",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = onConfigureCredentials) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "Lottery Credentials",
                                    tint = if (userPreferences.accountEmail.isNotBlank()) LotteryEmerald else LuckyGold
                                )
                            }
                            IconButton(onClick = { isHudExpanded = !isHudExpanded }) {
                                Icon(
                                    imageVector = if (isHudExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle HUD"
                                )
                            }
                        }
                    }

                    // Progress Bar
                    if (pendingTickets.isNotEmpty()) {
                        val totalBatch = pendingTickets.size + completedInSessionCount
                        val progress = if (totalBatch > 0) {
                            completedInSessionCount.toFloat() / totalBatch.toFloat()
                        } else {
                            0f
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = LotteryEmerald,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = isHudExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))

                            if (currentTicket != null) {
                                // Current Ticket Details Box
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${currentTicket.gameName} (Game #${currentTicket.gameNumber})",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = currentTicket.formattedCode,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = LotteryEmerald
                                            )
                                            if (currentTicket.packNumber.isNotBlank() || currentTicket.ticketNumber.isNotBlank()) {
                                                Text(
                                                    text = "Pack: ${currentTicket.packNumber.ifEmpty { "--" }} • Ticket: ${currentTicket.ticketNumber.ifEmpty { "--" }}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("Lottery Code", currentTicket.entryCode)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Code copied: ${currentTicket.entryCode}", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.testTag("copy_ticket_code_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy code",
                                                tint = LuckyGold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Automation Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Auto-Fill Code & Submit
                                    Button(
                                        onClick = {
                                            val js = SubmissionAutomationHelper.generateCodeSubmissionScript(
                                                currentTicket.entryCode,
                                                currentTicket.gameNumber
                                            )
                                            val ticketId = currentTicket.id
                                            webViewInstance?.evaluateJavascript(js) { result ->
                                                val status = SubmissionAutomationHelper.unquoteJsString(result)
                                                lastAutomationStatus = status
                                                if (status.startsWith("SUCCESS")) {
                                                    awaitingResultForTicketId = ticketId
                                                    Toast.makeText(context, "Code injected into entry form", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        status.ifEmpty { "Could not locate entry field on this page" },
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = LotteryEmerald),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(44.dp)
                                            .testTag("auto_fill_code_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Auto-Fill Code", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    // Auto-Login
                                    OutlinedButton(
                                        onClick = {
                                            if (userPreferences.accountEmail.isBlank()) {
                                                onConfigureCredentials()
                                            } else {
                                                val js = SubmissionAutomationHelper.generateLoginScript(
                                                    userPreferences.accountEmail,
                                                    userPreferences.accountPassword
                                                )
                                                webViewInstance?.evaluateJavascript(js) { result ->
                                                    val status = SubmissionAutomationHelper.unquoteJsString(result)
                                                    lastAutomationStatus = status
                                                    val message = if (status.startsWith("SUCCESS")) {
                                                        "Attempting login with saved credentials"
                                                    } else {
                                                        status.ifEmpty { "Login fields not detected on current page" }
                                                    }
                                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("auto_login_button")
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = null, tint = LuckyGold, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Auto-Login", fontSize = 12.sp)
                                    }

                                    // Mark Submitted & Advance
                                    Button(
                                        onClick = {
                                            awaitingResultForTicketId = null
                                            completedInSessionCount++
                                            onTicketSubmitted(currentTicket.id, null)
                                            Toast.makeText(context, "Marked as entered! Next ticket queued.", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .height(44.dp)
                                            .testTag("mark_submitted_button")
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Confirm & Next", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                if (lastAutomationStatus != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = lastAutomationStatus ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (lastAutomationStatus?.contains("SUCCESS") == true) LotteryEmerald else LuckyGold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                            } else {
                                // No pending tickets
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = LotteryEmerald,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "All Collected Tickets Submitted!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Scan more losing scratch-off tickets to build your next batch.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Web Browser Navigation Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row {
                            IconButton(
                                onClick = { webViewInstance?.goBack() },
                                enabled = webViewInstance?.canGoBack() == true,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { webViewInstance?.goForward() },
                                enabled = webViewInstance?.canGoForward() == true,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { webViewInstance?.reload() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(18.dp))
                            }
                        }

                        // Quick Navigation Chips
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (currentPromotion != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = LotteryEmerald.copy(alpha = 0.2f),
                                    modifier = Modifier
                                        .clickable { webViewInstance?.loadUrl(currentPromotion.portalUrl) }
                                        .testTag("promotion_portal_chip")
                                ) {
                                    Text(
                                        text = currentPromotion.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LotteryEmerald,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    webViewInstance?.loadUrl(IllinoisLotteryGames.SECOND_CHANCE_ZONE_URL)
                                }
                            ) {
                                Text(
                                    text = "2nd Chance Zone",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LotteryEmerald,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    webViewInstance?.loadUrl(IllinoisLotteryGames.SECOND_CHANCE_LOGIN_URL)
                                }
                            ) {
                                Text(
                                    text = "Login",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LuckyGold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Page Loading Bar
            if (isPageLoading && pageLoadProgress < 1.0f) {
                LinearProgressIndicator(
                    progress = { pageLoadProgress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = LotteryEmerald
                )
            }

            // Embedded WebView
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = userAgentString.replace("; wv", "") // standard browser user-agent
                        }

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageLoadProgress = newProgress / 100f
                                isPageLoading = newProgress < 100
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isPageLoading = true
                                url?.let { currentWebUrl = it }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isPageLoading = false
                                url?.let { currentWebUrl = it }

                                // Check if user successfully submitted code via DOM inspection
                                // Only acts on a ticket whose code we just injected, so reloading
                                // a success page can't mark a different ticket as entered.
                                val ticketId = awaitingResultForTicketId ?: return
                                evaluateJavascript(SubmissionAutomationHelper.generateCheckSuccessScript()) { result ->
                                    if (awaitingResultForTicketId != ticketId) return@evaluateJavascript
                                    when (SubmissionAutomationHelper.parsePortalResult(result)) {
                                        PortalResult.SUBMITTED -> {
                                            awaitingResultForTicketId = null
                                            completedInSessionCount++
                                            latestOnTicketSubmitted(ticketId, null)
                                        }
                                        PortalResult.DUPLICATE -> {
                                            awaitingResultForTicketId = null
                                            completedInSessionCount++
                                            latestOnTicketDuplicate(ticketId)
                                        }
                                        // Inconclusive: stop watching on page load, so a later unrelated page can't
                                        // mark this ticket. "Confirm & Next" still marks it by hand.
                                        PortalResult.UNKNOWN -> {
                                            awaitingResultForTicketId = null
                                        }
                                    }
                                }
                            }
                        }

                        loadUrl(IllinoisLotteryGames.SECOND_CHANCE_ZONE_URL)
                        webViewInstance = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("lottery_webview")
            )
        }
    }
}
