package com.example.data

/**
 * An Illinois Lottery 2nd Chance promotion run on the 2nd Chance Zone.
 *
 * Source: illinois.secondchancebonuszone.com promotion pages, checked 2026-11-11.
 * Weekly drawings close each Sunday at 11:59:59 PM Central and are drawn the following Tuesday.
 */
data class SecondChancePromotion(
    val name: String,
    val portalUrl: String,
    /** Last day any eligible ticket can be entered. */
    val finalEntryDeadline: String,
    /** Date of the last drawing. */
    val finalDrawingDate: String,
    val prizeSummary: String
)

data class LotteryGame(
    /** Four-digit Illinois game number printed on the ticket, e.g. "7676". */
    val gameNumber: String,
    val title: String,
    val ticketPrice: String,
    val topPrize: String,
    /** The open 2nd Chance promotion this game can be entered into, or null if none. */
    val promotion: SecondChancePromotion? = null,
    val isActive: Boolean = true,
    /**
     * The letter that starts every 2nd Chance entry code on this game's tickets (e.g. "F" in
     * F1234567890), or null when unknown. See [IllinoisLotteryGames.activeGames] for the source.
     */
    val entryCodeLetter: String? = null,
    /** True when the letter was read off a real ticket; false when inferred from the pattern. */
    val entryCodeLetterConfirmed: Boolean = false
) {
    val isSecondChanceEligible: Boolean get() = promotion != null

    /** Final entry deadline for this game's promotion, or a prompt to check when there is none. */
    val drawingDeadline: String
        get() = promotion?.finalEntryDeadline ?: "No open 2nd Chance promotion"

    val drawingDate: String
        get() = promotion?.finalDrawingDate ?: ""

    val portalUrl: String
        get() = promotion?.portalUrl ?: IllinoisLotteryGames.SECOND_CHANCE_ZONE_URL
}

object IllinoisLotteryGames {

    const val SECOND_CHANCE_ZONE_URL = "https://illinois.secondchancebonuszone.com/2ndchance"
    const val SECOND_CHANCE_LOGIN_URL = "https://illinois.secondchancebonuszone.com/2ndchance/login"
    /** "Enter Your Tickets" page: a "Select game" dropdown ("NAME - 7676") and an entry code box. Needs login. */
    const val SECOND_CHANCE_ENTER_URL = "https://illinois.secondchancebonuszone.com/2ndchance/enter"

    /** Max eligible tickets a player can enter per day, across promotions (2nd Chance Zone FAQ). */
    const val MAX_ENTRIES_PER_DAY = 25

    val TWELVE_WEEKS_OF_WINNING = SecondChancePromotion(
        name = "12 Weeks of Winning",
        portalUrl = "$SECOND_CHANCE_ZONE_URL/promotion/twelveweeksofwinning",
        finalEntryDeadline = "Jan 10, 2027",
        finalDrawingDate = "Jan 12, 2027",
        prizeSummary = "100 × \$500 every week"
    )

    val CROSSWORD_MILLIONAIRE_BONUS = SecondChancePromotion(
        name = "Crossword Millionaire Bonus",
        portalUrl = "$SECOND_CHANCE_ZONE_URL/promotion/crosswordmillionairebonus",
        finalEntryDeadline = "Jun 20, 2027",
        finalDrawingDate = "Jun 22, 2027",
        prizeSummary = "20 × \$500 weekly, \$1,000,000 final"
    )

