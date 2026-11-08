package com.studioguu.miniaturegarden.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.studioguu.miniaturegarden.ui.theme.GardenTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The settings' "play the first steps again" asks first (2026-11-08). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReplayTutorialDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `it asks, and only "start" plays the first steps again`() {
        var started = 0
        var dismissed = 0
        compose.setContent { GardenTheme { ReplayTutorialDialog(onConfirm = { started++ }, onDismiss = { dismissed++ }) } }
        compose.onNodeWithTag(REPLAY_QUESTION_TAG).assertIsDisplayed()
        compose.onNodeWithText("やめる").performClick()
        assertEquals(0, started)
        assertEquals(1, dismissed)
        compose.onNodeWithText("はじめる").performClick()
        assertEquals(1, started)
    }

    @Test
    fun `the question breaks between its phrases, and the settings go through it`() {
        val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val strings = File(main, "res/values/strings.xml").readText()
        assertTrue(strings.contains("<string name=\"replay_tutorial_question\">チュートリアルを\\nもう一度はじめますか？</string>"))
        val screen = File(main, "java/com/studioguu/miniaturegarden/ui/GardenScreen.kt").readText().replace("\r\n", "\n")
        // The settings button only opens the question; the question starts it.
        assertTrue(Regex("onReplayTutorial = \\{\\s*showSettings = false\\s*confirmReplay = true").containsMatchIn(screen))
        assertTrue(Regex("ReplayTutorialDialog\\(\\s*onConfirm = \\{\\s*confirmReplay = false\\s*viewModel\\.replayTutorial\\(\\)").containsMatchIn(screen))
    }
}
