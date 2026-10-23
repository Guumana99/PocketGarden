package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GardenExpansion
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.LetterKind
import com.studioguu.miniaturegarden.domain.model.LevelTable
import com.studioguu.miniaturegarden.domain.model.PostOffice
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.ui.render.IsometricTransform
import com.studioguu.miniaturegarden.ui.render.TapTargets
import com.studioguu.miniaturegarden.ui.render.TileCoord
import com.studioguu.miniaturegarden.ui.render.TilePicker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The garden says when it can grow (#45): a bubble over the cottage, and a letter once per size. */
class ExpansionGuideTest {

    private val t0 = 1_700_000_000_000L

    private fun cottage(work: WorkKind = WorkKind.NONE, x: Int = 3, y: Int = 3) = GridObject(
        instanceId = "cottage", gridX = x, gridY = y, objectId = StructureData.COTTAGE.objectId,
        lastUpdatedTimestamp = t0, workKind = work,
    )

    // --- What to point at ---

    @Test
    fun `nothing is pointed at until a finished cottage stands, and then the cottage is`() {
        assertNull(GardenExpansion.growableFrom(9, emptyList(), 99))
        assertNull(GardenExpansion.growableFrom(9, listOf(cottage(WorkKind.BUILDING)), 99))
        // Under wraps it is still the "✨ ！" that asks to be tapped.
        assertNull(GardenExpansion.growableFrom(9, listOf(cottage(WorkKind.READY)), 99))
        assertEquals("cottage", GardenExpansion.growableFrom(9, listOf(cottage()), 6)?.instanceId)
    }

    @Test
    fun `the second ring waits for its level, and a full-grown garden points at nothing`() {
        assertNull(GardenExpansion.growableFrom(11, listOf(cottage()), 7))
        assertEquals("cottage", GardenExpansion.growableFrom(11, listOf(cottage()), 8)?.instanceId)
        assertNull(GardenExpansion.growableFrom(13, listOf(cottage()), 99))
    }

    // --- The letter ---

    @Test
    fun `a letter comes when the garden can grow, once for each size`() {
        val first = PostOffice.gardenCanGrow(9, listOf(cottage()), 6, emptyList(), Season.SPRING, t0)
        assertNotNull(first)
        assertEquals(LetterKind.GARDEN_CAN_GROW, first!!.kind)
        assertEquals(11, first.growSize)
        // Not again for the same size.
        assertNull(PostOffice.gardenCanGrow(9, listOf(cottage()), 6, listOf(first), Season.SPRING, t0 + 1))
        // Grown to 11, there is nothing to say until Lv.8; then a letter of its own.
        assertNull(PostOffice.gardenCanGrow(11, listOf(cottage()), 7, listOf(first), Season.SPRING, t0 + 2))
        val second = PostOffice.gardenCanGrow(11, listOf(cottage()), 8, listOf(first), Season.SPRING, t0 + 3)
        assertEquals(13, second?.growSize)
        assertNotEquals(first.letterId, second!!.letterId)
        assertNull(PostOffice.gardenCanGrow(13, listOf(cottage()), 99, listOf(first, second), Season.SPRING, t0 + 4))
    }

    @Test
    fun `no letter while the garden cannot grow`() {
        assertNull(PostOffice.gardenCanGrow(9, emptyList(), 99, emptyList(), Season.SPRING, t0))
        assertNull(PostOffice.gardenCanGrow(9, listOf(cottage(WorkKind.READY)), 99, emptyList(), Season.SPRING, t0))
    }

    @Test
    fun `other letters do not count as having said it`() {
        val tip = Letter.newStock(5, 6, Season.SPRING, t0)
        assertNotNull(PostOffice.gardenCanGrow(9, listOf(cottage()), 6, listOf(tip), Season.SPRING, t0))
        assertNull(Letter.newStock(5, 6, Season.SPRING, t0).growSize)
    }

    @Test
    fun `the post office posts it once, from what is stored`() = runBlocking {
        val repo = FakeGardenRepository().apply {
            profile = UserProfile(coins = 0, playerLevel = 6, currentXp = LevelTable.xpForLevel(6))
            // A garden saved at the old 9 x 9 start, which grows to 11 first.
            progress = GardenProgress(gardenSize = GridObject.LEGACY_GRID_SIZE)
            put(cottage())
        }
        val post = PostLettersUseCase(repo)
        assertEquals(11, post.gardenCanGrowIfDue(t0)?.growSize)
        assertNull(post.gardenCanGrowIfDue(t0 + 1_000))
        assertEquals(1, repo.letters.values.count { it.kind == LetterKind.GARDEN_CAN_GROW })
    }

    @Test
    fun `growing really happens, from the cottage's panel`() = runBlocking {
        val repo = FakeGardenRepository().apply {
            profile = UserProfile(coins = 0, playerLevel = 6, currentXp = LevelTable.xpForLevel(6))
            // A garden saved at the old 9 x 9 start, which grows to 11 first.
            progress = GardenProgress(gardenSize = GridObject.LEGACY_GRID_SIZE)
            put(cottage())
        }
        assertEquals(ActionResult.SUCCESS, ExpandGardenUseCase(repo)())
        assertEquals(11, repo.progress?.gardenSize)
        // The cottage moved one along with everything else, and there is no more to grow until Lv.8.
        assertEquals(4 to 4, repo.objects.getValue("cottage").let { it.gridX to it.gridY })
        assertNull(GardenExpansion.growableFrom(11, repo.objects.values.toList(), 6))
    }

    // --- The bubble ---

    @Test
    fun `the bubble over the cottage is tapped through to the cottage`() {
        val transform = IsometricTransform(tileWidth = 128f, tileHeight = 64f, heightStep = 16f, offsetX = 600f, offsetY = 300f)
        val building = cottage()
        val bubble = TapTargets.bubbleCentre(building, transform, growable = true)!!
        // Above the roof: read on the ground alone it is far behind the cottage.
        val ground = TilePicker.pick(bubble, transform)
        assertTrue(ground == null || ground.x < 3 || ground.y < 3)
        val grid = GardenGrid(GardenMap.allGrass(9), listOf(building))
        assertEquals(TileCoord(3, 3), TapTargets.resolve(bubble, transform, grid, listOf(building), editing = false, growableId = "cottage"))
        // Without the bubble there, the same tap is the cottage's roof, which picks it too, but
        // the bubble itself only exists while the garden can grow.
        assertNull(TapTargets.bubbleCentre(building, transform))
    }

    @Test
    fun `the garden screen hands the bubble to the garden`() {
        val main = listOf(java.io.File("src/main"), java.io.File("app/src/main")).first { it.isDirectory }
        val screen = java.io.File(main, "java/com/studioguu/miniaturegarden/ui/GardenScreen.kt").readText()
        assertTrue(screen.contains("growableId = if (cameraMode) null else growableBuilding?.instanceId"))
    }
}
