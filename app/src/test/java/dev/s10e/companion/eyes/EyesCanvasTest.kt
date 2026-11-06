package dev.s10e.companion.eyes

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class EyesCanvasTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val roborazziRule = RoborazziRule(
        composeRule = composeTestRule,
        captureRoot = composeTestRule.onRoot(),
        options = RoborazziRule.Options(
            outputDirectoryPath = "src/test/snapshots/images",
        )
    )

    @Test
    fun captureAllMoods() {
        composeTestRule.mainClock.autoAdvance = false
        // Need to loop over moods, but we can't call setContent multiple times.
        // Or we can just capture them in multiple tests. Let's do multiple tests!
    }

    @Test fun capture_NEUTRAL() = captureMood(Mood.NEUTRAL)
    @Test fun capture_HAPPY() = captureMood(Mood.HAPPY)
    @Test fun capture_ANGRY() = captureMood(Mood.ANGRY)
    @Test fun capture_LISTENING() = captureMood(Mood.LISTENING)
    @Test fun capture_THINKING() = captureMood(Mood.THINKING)
    @Test fun capture_DIZZY() = captureMood(Mood.DIZZY)
    @Test fun capture_SLEEPY() = captureMood(Mood.SLEEPY)
    @Test fun capture_SAD() = captureMood(Mood.SAD)
    @Test fun capture_SURPRISED() = captureMood(Mood.SURPRISED)
    @Test fun capture_CONFUSED() = captureMood(Mood.CONFUSED)
    @Test fun capture_EXCITED() = captureMood(Mood.EXCITED)
    @Test fun capture_LOVE() = captureMood(Mood.LOVE)

    private fun captureMood(mood: Mood) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            EyesCanvas(mood = mood)
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/images/mood_${mood.name.lowercase()}.png"
        )
    }
}
