package com.example

import com.example.scanner.LotteryCodeParser
import com.example.scanner.OcrSymbol
import com.example.ui.entryCodeHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OCR text here mimics the layout ML Kit returns for real Illinois instant tickets (entry code on its
 * own line, prize amounts, a 3-digit ticket number in a box), but every code and number is made up.
 */
class ScannerAccuracyTest {

    private fun symbols(text: String, low: Map<Int, Float> = emptyMap()): List<OcrSymbol> =
        text.mapIndexed { i, c -> OcrSymbol(c.toString(), low[i] ?: 0.9f) }

    // --- entry code format -------------------------------------------------------------------

    @Test
    fun strictCode_foundOnItsOwnLine() {
        val ocr = """
            12 WEEKS OF
            K2071935046
            ${'$'}10
            Win up to 23 times
            ${'$'}500
            072
        """.trimIndent()
        val parsed = LotteryCodeParser.parse(ocr)
        assertEquals("K2071935046", parsed.entryCode)
        assertTrue(parsed.isConfident)
        assertFalse(parsed.codeNeedsReview)
    }

    @Test
    fun strictCode_allowsSpacesAndHyphensInside() {
        assertEquals("K2071935046", LotteryCodeParser.parse("ENTRY CODE\nK207 1935 046").entryCode)
        assertEquals("K2071935046", LotteryCodeParser.parse("ENTRY CODE: K-2071-9350-46").entryCode)
    }

    @Test
    fun strayLeadingI_fromBoxEdge_isDropped() {
        val parsed = LotteryCodeParser.parse("WITH 2X, 5X, 10X\nIM3058214790\nAND 20X MULTIPLIERS!")
        assertEquals("M3058214790", parsed.entryCode)
        assertTrue(parsed.isConfident)
    }

    @Test
    fun strayPipeOrPunctuation_beforeCode_isIgnored() {
        assertEquals("M3058214790", LotteryCodeParser.parse("|M3058214790").entryCode)
        assertEquals("M3058214790", LotteryCodeParser.parse("[M3058214790]").entryCode)
    }

    @Test
    fun letterI_asTheRealFirstLetter_isKept() {
        assertEquals("I3058214790", LotteryCodeParser.parse("ENTRY CODE\nI3058214790").entryCode)
    }

    @Test
    fun digitConfusions_areCorrected_andFlagged() {
        // O->0, l->1, S->5, B->8, Z->2 in digit positions only; the leading letter is untouched.
        val parsed = LotteryCodeParser.parse("ENTRY CODE\nS1O3B427095")
        assertEquals("S1038427095", parsed.entryCode)
        assertEquals("Z1018427095", LotteryCodeParser.parse("Zl0l8427O95").entryCode)
        assertFalse(parsed.isConfident)
        assertTrue(parsed.codeNeedsReview)
    }

    @Test
    fun slashedZeroGlyph_isReadAsZero() {
        val parsed = LotteryCodeParser.parse("ENTRY CODE\nR40Ø2615Ø93")
        assertEquals("R4002615093", parsed.entryCode)
        assertTrue(parsed.isConfident)
    }

    @Test
    fun strictCode_isNotTakenFromLongerNumbersOrWords() {
        assertNull(LotteryCodeParser.parse("SERIAL A123456789012").entryCode.takeIf { LotteryCodeParser.isValidEntryCode(it) })
        assertEquals("", LotteryCodeParser.parse("CROSSWORD 50X\nGRID MULTIPLIER\n${'$'}1,000,000").entryCode)
    }

    @Test
    fun strictCode_beatsOldStyleCodeElsewhere() {
        val parsed = LotteryCodeParser.parse("SERIAL 849201582910\nENTRY CODE\nT5550192837")
        assertEquals("T5550192837", parsed.entryCode)
    }

    @Test
    fun oldFallback_stillFindsOtherFormats_butNotConfident() {
        val parsed = LotteryCodeParser.parse("ENTRY CODE: 84920158291048")
        assertEquals("84920158291048", parsed.entryCode)
        assertFalse(parsed.isConfident)
        assertTrue(parsed.codeNeedsReview)
    }

    // --- slashed zero read as 8 (per-character confidence) ------------------------------------

    @Test
    fun lowConfidence8_becomesZero_andAsksUserToCheck() {
        val ocr = "12 WEEKS OE\nP7738219406"
        // Index 4 is the printed slashed zero that OCR read as 8 with low confidence.
        val syms = listOf(symbols("P7738219406", low = mapOf(4 to 0.24f)))
        val parsed = LotteryCodeParser.parseWithTargetedRegion("", ocr, syms)
        assertEquals("P7730219406", parsed.entryCode)
        assertTrue(parsed.codeNeedsReview)
        assertTrue(parsed.codeReviewReason.contains("slashed 0"))
        assertFalse(parsed.isConfident)
    }

