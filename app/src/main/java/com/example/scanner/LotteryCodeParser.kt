package com.example.scanner

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.data.IllinoisLotteryGames
import com.example.data.LotteryGame
import com.google.mlkit.vision.text.Text
import kotlin.math.max
import kotlin.math.min

/**
 * Normalized region of interest (0.0f..1.0f) targeting where second-chance lottery
 * entry codes are located on the camera frame or full ticket image.
 */
data class TargetRegion(
    val leftFraction: Float,
    val topFraction: Float,
    val rightFraction: Float,
    val bottomFraction: Float,
    val label: String
) {
    fun toPixelRect(width: Int, height: Int): Rect {
        val left = (width * leftFraction).toInt().coerceIn(0, width)
        val top = (height * topFraction).toInt().coerceIn(0, height)
        val right = (width * rightFraction).toInt().coerceIn(left, width)
        val bottom = (height * bottomFraction).toInt().coerceIn(top, height)
        return Rect(left, top, right, bottom)
    }

    companion object {
        /**
         * Viewfinder center alignment box where users frame the 2nd chance entry code.
         */
        val VIEWFINDER_CODE_BOX = TargetRegion(
            leftFraction = 0.06f,
            topFraction = 0.32f,
            rightFraction = 0.94f,
            bottomFraction = 0.68f,
            label = "Center Code Box"
        )

        /**
         * Lower scratch-off validation band where Illinois instant tickets typically print
         * the 10-14 digit second-chance entry code and pack/ticket identifiers.
         */
        val TICKET_LOWER_SCRATCH_ZONE = TargetRegion(
            leftFraction = 0.05f,
            topFraction = 0.50f,
            rightFraction = 0.95f,
            bottomFraction = 0.92f,
            label = "Lower Scratch Zone"
        )
    }
}

data class ParsedLotteryData(
    val gameNumber: String = "",
    val gameName: String = "",
    val packNumber: String = "",
    val ticketNumber: String = "",
    val entryCode: String = "",
    val drawingDeadline: String = "",
    val rawText: String = "",
    val targetedRegionText: String = "",
    val isConfident: Boolean = false
)

object LotteryCodeParser {

    // Regex patterns for Illinois instant scratch tickets
    private val ENTRY_CODE_REGEX = Regex("""(?i)\b([0-9A-Z]{10,14})\b""")
    private val HYPHENATED_CODE_REGEX = Regex("""(?i)\b([0-9A-Z]{3,5})[- ]([0-9A-Z]{4,6})[- ]([0-9A-Z]{3,5})\b""")

    /**
     * Crops a bitmap to the specified [TargetRegion] so ML Kit Text Recognition
     * processes only the area where second-chance entry codes are located.
     */
    fun cropBitmapToRegion(bitmap: Bitmap, region: TargetRegion): Bitmap {
        val rect = region.toPixelRect(bitmap.width, bitmap.height)
        val cropWidth = max(1, rect.width())
        val cropHeight = max(1, rect.height())
        val safeLeft = min(rect.left, bitmap.width - cropWidth)
        val safeTop = min(rect.top, bitmap.height - cropHeight)
        return Bitmap.createBitmap(bitmap, safeLeft, safeTop, cropWidth, cropHeight)
    }

