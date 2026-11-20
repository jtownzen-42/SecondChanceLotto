package com.jtownzen.secondchanceil

import com.jtownzen.secondchanceil.data.normalizeEntryCode
import com.jtownzen.secondchanceil.submission.PortalResult
import com.jtownzen.secondchanceil.submission.SubmissionAutomationHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class SubmissionLogicTest {

    @Test
    fun `normalizeEntryCode strips spaces and hyphens and upper-cases`() {
        assertEquals("8492015829104A", normalizeEntryCode("8492-0158 2910-4a"))
        assertEquals("", normalizeEntryCode(" - "))
    }

    @Test
    fun `parsePortalResult reads the JSON string evaluateJavascript returns`() {
        assertEquals(PortalResult.SUBMITTED, SubmissionAutomationHelper.parsePortalResult("\"SUBMITTED\""))
        assertEquals(PortalResult.DUPLICATE, SubmissionAutomationHelper.parsePortalResult("\"DUPLICATE\""))
        assertEquals(PortalResult.REJECTED, SubmissionAutomationHelper.parsePortalResult("\"REJECTED\""))
        assertEquals(PortalResult.UNKNOWN, SubmissionAutomationHelper.parsePortalResult("\"PENDING\""))
        assertEquals(PortalResult.UNKNOWN, SubmissionAutomationHelper.parsePortalResult("null"))
        assertEquals(PortalResult.UNKNOWN, SubmissionAutomationHelper.parsePortalResult(null))
    }

    @Test
    fun `unquoteJsString safely handles null, JSON null, and quoted strings`() {
        assertEquals("", SubmissionAutomationHelper.unquoteJsString(null))
        assertEquals("", SubmissionAutomationHelper.unquoteJsString("null"))
        assertEquals("", SubmissionAutomationHelper.unquoteJsString("  "))
        assertEquals(
            "SUCCESS: Code injected into input field",
            SubmissionAutomationHelper.unquoteJsString("\"SUCCESS: Code injected into input field\"")
        )
    }
}
