package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.scanner.LotteryCodeParser
import com.example.ui.theme.LotteryEmerald

/**
 * Entry code input shared by the scan review sheet and manual entry. Real Illinois 2nd Chance
 * codes are 1 letter + 10 digits; anything else shows an inline error (saving still allowed after
 * a confirm). [reviewReason], when set, asks the user to compare a scanned code with the ticket.
 */
@Composable
fun EntryCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    reviewReason: String = "",
    emptyHint: String = "1 letter + 10 digits, under the scratch-off ENTRY CODE box"
) {
    Column(modifier = modifier) {
        if (reviewReason.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_code_review_warning")
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Double-check this code against your ticket: $reviewReason.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        val valid = LotteryCodeParser.isValidEntryCode(value)
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(LotteryCodeParser.normalizeEntryCodeInput(it).take(14)) },
            label = { Text("Entry Code (1 letter + 10 digits)*") },
            placeholder = { Text("e.g. B4920158291") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("entry_code_input"),
            textStyle = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = LotteryEmerald
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            isError = value.isNotEmpty() && !valid,
            supportingText = {
                Text(
                    text = entryCodeHint(value, emptyHint),
                    color = when {
                        value.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                        valid -> LotteryEmerald
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }
        )
    }
}

internal fun entryCodeHint(value: String, emptyHint: String): String = when {
    value.isEmpty() -> emptyHint
    LotteryCodeParser.isValidEntryCode(value) -> "Looks right: 1 letter + 10 digits"
    !value.first().isLetter() -> "Codes start with a letter (e.g. B4920158291)"
    value.drop(1).any { !it.isDigit() } -> "After the first letter, codes are digits only"
    value.length < 11 -> "Too short: ${value.length - 1} of 10 digits after the letter"
    else -> "Too long: ${value.length - 1} digits after the letter, expected 10"
}

/** Asks before saving a code that isn't 1 letter + 10 digits. */
@Composable
fun ConfirmUnusualCodeDialog(code: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save this code anyway?") },
        text = {
            Text(
                "\"$code\" doesn't match the usual Illinois entry code format (1 letter + 10 digits). " +
                    "Check it against your ticket before saving."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_unusual_code")) { Text("Save anyway") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fix code") }
        }
    )
}
