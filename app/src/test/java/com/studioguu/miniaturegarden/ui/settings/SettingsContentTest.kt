package com.studioguu.miniaturegarden.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.studioguu.miniaturegarden.domain.model.AppSettings
import com.studioguu.miniaturegarden.ui.components.WoodToggle
import com.studioguu.miniaturegarden.ui.theme.GardenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The settings sheet, driven by a stand-in for the ViewModel that keeps the settings and writes down each change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private class Recorder(start: AppSettings) : SettingsActions {
        var settings by mutableStateOf(start)
        val calls = mutableListOf<String>()
        override fun setBgmEnabled(enabled: Boolean) { calls += "bgm $enabled"; settings = settings.copy(bgmEnabled = enabled) }
        override fun setBgmVolume(volume: Float) { calls += "bgmVolume"; settings = settings.copy(bgmVolume = volume) }
        override fun setSoundEffectsEnabled(enabled: Boolean) { calls += "se $enabled"; settings = settings.copy(soundEffectsEnabled = enabled) }
        override fun setSoundEffectVolume(volume: Float) { calls += "seVolume"; settings = settings.copy(soundEffectVolume = volume) }
        override fun previewSoundEffect() { calls += "preview" }
        override fun setNotificationsEnabled(enabled: Boolean) { calls += "notifications $enabled"; settings = settings.copy(notificationsEnabled = enabled) }
    }

    private var dismissed by mutableIntStateOf(0)

    private fun show(start: AppSettings = AppSettings()): Recorder {
        val recorder = Recorder(start)
        compose.setContent {
            GardenTheme {
                SettingsContent(
                    settings = recorder.settings,
                    versionName = "9.9.9",
                    actions = recorder,
                    onDismiss = { dismissed++ },
                    // The real row asks the device for permission; a plain switch stands in for it.
                    notificationRow = { enabled, onChange ->
                        WoodToggle(checked = enabled, onCheckedChange = onChange, modifier = Modifier.testTag("notifications"))
                    },
                )
            }
        }
        return recorder
    }

    @Test
    fun `it opens on the sound tab, with the music and the effects each on a board of its own`() {
        show()
        compose.onNodeWithText("設定").assertIsDisplayed()
        compose.onNodeWithText("BGM").assertIsDisplayed()
        compose.onNodeWithText("効果音").assertIsDisplayed()
        compose.onNodeWithContentDescription("BGM").assertIsOn()
        compose.onNodeWithContentDescription("効果音").assertIsOn()
        // At their default volumes there is nothing to reset.
        compose.onAllNodesWithText("初期値に戻す").onFirst().assertIsNotEnabled()
        compose.onAllNodesWithText("初期値に戻す").onLast().assertIsNotEnabled()
    }

    @Test
    fun `muting the music switches it off and greys out its slider, keeping the effects`() {
        val recorder = show()
        compose.onNodeWithContentDescription("BGM").performClick()
        assertEquals(listOf("bgm false"), recorder.calls)
        compose.onNodeWithContentDescription("BGM").assertIsOff()
        compose.onNodeWithContentDescription("BGM の音量").assertIsNotEnabled()
        compose.onNodeWithContentDescription("効果音 の音量").assertIsEnabled()
    }

    @Test
    fun `moving the effects slider sets the volume and plays a sample when let go`() {
        val recorder = show()
        compose.onNodeWithContentDescription("効果音 の音量").performSemanticsAction(SemanticsActions.SetProgress) { it(0.4f) }
        assertEquals(0.4f, recorder.settings.soundEffectVolume, 0.01f)
        assertEquals(listOf("seVolume", "preview"), recorder.calls)
    }

    @Test
    fun `a volume off its default can be put back`() {
        val recorder = show(AppSettings(bgmVolume = 0.8f))
        compose.onAllNodesWithText("初期値に戻す").onFirst().assertIsEnabled().performClick()
        assertEquals(AppSettings.DEFAULT_BGM_VOLUME, recorder.settings.bgmVolume, 0.001f)
        compose.onAllNodesWithText("初期値に戻す").onFirst().assertIsNotEnabled()
    }

    @Test
    fun `the about tab shows the version, and the licences open on a sheet of their own`() {
        show()
        compose.onNodeWithText("情報").performClick()
        compose.onNodeWithText("v9.9.9", substring = true).assertIsDisplayed()
        compose.onNodeWithText("ライセンス").performClick()
        compose.onNodeWithText("オープンソースライブラリ", substring = true).assertIsDisplayed()
        // Two close buttons now: the licences' own, on top, closes only them.
        compose.onAllNodesWithText("閉じる").onLast().performClick()
        compose.onNodeWithText("オープンソースライブラリ", substring = true).assertDoesNotExist()
        compose.onNodeWithText("v9.9.9", substring = true).assertIsDisplayed()
        assertEquals(0, dismissed)
    }

    @Test
    fun `the other tab holds the notification switch`() {
        val recorder = show(AppSettings(notificationsEnabled = false))
        compose.onNodeWithText("その他").performClick()
        compose.onNodeWithTag("notifications").assertIsOff().performClick()
        assertEquals(listOf("notifications true"), recorder.calls)
        compose.onNodeWithTag("notifications").assertIsOn()
    }

    @Test
    fun `close dismisses the settings`() {
        show()
        compose.onNodeWithText("閉じる").performClick()
        assertEquals(1, dismissed)
    }
}
