package com.example

import com.example.data.IllinoisLotteryGames
import com.example.scanner.LotteryCodeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** All entry codes here are synthetic (1 letter + 10 digits, like real IL tickets) and not real tickets. */
class ExampleUnitTest {
    @Test
    fun testParseLotteryCode_standardIllinoisFormat() {
        val rawOcr = """
            ILLINOIS LOTTERY
            REINDEER RICHES
            GAME 7676 - PACK 04821 - TICKET 014
            SCRATCH FOR SECOND CHANCE
            ENTRY CODE: B4920158291
            KEEP TICKET TO CLAIM PRIZE
        """.trimIndent()

        val parsed = LotteryCodeParser.parse(rawOcr)

        assertEquals("7676", parsed.gameNumber)
        assertEquals("Reindeer Riches", parsed.gameName)
        assertEquals("04821", parsed.packNumber)
        assertEquals("014", parsed.ticketNumber)
        assertEquals("B4920158291", parsed.entryCode)
        assertEquals("Jan 10, 2027", parsed.drawingDeadline)
        assertTrue(parsed.isConfident)
    }

    @Test
    fun testParseLotteryCode_hyphenatedCode() {
        val rawOcr = """
            ${'$'}250,000 CROSSWORD
            ENTRY CODE: B492-0158-291
        """.trimIndent()

        val parsed = LotteryCodeParser.parse(rawOcr)

        assertEquals("B4920158291", parsed.entryCode)
        assertEquals("7664", parsed.gameNumber)
        assertTrue(parsed.isConfident)
    }

    @Test
    fun testParseLotteryCode_targetedRegionPrioritizesScratchZone() {
        val fullFrameText = """
            ILLINOIS LOTTERY
            WINTER WINNINGS
            SERIAL: 111111111111
            2ND CHANCE ENTRY: C9145029381
            GAME 7677 - PACK 00842 - TICKET 022
        """.trimIndent()

        val targetedScratchZoneText = """
            2ND CHANCE ENTRY: C9145029381
            GAME 7677 - PACK 00842 - TICKET 022
        """.trimIndent()

        val parsed = LotteryCodeParser.parseWithTargetedRegion(
            targetedRegionText = targetedScratchZoneText,
            fullFrameText = fullFrameText
        )

        assertEquals("C9145029381", parsed.entryCode)
        assertEquals("7677", parsed.gameNumber)
        assertEquals("Winter Winnings", parsed.gameName)
        assertEquals("00842", parsed.packNumber)
        assertEquals("022", parsed.ticketNumber)
        assertTrue(parsed.isConfident)
    }

    @Test
    fun gameTitle_beatsMisreadGameNumber() {
        val rawOcr = """
            TREEMENDOUS CASH
            GAME 7178 - PACK 00311 - TICKET 045
        """.trimIndent()

        val parsed = LotteryCodeParser.parse(rawOcr)

        assertEquals("7678", parsed.gameNumber)
        assertEquals("Treemendous Cash", parsed.gameName)
    }

    @Test
    fun printedGameLabel_beatsTitleWhenPackAndTicketMissing() {
        // The 2025 crossword has the same title as eligible 7665; the printed number must win.
        val rawOcr = """
            ${'$'}1,000,000 CROSSWORD 50X
            GAME 7639
        """.trimIndent()

        val parsed = LotteryCodeParser.parse(rawOcr)

        assertEquals("7639", parsed.gameNumber)
        assertEquals("No open 2nd Chance promotion", parsed.drawingDeadline)
    }

    @Test
    fun findGameInText_prefersLongestTitleAndOpenPromotion() {
        // "$100,000 Crossword" is contained in "$1,000,000 Crossword 50X"; the longer title must win,
        // and the 2026 game (open promotion) beats the 2025 game with the same name.
        val game = IllinoisLotteryGames.findGameInText("ILLINOIS ${'$'}1,000,000 CROSSWORD 50X")
        assertEquals("7665", game?.gameNumber)
        assertEquals("7663", IllinoisLotteryGames.findGameInText("${'$'}100,000 CROSSWORD")?.gameNumber)
        assertNull(IllinoisLotteryGames.findGameInText("ENTRY CODE B7112100000"))
    }

    @Test
    fun gamesWithoutPromotion_areNotEligible() {
        val monopoly = IllinoisLotteryGames.findGameByNumber("7632")
        assertEquals("Monopoly", monopoly?.title)
        assertFalse(monopoly!!.isSecondChanceEligible)
        assertTrue(IllinoisLotteryGames.activeGames.all { it.isSecondChanceEligible })
        assertEquals(9, IllinoisLotteryGames.activeGames.size)
        assertNull(IllinoisLotteryGames.findGameByNumber("852"))
    }
}
