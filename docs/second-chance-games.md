# Illinois 2nd Chance games

The games in the 2nd Chance Zone's **Enter Your Tickets → Select game** list, with the letter every
entry code for that game starts with. The app's copy of this list is `activeGames` in
`app/src/main/java/com/example/data/LotteryGame.kt`; update both together.

Last checked against the live "Select game" list: **2026-11-13**.

| Game name | Game # | Code letter | Letter status | Promotion | Final entry |
|---|---|---|---|---|---|
| Holiday Ca$h | 7675 | E | inferred | 12 Weeks of Winning | Jan 10, 2027 |
| Reindeer Riches | 7676 | F | confirmed | 12 Weeks of Winning | Jan 10, 2027 |
| Winter Winnings | 7677 | G | inferred | 12 Weeks of Winning | Jan 10, 2027 |
| Treemendous Cash | 7678 | H | confirmed | 12 Weeks of Winning | Jan 10, 2027 |
| Snowfall Mega Multiplier | 7679 | I | inferred | 12 Weeks of Winning | Jan 10, 2027 |
| Bonus Word Crossword | 7662 | A | confirmed | Crossword Millionaire Bonus | Jun 20, 2027 |
| $100,000 Crossword | 7663 | B | confirmed | Crossword Millionaire Bonus | Jun 20, 2027 |
| $250,000 Crossword | 7664 | C | confirmed | Crossword Millionaire Bonus | Jun 20, 2027 |
| $1,000,000 Crossword 50X | 7665 | D | confirmed | Crossword Millionaire Bonus | Jun 20, 2027 |

**Confirmed** letters were read off real tickets. **Inferred** letters follow the pattern the
confirmed ones show (each promotion's games get consecutive letters in game-number order); change
the status to confirmed, or fix the letter, the first time a ticket of that game is scanned.

## How the app uses it

- **Scanning:** the game is taken from the ticket's title or printed game number. If neither can
  be read (script titles like Treemendous Cash don't OCR well), the entry code's first letter picks
  the game.
- **Submitting:** Auto-Fill opens Enter Your Tickets if needed, picks the option ending in the
  ticket's game number (e.g. `REINDEER RICHES - 7676`), types the entry code and presses Submit Entry.
  If the game isn't in the list it fills the code but doesn't submit.

## When the list changes

When a promotion ends or a new one starts, compare the portal's Select game list with this table,
then update `activeGames` (and the promotion dates) and this file.
