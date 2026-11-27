package com.jtownzen.secondchanceil.submission

import com.jtownzen.secondchanceil.data.IllinoisLotteryGames
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.movableContentOf
import com.jtownzen.secondchanceil.ui.StepBadge
import com.jtownzen.secondchanceil.ui.StepState
import com.jtownzen.secondchanceil.ui.isLandscape
import com.jtownzen.secondchanceil.ui.stepStates
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.jtownzen.secondchanceil.data.GameSyncReport
import com.jtownzen.secondchanceil.data.LotteryTicket
import com.jtownzen.secondchanceil.data.PortalGameList
import com.jtownzen.secondchanceil.ui.LearnedGamesDialog
import androidx.compose.runtime.collectAsState
import com.jtownzen.secondchanceil.data.TicketStatus
import com.jtownzen.secondchanceil.data.UserPreferences
import com.jtownzen.secondchanceil.ui.theme.LotteryEmerald
import com.jtownzen.secondchanceil.ui.theme.LuckyGold
import com.jtownzen.secondchanceil.ui.theme.StatusSubmitted
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@SuppressLint("SetJavaScriptEnabled")
/**
 * When false (the default), Auto-Fill picks the game and types the code, and the user presses
 * the site's Submit Entry. Every failed try counts toward the site's 5-try lockout.
 */
const val AUTO_SUBMIT_ENTRIES = false

