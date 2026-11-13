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
    val isConfident: Boolean = false,
    /** True when the code was guessed or corrected and the user should compare it with the ticket. */
    val codeNeedsReview: Boolean = false,
    /** Human-readable reason shown on the review sheet when [codeNeedsReview] is true. */
    val codeReviewReason: String = ""
)

/** One recognised character from ML Kit with its confidence (0..1). */
data class OcrSymbol(val text: String, val confidence: Float)

/** An entry code found in OCR text, with where it sits in the searched string. */
internal data class EntryCodeMatch(
    val code: String,
    /** Index in the searched string of each of the 11 code characters. */
    val sourceIndices: List<Int>,
    /** True if letters in digit positions were converted (e.g. O to 0). */
    val substituted: Boolean
)

object LotteryCodeParser {

    /** Real Illinois 2nd Chance entry codes: one letter followed by ten digits. */
    private val VALID_ENTRY_CODE = Regex("""^[A-Z]\d{10}$""")

    /** Characters ML Kit commonly returns for a digit, mapped to that digit. */
    private val DIGIT_CONFUSIONS = mapOf(
        'O' to '0', 'D' to '0', 'Q' to '0', 'Ø' to '0',
        'I' to '1', 'L' to '1', '|' to '1', '!' to '1',
        'S' to '5', 'B' to '8', 'Z' to '2'
    )

    /** Below this, a recognised 8 is treated as the ticket's slashed zero (which OCR often reads as 8). */
    internal const val SLASHED_ZERO_MAX_CONFIDENCE = 0.45f

    /** Digits other than 0 below this confidence are flagged for the user to double-check. */
    internal const val LOW_DIGIT_CONFIDENCE = 0.40f

    fun isValidEntryCode(code: String): Boolean = VALID_ENTRY_CODE.matches(code)

    /** Normalises what the user types: drops spaces and hyphens, upper-cases, maps Ø to 0. */
    fun normalizeEntryCodeInput(input: String): String =
        input.filterNot { it.isWhitespace() || it == '-' }.uppercase().replace('Ø', '0')

    /**
     * Finds a "1 letter + 10 digits" entry code in [text]. Spaces or hyphens may split it, a stray
     * I / l / | or punctuation (often the printed box edge) may precede the letter, and common OCR
     * confusions in digit positions are corrected.
     */
    internal fun findStrictEntryCode(text: String): EntryCodeMatch? {
        val s = text.uppercase()
        for (start in s.indices) {
            val letter = s[start]
            if (!letter.isLetter() || letter == 'Ø') continue
            // The code must not continue a longer word or number, except for a stray edge character.
            if (start > 0) {
                val prev = s[start - 1]
                val prevIsStray = prev == 'I' || prev == '|' || prev == 'L' || prev == '1'
                val prevPrev = if (start > 1) s[start - 2] else ' '
                if (prev.isLetterOrDigit() && !(prevIsStray && !prevPrev.isLetterOrDigit())) continue
            }
            val indices = mutableListOf(start)
            val digits = StringBuilder()
            var substituted = false
            var realDigits = 0
            var i = start + 1
            while (i < s.length && digits.length < 10) {
                val c = s[i]
                when {
                    c.isDigit() -> { digits.append(c); realDigits++; indices.add(i) }
                    DIGIT_CONFUSIONS.containsKey(c) -> {
                        digits.append(DIGIT_CONFUSIONS.getValue(c)); indices.add(i)
                        if (c != 'Ø') substituted = true else realDigits++
                    }
                    // Allow single separators inside the code, but not two in a row.
                    (c == ' ' || c == '-') && i + 1 < s.length && s[i + 1] != ' ' && s[i + 1] != '-' -> Unit
                    else -> break
                }
                i++
            }
            if (digits.length < 10 || realDigits < 7) continue
            // Must not run on into more letters or digits (that would be a longer token or shifted match).
            if (i < s.length && (s[i].isLetterOrDigit() || (s[i] == '-' && i + 1 < s.length && s[i + 1].isLetterOrDigit()))) continue
            return EntryCodeMatch("$letter$digits", indices, substituted)
        }
        return null
    }

