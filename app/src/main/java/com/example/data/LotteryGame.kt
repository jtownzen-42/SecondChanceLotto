package com.example.data

data class LotteryGame(
    val gameNumber: String,
    val title: String,
    val ticketPrice: String,
    val topPrize: String,
    val drawingDeadline: String,
    val drawingDate: String,
    val portalUrl: String = "https://www.illinoislottery.com/second-chance",
    val isActive: Boolean = true
)

object IllinoisLotteryGames {
    val activeGames = listOf(
        LotteryGame(
            gameNumber = "852",
            title = "$100,000 Frenzy",
            ticketPrice = "$10",
            topPrize = "$100,000 Second Chance",
            drawingDeadline = "Nov 15, 2026",
            drawingDate = "Nov 18, 2026"
        ),
        LotteryGame(
            gameNumber = "777",
            title = "Triple 777 Jackpot",
            ticketPrice = "$5",
            topPrize = "$77,777 Second Chance",
            drawingDeadline = "Nov 22, 2026",
            drawingDate = "Nov 25, 2026"
        ),
        LotteryGame(
            gameNumber = "914",
            title = "Holiday Bonus Spectacular",
            ticketPrice = "$20",
            topPrize = "$500,000 Holiday Draw",
            drawingDeadline = "Dec 05, 2026",
            drawingDate = "Dec 08, 2026"
        ),
        LotteryGame(
            gameNumber = "620",
            title = "Diamond Mines Doubler",
            ticketPrice = "$5",
            topPrize = "$50,000 Second Chance",
            drawingDeadline = "Dec 12, 2026",
            drawingDate = "Dec 15, 2026"
        ),
        LotteryGame(
            gameNumber = "505",
            title = "Cash Crossword 10X",
            ticketPrice = "$3",
            topPrize = "$25,000 Word Bonus",
            drawingDeadline = "Dec 19, 2026",
            drawingDate = "Dec 22, 2026"
        ),
        LotteryGame(
            gameNumber = "990",
            title = "Illinois Millionaire Extra",
            ticketPrice = "$30",
            topPrize = "$1,000,000 Grand Finale",
            drawingDeadline = "Dec 31, 2026",
            drawingDate = "Jan 05, 2027"
        )
    )

    fun findGameByNumber(gameNumber: String): LotteryGame? {
        val clean = gameNumber.trim()
        return activeGames.find { it.gameNumber == clean }
    }

    fun findGameByName(name: String): LotteryGame? {
        val lower = name.lowercase().trim()
        return activeGames.find { it.title.lowercase().contains(lower) }
    }
}
