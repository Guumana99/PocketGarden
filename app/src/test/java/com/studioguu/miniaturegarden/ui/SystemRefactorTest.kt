package com.studioguu.miniaturegarden.ui

import com.studioguu.miniaturegarden.domain.model.ExpansionCheck
import com.studioguu.miniaturegarden.domain.model.GardenExpansion
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.LevelTable
import com.studioguu.miniaturegarden.domain.model.StarterGarden
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The system pass: a start-up that shows the sky at once and reads nothing heavy on the main
 * thread, kinder words for growing the garden, a level gauge instead of experience numbers, new
 * gardens at 11 x 11, and fewer frames drawn for nothing.
 */
class SystemRefactorTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private fun source(path: String) = File(main, "java/com/studioguu/miniaturegarden/$path").readText().replace("\r\n", "\n")
    private fun res(path: String) = File(main, "res/$path").readText().replace("\r\n", "\n")
    private val strings by lazy { res("values/strings.xml") }
    private fun string(name: String) = Regex("<string name=\"$name\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(strings)!!.groupValues[1]

    // --- 1. Start-up ---

    @Test
    fun `nothing is read synchronously while the app starts`() {
        val app = source("PocketGardenApplication.kt")
        assertFalse(app.contains("runBlocking"))
        assertTrue(app.contains("audio.holdUntilSettings()"))
        assertTrue(app.contains("flowOn(Dispatchers.IO)"))
        val audio = source("data/audio/AudioManager.kt")
        // The music is prepared in the background, and the effects' lengths are read off the main thread.
        assertFalse(audio.contains("MediaPlayer.create("))
        assertTrue(audio.contains("prepareAsync()"))
        assertTrue(audio.contains("durationReader.execute"))
    }

    @Test
    fun `the window starts as the title's sky, with a sprout until the garden is drawn`() {
        assertTrue(res("values/themes.xml").contains("@drawable/window_background"))
        assertTrue(res("values-v31/themes.xml").contains("android:windowSplashScreenBackground"))
        assertTrue(File(main, "res/drawable/window_background.xml").isFile)
        assertTrue(source("ui/title/TitleScreen.kt").contains("LoadingSprout(Modifier.align(Alignment.Center))"))
        assertTrue(source("ui/GardenScreen.kt").contains("if (!gardenReady) LoadingSprout("))
    }

    // --- 2. Growing the garden ---

    @Test
    fun `growing the garden is asked about kindly and without cell counts`() {
        assertEquals("お庭を広げますか？", string("expand_confirm_title"))
        for (name in listOf("expand_button", "expand_needs_level", "expand_confirm_title", "expand_confirm_text", "letter_grow_body")) {
            val text = string(name)
            assertFalse("$name shows a grid size", text.contains("×") || Regex("\\d+x\\d+").containsMatchIn(text))
            assertFalse("$name says 庭 without お", Regex("(?<!お)庭を広げ").containsMatchIn(text))
        }
        assertFalse(source("ui/hud/ExpandConfirmDialog.kt").contains("size: Int"))
    }

    // --- 3. Experience ---

    @Test
    fun `the gauge runs from empty at a new level to full at the next`() {
        assertEquals(0f, LevelTable.progressInLevel(0, 1), 0f)
        assertEquals(0.5f, LevelTable.progressInLevel(50, 1), 1e-6f)
        // Lv.2 at 100, Lv.3 at 300: 100 XP into Lv.2 is half way.
        assertEquals(0f, LevelTable.progressInLevel(100, 2), 0f)
        assertEquals(0.5f, LevelTable.progressInLevel(200, 2), 1e-6f)
        assertEquals(1f, LevelTable.progressInLevel(10_000, 2), 0f)
        assertEquals(1f, LevelTable.progressInLevel(0, LevelTable.MAX_LEVEL), 0f)
    }

    @Test
    fun `experience is shown as a gauge, never as numbers or floating toasts`() {
        assertEquals("Lv.%1\$d", string("status_level"))
        assertFalse(strings.contains("XP</string>"))
        assertFalse(strings.contains("name=\"effect_xp\""))
        val effects = source("ui/effects/FloatingEffects.kt")
        assertFalse(effects.contains("data class Xp("))
        assertFalse(source("ui/GardenViewModel.kt").contains("GardenEffect.Xp("))
        val bar = source("ui/hud/TopStatusBar.kt")
        assertTrue(bar.contains("LevelTable.progressInLevel(currentXp, playerLevel)"))
        // A level-up runs the gauge to full, then empties it.
        assertTrue(bar.contains("gauge.animateTo(1f") && bar.contains("gauge.snapTo(0f)"))
        // The profile arriving is not taken for a level-up.
        assertTrue(source("ui/GardenScreen.kt").contains("key(profile != null) { TopStatusBar("))
    }

    // --- 4. 11 x 11 ---

    @Test
    fun `a new garden is 11 x 11 with its crate in the middle`() {
        assertEquals(11, GridObject.GRID_SIZE)
        assertEquals(11, GardenProgress().gardenSize)
        assertEquals(11, GardenProgress.NEW_GARDEN.gardenSize)
        assertEquals(11, GardenMap.DEFAULT.size)
        assertEquals(5, StarterGarden.CRATE.x)
        assertEquals(5, StarterGarden.CRATE.y)
        // It grows once, to the largest size; gardens saved at 9 x 9 still grow from there.
        assertEquals(13, GardenExpansion.nextStage(GridObject.GRID_SIZE)?.size)
        assertEquals(11, GardenExpansion.nextStage(GridObject.LEGACY_GRID_SIZE)?.size)
        assertEquals(GridObject.LEGACY_GRID_SIZE, GardenProgress.EXISTING_GARDEN.gardenSize)
        assertEquals(ExpansionCheck.AT_MAX_SIZE, GardenExpansion.check(13, emptyList(), 99))
    }

    // --- 5. Fewer frames ---

    @Test
    fun `animations redraw without recomposing, and the rain is held to 30 frames a second`() {
        val rain = source("ui/effects/WeatherOverlay.kt")
        assertTrue(rain.contains("delay(RAIN_FRAME_MILLIS)"))
        assertTrue(rain.contains("RAIN_FRAME_MILLIS = 33L"))
        val book = source("ui/collection/CollectionDialog.kt")
        assertFalse(book.contains("label = \"beat\",\n        ).value"))
        assertTrue(book.contains("beat.value * (1f + reveal * 2f)"))
        // The sample garden's beat recomposes the sample alone.
        val title = source("ui/title/TitleScreen.kt")
        assertTrue(title.substringAfter("private fun SampleGarden(").contains("produceState(0)"))
        assertFalse(title.substringAfter("fun TitleScreen(").substringBefore("private fun SampleGarden(").contains("produceState(0)"))
    }
}
