package com.studioguu.miniaturegarden.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.studioguu.miniaturegarden.ui.theme.GardenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The cosy menu pieces: that each shows what it should and does what it says when touched. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CozyComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a wood toggle turns on and off when tapped`() {
        var on by mutableStateOf(false)
        compose.setContent {
            GardenTheme { WoodToggle(checked = on, onCheckedChange = { on = it }, modifier = Modifier.testTag("toggle")) }
        }
        compose.onNodeWithTag("toggle").assertIsOff().performClick()
        compose.onNodeWithTag("toggle").assertIsOn()
        assertEquals(true, on)
        compose.onNodeWithTag("toggle").performClick().assertIsOff()
    }

    @Test
    fun `a disabled wood toggle stays as it is`() {
        var on by mutableStateOf(true)
        compose.setContent {
            GardenTheme { WoodToggle(checked = on, onCheckedChange = { on = it }, enabled = false, modifier = Modifier.testTag("toggle")) }
        }
        compose.onNodeWithTag("toggle").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("toggle").assertIsOn()
    }

    @Test
    fun `a carved button answers a tap, and not while disabled`() {
        var taps by mutableIntStateOf(0)
        var enabled by mutableStateOf(true)
        compose.setContent {
            GardenTheme { CarvedButton(text = "読む", onClick = { taps++ }, enabled = enabled) }
        }
        compose.onNodeWithText("読む").assertHasClickAction().assertIsEnabled().performClick()
        assertEquals(1, taps)
        enabled = false
        compose.onNodeWithText("読む").assertIsNotEnabled().performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `a wood slider moves to where it is set`() {
        var value by mutableFloatStateOf(0.5f)
        var finished by mutableIntStateOf(0)
        compose.setContent {
            GardenTheme {
                WoodSlider(value = value, onValueChange = { value = it }, onValueChangeFinished = { finished++ }, modifier = Modifier.testTag("slider"))
            }
        }
        compose.onNodeWithTag("slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0.2f) }
        assertEquals(0.2f, value, 0.001f)
        assertEquals(1, finished)
    }

    @Test
    fun `a wood panel shows its title and what is on it`() {
        compose.setContent {
            GardenTheme { WoodPanel(title = "BGM") { Text("ここに つまみ") } }
        }
        compose.onNodeWithText("BGM").assertIsDisplayed()
        compose.onNodeWithText("ここに つまみ").assertIsDisplayed()
    }

    @Test
    fun `wood tabs mark the chosen one and report a tap on another`() {
        var selected by mutableIntStateOf(0)
        compose.setContent {
            GardenTheme { WoodTabs(labels = listOf("音量", "情報"), selectedIndex = selected, onSelect = { selected = it }) }
        }
        compose.onNodeWithText("音量").assertIsSelected()
        compose.onNodeWithText("情報").assertIsNotSelected().performClick()
        assertEquals(1, selected)
        compose.onNodeWithText("情報").assertIsSelected()
    }

    @Test
    fun `a parchment dialog shows its title, its content and its buttons`() {
        var open by mutableStateOf(true)
        compose.setContent {
            GardenTheme {
                if (open) {
                    ParchmentDialog(
                        onDismissRequest = { open = false },
                        title = "設定",
                        actions = listOf(DialogAction("閉じる", { open = false })),
                    ) {
                        Column { Text("中身") }
                    }
                }
            }
        }
        compose.onNodeWithText("設定").assertIsDisplayed()
        compose.onNodeWithText("中身").assertIsDisplayed()
        compose.onNodeWithText("閉じる").performClick()
        compose.waitForIdle()
        assertEquals(false, open)
        compose.onNodeWithText("設定").assertDoesNotExist()
    }
}
