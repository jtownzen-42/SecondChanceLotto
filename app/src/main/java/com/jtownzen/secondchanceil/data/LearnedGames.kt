package com.jtownzen.secondchanceil.data

/**
 * A game the app learned after it was built: from the 2nd Chance Zone's own "Select game" list,
 * or from a ticket the user scanned. Also carries entry-code letters confirmed on real tickets
 * for built-in games (an "overlay": same game number as a built-in game).
 */
data class LearnedGame(
    /** Four-digit Illinois game number, e.g. "7680". */
    val gameNumber: String,
    val title: String,
    /** The letter every entry code on this game starts with, when known. */
    val entryCodeLetter: String? = null,
    /** True when [entryCodeLetter] was read off a ticket the user saved. */
    val letterFromTicket: Boolean = false,
    /** Listed in the portal's Select game list at the last sync. Unlisted games can't be entered. */
    val onPortal: Boolean = true,
    val source: Source = Source.PORTAL,
    val learnedAt: Long = 0L
) {
    enum class Source { PORTAL, TICKET }
}

/** One entry of the portal's Select game list ("REINDEER RICHES - 7676"). */
data class PortalGameOption(val gameNumber: String, val title: String)

/**
 * The game list read off the Enter Your Tickets page. [authoritative] is true when it came from
 * the form's own `select#promotion_game_id`; only then may missing games be retired.
 */
data class PortalGameList(val options: List<PortalGameOption>, val authoritative: Boolean)

data class LearnedGamesState(
    val games: List<LearnedGame> = emptyList(),
    /** Built-in games that the portal no longer lists (their promotion ended). */
    val retiredBuiltIns: Set<String> = emptySet(),
    val lastSyncAt: Long = 0L
)

data class GameSyncReport(
    val listedCount: Int = 0,
    val added: List<LearnedGame> = emptyList(),
    val renamed: List<LearnedGame> = emptyList(),
    /** Game numbers the portal stopped listing in this sync. */
    val retired: List<String> = emptyList(),
    /** Game numbers that were retired and are listed again. */
    val returned: List<String> = emptyList()
) {
    val changed: Boolean get() = added.isNotEmpty() || renamed.isNotEmpty() || retired.isNotEmpty() || returned.isNotEmpty()

    companion object {
        val NONE = GameSyncReport()
    }
}

/** Pure rules for learning games. No Android types, so it is unit-tested on the JVM. */
object GameLearner {

    private val GAME_NUMBER = Regex("""^\d{4}$""")
    private val TRAILING_NUMBER = Regex("""-\s*(\d{4})\s*$""")

    fun isGameNumber(s: String): Boolean = GAME_NUMBER.matches(s.trim())

    fun placeholderTitle(gameNumber: String): String = "Game #$gameNumber"

    private fun isPlaceholder(g: LearnedGame): Boolean =
        g.title.isBlank() || g.title == placeholderTitle(g.gameNumber)

    /**
     * Reads one `<option>`: the number from its value ("7676") or the end of its text
     * ("REINDEER RICHES - 7676"); the title is the text before " - 7676". Returns null for
     * placeholders such as "Select game".
     */
    fun parsePortalOption(value: String, text: String): PortalGameOption? {
        val number = value.trim().takeIf { isGameNumber(it) }
            ?: TRAILING_NUMBER.find(text)?.groupValues?.get(1)
            ?: return null
        val rawTitle = text.replace(TRAILING_NUMBER, "").trim().trimEnd('-').trim()
        return PortalGameOption(number, titleCase(rawTitle).ifBlank { placeholderTitle(number) })
    }

    /**
     * "REINDEER RICHES" -> "Reindeer Riches", "$1,000,000 CROSSWORD 50X" -> "$1,000,000 Crossword 50X".
     * Text that already has lower-case letters is left alone; words with digits keep their case.
     */
    fun titleCase(s: String): String {
        if (s.any { it.isLowerCase() }) return s
        return s.split(' ').joinToString(" ") { word ->
            if (word.any(Char::isDigit) || word.none(Char::isLetter)) word
            else {
                val i = word.indexOfFirst(Char::isLetter)
                word.substring(0, i + 1) + word.substring(i + 1).lowercase()
            }
        }
    }

