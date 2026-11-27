package com.jtownzen.secondchanceil

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.jtownzen.secondchanceil.scanner.TargetRegion
import com.jtownzen.secondchanceil.scanner.previewRegionRect
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

    @Test
    fun `scan frame follows the preview crop`() {
        // A 4:3 picture filling an 800x360 box is scaled to 800x600 and 120 is cropped off the top.
        val r = previewRegionRect(800f, 360f, 4f / 3f, TargetRegion.VIEWFINDER_CODE_BOX)
        assertEquals(48f, r.left, 0.01f)
        assertEquals(600f * 0.32f - 120f, r.top, 0.01f)
        assertEquals(600f * 0.68f - 120f, r.bottom, 0.01f)
        // Same aspect as the picture: plain fractions.
        val same = previewRegionRect(400f, 300f, 4f / 3f, TargetRegion.VIEWFINDER_CODE_BOX)
        assertEquals(300f * 0.32f, same.top, 0.01f)
    }
}
