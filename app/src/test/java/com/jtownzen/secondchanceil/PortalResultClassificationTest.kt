package com.jtownzen.secondchanceil

import com.jtownzen.secondchanceil.submission.PortalResult
import com.jtownzen.secondchanceil.submission.PortalSnapshot
import com.jtownzen.secondchanceil.submission.SubmissionAutomationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the app reads the 2nd Chance Zone's answer after an entry. The live strings come from the
 * real Enter Your Tickets page (docs/second-chance-games.md, read from the phone 2026-11-13).
 */
class PortalResultClassificationTest {

    private val accepted = "Your entry has been accepted and added to your 'My Entries' page."
    private val notEntered = "Ticket not entered, please try again."

    /** Encodes like the page's encodeURIComponent: spaces as %20, never '+'. */
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** The raw value WebView.evaluateJavascript hands back: a JSON string literal. */
    private fun raw(
        lock: Boolean = false,
        seen: Boolean = true,
        alertClass: String = "",
        alertText: String = "",
        others: List<Pair<String, String>> = emptyList()
    ): String {
        val o = others.joinToString(",") { enc(it.first) + ":" + enc(it.second) }
        val fields = listOf(
            "SC1", if (lock) "1" else "0", if (seen) "1" else "0", if (alertText.isNotEmpty()) "1" else "0",
            enc(alertClass), enc(alertText), o
        )
        return "\"" + fields.joinToString("|") + "\""
    }

    @Test
    fun `accepted entry is SUBMITTED even when the page's own copy mentions rejection phrases`() {
        // The bug: instructions and rules on the page ("not eligible", "daily entry limit",
        // "please try again") used to overrule the green #entry-alert, so every good entry
        // was reported as NOT ENTERED.
        val result = raw(
            alertClass = "alert alert-success",
            alertText = accepted,
            others = listOf(
                "alert alert-info" to "Tickets from games not in the list are not eligible. There is a daily entry limit of 25.",
                "alert alert-warning" to "If a ticket is not entered, please try again."
            )
        )
        assertEquals(PortalResult.SUBMITTED, SubmissionAutomationHelper.parsePortalResult(result))
    }

    @Test
    fun `red entry-alert is REJECTED and its text is surfaced`() {
        val result = raw(alertClass = "alert alert-danger", alertText = notEntered)
        assertEquals(PortalResult.REJECTED, SubmissionAutomationHelper.parsePortalResult(result))
        assertEquals(notEntered, SubmissionAutomationHelper.portalMessage(result))
    }

    @Test
    fun `entry-alert styling decides when its wording is unfamiliar`() {
        assertEquals(
            PortalResult.REJECTED,
            SubmissionAutomationHelper.parsePortalResult(raw(alertClass = "alert alert-danger", alertText = "Something went wrong."))
        )
        assertEquals(
            PortalResult.SUBMITTED,
            SubmissionAutomationHelper.parsePortalResult(raw(alertClass = "alert alert-success", alertText = "Thanks! See My Entries."))
        )
        assertEquals(
            PortalResult.UNKNOWN,
            SubmissionAutomationHelper.parsePortalResult(raw(alertClass = "alert alert-info", alertText = "Processing your ticket…"))
        )
    }

    @Test
    fun `duplicate and daily-limit answers are told apart`() {
        assertEquals(
            PortalResult.DUPLICATE,
            SubmissionAutomationHelper.parsePortalResult(
                raw(alertClass = "alert alert-danger", alertText = "This ticket has already been entered.")
            )
        )
        // "already" alone is not a duplicate: a red daily-limit warning stays a rejection.
        assertEquals(
            PortalResult.REJECTED,
            SubmissionAutomationHelper.parsePortalResult(
                raw(alertClass = "alert alert-warning", alertText = "You have already entered 25 tickets today.")
            )
        )
    }

    @Test
    fun `negated success wording is never read as accepted`() {
        assertEquals(
            PortalResult.REJECTED,
            SubmissionAutomationHelper.parsePortalResult(
                raw(alertClass = "alert alert-danger", alertText = "Your entry has not been accepted. Invalid entry code.")
            )
        )
        assertNotEquals(
            PortalResult.SUBMITTED,
            SubmissionAutomationHelper.classifyAnswer("Ticket was not successfully entered", "", trustClass = false)
        )
    }

