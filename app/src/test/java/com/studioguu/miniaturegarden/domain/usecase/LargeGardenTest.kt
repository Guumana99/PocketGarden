package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.ExpansionCheck
import com.studioguu.miniaturegarden.domain.model.GardenExpansion
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GreenhouseDoor
import com.studioguu.miniaturegarden.domain.model.GreenhouseSide
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.LevelTable
import com.studioguu.miniaturegarden.domain.model.PostOffice
import com.studioguu.miniaturegarden.domain.model.RunKind
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.ui.render.GardenCamera
import com.studioguu.miniaturegarden.ui.render.IsometricMetrics
import androidx.compose.ui.geometry.Size
import com.studioguu.miniaturegarden.ui.shop.ExpansionOffer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The garden at 15 x 15 and 17 x 17: how it gets there, and what stays as it was. */
class LargeGardenTest {
    private fun at(x: Int, y: Int, id: String) =
        GridObject(instanceId = "${id}_${x}_$y", gridX = x, gridY = y, objectId = id, lastUpdatedTimestamp = 0L)

    private fun rich(level: Int, coins: Long) = UserProfile(coins = coins, playerLevel = level, currentXp = LevelTable.xpForLevel(level))

    @Test
    fun `the sizes open at their levels, one at a time`() {
        assertEquals(ExpansionCheck.NEEDS_LEVEL, GardenExpansion.check(13, 13, 100_000))
        assertEquals(ExpansionCheck.NEEDS_COINS, GardenExpansion.check(13, 14, 9_999))
        assertEquals(ExpansionCheck.READY, GardenExpansion.check(13, 14, 10_000))
        assertEquals(ExpansionCheck.NEEDS_LEVEL, GardenExpansion.check(15, 19, 100_000))
        assertEquals(ExpansionCheck.READY, GardenExpansion.check(15, 20, 20_000))
        assertEquals(ExpansionCheck.AT_MAX_SIZE, GardenExpansion.check(17, 20, 100_000))
        assertEquals(15, GardenExpansion.nextStage(13)?.size)
    }

    @Test
    fun `the workshop's board shows the level of the very next size`() {
        assertEquals(14, ExpansionOffer.of(13, 10, 0).level)
        assertEquals(20, ExpansionOffer.of(15, 10, 0).level)
        assertEquals(ExpansionCheck.AT_MAX_SIZE, ExpansionOffer.of(17, 20, 0).check)
    }

    @Test
    fun `everything moves with the ring, its joins and choices kept, and a price is paid each time`() = runBlocking {
        val glass = listOf(at(11, 11, GridObject.GREENHOUSE_OBJECT_ID), at(12, 11, GridObject.GREENHOUSE_OBJECT_ID)).map { it.copy(doorSide = GreenhouseSide.WEST) }
        val fence = listOf(at(1, 1, RunKind.WOOD_FENCE.objectId), at(2, 1, RunKind.WOOD_FENCE.objectId))
        val river = at(5, 0, GridObject.RIVER_OBJECT_ID)
        val repo = FakeGardenRepository().apply {
            profile = rich(20, 35_000)
            progress = GardenProgress(gardenSize = 13)
            (glass + fence + river).forEach { put(it) }
        }
        val expand = ExpandGardenUseCase(repo)
        assertEquals(ActionResult.SUCCESS, expand())
        assertEquals(15, repo.getProgress()?.gardenSize)
        assertEquals(25_000L, repo.profile?.coins)
        assertEquals(ActionResult.SUCCESS, expand())
        assertEquals(17, repo.getProgress()?.gardenSize)
        assertEquals(5_000L, repo.profile?.coins)
        assertEquals(ActionResult.GARDEN_AT_MAX_SIZE, expand())
        assertEquals(17, repo.getProgress()?.gardenSize)

        val after = repo.getGridObjects()
        assertEquals(5, after.size)
        assertEquals(GridCell(7, 2), after.first { it.instanceId == river.instanceId }.let { GridCell(it.gridX, it.gridY) })
        val cells = after.filter { it.isGreenhouse }.mapTo(HashSet()) { GridCell(it.gridX, it.gridY) }
        assertEquals(setOf(GridCell(13, 13), GridCell(14, 13)), cells)
        val map = GardenMap.allGrass(17)
        val group = GreenhouseDoor.groups(cells).single()
        assertEquals(GreenhouseSide.WEST, GreenhouseDoor.doorOf(group, GreenhouseDoor.chosenIn(group, after), map::isInside).side)
    }

    @Test
    fun `a garden of 13 at Lv_14 or more hears of the next size only, and once`() {
        fun letter(size: Int, level: Int, sent: List<Letter> = emptyList()) = PostOffice.gardenCanGrow(size, level, sent, Season.SPRING, 0L)
        assertNull(letter(13, 13))
        val first = letter(13, 14)
        assertNotNull(first)
        assertNull(letter(13, 14, listOf(first!!)))
        // At Lv.20 too it is the same letter: the next one waits for the garden to be 15 x 15.
        assertEquals(first.letterId, letter(13, 20)?.letterId)
        val second = letter(15, 20, listOf(first))
        assertNotNull(second)
        assertTrue(second!!.letterId != first.letterId)
        assertNull(letter(17, 20))
    }

    @Test
    fun `the whole garden fits on screen at the widest zoom, and the closest zoom is as close as ever`() {
        val viewport = Size(1080f, 2200f)
        for (size in listOf(13, 15, 17)) {
            val metrics = IsometricMetrics(tileWidth = 336f, tileHeight = 168f, heightStep = 42f, gridSize = size)
            val widest = GardenCamera.fitting(viewport, metrics)
            assertTrue(widest.minScale * metrics.gridBounds.width <= viewport.width)
            assertTrue(widest.minScale * metrics.gridBounds.height <= viewport.height)
            val start = GardenCamera.starting(viewport, metrics)
            assertEquals(GardenCamera.START_SCALE, start.scale, 0f)
            val closest = start.transformedBy(Offset0, Offset0, 100f, viewport, metrics)
            assertEquals(GardenCamera.MAX_SCALE, closest.scale, 0f)
        }
    }

    private val Offset0 = androidx.compose.ui.geometry.Offset.Zero
}