    /**
     * Uses ML Kit's per-character confidence to fix the one misread seen on real tickets: the
     * printed slashed zero read as 8. Returns the refined code and a review reason (blank if none).
     */
    internal fun refineWithSymbols(code: String, symbolLines: List<List<OcrSymbol>>): Pair<String, String> {
        for (line in symbolLines) {
            if (line.any { it.text.length != 1 }) continue
            val match = findStrictEntryCode(line.joinToString("") { it.text }) ?: continue
            if (match.code != code) continue
            val chars = code.toCharArray()
            val reasons = mutableListOf<String>()
            for (pos in 1 until chars.size) {
                val conf = line[match.sourceIndices[pos]].confidence
                if (chars[pos] == '8' && conf < SLASHED_ZERO_MAX_CONFIDENCE) {
                    chars[pos] = '0'
                    reasons += "digit ${pos + 1} was unclear (8 or slashed 0); we used 0"
                } else if (chars[pos] != '0' && conf < LOW_DIGIT_CONFIDENCE) {
                    reasons += "digit ${pos + 1} (${chars[pos]}) was hard to read"
                }
            }
            return String(chars) to reasons.joinToString("; ")
        }
        return code to ""
    }

    /** Collects per-line character confidences from ML Kit results for [refineWithSymbols], preserving spaces between words. */
    fun symbolLinesOf(vararg texts: Text): List<List<OcrSymbol>> =
        texts.flatMap { t ->
            t.textBlocks.flatMap { b -> b.lines }.map { line ->
                buildList {
                    line.elements.forEachIndexed { index, element ->
                        if (index > 0) add(OcrSymbol(" ", 1.0f))
                        element.symbols.forEach { sym ->
                            add(OcrSymbol(sym.text, sym.confidence))
                        }
                    }
                }
            }
        }

    /**
     * The printed ticket number: a line that is exactly three digits (e.g. "042"). If OCR saw more
     * than one, the most frequent wins, then the last one.
     */
    internal fun findStandaloneTicketNumber(rawText: String): String {
        val candidates = rawText.lines().map { it.trim() }.filter { Regex("""^\d{3}$""").matches(it) }
        if (candidates.isEmpty()) return ""
        val counts = candidates.groupingBy { it }.eachCount()
        val best = counts.values.max()
        return candidates.last { counts[it] == best }
    }

