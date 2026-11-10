package com.studioguu.miniaturegarden.ui.hud

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.LevelTable
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.theme.GardenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The level's bubble, opened from the level on the status bar (#fb-1107). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LevelDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the experience through the level, how far that is, and what the next level opens`() {
        // Lv.12: 7 200 from it to Lv.13; 68 % of the way along.
        val from = LevelTable.xpForLevel(12)
        val info = LevelInfo.of(12, from + 4_896L)
        assertEquals(4_896L, info.gained)
        assertEquals(7_200L, info.needed)
        assertEquals(68, info.percent)
        assertEquals(0.68f, info.progress, 1e-4f)
        assertEquals(GardenItem.unlockedBetween(12, 13), info.nextItems)
        // Just reached, and a moment before the next.
        assertEquals(0, LevelInfo.of(5, LevelTable.xpForLevel(5)).percent)
        assertEquals(99, LevelInfo.of(5, LevelTable.xpForLevel(6) - 1).percent)
        // The top level: nothing more to gain, and nothing comes after it.
        val top = LevelInfo.of(LevelTable.MAX_LEVEL, LevelTable.xpForLevel(LevelTable.MAX_LEVEL) + 10)
        assertTrue(top.isMax)
        assertTrue(top.nextItems.isEmpty())
        // Somewhere the next level opens something, somewhere it opens nothing.
        val levels = 1 until 30
        assertTrue(levels.any { LevelInfo.of(it, LevelTable.xpForLevel(it)).nextItems.isNotEmpty() })
        assertTrue(levels.any { LevelInfo.of(it, LevelTable.xpForLevel(it)).nextItems.isEmpty() })
    }

    @Test
    fun `the bubble shows the level, the experience line and the next level's line`() {
        val level = (1 until 30).first { GardenItem.unlockedBetween(it, it + 1).isNotEmpty() }
        compose.setContent { GardenTheme { LevelDialog(LevelInfo.of(level, LevelTable.xpForLevel(level)), onDismiss = {}) } }
        compose.onNodeWithText("Lv.$level").assertIsDisplayed()
        compose.onNodeWithTag(LEVEL_XP_TAG).assertIsDisplayed()
        compose.onNodeWithText("経験値 0 / ${LevelTable.xpForLevel(level + 1) - LevelTable.xpForLevel(level)}（0％）").assertIsDisplayed()
        compose.onNodeWithTag(LEVEL_NEXT_TAG).assertIsDisplayed()
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasTestTag(LEVEL_MAX_TAG)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `the next level's line is worded by how many things it opens`() {
        var info by mutableStateOf(LevelInfo.of(1, 0L))
        var shownNames = emptyList<String>()
        compose.setContent {
            GardenTheme {
                shownNames = info.nextItems.map { com.studioguu.miniaturegarden.ui.itemDisplayName(it).toList().joinToString("⁠") }
                LevelDialog(info, onDismiss = {})
            }
        }
        val seen = mutableSetOf<Int>()
        for (level in 1 until 20) {
            val items = GardenItem.unlockedBetween(level, level + 1)
            if (items.isEmpty()) continue
            info = LevelInfo.of(level, LevelTable.xpForLevel(level))
            compose.waitForIdle()
            val names = shownNames
            val expected = when (items.size) {
                1 -> "次の Lv で ${names[0]} が手に入ります"
                2 -> "次の Lv で ${names[0]} と ${names[1]} が手に入ります"
                else -> "次の Lv で ${names[0]} や ${names[1]} などが手に入ります"
            }
            compose.onNodeWithText(expected).assertIsDisplayed()
            seen += items.size.coerceAtMost(3)
        }
        // The table has levels opening one thing and levels opening two.
        assertTrue(seen.containsAll(listOf(1, 2)))
    }

    @Test
    fun `with nothing coming next there is no next line, and at the top level no experience line`() {
        val quiet = (1 until 30).first { GardenItem.unlockedBetween(it, it + 1).isEmpty() }
        var info by mutableStateOf(LevelInfo.of(quiet, LevelTable.xpForLevel(quiet)))
        compose.setContent { GardenTheme { LevelDialog(info, onDismiss = {}) } }
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasTestTag(LEVEL_NEXT_TAG)).fetchSemanticsNodes().isEmpty())
        info = LevelInfo.of(LevelTable.MAX_LEVEL, LevelTable.xpForLevel(LevelTable.MAX_LEVEL))
        compose.onNodeWithText("最大レベル").assertIsDisplayed()
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasTestTag(LEVEL_XP_TAG)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `tapping the level on the bar opens it, and the weather opens the weather`() {
        var level = false
        var weather = false
        compose.setContent {
            GardenTheme {
                TopStatusBar(coins = 10, playerLevel = 3, currentXp = 400, weather = WeatherType.SUNNY, onWeatherTap = { weather = true }, onLevelTap = { level = true })
            }
        }
        compose.onNodeWithTag(LEVEL_CHIP_TAG).performClick()
        assertTrue(level)
        assertFalse(weather)
    }
}
