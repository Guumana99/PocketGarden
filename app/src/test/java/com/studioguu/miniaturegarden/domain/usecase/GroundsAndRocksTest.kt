package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.Footprint
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.LawnGrowth
import com.studioguu.miniaturegarden.domain.model.PlacementCheck
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.ui.render.SoilJoins
import com.studioguu.miniaturegarden.ui.render.SoilTexture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the grounds meet, and the rocks that stand on a lawn (device feedback of 11/1). */
class GroundsAndRocksTest {
    private val map = GardenMap.allGrass()
    private val rocks = GardenItem.ROCK_KINDS.map { it.objectId }

    private fun at(x: Int, y: Int, objectId: String) =
        GridObject(instanceId = "${objectId}_${x}_$y", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = 0L)

    // --- Beds and what lies beside them ---

    @Test
    fun `a bed runs right up to whatever is laid on the ground beside it`() {
        val grid = GardenGrid(
            map,
            listOf(
                at(1, 0, LawnGrowth.OBJECT_ID),
                at(2, 0, GridObject.COBBLESTONE_OBJECT_ID),
                at(3, 0, GridObject.WOOD_DECK_OBJECT_ID),
                at(4, 0, "ground_pebble"),
                at(5, 0, "ground_flowers"),
                at(6, 0, GridObject.TILLED_SOIL_OBJECT_ID),
                at(7, 0, GridObject.LOOSE_SOIL_OBJECT_ID),
            ),
        )
        for (x in 1..7) assertTrue("x = $x", grid.meetsBed(x, 0))
    }

    @Test
    fun `open earth, water and the garden's edge leave a bed its own rim`() {
        val grid = GardenGrid(map, listOf(at(1, 0, GridObject.RIVER_OBJECT_ID), at(2, 0, GridObject.POND_OBJECT_ID)))
        assertFalse(grid.meetsBed(0, 0))
        assertFalse(grid.meetsBed(1, 0))
        assertFalse(grid.meetsBed(2, 0))
        assertFalse(grid.meetsBed(-1, 0))
        assertFalse(grid.meetsBed(0, map.size))
    }

    @Test
    fun `what stands on a lawn does not leave a gap beside the bed`() {
        val grid = GardenGrid(map, listOf(at(1, 0, LawnGrowth.OBJECT_ID), at(1, 0, "deco_rock"), at(2, 0, GridObject.COBBLESTONE_OBJECT_ID), at(2, 0, "deco_bench")))
        assertTrue(grid.meetsBed(1, 0))
        assertTrue(grid.meetsBed(2, 0))
    }

    @Test
    fun `the side of a bed beside a lawn lies on the edge of its cell, with no earth spilled over it`() {
        val grid = GardenGrid(map, listOf(at(4, 3, LawnGrowth.OBJECT_ID), at(3, 3, GridObject.LOOSE_SOIL_OBJECT_ID)))
        val joins = SoilJoins.at(3, 3, grid::meetsBed)
        assertEquals(SoilJoins(east = true), joins)
        assertTrue(SoilTexture.outline(3, 3, joins).subList(5, 10).all { it.first == 4f })
        assertTrue(SoilTexture.drift(3, 3, joins).none { it.x > 4f })
    }

    // --- Rocks ---

    @Test
    fun `every rock may stand on a lawn, and a lawn be sown under it`() {
        assertEquals(5, rocks.size)
        for (rock in rocks) {
            val lawn = GardenGrid(map, listOf(at(3, 3, LawnGrowth.OBJECT_ID)))
            assertEquals(rock, PlacementCheck.OK, lawn.checkPlacement(Footprint.SINGLE, 3, 3, PlacementKind.OBJECT, rock))
            assertEquals(rock, ActionResult.SUCCESS, lawn.fits(rock, 3, 3))
            val standing = GardenGrid(map, listOf(at(3, 3, rock)))
            assertEquals(rock, PlacementCheck.OK, standing.checkPlacement(Footprint.SINGLE, 3, 3, PlacementKind.OBJECT, LawnGrowth.OBJECT_ID))
        }
    }

    @Test
    fun `a rock on a lawn is what stands there, with the lawn under it`() {
        val grid = GardenGrid(map, listOf(at(3, 3, LawnGrowth.OBJECT_ID), at(3, 3, "deco_rock")))
        assertEquals("deco_rock", grid.occupantAt(3, 3)?.objectId)
        assertEquals("deco_rock", grid.objectAt(3, 3)?.objectId)
        assertNotNull(grid.lawnAt(3, 3))
    }

    @Test
    fun `two rocks do not share a cell, nor a bench a lawn`() {
        val rock = GardenGrid(map, listOf(at(3, 3, LawnGrowth.OBJECT_ID), at(3, 3, "deco_rock")))
        assertEquals(PlacementCheck.OCCUPIED, rock.checkPlacement(Footprint.SINGLE, 3, 3, PlacementKind.OBJECT, "deco_rock_flat"))
        val lawn = GardenGrid(map, listOf(at(3, 3, LawnGrowth.OBJECT_ID)))
        assertEquals(PlacementCheck.OCCUPIED, lawn.checkPlacement(Footprint.SINGLE, 3, 3, PlacementKind.OBJECT, "deco_bench"))
    }

    @Test
    fun `a rock put on a lawn leaves the lawn behind when it goes`() = runBlocking {
        val repo = FakeGardenRepository().apply {
            profile = UserProfile(coins = 100)
            put(at(3, 3, LawnGrowth.OBJECT_ID))
            putSlot(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 1))
        }
        assertEquals(ActionResult.SUCCESS, PlaceItemUseCase(repo, map, RecordCollectionUseCase(repo))(0, 3, 3, 0L))
        val rock = repo.objects.values.first { it.objectId == "deco_rock" }
        assertEquals(setOf("deco_rock", LawnGrowth.OBJECT_ID), repo.getGridObjects().map { it.objectId }.toSet())

        // As read back from the save: the two stand apart, each with its own record.
        val saved = GardenGrid(map, repo.getGridObjects())
        assertEquals(rock.instanceId, saved.occupantAt(3, 3)?.instanceId)
        assertNotNull(saved.lawnAt(3, 3))

        assertEquals(ActionResult.SUCCESS, RemoveObjectUseCase(repo)(rock.instanceId))
        val left = GardenGrid(map, repo.getGridObjects())
        assertNotNull(left.lawnAt(3, 3))
        assertEquals(LawnGrowth.OBJECT_ID, left.occupantAt(3, 3)?.objectId)
    }

    @Test
    fun `a rock can be moved from one lawn to another and leaves both as they were`() = runBlocking {
        val repo = FakeGardenRepository().apply {
            put(at(3, 3, LawnGrowth.OBJECT_ID))
            put(at(5, 5, LawnGrowth.OBJECT_ID))
            put(at(3, 3, "deco_rock"))
        }
        val move = MoveObjectUseCase(repo, map, CalculateOfflineProgressUseCase())
        assertEquals(ActionResult.SUCCESS, move("deco_rock_3_3", 5, 5))
        val grid = GardenGrid(map, repo.getGridObjects())
        assertEquals("deco_rock", grid.occupantAt(5, 5)?.objectId)
        assertNotNull(grid.lawnAt(3, 3))
        assertNotNull(grid.lawnAt(5, 5))
        assertNull(grid.occupantAt(4, 4))
    }
}
