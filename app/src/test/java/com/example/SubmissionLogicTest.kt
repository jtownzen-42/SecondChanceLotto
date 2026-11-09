package com.example

import com.example.data.normalizeEntryCode
import com.example.submission.PortalResult
import com.example.submission.SubmissionAutomationHelper
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
        assertEquals(PortalResult.UNKNOWN, SubmissionAutomationHelper.parsePortalResult("\"PENDING\""))
        assertEquals(PortalResult.UNKNOWN, SubmissionAutomationHelper.parsePortalResult("null"))
        assertEquals(PortalResult.UNKNOWN, SubmissionAutomationHelper.parsePortalResult(null))
    }
}
