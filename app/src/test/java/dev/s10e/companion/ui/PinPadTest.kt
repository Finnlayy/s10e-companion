package dev.s10e.companion.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [31], qualifiers = "w384dp-h811dp-port")
class PinPadTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun portrait_allKeysReachable_andOkSubmits() {
        var submitted: String? = null
        composeTestRule.setContent {
            PinPad(title = "Neue PIN festlegen", message = null, onSubmit = { submitted = it }, onCancel = {})
        }

        listOf("3", "6", "9", "3").forEach { composeTestRule.onNodeWithText(it).assertIsDisplayed().performClick() }
        composeTestRule.onNodeWithText("OK").assertIsDisplayed().performClick()

        assertEquals("3693", submitted)
    }
}