    @Test
    fun confident8_isKept() {
        val ocr = "P7738219406"
        val syms = listOf(symbols("P7738219406", low = mapOf(4 to 0.84f)))
        val parsed = LotteryCodeParser.parseWithTargetedRegion("", ocr, syms)
        assertEquals("P7738219406", parsed.entryCode)
        assertFalse(parsed.codeNeedsReview)
        assertTrue(parsed.isConfident)
    }

    @Test
    fun lowConfidenceZero_isNormal_forSlashedZero_andNotFlagged() {
        val syms = listOf(symbols("P7708219406", low = mapOf(3 to 0.11f, 9 to 0.30f)))
        val parsed = LotteryCodeParser.parseWithTargetedRegion("", "P7708219406", syms)
        assertEquals("P7708219406", parsed.entryCode)
        assertFalse(parsed.codeNeedsReview)
    }

    @Test
    fun veryLowConfidenceOtherDigit_isFlagged() {
        val syms = listOf(symbols("P7708219406", low = mapOf(5 to 0.20f)))
        val parsed = LotteryCodeParser.parseWithTargetedRegion("", "P7708219406", syms)
        assertEquals("P7708219406", parsed.entryCode)
        assertTrue(parsed.codeNeedsReview)
        assertTrue(parsed.codeReviewReason.contains("digit 6"))
    }

    @Test
    fun symbolRefinement_handlesStrayPrefixAlignment() {
        // Stray "I" at index 0 shifts the code by one; the low-confidence 8 is code digit 5.
        val syms = listOf(symbols("IM3058814790", low = mapOf(0 to 0.19f, 6 to 0.30f)))
        val parsed = LotteryCodeParser.parseWithTargetedRegion("", "IM3058814790", syms)
        assertEquals("M3058014790", parsed.entryCode)
        assertTrue(parsed.codeNeedsReview)
    }

    // --- ticket and pack numbers -------------------------------------------------------------

    @Test
    fun standaloneThreeDigitLine_isTheTicketNumber() {
        val ocr = """
            G4410582936
            ${'$'}5
            GRID 1
            ${'$'}100
            3
            ${'$'}10,000
            9
            GRID 2
            115
        """.trimIndent()
        val parsed = LotteryCodeParser.parse(ocr)
        assertEquals("115", parsed.ticketNumber)
        assertEquals("", parsed.packNumber)
    }

    @Test
    fun ticketNumber_keepsLeadingZeros_andIgnoresPrizeAmounts() {
        val ocr = "${'$'}500\nS100\n5000\n${'$'}25.00,\n007"
        assertEquals("007", LotteryCodeParser.parse(ocr).ticketNumber)
    }

    @Test
    fun ticketNumber_seenTwice_beatsOneOff() {
        assertEquals("064", LotteryCodeParser.parse("064\n310\n064").ticketNumber)
    }

    @Test
    fun labelledPackAndTicket_stillWin() {
        val parsed = LotteryCodeParser.parse("PACK 03317 TICKET 029\n115")
        assertEquals("03317", parsed.packNumber)
        assertEquals("029", parsed.ticketNumber)
    }

    @Test
    fun targetedRegion_ticketNumber_fallsBackToFullFrame() {
        val parsed = LotteryCodeParser.parseWithTargetedRegion("GRID 3 Y", "W9182736450\n${'$'}25\n042")
        assertEquals("042", parsed.ticketNumber)
        assertEquals("W9182736450", parsed.entryCode)
    }

    // --- input helpers ---------------------------------------------------------------------

    @Test
    fun validation_andInputNormalisation() {
        assertTrue(LotteryCodeParser.isValidEntryCode("K2071935046"))
        assertFalse(LotteryCodeParser.isValidEntryCode("2071935046"))
        assertFalse(LotteryCodeParser.isValidEntryCode("K207193504"))
        assertFalse(LotteryCodeParser.isValidEntryCode("KK071935046"))
        assertEquals("K2071935046", LotteryCodeParser.normalizeEntryCodeInput("k207-1935 046"))
        assertEquals("K2071935046", LotteryCodeParser.normalizeEntryCodeInput("K2Ø71935Ø46"))
    }

    @Test
    fun entryCodeHints_explainWhatIsWrong() {
        assertTrue(entryCodeHint("K2071935046", "").startsWith("Looks right"))
        assertTrue(entryCodeHint("2071935046", "").contains("start with a letter"))
        assertTrue(entryCodeHint("K20719", "").contains("Too short"))
        assertTrue(entryCodeHint("K207193504612", "").contains("Too long"))
        assertTrue(entryCodeHint("K20719350A6", "").contains("digits only"))
    }
}