    @Test
    fun `green styling never outweighs negated words`() {
        // Seen mid-update: the class is still green, the words already say no.
        assertEquals(
            PortalResult.REJECTED,
            SubmissionAutomationHelper.parsePortalResult(
                raw(alertClass = "alert alert-success", alertText = "Your entry has not been accepted.")
            )
        )
        assertNotEquals(
            PortalResult.SUBMITTED,
            SubmissionAutomationHelper.classifyAnswer("Unable to process. Try later.", "alert alert-success", trustClass = true)
        )
    }

    @Test
    fun `fallback only reads alerts that appeared after Auto-Fill`() {
        // The page script drops alerts already showing at fill time (rules, notices) and reads
        // none at all without a fill, so instructions can't pass for an answer.
        val fill = SubmissionAutomationHelper.generateCodeSubmissionScript("C1234567890", "7664")
        val check = SubmissionAutomationHelper.generateCheckSuccessScript()
        assertTrue(fill.contains("__scAlertBaseline"))
        assertTrue(check.contains("__scAlertBaseline"))
        assertTrue(check.contains("!base[t]"))
    }

    @Test
    fun `lockout wins over everything`() {
        assertEquals(
            PortalResult.LOCKED,
            SubmissionAutomationHelper.parsePortalResult(raw(lock = true, alertClass = "alert alert-success", alertText = accepted))
        )
    }

    @Test
    fun `without entry-alert, styled banners need matching words to count`() {
        // A maintenance notice styled like an error must not read as a rejection.
        assertEquals(
            PortalResult.UNKNOWN,
            SubmissionAutomationHelper.parsePortalResult(
                raw(others = listOf("alert alert-danger" to "Scheduled maintenance Sunday 2-4 AM."))
            )
        )
        assertEquals(
            PortalResult.REJECTED,
            SubmissionAutomationHelper.parsePortalResult(
                raw(others = listOf("toast" to "Invalid entry code."))
            )
        )
    }

    @Test
    fun `conflicting fallback alerts are UNKNOWN, never a guess`() {
        val snap = PortalSnapshot(
            lockout = false, submitSeen = true, entryAlertVisible = false, entryAlertClass = "", entryAlertText = "",
            otherAlerts = listOf("alert" to accepted, "alert" to notEntered)
        )
        assertEquals(PortalResult.UNKNOWN, SubmissionAutomationHelper.classify(snap))
    }

    @Test
    fun `snapshot carries the submit-seen flag`() {
        assertTrue(SubmissionAutomationHelper.submitSeen(raw(seen = true, alertText = accepted)))
        assertFalse(SubmissionAutomationHelper.submitSeen(raw(seen = false, alertText = accepted)))
    }

    @Test
    fun `text with separators and quotes round-trips intact`() {
        val tricky = "Entry \"F1234567890\" | game 7676: not a valid code, please try again."
        val result = raw(alertClass = "alert alert-danger", alertText = tricky)
        assertEquals(tricky, SubmissionAutomationHelper.portalMessage(result))
        assertEquals(PortalResult.REJECTED, SubmissionAutomationHelper.parsePortalResult(result))
    }

    @Test
    fun `older plain answers still parse`() {
        assertEquals(PortalResult.SUBMITTED, SubmissionAutomationHelper.parsePortalResult("\"SUBMITTED\""))
        assertEquals(PortalResult.REJECTED, SubmissionAutomationHelper.parsePortalResult("\"REJECTED|S\""))
        assertTrue(SubmissionAutomationHelper.submitSeen("\"REJECTED|S\""))
        assertEquals("", SubmissionAutomationHelper.portalMessage("\"REJECTED\""))
    }

    @Test
    fun `check script reads the page but no longer decides`() {
        val js = SubmissionAutomationHelper.generateCheckSuccessScript()
        assertTrue(js.contains("entry-alert"))
        assertTrue(js.contains(SubmissionAutomationHelper.LOCKOUT_PHRASE))
        // The old whole-page rejection phrases must not come back into the script.
        assertFalse(js.contains("is not eligible"))
        assertFalse(js.contains("daily entry limit"))
    }
}