    /**
     * Merges a freshly read portal list. New numbers are learned with the portal's title; learned
     * games keep up with the portal's title unless the user named them; games that disappear are
     * retired (only when [PortalGameList.authoritative]) and come back if listed again. An empty
     * list changes nothing, so a half-loaded page can never wipe the game list.
     */
    fun mergePortalList(
        state: LearnedGamesState,
        builtInActive: List<LotteryGame>,
        list: PortalGameList,
        now: Long
    ): Pair<LearnedGamesState, GameSyncReport> {
        val options = list.options.distinctBy { it.gameNumber }
        if (options.isEmpty()) return state to GameSyncReport.NONE

        val listed = options.map { it.gameNumber }.toSet()
        val builtInNumbers = builtInActive.map { it.gameNumber }.toSet()
        val byNumber = state.games.associateBy { it.gameNumber }.toMutableMap()
        val added = mutableListOf<LearnedGame>()
        val renamed = mutableListOf<LearnedGame>()
        val returned = mutableListOf<String>()
        val retired = mutableListOf<String>()
        var retiredBuiltIns = state.retiredBuiltIns

        for (opt in options) {
            if (opt.gameNumber in builtInNumbers) {
                if (opt.gameNumber in retiredBuiltIns) {
                    retiredBuiltIns = retiredBuiltIns - opt.gameNumber
                    returned += opt.gameNumber
                }
                continue
            }
            val existing = byNumber[opt.gameNumber]
            if (existing == null) {
                val g = LearnedGame(opt.gameNumber, opt.title, source = LearnedGame.Source.PORTAL, learnedAt = now)
                byNumber[opt.gameNumber] = g
                added += g
                continue
            }
            var updated = existing
            if (!existing.onPortal) {
                updated = updated.copy(onPortal = true)
                returned += opt.gameNumber
            }
            // The portal's name is the official one; a name the user typed for a ticket-learned
            // game is kept unless it was only a placeholder.
            val takePortalTitle = existing.source == LearnedGame.Source.PORTAL || isPlaceholder(existing)
            if (takePortalTitle && existing.title != opt.title) {
                updated = updated.copy(title = opt.title)
                renamed += updated
            }
            byNumber[opt.gameNumber] = updated
        }

        if (list.authoritative) {
            for (n in builtInNumbers) {
                if (n !in listed && n !in retiredBuiltIns) {
                    retiredBuiltIns = retiredBuiltIns + n
                    retired += n
                }
            }
            for ((n, g) in byNumber.toMap()) {
                if (n in builtInNumbers) continue
                if (n !in listed && g.onPortal) {
                    byNumber[n] = g.copy(onPortal = false)
                    retired += n
                }
            }
        }

        val newState = LearnedGamesState(
            games = byNumber.values.sortedBy { it.gameNumber },
            retiredBuiltIns = retiredBuiltIns,
            lastSyncAt = now
        )
        return newState to GameSyncReport(options.size, added, renamed, retired.sorted(), returned.sorted())
    }

    /**
     * The "Learn this game" button: adds (or brings back) [gameNumber] as an enterable game.
     * [entryCode]'s first letter is learned with it when the code has the real format.
     */
    fun learnGame(
        state: LearnedGamesState,
        gameNumber: String,
        title: String,
        entryCode: String?,
        builtInActive: List<LotteryGame>,
        now: Long,
        /** Games enterable now; a letter another of them has confirmed is never taken. */
        activeGames: List<LotteryGame> = builtInActive
    ): LearnedGamesState {
        val number = gameNumber.trim()
        require(isGameNumber(number)) { "Game number must be 4 digits" }
        // A letter confirmed for a different game means the number or the code is misread:
        // learn the game, but not the letter, so code-only lookups keep working for both.
        val letter = entryCode?.let(::codeLetter)?.takeUnless { l ->
            activeGames.any { it.gameNumber != number && it.entryCodeLetterConfirmed && it.entryCodeLetter == l }
        }
        if (number in builtInActive.map { it.gameNumber }) {
            // A built-in game the portal had retired: the user says it is enterable again.
            val s = state.copy(retiredBuiltIns = state.retiredBuiltIns - number)
            return if (letter != null) learnLetter(s, number, letter, builtInActive.first { it.gameNumber == number }.title) else s
        }
        val cleanTitle = title.replace(Regex("""\s+"""), " ").trim().ifBlank { placeholderTitle(number) }
        val existing = state.games.firstOrNull { it.gameNumber == number }
        val g = (existing ?: LearnedGame(number, cleanTitle, source = LearnedGame.Source.TICKET, learnedAt = now)).let {
            it.copy(
                title = if (existing == null || isPlaceholder(existing)) cleanTitle else it.title,
                onPortal = true,
                entryCodeLetter = letter ?: it.entryCodeLetter,
                letterFromTicket = letter != null || it.letterFromTicket
            )
        }
        return state.copy(games = (state.games.filterNot { it.gameNumber == number } + g).sortedBy { it.gameNumber })
    }

