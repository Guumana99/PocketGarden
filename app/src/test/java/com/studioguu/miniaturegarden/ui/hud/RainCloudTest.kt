package com.studioguu.miniaturegarden.ui.hud

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.render.RainGlint
import com.studioguu.miniaturegarden.ui.render.SceneTint
import com.studioguu.miniaturegarden.domain.model.TimeOfDay
import com.studioguu.miniaturegarden.ui.theme.GardenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The cloud that calls the rain: what it shows, how it moves, and what a tap on it does. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp")
class RainCloudTest {

    @get:Rule
    val compose = createComposeRule()

    private fun state(left: Int = 1, growing: Boolean = true, thirsty: Boolean = false, ad: Boolean = true) =
        RainCloudState.of(left, growing, thirsty, ad)

    @Test
    fun `it calls the rain while there is one to call and something for it to do`() {
        assertEquals(RainCloudState.READY, state())
        assertEquals(RainCloudState.PARCHED, state(thirsty = true))
        assertTrue(RainCloudState.READY.calling && RainCloudState.PARCHED.calling)
        assertNull(RainCloudState.READY.restMessage)
    }

    @Test
    fun `otherwise it is a white cloud, which says why when tapped`() {
        assertEquals(RainCloudState.USED_TODAY, state(left = 0, thirsty = true))
        assertEquals(RainCloudState.NOTHING_GROWING, state(growing = false))
        assertEquals(RainCloudState.NO_AD, state(ad = false, thirsty = true))
        assertEquals(R.string.msg_rain_used, RainCloudState.USED_TODAY.restMessage)
        assertEquals(R.string.msg_no_growing_plants, RainCloudState.NOTHING_GROWING.restMessage)
        assertEquals(R.string.msg_reward_unavailable, RainCloudState.NO_AD.restMessage)
        for (resting in listOf(RainCloudState.USED_TODAY, RainCloudState.NOTHING_GROWING, RainCloudState.NO_AD)) {
            assertFalse(resting.calling)
            assertEquals(0f, resting.rain, 0f)
        }
    }

    @Test
    fun `while it is introduced it looks a rain cloud, and a tap on it still does what it would`() {
        for (resting in listOf(RainCloudState.USED_TODAY, RainCloudState.NOTHING_GROWING, RainCloudState.NO_AD)) {
            assertEquals(RainCloudState.READY, resting.look(introducing = true))
            assertEquals(resting, resting.look(introducing = false))
        }
        assertEquals(RainCloudState.PARCHED, RainCloudState.PARCHED.look(introducing = true))
        val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val screen = File(main, "java/com/studioguu/miniaturegarden/ui/GardenScreen.kt").readText()
        assertTrue(screen.contains("val rest = rainCloud.restMessage"))
    }

    @Test
    fun `its words look forward`() {
        val app = RuntimeEnvironment.getApplication()
        assertEquals("また明日、雨を呼べます", app.getString(R.string.msg_rain_used))
        assertEquals("作物を育てると、雨を呼べます", app.getString(R.string.msg_no_growing_plants))
        assertTrue(app.getString(R.string.reveal_rain_text).startsWith("空の雨雲をタップすると"))
    }

    @Test
    fun `a white cloud lets nothing fall, a thirsty garden's a little more, and only now and then`() {
        fun most(rain: Float) = (0L until RainCloudLook.DROP_MILLIS step 50L).maxOf { RainCloudLook.dropsAt(it, rain).size }
        fun busy(rain: Float) = (0L until RainCloudLook.DROP_MILLIS step 50L).count { RainCloudLook.dropsAt(it, rain).isNotEmpty() }
        assertEquals(0, most(0f))
        assertTrue(busy(RainCloudState.PARCHED.rain) > busy(RainCloudState.READY.rain))
        assertTrue(most(RainCloudState.PARCHED.rain) <= 3)
        assertTrue(busy(RainCloudState.READY.rain) < (RainCloudLook.DROP_MILLIS / 50L).toInt())
        for (t in 0L..7_000L step 70L) {
            for (drop in RainCloudLook.dropsAt(t, 1f)) {
                assertTrue(drop.fall in 0f..1f && drop.alpha in 0f..1f)
            }
        }
    }

    @Test
    fun `it drifts gently`() {
        assertEquals(0f, RainCloudLook.bobAt(0L), 1e-4f)
        assertTrue(RainCloudLook.BOB_DP <= 3f && RainCloudLook.BOB_MILLIS >= 4_000L)
        assertNotEquals(RainCloudLook.bobAt(0L), RainCloudLook.bobAt(RainCloudLook.BOB_MILLIS / 4), 0.5f)
    }

    @Test
    fun `its colour comes down with the light, and the darker it is the more it looks like rain`() {
        val clear = Color.Transparent
        val night = SceneTint.of(TimeOfDay.NIGHT)
        assertEquals(RainCloudLook.colourOf(0f, SceneTint.of(TimeOfDay.DAY)).red, RainCloudLook.colourOf(0f, clear.copy(alpha = 0f)).red, 0.01f)
        assertTrue(RainCloudLook.colourOf(0f, night).red < RainCloudLook.colourOf(0f, clear).red)
        assertTrue(RainCloudLook.colourOf(1f, clear).red < RainCloudLook.colourOf(0.75f, clear).red)
    }

    @Test
    fun `it can be found by name and takes a finger, and a tap reaches the screen`() {
        var taps = 0
        compose.setContent { GardenTheme { RainCloud(RainCloudState.READY, Color.Transparent, { taps++ }) } }
        val name = RuntimeEnvironment.getApplication().getString(R.string.reward_rain_button)
        assertEquals("雨乞い", name)
        compose.onNodeWithContentDescription(name).assertExists()
        compose.onNodeWithTag(RAIN_CLOUD_TAG).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `the brown button is gone from the garden`() {
        val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val screen = File(main, "java/com/studioguu/miniaturegarden/ui/GardenScreen.kt").readText()
        assertFalse(screen.contains("R.string.reward_rain_button"))
        assertTrue(screen.contains("RainCloud("))
    }

    @Test
    fun `the crops glint for a moment, not all alike, and it is over by the end`() {
        assertEquals(0f, RainGlint.strengthAt(0f, 3), 0f)
        assertEquals(0f, RainGlint.strengthAt(1f, 3), 0f)
        assertTrue((0..100).any { RainGlint.strengthAt(it / 100f, 3) > 0.9f })
        assertNotEquals(RainGlint.strengthAt(0.3f, 0), RainGlint.strengthAt(0.3f, 6), 0.05f)
    }
}
