package com.example

import com.example.data.IllinoisLotteryGames
import com.example.scanner.LotteryCodeParser
import com.example.submission.SubmissionAutomationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Game list, entry-code letters, and the portal "Select game" automation. Codes are made up. */
class GameSelectionTest {

    @Test
    fun activeGames_matchPortalSelectGameList() {
        // Order and numbers as shown in the 2nd Chance Zone "Select game" dropdown, 2026-11-13.
        assertEquals(
            listOf("7675", "7676", "7677", "7678", "7679", "7662", "7663", "7664", "7665"),
            IllinoisLotteryGames.activeGames.map { it.gameNumber }
        )
    }

    @Test
    fun everyActiveGame_hasItsOwnEntryCodeLetter() {
        val letters = IllinoisLotteryGames.activeGames.map { it.entryCodeLetter }
        assertTrue(letters.none { it == null })
        assertEquals(letters.size, letters.toSet().size)
    }

    @Test
    fun findGameByEntryCode_usesTheLetter() {
        assertEquals("7676", IllinoisLotteryGames.findGameByEntryCode("F1234567890")?.gameNumber)
        assertEquals("7678", IllinoisLotteryGames.findGameByEntryCode("h1234567890")?.gameNumber)
        assertEquals("7664", IllinoisLotteryGames.findGameByEntryCode("C1234567890")?.gameNumber)
        assertNull(IllinoisLotteryGames.findGameByEntryCode("Z1234567890"))
        assertNull(IllinoisLotteryGames.findGameByEntryCode("1234567890"))
        assertNull(IllinoisLotteryGames.findGameByEntryCode(""))
    }

    @Test
    fun scan_withUnreadableTitle_getsGameFromCodeLetter() {
        // Treemendous Cash's script title doesn't OCR; the H code still identifies the game.
        val parsed = LotteryCodeParser.parse("~~ ~~~\nENTRY CODE\nH1234567890\n003")
        assertEquals("7678", parsed.gameNumber)
        assertEquals("Treemendous Cash", parsed.gameName)
        assertEquals("Jan 10, 2027", parsed.drawingDeadline)
    }

    @Test
    fun scan_readTitleBeatsCodeLetter() {
        val parsed = LotteryCodeParser.parse("REINDEER RICHES\nENTRY CODE\nH1234567890")
        assertEquals("7676", parsed.gameNumber)
    }

    @Test
    fun scan_targetedRegion_alsoFallsBackToLetter() {
        val parsed = LotteryCodeParser.parseWithTargetedRegion("C1234567890", "garbled title\nC1234567890")
        assertEquals("7664", parsed.gameNumber)
        assertEquals("\$250,000 Crossword", parsed.gameName)
    }

    @Test
    fun submissionScript_selectsGameByNumberAndTitle() {
        val js = SubmissionAutomationHelper.generateCodeSubmissionScript("F1234567890", "7676", "Reindeer Riches")
        assertTrue(js.contains("CODE = 'F1234567890'"))
        assertTrue(js.contains("GAME = '7676'"))
        assertTrue(js.contains("TITLE = 'Reindeer Riches'"))
        assertTrue(js.contains("querySelectorAll('select')"))
        assertTrue(js.contains("NO_GAME"))
        // The JS regex end anchor must survive Kotlin string templating.
        assertTrue(js.contains("[0-9]{4}\$/"))
    }

    @Test
    fun submissionScript_escapesQuotesInTitle() {
        val js = SubmissionAutomationHelper.generateCodeSubmissionScript("A1234567890", "7662", "Bob's Game")
        assertTrue(js.contains("TITLE = 'Bob\\'s Game'"))
    }

    @Test
    fun plainHttpPortalLinks_areUpgradedToHttps() {
        assertEquals(
            "https://illinois.secondchancebonuszone.com/",
            IllinoisLotteryGames.upgradeToHttps("http://illinois.secondchancebonuszone.com/")
        )
        assertEquals(
            "https://illinois.secondchancebonuszone.com/2ndchance/enter?x=1",
            IllinoisLotteryGames.upgradeToHttps("HTTP://illinois.secondchancebonuszone.com/2ndchance/enter?x=1")
        )
        assertNull(IllinoisLotteryGames.upgradeToHttps("https://illinois.secondchancebonuszone.com/"))
        assertNull(IllinoisLotteryGames.upgradeToHttps("http://example.com/"))
        assertNull(IllinoisLotteryGames.upgradeToHttps("http://illinois.secondchancebonuszone.com.evil.test/"))
        assertNull(IllinoisLotteryGames.upgradeToHttps(null))
    }
}
