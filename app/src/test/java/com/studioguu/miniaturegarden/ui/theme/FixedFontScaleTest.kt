package com.studioguu.miniaturegarden.ui.theme

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The device's type setting plays no part in the game, and the game stands upright on every
 * screen (device feedback 11/03).
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FixedFontScaleTest {

    private val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    @Test
    fun `whatever the device's type setting, the game's context reads one`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        for (deviceScale in listOf(0.85f, 1f, 1.3f, 2f)) {
            val device = app.createConfigurationContext(Configuration(app.resources.configuration).apply { fontScale = deviceScale })
            assertEquals(deviceScale, device.resources.configuration.fontScale, 1e-4f)
            assertEquals(1f, FixedFontScale.wrap(device).resources.configuration.fontScale, 0f)
        }
        // Nothing else in the configuration is touched.
        val base = Configuration(app.resources.configuration).apply { fontScale = 1.3f }
        assertEquals(Configuration(base).apply { fontScale = 1f }, FixedFontScale.configuration(base))
    }

    @Test
    fun `the one activity takes its context through it, and is held upright`() {
        val activity = File(main, "java/com/studioguu/miniaturegarden/MainActivity.kt").readText()
        assertTrue(activity.contains("super.attachBaseContext(FixedFontScale.wrap(newBase))"))
        val manifest = File(main, "AndroidManifest.xml").readText()
        assertEquals(1, Regex("<activity\\b").findAll(manifest).count())
        assertTrue(manifest.contains("android:screenOrientation=\"portrait\""))
    }
}
