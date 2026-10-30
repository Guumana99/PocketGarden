package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.AppSettings
import com.studioguu.miniaturegarden.domain.model.GardenTip
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.StoreReviewRule
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tips given once, and when the store's review sheet may be asked for. */
class FirstMeetingsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    private fun plant(status: PlantStatus) = GridObject("p", 0, 0, objectId = "plant_tulip", status = status, lastUpdatedTimestamp = 0L)

    @Test
    fun `a tip is due when its thing is met, and only until it has been seen`() {
        assertNull(GardenTip.due(emptySet(), emptyList(), 0, unmetVisitor = false))
        val hungry = listOf(plant(PlantStatus.UNNOURISHED))
        assertEquals(GardenTip.NO_FOOD, GardenTip.due(emptySet(), hungry, 0, false))
        assertNull(GardenTip.due(setOf("NO_FOOD"), hungry, 0, false))
        assertEquals(GardenTip.WITHERED, GardenTip.due(emptySet(), listOf(plant(PlantStatus.WITHERED)), 0, false))
        assertEquals(GardenTip.NEW_PAGE, GardenTip.due(emptySet(), emptyList(), 1, false))
        assertEquals(GardenTip.VISITOR, GardenTip.due(emptySet(), emptyList(), 0, true))
    }

    private val ready = AppSettings(playDays = 5)

    @Test
    fun `a day counts once`() {
        val first = AppSettings().onDay(100)
        assertEquals(1, first.playDays)
        assertEquals(1, first.onDay(100).playDays)
        assertEquals(2, first.onDay(101).playDays)
    }

    @Test
    fun `the review sheet needs five days, Lv 4 and the first steps behind`() {
        assertTrue(StoreReviewRule.canAsk(ready, 4, TutorialStep.DONE, now))
        assertFalse(StoreReviewRule.canAsk(ready.copy(playDays = 4), 4, TutorialStep.DONE, now))
        assertFalse(StoreReviewRule.canAsk(ready, 3, TutorialStep.DONE, now))
        assertFalse(StoreReviewRule.canAsk(ready, 4, TutorialStep.REVEAL_POST, now))
        assertFalse(StoreReviewRule.canAsk(ready, 4, TutorialStep.HARVEST, now))
    }

    @Test
    fun `once asked it waits thirty days`() {
        val asked = ready.copy(reviewAskedAt = now - 29 * day)
        assertFalse(StoreReviewRule.canAsk(asked, 4, TutorialStep.DONE, now))
        assertTrue(StoreReviewRule.canAsk(asked, 4, TutorialStep.DONE, now + day))
    }

    @Test
    fun `not with a crop in trouble`() {
        assertTrue(StoreReviewRule.cropsInTrouble(listOf(plant(PlantStatus.UNNOURISHED))))
        assertTrue(StoreReviewRule.cropsInTrouble(listOf(plant(PlantStatus.WITHERED))))
        assertFalse(StoreReviewRule.cropsInTrouble(listOf(plant(PlantStatus.HEALTHY))))
    }

    @Test
    fun `the screen asks only on the plain garden, and the settings have no review button`() {
        val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        fun source(path: String) = File(main, "java/com/studioguu/miniaturegarden/$path").readText()
        val screen = source("ui/GardenScreen.kt")
        assertTrue(screen.contains("if (reviewDue && gardenAlone)"))
        for (open in listOf("!showShop", "!showPost", "!showSettings", "!showCollection", "levelUpNow == null")) {
            assertTrue(open, screen.substringAfter("val gardenAlone =").substringBefore("LaunchedEffect").contains(open))
        }
        assertFalse(source("ui/settings/SettingsDialog.kt").contains("StoreReview"))
    }
}
