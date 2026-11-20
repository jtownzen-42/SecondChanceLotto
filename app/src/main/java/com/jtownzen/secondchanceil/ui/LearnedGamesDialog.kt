package com.jtownzen.secondchanceil.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jtownzen.secondchanceil.data.GameSyncReport
import com.jtownzen.secondchanceil.data.IllinoisLotteryGames
import com.jtownzen.secondchanceil.data.LearnedGame
import com.jtownzen.secondchanceil.data.LearnedGamesState

/**
 * What an Update games sync found, and the games the app has learned since it was built, with a
 * way to remove one that was learned by mistake (for example from a misread game number).
 */
@Composable
fun LearnedGamesDialog(
    report: GameSyncReport,
    state: LearnedGamesState,
    onRemove: (gameNumber: String) -> Unit,
    onDismiss: () -> Unit
) {
    val builtIn = IllinoisLotteryGames.builtInActiveGames.map { it.gameNumber }.toSet()
    // Letter-only overlays on built-in games aren't separate games; they're listed by letter below.
    val learnedGames = state.games.filter { it.gameNumber !in builtIn }
    val confirmedLetters = state.games.filter { it.gameNumber in builtIn && it.letterFromTicket }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("learned_games_dialog"),
        title = { Text("Games") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(summary(report), style = MaterialTheme.typography.bodyMedium)
                if (report.added.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("New: " + report.added.joinToString(", ") { "${it.title} (#${it.gameNumber})" })
                }
                if (report.retired.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No longer listed (promotion ended): " + report.retired.joinToString(", ") { "#$it" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("Learned since this version was built", fontWeight = FontWeight.Bold)
                if (learnedGames.isEmpty()) {
                    Text(
                        "None yet. New games are added automatically when they appear on Enter Your Tickets.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                learnedGames.forEach { g -> LearnedGameRow(g, onRemove) }

                if (confirmedLetters.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Code letters confirmed from your tickets: " +
                            confirmedLetters.joinToString(", ") { "${it.entryCodeLetter} = #${it.gameNumber}" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun LearnedGameRow(g: LearnedGame, onRemove: (String) -> Unit) {
    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(g.title, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(
                    "Game #${g.gameNumber}",
                    g.entryCodeLetter?.let { "codes start with $it" },
                    if (g.source == LearnedGame.Source.PORTAL) "from the portal" else "from a ticket",
                    if (!g.onPortal) "not listed now" else null
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = { onRemove(g.gameNumber) }, modifier = Modifier.testTag("remove_learned_${g.gameNumber}")) {
            Icon(Icons.Default.Delete, contentDescription = "Remove ${g.title}")
        }
    }
}

private fun summary(r: GameSyncReport): String = when {
    r.listedCount == 0 -> "Couldn't read the game list."
    !r.changed -> "Up to date: the 2nd Chance Zone lists ${r.listedCount} games and the app knows them all."
    else -> "The 2nd Chance Zone lists ${r.listedCount} games." +
        (if (r.added.isNotEmpty()) " ${r.added.size} new." else "") +
        (if (r.retired.isNotEmpty()) " ${r.retired.size} no longer listed." else "") +
        (if (r.returned.isNotEmpty()) " ${r.returned.size} listed again." else "") +
        (if (r.renamed.isNotEmpty()) " ${r.renamed.size} renamed." else "")
}
