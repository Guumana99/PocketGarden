package com.studioguu.miniaturegarden.ui.hud

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.SeasonPhase
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.theme.GardenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** The bar at the top: one line and one height at any type size, on a phone 360 dp across. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp")
class TopStatusBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val roomy = TopBarFit.Spacing(padding = 14f, gap = 8f)
    private val tight = TopBarFit.Spacing(padding = 9f, gap = 5f)

    private var fontScale by mutableFloatStateOf(1f)

    private fun showBar() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GardenTheme {
                    Box(Modifier.width(360.dp).testTag("bar")) {
                        TopStatusBar(1234, 12, 0, WeatherType.RAINY, season = Season.AUTUMN, phase = SeasonPhase.MID)
                    }
                }
            }
        }
    }

    private fun heightAt(scale: Float): Float {
        fontScale = scale
        compose.waitForIdle()
        return compose.onNodeWithTag("bar").getBoundsInRoot().let { (it.bottom - it.top).value }
    }

    @Test
    fun `the tags keep their room while they fit, then give it up, and only then the words shrink`() {
        val fits = TopBarFit.of(320f, listOf(70f, 56f, 90f), roomy, tight)
        assertEquals(TopBarFit(1f, 14f, 8f), fits)
        val tighter = TopBarFit.of(290f, listOf(70f, 56f, 90f), roomy, tight)
        assertEquals(TopBarFit(1f, 9f, 5f), tighter)
        val shrunk = TopBarFit.of(240f, listOf(70f, 56f, 90f), roomy, tight)
        assertEquals(9f, shrunk.padding, 0f)
        assertTrue(shrunk.textScale < 1f)
    }

    @Test
    fun `however long the words, shrunk they take up just the room there is`() {
        val bodies = listOf(120f, 90f, 260f)
        val fit = TopBarFit.of(320f, bodies, roomy, tight)
        val laidOut = bodies.sumOf { it.toDouble() * fit.textScale } + 6 * fit.padding + 2 * fit.gap
        assertEquals(320.0, laidOut, 0.01)
    }

    @Test
    fun `the bar is as tall at twice the type as at the largest it follows`() {
        showBar()
        assertEquals(heightAt(TOP_BAR_MAX_FONT_SCALE), heightAt(2f), 0.5f)
    }

    @Test
    fun `the words never wrap`() {
        val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val bar = File(main, "java/com/studioguu/miniaturegarden/ui/hud/TopStatusBar.kt").readText()
        assertEquals(2, Regex("softWrap = false,\n").findAll(bar.replace("\r\n", "\n")).count())
        assertTrue(bar.contains("Density(device.density, min(device.fontScale, TOP_BAR_MAX_FONT_SCALE))"))
    }
}
