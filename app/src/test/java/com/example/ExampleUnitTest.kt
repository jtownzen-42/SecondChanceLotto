package com.example

import com.example.scanner.LotteryCodeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testParseLotteryCode_standardIllinoisFormat() {
        val rawOcr = """
            ILLINOIS LOTTERY
            ${'$'}100,000 FRENZY
            GAME 852 - PACK 04821 - TICKET 014
            SCRATCH FOR SECOND CHANCE
            ENTRY CODE: 84920158291048
            KEEP TICKET TO CLAIM PRIZE
        """.trimIndent()

        val parsed = LotteryCodeParser.parse(rawOcr)

        assertEquals("852", parsed.gameNumber)
        assertEquals("$100,000 Frenzy", parsed.gameName)
        assertEquals("04821", parsed.packNumber)
        assertEquals("014", parsed.ticketNumber)
        assertEquals("84920158291048", parsed.entryCode)
        assertTrue(parsed.isConfident)
    }

    @Test
    fun testParseLotteryCode_hyphenatedCode() {
        val rawOcr = """
            TRIPLE 777 JACKPOT
            ENTRY CODE: 7730-19482-71603
        """.trimIndent()

        val parsed = LotteryCodeParser.parse(rawOcr)

        assertEquals("77301948271603", parsed.entryCode)
        assertEquals("777", parsed.gameNumber)
        assertTrue(parsed.isConfident)
    }

    @Test
    fun testParseLotteryCode_targetedRegionPrioritizesScratchZone() {
        val fullFrameText = """
            ILLINOIS LOTTERY
            HOLIDAY BONUS SPECTACULAR
            SERIAL: 111111111111
            2ND CHANCE ENTRY: 91450293817264
            GAME 914 - PACK 00842 - TICKET 022
        """.trimIndent()

        val targetedScratchZoneText = """
            2ND CHANCE ENTRY: 91450293817264
            GAME 914 - PACK 00842 - TICKET 022
        """.trimIndent()

        val parsed = LotteryCodeParser.parseWithTargetedRegion(
            targetedRegionText = targetedScratchZoneText,
            fullFrameText = fullFrameText
        )

        assertEquals("91450293817264", parsed.entryCode)
        assertEquals("914", parsed.gameNumber)
        assertEquals("Holiday Bonus Spectacular", parsed.gameName)
        assertEquals("00842", parsed.packNumber)
        assertEquals("022", parsed.ticketNumber)
        assertTrue(parsed.isConfident)
    }
}