    /**
     * After a ticket is saved: learns its game's entry-code letter when the app didn't have one
     * confirmed. Never overwrites a letter already confirmed on a real ticket, and never takes a
     * letter another active game has confirmed (that would point to a misread). Returns null when
     * nothing changes.
     */
    fun learnLetterFromTicket(
        state: LearnedGamesState,
        gameNumber: String,
        entryCode: String,
        activeGames: List<LotteryGame>
    ): LearnedGamesState? {
        val letter = codeLetter(entryCode) ?: return null
        val game = activeGames.firstOrNull { it.gameNumber == gameNumber.trim() } ?: return null
        if (game.entryCodeLetterConfirmed) return null
        val takenElsewhere = activeGames.any {
            it.gameNumber != game.gameNumber && it.entryCodeLetterConfirmed && it.entryCodeLetter == letter
        }
        if (takenElsewhere) return null
        return learnLetter(state, game.gameNumber, letter, game.title)
    }

    private fun learnLetter(state: LearnedGamesState, gameNumber: String, letter: String, title: String): LearnedGamesState {
        val existing = state.games.firstOrNull { it.gameNumber == gameNumber }
        val g = existing?.copy(entryCodeLetter = letter, letterFromTicket = true)
            ?: LearnedGame(gameNumber, title, letter, letterFromTicket = true, source = LearnedGame.Source.TICKET)
        return state.copy(games = (state.games.filterNot { it.gameNumber == gameNumber } + g).sortedBy { it.gameNumber })
    }

    fun remove(state: LearnedGamesState, gameNumber: String): LearnedGamesState =
        state.copy(games = state.games.filterNot { it.gameNumber == gameNumber })

    /** The letter of a real-format entry code (1 letter + 10 digits), or null. */
    fun codeLetter(entryCode: String): String? {
        val c = entryCode.trim().uppercase()
        return if (Regex("""^[A-Z]\d{10}$""").matches(c)) c.substring(0, 1) else null
    }

    // ---- storage format: plain text, one record per line, tab-separated ----

    private const val VERSION = "LG1"

    fun encode(state: LearnedGamesState): String = buildString {
        appendLine(VERSION)
        appendLine("S\t${state.lastSyncAt}")
        appendLine("R\t${state.retiredBuiltIns.sorted().joinToString(",")}")
        for (g in state.games) {
            appendLine(
                listOf(
                    "G", g.gameNumber, clean(g.title), g.entryCodeLetter.orEmpty(),
                    if (g.letterFromTicket) "1" else "0", if (g.onPortal) "1" else "0", g.source.name, g.learnedAt.toString()
                ).joinToString("\t")
            )
        }
    }

    fun decode(text: String): LearnedGamesState {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != VERSION) return LearnedGamesState()
        var lastSync = 0L
        var retired = emptySet<String>()
        val games = mutableListOf<LearnedGame>()
        for (line in lines.drop(1)) {
            val f = line.split('\t')
            when (f.firstOrNull()) {
                "S" -> lastSync = f.getOrNull(1)?.toLongOrNull() ?: 0L
                "R" -> retired = f.getOrNull(1).orEmpty().split(',').filter(::isGameNumber).toSet()
                "G" -> {
                    if (f.size < 8 || !isGameNumber(f[1])) continue
                    games += LearnedGame(
                        gameNumber = f[1],
                        title = f[2].ifBlank { placeholderTitle(f[1]) },
                        entryCodeLetter = f[3].takeIf { it.length == 1 && it[0].isLetter() },
                        letterFromTicket = f[4] == "1",
                        onPortal = f[5] == "1",
                        source = runCatching { LearnedGame.Source.valueOf(f[6]) }.getOrDefault(LearnedGame.Source.PORTAL),
                        learnedAt = f[7].toLongOrNull() ?: 0L
                    )
                }
            }
        }
        return LearnedGamesState(games.distinctBy { it.gameNumber }, retired, lastSync)
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}
