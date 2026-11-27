package com.jtownzen.secondchanceil

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.jtownzen.secondchanceil.ui.StepState
import com.jtownzen.secondchanceil.ui.stepStates
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LandscapeLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `step states mark earlier steps done and later ones upcoming`() {
        assertEquals(
            listOf(StepState.DONE, StepState.CURRENT, StepState.UPCOMING),
            stepStates(3, 1)
        )
        assertEquals(listOf(StepState.CURRENT, StepState.UPCOMING, StepState.UPCOMING), stepStates(3, 0))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port")
    fun `portrait keeps the bottom tab bar`() {
        composeRule.onNodeWithTag("bottom_nav_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("side_nav_rail").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land")
    fun `landscape moves the tabs to a side rail`() {
        composeRule.onNodeWithTag("side_nav_rail").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_nav_bar").assertDoesNotExist()
        // The title bar's buttons stay reachable from the rail.
        composeRule.onNodeWithTag("top_bar_account_button").assertIsDisplayed()
    }
}