    /**
     * Extracts text lines from ML Kit's [Text] result whose bounding boxes fall within
     * or intersect the target region where second-chance entry codes are located.
     */
    fun extractTextInRegion(
        visionText: Text,
        imageWidth: Int,
        imageHeight: Int,
        region: TargetRegion = TargetRegion.VIEWFINDER_CODE_BOX
    ): String {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return visionText.text
        }
        val roiRect = region.toPixelRect(imageWidth, imageHeight)
        val targetedLines = mutableListOf<String>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox
                if (box == null || isLineInTargetRegion(box, roiRect)) {
                    targetedLines.add(line.text)
                }
            }
        }
        return targetedLines.joinToString("\n")
    }

    fun isLineInTargetRegion(lineBox: Rect, roiRect: Rect): Boolean {
        val centerX = lineBox.centerX()
        val centerY = lineBox.centerY()
        if (roiRect.contains(centerX, centerY)) {
            return true
        }
        return Rect.intersects(lineBox, roiRect)
    }

    /**
     * Parses an ML Kit [Text] result by prioritizing text blocks inside the [targetRegion]
     * for the 2nd chance entry code while using the full frame for game header context.
     */
    fun parseVisionText(
        visionText: Text,
        imageWidth: Int,
        imageHeight: Int,
        targetRegion: TargetRegion = TargetRegion.VIEWFINDER_CODE_BOX
    ): ParsedLotteryData {
        val roiText = extractTextInRegion(visionText, imageWidth, imageHeight, targetRegion)
        return parseWithTargetedRegion(
            targetedRegionText = roiText,
            fullFrameText = visionText.text
        )
    }

    /**
     * Parses lottery ticket details by prioritizing [targetedRegionText] for extracting
     * the second-chance entry code, falling back to [fullFrameText] for game metadata.
     */
    fun parseWithTargetedRegion(
        targetedRegionText: String,
        fullFrameText: String
    ): ParsedLotteryData {
        val roiParsed = parse(targetedRegionText)
        val fullParsed = parse(fullFrameText)

        val chosenEntryCode = roiParsed.entryCode.ifEmpty { fullParsed.entryCode }
        val chosenGameNumber = fullParsed.gameNumber.ifEmpty { roiParsed.gameNumber }
        val chosenGameName = if (fullParsed.gameNumber.isNotEmpty()) fullParsed.gameName else roiParsed.gameName
        val chosenPack = roiParsed.packNumber.ifEmpty { fullParsed.packNumber }
        val chosenTicket = roiParsed.ticketNumber.ifEmpty { fullParsed.ticketNumber }
        val chosenDeadline = if (fullParsed.gameNumber.isNotEmpty()) fullParsed.drawingDeadline else roiParsed.drawingDeadline

        return ParsedLotteryData(
            gameNumber = chosenGameNumber,
            gameName = chosenGameName,
            packNumber = chosenPack,
            ticketNumber = chosenTicket,
            entryCode = chosenEntryCode,
            drawingDeadline = chosenDeadline,
            rawText = fullFrameText,
            targetedRegionText = targetedRegionText,
            isConfident = chosenEntryCode.isNotEmpty()
        )
    }

    fun parse(rawText: String): ParsedLotteryData {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        var detectedGame: LotteryGame? = null
        var gameNum = ""
        var packNum = ""
        var ticketNum = ""
        var entryCode = ""

        // 1. Check if known game titles appear in the text
        for (game in IllinoisLotteryGames.activeGames) {
            val titleWords = game.title.lowercase().split(" ")
            val containsTitle = titleWords.any { word ->
                word.length > 3 && rawText.lowercase().contains(word)
            }
            if (containsTitle) {
                detectedGame = game
                gameNum = game.gameNumber
                break
            }
        }

        // 2. Check for GAME-PACK-TICKET formatted string e.g., "852-04821-014" or "GAME 852 - PACK 04821 - TICKET 014"
        val gamePackTicketRegex = Regex("""(?i)\b(?:game\s*[:#]?\s*)?(\d{3,4})[\s\-]+(?:pack\s*[:#]?\s*)?(\d{5})[\s\-]+(?:ticket\s*[:#]?\s*)?(\d{3})\b""")
        val gamePackTicketMatch = gamePackTicketRegex.find(rawText)
        if (gamePackTicketMatch != null) {
            val (g, p, t) = gamePackTicketMatch.destructured
            gameNum = g
            packNum = p
            ticketNum = t
            if (detectedGame == null) {
                detectedGame = IllinoisLotteryGames.findGameByNumber(gameNum)
            }
        } else {
            // Check individual labels
            if (packNum.isEmpty()) {
                val packMatch = Regex("""(?i)\bpack\s*[:#]?\s*(\d{5})\b""").find(rawText)
                if (packMatch != null) packNum = packMatch.groupValues[1]
            }
            if (ticketNum.isEmpty()) {
                val ticketMatch = Regex("""(?i)\bticket\s*[:#]?\s*(\d{2,3})\b""").find(rawText)
                if (ticketMatch != null) ticketNum = ticketMatch.groupValues[1]
            }
            if (gameNum.isEmpty()) {
                val gameMatch = Regex("""(?i)\bgame\s*[:#]?\s*(\d{3,4})\b""").find(rawText)
                if (gameMatch != null) {
                    gameNum = gameMatch.groupValues[1]
                    if (detectedGame == null) {
                        detectedGame = IllinoisLotteryGames.findGameByNumber(gameNum)
                    }
                }
            }
        }

        // 3. Search for Entry Code (10 to 14 characters)
        val hyphenatedMatch = HYPHENATED_CODE_REGEX.findAll(rawText).firstOrNull { match ->
            val cleaned = match.value.replace("-", "").replace(" ", "").uppercase()
            cleaned.length in 10..14 && cleaned.count { it.isDigit() } >= 8
        }
        if (hyphenatedMatch != null) {
            entryCode = hyphenatedMatch.value.replace("-", "").replace(" ", "").uppercase()
        }

        // Prioritize lines near keywords like "ENTRY", "CODE", "SECOND", "CHANCE", "VALIDATION"
        if (entryCode.isEmpty()) {
            for (i in lines.indices) {
                val line = lines[i]
                if (line.contains("entry", ignoreCase = true) ||
                    line.contains("code", ignoreCase = true) ||
                    line.contains("chance", ignoreCase = true) ||
                    line.contains("validation", ignoreCase = true) ||
                    line.contains("ticket", ignoreCase = true)
                ) {
                    val candidates = listOfNotNull(
                        line,
                        lines.getOrNull(i + 1),
                        lines.getOrNull(i - 1)
                    )
                    for (candidate in candidates) {
                        val sanitized = sanitizePotentialCode(candidate)
                        val match = ENTRY_CODE_REGEX.findAll(sanitized).firstOrNull { m ->
                            m.value.length in 10..14 && m.value.count { it.isDigit() } >= 8
                        }
                        if (match != null) {
                            entryCode = match.value
                            break
                        }
                    }
                    if (entryCode.isNotEmpty()) break
                }
            }
        }

        // Fallback: scan all text for any 10-14 alphanumeric string containing at least 8 digits
        if (entryCode.isEmpty()) {
            val allMatches = ENTRY_CODE_REGEX.findAll(sanitizePotentialCode(rawText))
            for (m in allMatches) {
                val valStr = m.value
                if (valStr.count { it.isDigit() } >= 8 && valStr.length in 10..14) {
                    entryCode = valStr
                    break
                }
            }
        }

        // Check if game number wasn't found yet
        if (gameNum.isEmpty()) {
            for (line in lines) {
                val match = Regex("""\b(\d{3,4})\b""").find(line)
                if (match != null) {
                    val potentialGame = IllinoisLotteryGames.findGameByNumber(match.value)
                    if (potentialGame != null) {
                        detectedGame = potentialGame
                        gameNum = potentialGame.gameNumber
                        break
                    }
                }
            }
        }

        val gameTitle = detectedGame?.title
            ?: if (gameNum.isNotBlank()) "Illinois Scratch Game #$gameNum" else "Illinois Instant Ticket"
        val deadline = detectedGame?.drawingDeadline ?: "Check Official Site"

        return ParsedLotteryData(
            gameNumber = gameNum,
            gameName = gameTitle,
            packNumber = packNum,
            ticketNumber = ticketNum,
            entryCode = entryCode,
            drawingDeadline = deadline,
            rawText = rawText,
            targetedRegionText = rawText,
            isConfident = entryCode.isNotEmpty()
        )
    }

    private fun sanitizePotentialCode(input: String): String {
        return input.replace(":", " ")
            .replace(".", "")
            .replace(",", "")
            .replace("/", "")
            .replace("\\", "")
            .uppercase()
    }
}