    // Regex patterns for Illinois instant scratch tickets
    private val ENTRY_CODE_REGEX = Regex("""(?i)\b([0-9A-Z]{10,14})\b""")
    private val HYPHENATED_CODE_REGEX = Regex("""(?i)\b([0-9A-Z]{3,5})[- ]([0-9A-Z]{4,6})[- ]([0-9A-Z]{3,5})\b""")
    private val EXPLICIT_TICKET_REGEX = Regex("""(?i)\bticket\s*[:#]?\s*\d{2,3}\b|\b\d{3,4}[\s\-]+\d{5}[\s\-]+\d{3}\b""")

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
            fullFrameText = visionText.text,
            symbolLines = symbolLinesOf(visionText)
        )
    }

    /**
     * Parses lottery ticket details by prioritizing [targetedRegionText] for extracting
     * the second-chance entry code, falling back to [fullFrameText] for game metadata.
     */
    fun parseWithTargetedRegion(
        targetedRegionText: String,
        fullFrameText: String,
        symbolLines: List<List<OcrSymbol>> = emptyList()
    ): ParsedLotteryData {
        val roiParsed = parse(targetedRegionText)
        val fullParsed = parse(fullFrameText)

        // A real-format code anywhere beats a fallback guess; otherwise the targeted region wins.
        val codeSource = when {
            roiParsed.entryCode.isNotEmpty() && roiParsed.isConfident -> roiParsed
            fullParsed.entryCode.isNotEmpty() && fullParsed.isConfident -> fullParsed
            roiParsed.entryCode.isNotEmpty() && !roiParsed.codeNeedsReview -> roiParsed
            fullParsed.entryCode.isNotEmpty() && !fullParsed.codeNeedsReview -> fullParsed
            roiParsed.entryCode.isNotEmpty() -> roiParsed
            else -> fullParsed
        }
        val (chosenEntryCode, symbolReason) = if (isValidEntryCode(codeSource.entryCode)) {
            refineWithSymbols(codeSource.entryCode, symbolLines)
        } else {
            codeSource.entryCode to ""
        }
        val reviewReason = listOf(codeSource.codeReviewReason, symbolReason)
            .filter { it.isNotEmpty() }.joinToString(". ")
        val chosenGameNumber = fullParsed.gameNumber.ifEmpty { roiParsed.gameNumber }
        val chosenGameName = if (fullParsed.gameNumber.isNotEmpty()) fullParsed.gameName else roiParsed.gameName
        val chosenPack = roiParsed.packNumber.ifEmpty { fullParsed.packNumber }
        val chosenTicket = if (
            fullParsed.ticketNumber.isNotEmpty() &&
            EXPLICIT_TICKET_REGEX.containsMatchIn(fullFrameText) &&
            !EXPLICIT_TICKET_REGEX.containsMatchIn(targetedRegionText)
        ) {
            fullParsed.ticketNumber
        } else {
            roiParsed.ticketNumber.ifEmpty { fullParsed.ticketNumber }
        }
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
            isConfident = chosenEntryCode.isNotEmpty() && codeSource.isConfident && reviewReason.isEmpty(),
            codeNeedsReview = reviewReason.isNotEmpty(),
            codeReviewReason = reviewReason
        )
    }

    fun parse(rawText: String): ParsedLotteryData {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        var detectedGame: LotteryGame? = null
        var gameNum = ""
        var packNum = ""
        var ticketNum = ""
        var entryCode = ""

        // 1. Check if a known game's full title appears in the text (longest title wins)
        // Kept separate from gameNum so a printed "GAME ####" is still parsed below and can override it.
        val titleGame = IllinoisLotteryGames.findGameInText(rawText)
        detectedGame = titleGame

        // 2. Check for GAME-PACK-TICKET formatted string e.g., "7676-04821-014" or "GAME 7676 - PACK 04821 - TICKET 014"
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

        // A printed game number that matches a known game beats the title guess; an unknown
        // number (often an OCR slip) yields to the title match.
        val numberGame = if (gameNum.isNotEmpty()) IllinoisLotteryGames.findGameByNumber(gameNum) else null
        if (numberGame != null) {
            detectedGame = numberGame
        } else if (titleGame != null) {
            gameNum = titleGame.gameNumber
        }

        // 3. Search for the entry code. Real codes are 1 letter + 10 digits; look for that first.
        var strictCode = false
        var substituted = false
        val strict = lines.firstNotNullOfOrNull { findStrictEntryCode(it) } ?: findStrictEntryCode(rawText)
        if (strict != null) {
            entryCode = strict.code
            strictCode = true
            substituted = strict.substituted
        }

        // Older 10-14 character fallback, only for codes that don't fit the real format.
        val hyphenatedMatch = if (entryCode.isNotEmpty()) null else HYPHENATED_CODE_REGEX.findAll(rawText).firstOrNull { match ->
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
                val match = Regex("""\b(\d{4})\b""").find(line)
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

        // Ticket number printed on its own (e.g. "042" in a small box) when no label was found.
        if (ticketNum.isEmpty()) {
            ticketNum = findStandaloneTicketNumber(rawText)
        }

        val gameTitle = detectedGame?.title
            ?: if (gameNum.isNotBlank()) "Illinois Scratch Game #$gameNum" else "Illinois Instant Ticket"
        val deadline = detectedGame?.drawingDeadline ?: "Check Official Site"

        val reviewReason = when {
            entryCode.isEmpty() -> ""
            !strictCode -> "This doesn't look like a normal entry code (1 letter + 10 digits)"
            substituted -> "Some characters were unclear and were corrected"
            else -> ""
        }

        return ParsedLotteryData(
            gameNumber = gameNum,
            gameName = gameTitle,
            packNumber = packNum,
            ticketNumber = ticketNum,
            entryCode = entryCode,
            drawingDeadline = deadline,
            rawText = rawText,
            targetedRegionText = rawText,
            isConfident = strictCode && !substituted,
            codeNeedsReview = reviewReason.isNotEmpty(),
            codeReviewReason = reviewReason
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
