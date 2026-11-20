package com.jtownzen.secondchanceil

import com.jtownzen.secondchanceil.data.GameLearner
import com.jtownzen.secondchanceil.data.IllinoisLotteryGames
import com.jtownzen.secondchanceil.data.LearnedGame
import com.jtownzen.secondchanceil.data.LearnedGamesState
import com.jtownzen.secondchanceil.data.PortalGameList
import com.jtownzen.secondchanceil.data.PortalGameOption
import com.jtownzen.secondchanceil.scanner.LotteryCodeParser
import com.jtownzen.secondchanceil.submission.SubmissionAutomationHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LearnedGamesTest {

    private val builtIn get() = IllinoisLotteryGames.builtInActiveGames

    /** The portal list as it was on 2026-11-13, plus a game that is new to the app. */
    private val portalWithNewGame = PortalGameList(
        builtIn.map { PortalGameOption(it.gameNumber, it.title) } + PortalGameOption("7680", "Snow Globe Riches"),
        authoritative = true
    )

    @Before
    fun reset() = IllinoisLotteryGames.applyLearned(LearnedGamesState())

    @After
    fun resetAfter() = IllinoisLotteryGames.applyLearned(LearnedGamesState())

    private fun sync(list: PortalGameList, from: LearnedGamesState = IllinoisLotteryGames.learnedState.value) =
        GameLearner.mergePortalList(from, builtIn, list, now = 1_000L).also { IllinoisLotteryGames.applyLearned(it.first) }

    // ---- reading the portal's list ----

    @Test
    fun `portal options give the number and a readable title`() {
        assertEquals(PortalGameOption("7676", "Reindeer Riches"), GameLearner.parsePortalOption("7676", "REINDEER RICHES - 7676"))
        assertEquals(
            PortalGameOption("7665", "\$1,000,000 Crossword 50X"),
            GameLearner.parsePortalOption("", "\$1,000,000 CROSSWORD 50X - 7665")
        )
        assertEquals(PortalGameOption("7675", "Holiday Ca\$h"), GameLearner.parsePortalOption("7675", "HOLIDAY CA\$H - 7675"))
        assertNull(GameLearner.parsePortalOption("", "Select game"))
        assertEquals("Already Mixed Case", GameLearner.titleCase("Already Mixed Case"))
    }

    @Test
    fun `game list script output parses, placeholders dropped`() {
        fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        val raw = "\"GL1|1|" + listOf("" to "Select game", "7676" to "REINDEER RICHES - 7676", "7680" to "SNOW GLOBE RICHES - 7680")
            .joinToString(",") { enc(it.first) + ":" + enc(it.second) } + "\""
        val list = SubmissionAutomationHelper.parsePortalGameList(raw)
        assertNotNull(list)
        assertTrue(list!!.authoritative)
        assertEquals(listOf("7676", "7680"), list.options.map { it.gameNumber })
        assertEquals("Snow Globe Riches", list.options[1].title)

        assertNull(SubmissionAutomationHelper.parsePortalGameList("\"GL1|0|\""))
        assertNull(SubmissionAutomationHelper.parsePortalGameList("\"SC1|0|0|0|||\""))
        assertNull(SubmissionAutomationHelper.parsePortalGameList(null))
        assertTrue(SubmissionAutomationHelper.generateReadGameListScript().contains("promotion_game_id"))
    }

    // ---- syncing ----

    @Test
    fun `a new portal game becomes enterable and recognisable`() {
        val (_, report) = sync(portalWithNewGame)
        assertEquals(listOf("7680"), report.added.map { it.gameNumber })
        assertTrue(report.retired.isEmpty())

        val game = IllinoisLotteryGames.findGameByNumber("7680")
        assertNotNull(game)
        assertTrue(game!!.isSecondChanceEligible)
        assertEquals("Snow Globe Riches", game.title)
        assertTrue(IllinoisLotteryGames.activeGames.any { it.gameNumber == "7680" })
        // Built-in games are untouched.
        assertEquals(builtIn.size + 1, IllinoisLotteryGames.activeGames.size)
    }

    @Test
    fun `a synced title is found on a scanned ticket`() {
        sync(portalWithNewGame)
        // Title only: no printed game number, a code letter no game has yet.
        val parsed = LotteryCodeParser.parse("SNOW GLOBE RICHES\n\$10\nENTRY CODE\nJ1234567890")
        assertEquals("7680", parsed.gameNumber)
    }

    @Test
    fun `games the portal stops listing are retired, and come back`() {
        val ended = PortalGameList(
            builtIn.filter { it.promotion == IllinoisLotteryGames.TWELVE_WEEKS_OF_WINNING }
                .map { PortalGameOption(it.gameNumber, it.title) },
            authoritative = true
        )
        val (_, report) = sync(ended)
        assertEquals(listOf("7662", "7663", "7664", "7665"), report.retired)
        assertNull(IllinoisLotteryGames.activeGames.find { it.gameNumber == "7664" })
        // Still recognised, but no longer enterable.
        assertFalse(IllinoisLotteryGames.findGameByNumber("7664")!!.isSecondChanceEligible)

        val (_, back) = sync(portalWithNewGame)
        assertEquals(listOf("7662", "7663", "7664", "7665"), back.returned)
        assertTrue(IllinoisLotteryGames.findGameByNumber("7664")!!.isSecondChanceEligible)
    }

    @Test
    fun `a list not read from the real form never retires anything`() {
        val (_, report) = sync(PortalGameList(listOf(PortalGameOption("7680", "Snow Globe Riches")), authoritative = false))
        assertTrue(report.retired.isEmpty())
        assertEquals(builtIn.size + 1, IllinoisLotteryGames.activeGames.size)
    }

    @Test
    fun `an empty list changes nothing`() {
        val before = IllinoisLotteryGames.learnedState.value
        val (after, report) = sync(PortalGameList(emptyList(), authoritative = true))
        assertEquals(before, after)
        assertFalse(report.changed)
        assertEquals(builtIn.size, IllinoisLotteryGames.activeGames.size)
    }

    // ---- learning from tickets ----

    @Test
    fun `saving a ticket confirms an inferred letter`() {
        // 7679 Snowfall Mega Multiplier has "I" from the pattern, not yet seen on a ticket.
        val next = GameLearner.learnLetterFromTicket(
            IllinoisLotteryGames.learnedState.value, "7679", "I1234567890", IllinoisLotteryGames.activeGames
        )
        assertNotNull(next)
        IllinoisLotteryGames.applyLearned(next!!)
        assertTrue(IllinoisLotteryGames.findGameByNumber("7679")!!.entryCodeLetterConfirmed)
        // Not listed as a separate game.
        assertEquals(builtIn.size, IllinoisLotteryGames.activeGames.size)
    }

    @Test
    fun `a confirmed letter is never overwritten or stolen`() {
        val active = IllinoisLotteryGames.activeGames
        // 7676 is confirmed "F": a ticket saying otherwise is a misread, not news.
        assertNull(GameLearner.learnLetterFromTicket(LearnedGamesState(), "7676", "Z1234567890", active))
        // "F" belongs to 7676 (confirmed); 7679 can't take it.
        assertNull(GameLearner.learnLetterFromTicket(LearnedGamesState(), "7679", "F1234567890", active))
        // Not a real-format code.
        assertNull(GameLearner.learnLetterFromTicket(LearnedGamesState(), "7679", "I12345", active))
    }

    @Test
    fun `after one ticket, a new game is found from its code alone`() {
        sync(portalWithNewGame)
        val next = GameLearner.learnLetterFromTicket(
            IllinoisLotteryGames.learnedState.value, "7680", "J1234567890", IllinoisLotteryGames.activeGames
        )
        IllinoisLotteryGames.applyLearned(next!!)
        assertEquals("7680", IllinoisLotteryGames.findGameByEntryCode("J9999999999")?.gameNumber)
        assertEquals("7680", LotteryCodeParser.parse("J9999999999").gameNumber)
    }

    @Test
    fun `the learn button adds an unknown game from the ticket`() {
        val next = GameLearner.learnGame(LearnedGamesState(), "7681", "", "K1234567890", builtIn, now = 5L)
        IllinoisLotteryGames.applyLearned(next)
        val g = IllinoisLotteryGames.findGameByNumber("7681")!!
        assertTrue(g.isSecondChanceEligible)
        assertEquals("Game #7681", g.title)
        assertEquals("K", g.entryCodeLetter)

        // The portal's official name replaces the placeholder at the next sync.
        sync(PortalGameList(listOf(PortalGameOption("7681", "Frosty Fortune")), authoritative = false))
        assertEquals("Frosty Fortune", IllinoisLotteryGames.findGameByNumber("7681")!!.title)
    }

    @Test
    fun `a name the user typed survives sync`() {
        val typed = GameLearner.learnGame(LearnedGamesState(), "7681", "Frosty", null, builtIn, now = 5L)
        IllinoisLotteryGames.applyLearned(typed)
        sync(PortalGameList(listOf(PortalGameOption("7681", "Frosty Fortune")), authoritative = false))
        assertEquals("Frosty", IllinoisLotteryGames.findGameByNumber("7681")!!.title)
    }

    @Test
    fun `learning a known but non-promotion game makes it enterable`() {
        // 7656 is in the on-sale list with no promotion; a new promotion may have picked it up.
        assertFalse(IllinoisLotteryGames.findGameByNumber("7656")!!.isSecondChanceEligible)
        IllinoisLotteryGames.applyLearned(GameLearner.learnGame(LearnedGamesState(), "7656", "", null, builtIn, 0L))
        assertTrue(IllinoisLotteryGames.findGameByNumber("7656")!!.isSecondChanceEligible)
    }

    @Test
    fun `removing a mistaken game takes it back out`() {
        IllinoisLotteryGames.applyLearned(GameLearner.learnGame(LearnedGamesState(), "7999", "Oops", null, builtIn, 0L))
        assertNotNull(IllinoisLotteryGames.activeGames.find { it.gameNumber == "7999" })
        IllinoisLotteryGames.applyLearned(GameLearner.remove(IllinoisLotteryGames.learnedState.value, "7999"))
        assertNull(IllinoisLotteryGames.findGameByNumber("7999"))
    }

    // ---- storage ----

    @Test
    fun `state round-trips through storage`() {
        val state = LearnedGamesState(
            games = listOf(
                LearnedGame("7680", "Snow\tGlobe\nRiches", "J", true, true, LearnedGame.Source.PORTAL, 42L),
                LearnedGame("7681", "Game #7681", null, false, false, LearnedGame.Source.TICKET, 43L)
            ),
            retiredBuiltIns = setOf("7662", "7663"),
            lastSyncAt = 99L
        )
        val back = GameLearner.decode(GameLearner.encode(state))
        assertEquals("Snow Globe Riches", back.games[0].title)
        assertEquals(state.games[1], back.games[1])
        assertEquals(state.retiredBuiltIns, back.retiredBuiltIns)
        assertEquals(99L, back.lastSyncAt)
        assertEquals(LearnedGamesState(), GameLearner.decode("garbage"))
        assertEquals(LearnedGamesState(), GameLearner.decode(""))
    }
}
