package com.example.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.IllinoisLotteryGames
import com.example.data.LotteryTicket
import com.example.data.TicketStatus
import com.example.ui.ConfirmUnusualCodeDialog
import com.example.ui.EntryCodeField
import com.example.ui.theme.LotteryEmerald
import com.example.ui.theme.LuckyGold
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScannerScreen(
    onTicketSaved: (LotteryTicket) -> Unit,
    onManualEntryRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isProcessing by remember { mutableStateOf(false) }
    var detectedLiveCode by remember { mutableStateOf<String?>(null) }
    var targetedZoneSnippet by remember { mutableStateOf<String?>(null) }
    var parsedCandidate by remember { mutableStateOf<ParsedLotteryData?>(null) }
    var capturedImageUri by remember { mutableStateOf<String?>(null) }
    var showReviewSheet by remember { mutableStateOf(false) }
    var activeTargetRegion by remember { mutableStateOf(TargetRegion.VIEWFINDER_CODE_BOX) }

    // Photo picker launcher for gallery tickets
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            processImageUriWithTargetedRegion(context, uri, TargetRegion.TICKET_LOWER_SCRATCH_ZONE) { parsed, savedPath ->
                isProcessing = false
                parsedCandidate = parsed
                capturedImageUri = savedPath
                showReviewSheet = true
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            // Live Camera View with ML Kit Text Recognition targeting the 2nd chance entry code zone
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraExecutor = Executors.newSingleThreadExecutor()
                    val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            processCameraFrameForEntryCode(
                                imageProxy = imageProxy,
                                textRecognizer = textRecognizer,
                                targetRegion = activeTargetRegion
                            ) { parsed ->
                                if (parsed.targetedRegionText.isNotBlank()) {
                                    targetedZoneSnippet = parsed.targetedRegionText
                                        .lines()
                                        .firstOrNull { it.isNotBlank() }
                                }
                                if (parsed.entryCode.isNotEmpty()) {
                                    detectedLiveCode = parsed.entryCode
                                    if (!showReviewSheet) {
                                        parsedCandidate = parsed
                                    }
                                }
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("CameraScanner", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize().testTag("camera_preview_view")
            )
        } else {
            // Permission Denied / Alternative View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = LotteryEmerald,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Access Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Grant camera permission to point-and-scan your instant lottery scratch-off ticket and second-chance codes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = LotteryEmerald),
                    modifier = Modifier.testTag("request_camera_perm_button")
                ) {
                    Text("Grant Permission", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.testTag("pick_photo_fallback_button")
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = LotteryEmerald)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Ticket from Photos", color = Color.White)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onManualEntryRequested,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.testTag("manual_entry_fallback_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = LuckyGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enter Code Manually", color = Color.White)
                }
            }
        }

        // Viewfinder Alignment Overlay & Targeted Entry Code Bounding Box
        if (hasCameraPermission) {
            // Top Region Selector Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        tint = LotteryEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "OCR Target Zone:",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = activeTargetRegion == TargetRegion.VIEWFINDER_CODE_BOX,
                        onClick = { activeTargetRegion = TargetRegion.VIEWFINDER_CODE_BOX },
                        label = { Text("Code Box", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LotteryEmerald,
                            selectedLabelColor = Color.Black,
                            labelColor = Color.White
                        ),
                        modifier = Modifier.testTag("roi_center_box_chip")
                    )
                    FilterChip(
                        selected = activeTargetRegion == TargetRegion.TICKET_LOWER_SCRATCH_ZONE,
                        onClick = { activeTargetRegion = TargetRegion.TICKET_LOWER_SCRATCH_ZONE },
                        label = { Text("Lower Ticket Area", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LuckyGold,
                            selectedLabelColor = Color.Black,
                            labelColor = Color.White
                        ),
                        modifier = Modifier.testTag("roi_lower_zone_chip")
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 80.dp),
                contentAlignment = if (activeTargetRegion == TargetRegion.TICKET_LOWER_SCRATCH_ZONE) {
                    Alignment.BottomCenter
                } else {
                    Alignment.Center
                }
            ) {
                // Scratch-off entry code target frame
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ML Kit Target: ${activeTargetRegion.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.95f),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .border(
                                width = 2.5.dp,
                                color = if (detectedLiveCode != null) LotteryEmerald else LuckyGold,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .testTag("viewfinder_frame"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (detectedLiveCode != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = LotteryEmerald,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Entry Code: $detectedLiveCode",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                if (!targetedZoneSnippet.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Zone OCR: $targetedZoneSnippet",
                                        color = LotteryEmerald,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Position the 2nd Chance Entry Code\ninside this target zone",
                                    color = Color.White.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (!targetedZoneSnippet.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Scanning: $targetedZoneSnippet",
                                        color = LuckyGold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Targeting the ENTRY CODE (1 letter + 10 digits) under the scratch-off",
                        color = Color.LightGray.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Bottom Controls Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pick from Gallery
                    IconButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .size(52.dp)
                            .testTag("gallery_picker_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Pick ticket from photos",
                            tint = Color.White
                        )
                    }

                    // Primary Capture / Freeze Button
                    Button(
                        onClick = {
                            if (parsedCandidate != null) {
                                showReviewSheet = true
                            } else {
                                // Nothing recognized yet: open the review sheet empty so the
                                // code is typed in, rather than pre-filling a made-up one.
                                parsedCandidate = ParsedLotteryData(
                                    entryCode = detectedLiveCode ?: "",
                                    isConfident = detectedLiveCode != null
                                )
                                showReviewSheet = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LotteryEmerald),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("snap_ticket_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Capture Ticket",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Manual Entry Button
                    IconButton(
                        onClick = onManualEntryRequested,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .size(52.dp)
                            .testTag("manual_entry_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Enter Manually",
                            tint = LuckyGold
                        )
                    }
                }
            }
        }

        // Processing Overlay
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = LotteryEmerald)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Extracting Entry Code from Target Zone...", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Ticket Review Bottom Sheet
        if (showReviewSheet && parsedCandidate != null) {
            TicketReviewSheet(
                initialData = parsedCandidate!!,
                photoUri = capturedImageUri,
                onDismiss = { showReviewSheet = false },
                onSave = { ticket ->
                    showReviewSheet = false
                    detectedLiveCode = null
                    parsedCandidate = null
                    onTicketSaved(ticket)
                }
            )
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketReviewSheet(
    initialData: ParsedLotteryData,
    photoUri: String?,
    onDismiss: () -> Unit,
    onSave: (LotteryTicket) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // No made-up default: if the scan didn't recognise the game, the user must pick it.
    var selectedGameNumber by remember { mutableStateOf(initialData.gameNumber) }
    var selectedGameTitle by remember { mutableStateOf(initialData.gameName) }
    var packNumber by remember { mutableStateOf(initialData.packNumber) }
    var ticketNumber by remember { mutableStateOf(initialData.ticketNumber) }
    var entryCode by remember { mutableStateOf(initialData.entryCode) }
    var drawingDeadline by remember { mutableStateOf(initialData.drawingDeadline) }
    var notes by remember { mutableStateOf("") }
    var gameDropdownExpanded by remember { mutableStateOf(false) }
    var confirmUnusualCode by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Verify Ticket Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Review scanned OCR fields before saving",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Photo Preview if available
            if (photoUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray)
                ) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Scanned ticket photo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Entry Code Input (Highest Priority). The scan's warning stays until the user edits the code.
            EntryCodeField(
                value = entryCode,
                onValueChange = { entryCode = it },
                modifier = Modifier.testTag("review_entry_code_input"),
                reviewReason = if (entryCode == initialData.entryCode && initialData.codeNeedsReview) {
                    initialData.codeReviewReason
                } else {
                    ""
                },
                emptyHint = "Not found in the photo. Type the 1 letter + 10 digits from the ENTRY CODE box"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Game Selection Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                val knownGame = IllinoisLotteryGames.findGameByNumber(selectedGameNumber)
                OutlinedTextField(
                    value = if (selectedGameNumber.isBlank()) "Select the game on your ticket" else "$selectedGameTitle (#$selectedGameNumber)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Illinois Instant Game") },
                    isError = knownGame?.isSecondChanceEligible != true,
                    supportingText = {
                        Text(
                            when {
                                selectedGameNumber.isBlank() -> "Game not recognised. Pick it from the list."
                                knownGame == null -> "Game #$selectedGameNumber isn't a known Illinois game. Pick it from the list."
                                knownGame.promotion == null -> "This game has no open 2nd Chance promotion."
                                else -> "${knownGame.promotion.name} • enter by ${knownGame.promotion.finalEntryDeadline}"
                            }
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { gameDropdownExpanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { gameDropdownExpanded = true }
                        .testTag("review_game_selector")
                )

                DropdownMenu(
                    expanded = gameDropdownExpanded,
                    onDismissRequest = { gameDropdownExpanded = false }
                ) {
                    IllinoisLotteryGames.activeGames.forEach { game ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(game.title, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Game #${game.gameNumber} • ${game.ticketPrice} • ${game.promotion?.name} • enter by ${game.drawingDeadline}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            onClick = {
                                selectedGameNumber = game.gameNumber
                                selectedGameTitle = game.title
                                drawingDeadline = game.drawingDeadline
                                gameDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pack & Ticket Number Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = packNumber,
                    onValueChange = { packNumber = it },
                    label = { Text("Pack # (Optional)") },
                    modifier = Modifier.weight(1f).testTag("review_pack_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = ticketNumber,
                    onValueChange = { ticketNumber = it },
                    label = { Text("Ticket # (Optional)") },
                    modifier = Modifier.weight(1f).testTag("review_ticket_num_input"),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (Optional)") },
                modifier = Modifier.fillMaxWidth().testTag("review_notes_input"),
                placeholder = { Text("e.g., Gas station purchase, gift") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            val saveTicket = {
                val ticket = LotteryTicket(
                    gameNumber = selectedGameNumber,
                    gameName = selectedGameTitle,
                    packNumber = packNumber,
                    ticketNumber = ticketNumber,
                    entryCode = entryCode,
                    imageUri = photoUri,
                    drawingDeadline = drawingDeadline,
                    expirationDate = drawingDeadline,
                    status = TicketStatus.PENDING,
                    notes = notes
                )
                onSave(ticket)
            }

            if (confirmUnusualCode) {
                ConfirmUnusualCodeDialog(
                    code = entryCode,
                    onConfirm = { confirmUnusualCode = false; saveTicket() },
                    onDismiss = { confirmUnusualCode = false }
                )
            }

            Button(
                onClick = {
                    if (LotteryCodeParser.isValidEntryCode(entryCode)) saveTicket() else confirmUnusualCode = true
                },
                enabled = entryCode.isNotBlank() &&
                    IllinoisLotteryGames.findGameByNumber(selectedGameNumber)?.isSecondChanceEligible == true,
                colors = ButtonDefaults.buttonColors(containerColor = LotteryEmerald),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_ticket_to_batch_button")
            ) {
                Text(
                    text = "Add to Batch Submission Queue",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

/**
 * Configures ML Kit Text Recognition on the live camera [ImageProxy], filtering recognized
 * text blocks by bounding box within the [targetRegion] where second-chance entry codes
 * are located while retaining full-frame context for game identification.
 */
@OptIn(ExperimentalGetImage::class)
private fun processCameraFrameForEntryCode(
    imageProxy: ImageProxy,
    textRecognizer: TextRecognizer,
    targetRegion: TargetRegion,
    onResult: (ParsedLotteryData) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val rotatedWidth = if (isRotated) imageProxy.height else imageProxy.width
        val rotatedHeight = if (isRotated) imageProxy.width else imageProxy.height

        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val parsed = LotteryCodeParser.parseVisionText(
                    visionText = visionText,
                    imageWidth = rotatedWidth,
                    imageHeight = rotatedHeight,
                    targetRegion = targetRegion
                )
                onResult(parsed)
            }
            .addOnFailureListener {
                // Ignore transient frame errors
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

/**
 * Runs ML Kit Text Recognition on both the cropped [TargetRegion] (where second-chance
 * lottery entry codes are located) and the full ticket image.
 */
private fun processImageUriWithTargetedRegion(
    context: Context,
    uri: Uri,
    targetRegion: TargetRegion,
    onComplete: (ParsedLotteryData, String?) -> Unit
) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (bitmap != null) {
            val localFile = File(context.filesDir, "ticket_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(localFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.flush()
            outputStream.close()

            val croppedRegionBitmap = LotteryCodeParser.cropBitmapToRegion(bitmap, targetRegion)
            val regionInputImage = InputImage.fromBitmap(croppedRegionBitmap, 0)
            val fullInputImage = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(regionInputImage)
                .addOnSuccessListener { regionVisionText ->
                    recognizer.process(fullInputImage)
                        .addOnSuccessListener { fullVisionText ->
                            val parsed = LotteryCodeParser.parseWithTargetedRegion(
                                targetedRegionText = regionVisionText.text,
                                fullFrameText = fullVisionText.text,
                                symbolLines = LotteryCodeParser.symbolLinesOf(regionVisionText, fullVisionText)
                            )
                            onComplete(parsed, localFile.absolutePath)
                        }
                        .addOnFailureListener {
                            val parsed = LotteryCodeParser.parse(regionVisionText.text)
                            onComplete(parsed, localFile.absolutePath)
                        }
                }
                .addOnFailureListener {
                    onComplete(ParsedLotteryData(rawText = "Failed to parse OCR"), localFile.absolutePath)
                }
        } else {
            onComplete(ParsedLotteryData(), null)
        }
    } catch (e: Exception) {
        Log.e("CameraScanner", "Failed to process photo uri", e)
        onComplete(ParsedLotteryData(), null)
    }
}