    /**
     * Games that can currently be entered into a 2nd Chance promotion, in the same order as the
     * "Select game" list on the 2nd Chance Zone's Enter Tickets page (checked 2026-11-13).
     * These are shown on the Dashboard, offered in the game pickers, and auto-selected on the portal.
     *
     * Entry-code letters: every entry code is 1 letter + 10 digits, and each game in a promotion
     * gets its own letter in game-number order (Crossword family A-D, holiday family E-I).
     * "confirmed" = seen on a real ticket (Jason's photos, 2026-11-09); the rest follow the pattern
     * and should be confirmed when a ticket of that game is scanned. Keep docs/second-chance-games.md
     * in step with this list.
     */
    val activeGames: List<LotteryGame> = listOf(
        // 12 Weeks of Winning (2026 holiday tickets)
        LotteryGame("7675", "Holiday Ca\$h", "\$1", "\$500", TWELVE_WEEKS_OF_WINNING,
            entryCodeLetter = "E"),
        LotteryGame("7676", "Reindeer Riches", "\$2", "\$50,000", TWELVE_WEEKS_OF_WINNING,
            entryCodeLetter = "F", entryCodeLetterConfirmed = true),
        LotteryGame("7677", "Winter Winnings", "\$5", "\$250,000", TWELVE_WEEKS_OF_WINNING,
            entryCodeLetter = "G"),
        LotteryGame("7678", "Treemendous Cash", "\$10", "\$500,000", TWELVE_WEEKS_OF_WINNING,
            entryCodeLetter = "H", entryCodeLetterConfirmed = true),
        LotteryGame("7679", "Snowfall Mega Multiplier", "\$20", "\$1,000,000", TWELVE_WEEKS_OF_WINNING,
            entryCodeLetter = "I"),
        // Crossword Millionaire Bonus (2026 crossword family)
        LotteryGame("7662", "Bonus Word Crossword", "\$3", "\$50,000", CROSSWORD_MILLIONAIRE_BONUS,
            entryCodeLetter = "A"),
        LotteryGame("7663", "\$100,000 Crossword", "\$5", "\$100,000", CROSSWORD_MILLIONAIRE_BONUS,
            entryCodeLetter = "B", entryCodeLetterConfirmed = true),
        LotteryGame("7664", "\$250,000 Crossword", "\$10", "\$250,000", CROSSWORD_MILLIONAIRE_BONUS,
            entryCodeLetter = "C", entryCodeLetterConfirmed = true),
        LotteryGame("7665", "\$1,000,000 Crossword 50X", "\$25", "\$1,000,000", CROSSWORD_MILLIONAIRE_BONUS,
            entryCodeLetter = "D", entryCodeLetterConfirmed = true)
    )

    /**
     * Other Illinois instant games on sale (no open 2nd Chance promotion). Used only to recognise
     * a scanned ticket so the app can say it isn't eligible, instead of guessing a wrong game.
     * Source: published Illinois Lottery game data via lotteryvalley.com, updated 2026-10-14.
     */
    val otherGames: List<LotteryGame> = listOf(
        LotteryGame("7563", "\$2,000,000 Celebration", "\$20", "\$2,000,000"),
        LotteryGame("7575", "\$10,000,000 Bankroll", "\$50", "\$10,000,000"),
        LotteryGame("7590", "200X the Cash", "\$30", "\$3,000,000"),
        LotteryGame("7598", "In the Money", "\$5", "\$400,000"),
        LotteryGame("7601", "More Money", "\$5", "\$400,000"),
        LotteryGame("7611", "Millionaire Club", "\$50", "\$1,000,000"),
        LotteryGame("7616", "\$1,000,000 Ca\$h Cha\$er", "\$10", "\$1,000,000"),
        LotteryGame("7621", "\$2,000,000 Diamond Deluxe", "\$20", "\$2,000,000"),
        LotteryGame("7622", "Golden Casino", "\$10", "\$1,000,000"),
        LotteryGame("7623", "Royal Riches", "\$30", "\$3,000,000"),
        LotteryGame("7624", "\$2,000,000 Maximum Multiplier", "\$20", "\$2,000,000"),
        LotteryGame("7631", "Amazing Bonus Blowout", "\$10", "\$500"),
        LotteryGame("7632", "Monopoly", "\$2", "\$20,000"),
        LotteryGame("7634", "5X Xtra", "\$1", "\$20,000"),
        LotteryGame("7635", "10X Xtra", "\$2", "\$50,000"),
        LotteryGame("7636", "25X Xtra", "\$5", "\$500,000"),
        LotteryGame("7637", "50X Xtra", "\$10", "\$1,000,000"),
        LotteryGame("7638", "100X Xtra", "\$20", "\$1,000,000"),
        LotteryGame("7639", "\$1,000,000 Crossword 50X (2025)", "\$25", "\$1,000,000"),
        LotteryGame("7642", "Loose Change Boost", "\$1", "\$1,000"),
        LotteryGame("7643", "7-11-21", "\$1", "\$3,000"),
        LotteryGame("7645", "Electric Cash", "\$5", "\$400,000"),
        LotteryGame("7646", "Gold Rush Supreme", "\$10", "\$100,000"),
        LotteryGame("7647", "Emeralds", "\$1", "\$50,000"),
        LotteryGame("7648", "Rubies", "\$2", "\$100,000"),
        LotteryGame("7649", "Onyx", "\$5", "\$1,000,000"),
        LotteryGame("7650", "Diamonds", "\$10", "\$2,000,000"),
        LotteryGame("7651", "Loteria", "\$3", "\$80,000"),
        LotteryGame("7652", "Money Rush", "\$5", "\$400,000"),
        LotteryGame("7653", "Blowout X", "\$30", "\$3,000"),
        LotteryGame("7654", "Sapphire 10s", "\$10", "\$1,000,000"),
        LotteryGame("7655", "Add It Up", "\$2", "\$20,000"),
        LotteryGame("7656", "Power Up! Multiplier", "\$5", "\$400,000"),
        LotteryGame("7657", "Double the Luck", "\$20", "\$3,000,000"),
        LotteryGame("7658", "7X Bingo Multiplier", "\$5", "\$200,000"),
        LotteryGame("7659", "Money Match", "\$10", "\$600,000"),
        LotteryGame("7660", "Millionaire 7", "\$10", "\$1,000,000"),
        LotteryGame("7661", "\$3 Million Vault", "\$20", "\$3,000,000"),
        LotteryGame("7668", "Big Time Blowout", "\$20", "\$2,000"),
        LotteryGame("7669", "Galaxy Blast", "\$5", "\$555,555"),
        LotteryGame("7670", "\$5,000,000 Jackpot", "\$30", "\$5,000,000"),
        LotteryGame("7671", "In the Green", "\$5", "\$500,000"),
        LotteryGame("7672", "Liquid Assets", "\$10", "\$1,000,000"),
        LotteryGame("7673", "\$2,000,000 Multiplier Bonanza", "\$20", "\$2,000,000")
    )