@Composable
fun BatchSubmissionScreen(
    pendingTickets: List<LotteryTicket>,
    userPreferences: UserPreferences,
    onTicketSubmitted: (ticketId: Long, confirmation: String?) -> Unit,
    onTicketDuplicate: (ticketId: Long) -> Unit,
    onConfigureCredentials: () -> Unit,
    modifier: Modifier = Modifier,
    sessionResetCount: Int = 0,
    /** Merges the portal's Select game list into the app's games; returns what changed. */
    onPortalGameList: (PortalGameList) -> GameSyncReport = { GameSyncReport.NONE },
    onRemoveLearnedGame: (gameNumber: String) -> Unit = {}
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

    // --- Lockout guard -----------------------------------------------------------------
    // The site allows 5 failed entry attempts, then blocks entries for hours. The app never
    // submits while that block is on, and never retries a rejected code without asking.
    val guardPrefs = remember { context.getSharedPreferences("portal_guard", Context.MODE_PRIVATE) }
    var lockedUntilMillis by remember { mutableStateOf(guardPrefs.getLong("locked_until", 0L)) }
    fun isLockedOut(): Boolean = System.currentTimeMillis() < lockedUntilMillis
    fun lockedUntilText(): String =
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(lockedUntilMillis))
    fun recordLockout(hours: Int) {
        val until = System.currentTimeMillis() + hours * 60L * 60L * 1000L
        if (until > lockedUntilMillis) {
            lockedUntilMillis = until
            guardPrefs.edit().putLong("locked_until", until).apply()
        }
    }
    // Game list sync: read the portal's Select game list whenever Enter Your Tickets loads (at most
    // every couple of minutes), or on demand from the Update games button.
    var gameListReadRequest by remember { mutableStateOf(0) }
    var manualGameSync by remember { mutableStateOf(false) }
    var lastAutoGameSyncAt by remember { mutableStateOf(0L) }
    var gameSyncDialog by remember { mutableStateOf<GameSyncReport?>(null) }
    // Tickets the site rejected in this session; Auto-Fill asks before trying them again.
    val rejectedTicketIds = remember { mutableStateListOf<Long>() }
    var confirmRetryFor by remember { mutableStateOf<LotteryTicket?>(null) }
    // The result the page already showed when Auto-Fill ran; cleared when a new page loads.
    var staleResultOnPage by remember { mutableStateOf<PortalResult?>(null) }

    // After a ticket is done, go straight back to Enter Your Tickets for the next one.
    fun openEntryFormForNextTicket(finishedTicketId: Long) {
        if (pendingTickets.any { it.id != finishedTicketId }) {
            webViewInstance?.loadUrl(IllinoisLotteryGames.SECOND_CHANCE_ENTER_URL)
        }
    }

    // A ticket is only marked entered when the site itself says the entry went in.
    // Returns true when the result was conclusive (the ticket is no longer being watched).
    fun handlePortalResult(
        ticketId: Long,
        result: PortalResult,
        submitSeen: Boolean = false,
        siteMessage: String = ""
    ): Boolean {
        if (awaitingResultForTicketId != ticketId) return true
        // A message that was already on the page before this attempt (e.g. an old error banner)
        // says nothing about it, unless Submit has been pressed since (then it's the answer).
        if (result == staleResultOnPage && !submitSeen) return false
        return when (result) {
            PortalResult.SUBMITTED -> {
                awaitingResultForTicketId = null
                completedInSessionCount++
                lastAutomationStatus = "ENTERED: the site accepted this entry"
                latestOnTicketSubmitted(ticketId, null)
                Toast.makeText(context, "Entry accepted by the 2nd Chance Zone", Toast.LENGTH_SHORT).show()
                openEntryFormForNextTicket(ticketId)
                true
            }
            PortalResult.DUPLICATE -> {
                awaitingResultForTicketId = null
                completedInSessionCount++
                lastAutomationStatus = "DUPLICATE: the site says this code was already entered"
                latestOnTicketDuplicate(ticketId)
                Toast.makeText(context, "Already entered before. Moved to Duplicates.", Toast.LENGTH_LONG).show()
                openEntryFormForNextTicket(ticketId)
                true
            }
            PortalResult.REJECTED -> {
                awaitingResultForTicketId = null
                if (!rejectedTicketIds.contains(ticketId)) rejectedTicketIds.add(ticketId)
                lastAutomationStatus = if (siteMessage.isNotBlank()) {
                    "NOT ENTERED: the site said \"$siteMessage\" Ticket stays in the queue."
                } else {
                    "NOT ENTERED: the site rejected the entry. Ticket stays in the queue."
                }
                Toast.makeText(
                    context,
                    "The site didn't accept this entry. Failed tries count toward a lockout, so check the code before trying again.",
                    Toast.LENGTH_LONG
                ).show()
                true
            }
            PortalResult.LOCKED -> {
                awaitingResultForTicketId = null
                if (!rejectedTicketIds.contains(ticketId)) rejectedTicketIds.add(ticketId)
                recordLockout(SubmissionAutomationHelper.DEFAULT_LOCKOUT_HOURS)
                lastAutomationStatus = "LOCKED: too many failed tries. Entries blocked until ${lockedUntilText()}."
                Toast.makeText(context, "The site has blocked entries until about ${lockedUntilText()}.", Toast.LENGTH_LONG).show()
                true
            }
            PortalResult.UNKNOWN -> false
        }
    }

    val latestHandlePortalResult by rememberUpdatedState(::handlePortalResult)

    // Poll DOM for SPA / XHR submission results that don't trigger a full page reload
    LaunchedEffect(awaitingResultForTicketId) {
        val targetId = awaitingResultForTicketId ?: return@LaunchedEffect
        val maxAttempts = 120
        for (attempt in 1..maxAttempts) {
            delay(1000L)
            if (awaitingResultForTicketId != targetId) return@LaunchedEffect
            val wv = webViewInstance ?: break
            val isLastAttempt = attempt == maxAttempts
            wv.evaluateJavascript(SubmissionAutomationHelper.generateCheckSuccessScript()) { result ->
                val conclusive = handlePortalResult(
                    targetId,
                    SubmissionAutomationHelper.parsePortalResult(result),
                    SubmissionAutomationHelper.submitSeen(result),
                    SubmissionAutomationHelper.portalMessage(result)
                )
                if (!conclusive && isLastAttempt && awaitingResultForTicketId == targetId) {
                    awaitingResultForTicketId = null
                    lastAutomationStatus = "UNCONFIRMED: no confirmation from the site. Ticket stays in the queue."
                    Toast.makeText(
                        context,
                        "No confirmation from the site, so the ticket stays in your queue. Check My Entries.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(sessionResetCount) {
        if (sessionResetCount > 0) {
            awaitingResultForTicketId = null
            webViewInstance?.apply {
                clearCache(true)
                clearHistory()
                loadUrl(IllinoisLotteryGames.SECOND_CHANCE_ZONE_URL)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.apply {
                stopLoading()
                removeAllViews()
                destroy()
            }
            webViewInstance = null
        }
    }

    val currentTicket = pendingTickets.getOrNull(currentQueueIndex)

    val scope = rememberCoroutineScope()
    var autoFillJob by remember { mutableStateOf<Job?>(null) }

    suspend fun runJs(wv: WebView, js: String): String = suspendCancellableCoroutine { cont ->
        wv.evaluateJavascript(js) { result ->
            if (cont.isActive) cont.resume(SubmissionAutomationHelper.unquoteJsString(result))
        }
    }

    // Reads the Select game list once the page has built it (it is a script-built form).
    LaunchedEffect(gameListReadRequest) {
        if (gameListReadRequest == 0) return@LaunchedEffect
        val manual = manualGameSync
        // The dropdown is script-built: accept it only once two reads a second apart agree, so a
        // half-filled list can never retire the games that hadn't loaded yet.
        var list: PortalGameList? = null
        var previous: PortalGameList? = null
        for (attempt in 1..15) {
            delay(1000L)
            val wv = webViewInstance ?: break
            val read = SubmissionAutomationHelper.parsePortalGameList(runJs(wv, SubmissionAutomationHelper.generateReadGameListScript()))
            if (read != null && read == previous) {
                list = read
                break
            }
            previous = read
        }
        manualGameSync = false
        val found = list
        if (found == null) {
            if (manual) {
                Toast.makeText(
                    context,
                    if (currentWebUrl.contains("login", ignoreCase = true)) "Log in to the 2nd Chance Zone, then tap Update games again."
                    else "Couldn't find the game list on Enter Your Tickets.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return@LaunchedEffect
        }
        val report = onPortalGameList(found)
        if (manual) {
            gameSyncDialog = report
        } else if (report.added.isNotEmpty()) {
            val names = report.added.joinToString(", ") { "${it.title} (#${it.gameNumber})" }
            Toast.makeText(context, "New on the 2nd Chance Zone: $names. Added to your games.", Toast.LENGTH_LONG).show()
        }
    }

    // Picks the ticket's game in the portal's "Select game" list, fills the entry code and submits.
    // If the current page has no entry form, opens "Enter Your Tickets" first and waits for it.
    fun startAutoFill(ticket: LotteryTicket, retryConfirmed: Boolean = false) {
        val wv = webViewInstance ?: return
        if (isLockedOut()) {
            lastAutomationStatus = "LOCKED: entries blocked until ${lockedUntilText()}."
            Toast.makeText(context, "The site has blocked entries until about ${lockedUntilText()}. Nothing was sent.", Toast.LENGTH_LONG).show()
            return
        }
        if (!retryConfirmed && rejectedTicketIds.contains(ticket.id)) {
            confirmRetryFor = ticket
            return
        }
        autoFillJob?.cancel()
        awaitingResultForTicketId = null
        autoFillJob = scope.launch {
            val js = SubmissionAutomationHelper.generateCodeSubmissionScript(
                ticket.entryCode,
                ticket.gameNumber,
                IllinoisLotteryGames.findGameByNumber(ticket.gameNumber)?.title ?: ticket.gameName,
                autoSubmit = AUTO_SUBMIT_ENTRIES
            )
            // Also put the code on the clipboard, so it can be pasted by hand if the page refuses it.
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.setPrimaryClip(ClipData.newPlainText("Lottery Code", ticket.entryCode))
            var status = if (IllinoisLotteryGames.isAllowedPortalUrl(wv.url)) {
                // Note what the page already says before anything is filled or sent.
                staleResultOnPage = SubmissionAutomationHelper.parsePortalResult(
                    runJs(wv, SubmissionAutomationHelper.generateCheckSuccessScript())
                ).takeIf { it != PortalResult.UNKNOWN }
                runJs(wv, js)
            } else {
                "NO_FORM"
            }
            if (status.startsWith("NO_FORM")) {
                if (!IllinoisLotteryGames.isAllowedPortalUrl(wv.url) || !currentWebUrl.contains("/2ndchance/enter")) {
                    Toast.makeText(context, "Opening Enter Your Tickets…", Toast.LENGTH_SHORT).show()
                    // The site preselects the game from ?game_id=.
                    wv.loadUrl(IllinoisLotteryGames.enterUrlFor(ticket.gameNumber))
                }
                // Wait for the page (and any script-built form) to appear, up to ~15 s.
                for (attempt in 1..20) {
                    delay(750L)
                    if (isPageLoading) continue
                    if (!IllinoisLotteryGames.isAllowedPortalUrl(wv.url)) continue
                    status = runJs(wv, js)
                    if (!status.startsWith("NO_FORM")) break
                }
            }
            if (!IllinoisLotteryGames.isAllowedPortalUrl(wv.url) && status.startsWith("NO_FORM")) {
                lastAutomationStatus = "BLOCKED: auto-fill only runs on the official Illinois 2nd Chance site"
                Toast.makeText(context, "Open the official Illinois 2nd Chance site before auto-filling.", Toast.LENGTH_LONG).show()
                return@launch
            }
            lastAutomationStatus = status
            val message = when {
                status.startsWith("SUCCESS") -> {
                    awaitingResultForTicketId = ticket.id
                    lastAutomationStatus = "WAITING: submitted, waiting for the site to confirm…"
                    "Submitted. Waiting for the site to confirm…"
                }
                status.startsWith("FILLED") -> {
                    // The user presses Submit Entry; keep watching for the site's answer.
                    awaitingResultForTicketId = ticket.id
                    lastAutomationStatus = "FILLED: check the game and code, then tap Submit Entry on the page."
                    "Filled in. Check the code matches your ticket, then tap Submit Entry."
                }
                status.startsWith("LOCKED") -> {
                    if (!rejectedTicketIds.contains(ticket.id)) rejectedTicketIds.add(ticket.id)
                    recordLockout(SubmissionAutomationHelper.lockoutHours(status))
                    lastAutomationStatus = "LOCKED: entries blocked until ${lockedUntilText()}."
                    "The site has blocked entries until about ${lockedUntilText()}. Nothing was sent."
                }
                status.startsWith("NO_GAME") -> status.removePrefix("NO_GAME: ")
                status.startsWith("NO_CODE_BOX") -> status.removePrefix("NO_CODE_BOX: ") + " (code copied)"
                status.startsWith("NOT_FILLED") -> status.removePrefix("NOT_FILLED: ")
                status.startsWith("NO_FORM") && currentWebUrl.contains("login", ignoreCase = true) ->
                    "Log in to the 2nd Chance Zone, then tap Auto-Fill again"
                status.startsWith("NO_FORM") -> "Couldn't find the entry form. Open Enter Your Tickets and try again."
                else -> status.ifEmpty { "Could not locate entry field on this page" }
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    gameSyncDialog?.let { report ->
        val learnedState by IllinoisLotteryGames.learnedState.collectAsState()
        LearnedGamesDialog(
            report = report,
            state = learnedState,
            onRemove = onRemoveLearnedGame,
            onDismiss = { gameSyncDialog = null }
        )
    }
    confirmRetryFor?.let { ticket ->
        AlertDialog(
            onDismissRequest = { confirmRetryFor = null },
            title = { Text("Try this code again?") },
            text = {
                Text(
                    "The site already rejected ${ticket.formattedCode}. After 5 failed tries it blocks all " +
                        "entries for 8 hours. Check the code against your ticket (0 vs O, 1 vs I, 8 vs B) " +
                        "before trying again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRetryFor = null
                    startAutoFill(ticket, retryConfirmed = true)
                }) { Text("Try again") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRetryFor = null }) { Text("Cancel") }
            }
        )
    }

    // "Confirm & Next" marks a ticket entered by hand, so it asks first.
    var confirmManualEntryFor by remember { mutableStateOf<LotteryTicket?>(null) }
    confirmManualEntryFor?.let { ticket ->
        AlertDialog(
            onDismissRequest = { confirmManualEntryFor = null },
            title = { Text("Mark as entered?") },
            text = {
                Text(
                    "Only do this if the 2nd Chance Zone showed that ${ticket.formattedCode} was accepted " +
                        "(it's listed under My Entries). Otherwise use Skip to move on and keep it in the queue."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmManualEntryFor = null
                    awaitingResultForTicketId = null
                    completedInSessionCount++
                    onTicketSubmitted(ticket.id, null)
                    Toast.makeText(context, "Marked as entered.", Toast.LENGTH_SHORT).show()
                    openEntryFormForNextTicket(ticket.id)
                }) { Text("Yes, it was accepted") }
            },
            dismissButton = {
                TextButton(onClick = { confirmManualEntryFor = null }) { Text("Cancel") }
            }
        )
    }

    // Step 1: fills the saved login into the official site's login form.
    fun runAutoLogin() {
        if (userPreferences.accountEmail.isBlank()) {
            onConfigureCredentials()
        } else {
            val wv = webViewInstance
            if (wv == null || !IllinoisLotteryGames.isAllowedPortalUrl(wv.url)) {
                lastAutomationStatus = "BLOCKED: auto-login only runs on the official Illinois 2nd Chance site"
                Toast.makeText(
                    context,
                    "Open the official Illinois 2nd Chance Login page before auto-filling credentials.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val js = SubmissionAutomationHelper.generateLoginScript(
                    userPreferences.accountEmail,
                    userPreferences.accountPassword
                )
                wv.evaluateJavascript(js) { result ->
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
        }
    }

    val landscape = isLandscape()
    // Which step is next: on the login page it's 1; after Auto-Fill (waiting for the site) it's 3; otherwise 2.
    val currentSubmitStep = when {
        currentWebUrl.contains("login", ignoreCase = true) -> 0
        currentTicket != null && awaitingResultForTicketId == currentTicket.id -> 2
        else -> 1
    }
    val submitStepStates = stepStates(3, currentSubmitStep)

    val currentPromotion = remember(currentTicket?.gameNumber) {
        currentTicket?.let { IllinoisLotteryGames.findGameByNumber(it.gameNumber)?.promotion }
    }

    // The page lives in movable content so turning the phone moves it instead of reloading it
    // (a reload would lose the login and anything Auto-Fill typed).
    val webContent = remember {
        movableContentOf { webModifier: Modifier ->
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
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
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
                            // Keep only the official Illinois 2nd Chance HTTPS site inside the
                            // credentialed WebView; external links open in the device browser.
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val targetUrl = request?.url?.toString()
                                val isMainFrame = request?.isForMainFrame ?: true
                                if (!isMainFrame) {
                                    val scheme = request?.url?.scheme?.lowercase()
                                    return scheme != "https" && targetUrl != "about:blank"
                                }
                                val secure = IllinoisLotteryGames.upgradeToHttps(targetUrl)
                                if (secure != null) {
                                    view?.loadUrl(secure)
                                    return true
                                }
                                if (IllinoisLotteryGames.isAllowedPortalUrl(targetUrl)) {
                                    return false
                                }
                                if (IllinoisLotteryGames.isExternalHttpUrl(targetUrl)) {
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                                        Toast.makeText(ctx, "Opened external link in browser", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {
                                        Toast.makeText(ctx, "No browser available for external link", Toast.LENGTH_SHORT).show()
                                    }
                                    return true
                                }
                                return true
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                val secure = IllinoisLotteryGames.upgradeToHttps(url)
                                if (secure != null) {
                                    view?.stopLoading()
                                    view?.loadUrl(secure)
                                    return
                                }
                                if (url != null && !IllinoisLotteryGames.isAllowedPortalUrl(url)) {
                                    view?.stopLoading()
                                    return
                                }
                                isPageLoading = true
                                staleResultOnPage = null
                                url?.let { currentWebUrl = it }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isPageLoading = false
                                url?.let { currentWebUrl = it }

                                // Learn the portal's current game list from Enter Your Tickets.
                                if (url != null && url.contains("/2ndchance/enter")) {
                                    val now = System.currentTimeMillis()
                                    if (manualGameSync || now - lastAutoGameSyncAt > 2 * 60_000L) {
                                        lastAutoGameSyncAt = now
                                        gameListReadRequest++
                                    }
                                } else if (manualGameSync && url != null && url.contains("login", ignoreCase = true)) {
                                    manualGameSync = false
                                    Toast.makeText(context, "Log in to the 2nd Chance Zone, then tap Update games again.", Toast.LENGTH_LONG).show()
                                }

                                // Check if user successfully submitted code via DOM inspection
                                // Only acts on a ticket whose code we just injected, so reloading
                                // a success page can't mark a different ticket as entered.
                                val ticketId = awaitingResultForTicketId ?: return
                                // Inconclusive results keep watching; the poll above gives up after ~30 s.
                                evaluateJavascript(SubmissionAutomationHelper.generateCheckSuccessScript()) { result ->
                                    latestHandlePortalResult(
                                        ticketId,
                                        SubmissionAutomationHelper.parsePortalResult(result),
                                        SubmissionAutomationHelper.submitSeen(result),
                                        SubmissionAutomationHelper.portalMessage(result)
                                    )
                                }
                            }
                        }

                        loadUrl(IllinoisLotteryGames.SECOND_CHANCE_ZONE_URL)
                        webViewInstance = this
                    }
                },
                modifier = webModifier.testTag("lottery_webview")
            )
        }
    }

    val hud: @Composable (Modifier) -> Unit = { hudModifier ->
            Card(
                modifier = hudModifier.testTag("batch_automation_hud"),
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
                            IconButton(
                                onClick = {
                                    manualGameSync = true
                                    if (currentWebUrl.contains("/2ndchance/enter")) {
                                        gameListReadRequest++
                                    } else {
                                        webViewInstance?.loadUrl(IllinoisLotteryGames.SECOND_CHANCE_ENTER_URL)
                                    }
                                },
                                modifier = Modifier.testTag("update_games_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Update games from the 2nd Chance Zone",
                                    tint = if (manualGameSync) LuckyGold else MaterialTheme.colorScheme.onSurfaceVariant
                                )
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

                                // The steps, in the order they are done: 1 log in, 2 Auto-Fill,
                                // 3 the user taps the site's Submit Entry (Auto-Fill never submits).
                                // The next step is the filled button; the others are outlined.
                                val loginButton: @Composable (Modifier) -> Unit = { buttonModifier ->
                                    StepActionButton(
                                        number = 1,
                                        label = "Log In",
                                        state = submitStepStates[0],
                                        onClick = ::runAutoLogin,
                                        modifier = buttonModifier.testTag("auto_login_button")
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                                val autoFillButton: @Composable (Modifier) -> Unit = { buttonModifier ->
                                    StepActionButton(
                                        number = 2,
                                        label = "Auto-Fill Game & Code",
                                        state = submitStepStates[1],
                                        onClick = { startAutoFill(currentTicket) },
                                        modifier = buttonModifier.testTag("auto_fill_code_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }

                                if (landscape) {
                                    loginButton(Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(8.dp))
                                    autoFillButton(Modifier.fillMaxWidth())
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        loginButton(Modifier.weight(0.8f))
                                        autoFillButton(Modifier.weight(1.2f))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Step 3 is on the website itself, so it's an instruction, not a button.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (submitStepStates[2] == StepState.CURRENT) {
                                                Modifier.border(1.5.dp, LuckyGold, RoundedCornerShape(10.dp))
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("submit_step_three")
                                ) {
                                    StepBadge(number = 3, state = submitStepStates[2], size = 22)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (landscape) "Tap Submit Entry on the page" else "Tap Submit Entry on the page below",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (submitStepStates[2] == StepState.CURRENT) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (submitStepStates[2] == StepState.CURRENT) {
                                            Text(
                                                text = "Check the code matches your ticket first. The next ticket loads when the site accepts it.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Fallback for when the site accepted it but the app didn't see the message.
                                TextButton(
                                    onClick = { confirmManualEntryFor = currentTicket },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.testTag("mark_submitted_button")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Site accepted it? Mark entered & next",
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (lastAutomationStatus != null) {
                                    Text(
                                        text = lastAutomationStatus ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (lastAutomationStatus?.let { it.startsWith("ENTERED") || it.startsWith("WAITING") || it.startsWith("FILLED") } == true) LotteryEmerald else LuckyGold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = if (landscape) 4 else 1
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
                                onClick = {
                                    awaitingResultForTicketId = null
                                    webViewInstance?.goBack()
                                },
                                enabled = webViewInstance?.canGoBack() == true,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    awaitingResultForTicketId = null
                                    webViewInstance?.goForward()
                                },
                                enabled = webViewInstance?.canGoForward() == true,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    awaitingResultForTicketId = null
                                    webViewInstance?.reload()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(18.dp))
                            }
                        }

                        // Quick Navigation Chips: one line each; scroll sideways when they don't all fit.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            if (currentPromotion != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = LotteryEmerald.copy(alpha = 0.2f),
                                    modifier = Modifier
                                        .clickable {
                                            awaitingResultForTicketId = null
                                            webViewInstance?.loadUrl(currentPromotion.portalUrl)
                                        }
                                        .testTag("promotion_portal_chip")
                                ) {
                                    Text(
                                        text = currentPromotion.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = LotteryEmerald,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    awaitingResultForTicketId = null
                                    webViewInstance?.loadUrl(IllinoisLotteryGames.SECOND_CHANCE_ZONE_URL)
                                }
                            ) {
                                Text(
                                    text = "2nd Chance Zone",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = LotteryEmerald,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    awaitingResultForTicketId = null
                                    webViewInstance?.loadUrl(IllinoisLotteryGames.SECOND_CHANCE_LOGIN_URL)
                                }
                            ) {
                                Text(
                                    text = "Login",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = LuckyGold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
    }

    val loadingBar: @Composable () -> Unit = {
            if (isPageLoading && pageLoadProgress < 1.0f) {
                LinearProgressIndicator(
                    progress = { pageLoadProgress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = LotteryEmerald
                )
            }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (landscape) {
            // Landscape: the website on the left, the steps in a column on the right.
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    loadingBar()
                    webContent(Modifier.fillMaxSize())
                }
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    hud(Modifier.fillMaxWidth().padding(8.dp))
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                hud(Modifier.fillMaxWidth().padding(8.dp))
                loadingBar()
                webContent(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

/**
 * A numbered step button. The step to do next is a filled green button; a step that is done
 * or not yet due is outlined, so the eye goes to the right button first.
 */
@Composable
private fun StepActionButton(
    number: Int,
    label: String,
    state: StepState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val content: @Composable () -> Unit = {
        StepBadge(number = number, state = state, size = 20)
        Spacer(modifier = Modifier.width(6.dp))
        icon()
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (state == StepState.CURRENT) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = LotteryEmerald, contentColor = Color.Black),
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = modifier.heightIn(min = 48.dp)
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = modifier.heightIn(min = 48.dp)
        ) { content() }
    }
}