    val allGames: List<LotteryGame> = activeGames + otherGames

    fun findGameByNumber(gameNumber: String): LotteryGame? {
        val clean = gameNumber.trim()
        return allGames.find { it.gameNumber == clean }
    }

    /**
     * The open-promotion game whose entry codes start with this code's letter, or null when the
     * code doesn't start with a letter or no single game uses it. Used when the ticket's title and
     * game number can't be read (script titles such as Treemendous Cash defeat OCR).
     */
    fun findGameByEntryCode(entryCode: String): LotteryGame? {
        val letter = entryCode.trim().firstOrNull()?.takeIf { it.isLetter() }?.uppercase() ?: return null
        return activeGames.filter { it.entryCodeLetter == letter }.singleOrNull()
    }

    fun findGameByName(name: String): LotteryGame? {
        val wanted = normalizeTitle(name)
        if (wanted.isEmpty()) return null
        return allGames.find { normalizeTitle(it.title) == wanted }
            ?: allGames.find { normalizeTitle(it.title).contains(wanted) }
    }

    /**
     * Finds the game whose full title appears in OCR text from a ticket. Matching ignores case,
     * punctuation, "$" and spacing, and prefers the longest title so "$1,000,000 Crossword 50X"
     * beats "Crossword" style partial hits. Eligible (active) games win ties.
     */
    fun findGameInText(text: String): LotteryGame? {
        val haystack = normalizeTitle(text)
        if (haystack.isEmpty()) return null
        return allGames
            // Titles must contain a letter: an all-digit title like "7-11-21" would match inside codes.
            .filter { normalizeTitle(it.title).let { t -> t.length >= 4 && t.any(Char::isLetter) && haystack.contains(t) } }
            .maxWithOrNull(
                compareBy<LotteryGame> { normalizeTitle(it.title).length }
                    .thenBy { it.isSecondChanceEligible }
            )
    }

    /** Lower-cases and keeps only letters and digits ("$1,000,000 Crossword 50X" -> "1000000crossword50x"). */
    internal fun normalizeTitle(s: String): String =
        s.replace("(2025)", "").lowercase().filter { it.isLetterOrDigit() }
}
