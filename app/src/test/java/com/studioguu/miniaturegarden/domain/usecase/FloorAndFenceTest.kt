package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.FencePart
import com.studioguu.miniaturegarden.domain.model.Footprint
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.PlacementCheck
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.render.RiverAutotile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorAndFenceTest {

    private val t0 = 1_700_000_000_000L

    private val repo = FakeGardenRepository().apply { profile = UserProfile(coins = 100) }
    private val calc = CalculateOfflineProgressUseCase()
    private val place = PlaceItemUseCase(repo, GardenMap.DEFAULT, RecordCollectionUseCase(repo))
    private val remove = RemoveObjectUseCase(repo)

    private fun obj(id: String, x: Int, y: Int, objectId: String) =
        GridObject(instanceId = id, gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = t0)

    private fun path(id: String, x: Int, y: Int) = obj(id, x, y, GridObject.COBBLESTONE_OBJECT_ID)
    private fun river(id: String, x: Int, y: Int) = obj(id, x, y, GridObject.RIVER_OBJECT_ID)
    private fun fence(id: String, x: Int, y: Int) = obj(id, x, y, GridObject.FENCE_OBJECT_ID)
    private fun tulip(id: String, x: Int, y: Int) = obj(id, x, y, PlantSpecies.TULIP.objectId)

    private fun gridOf(vararg objects: GridObject) = GardenGrid(GardenMap.DEFAULT, objects.toList())

    // --- Kinds ---

    @Test
    fun `object ids sort into the three placement layers`() {
        assertEquals(PlacementKind.TERRAIN, PlacementKind.forObjectId(GridObject.RIVER_OBJECT_ID))
        assertEquals(PlacementKind.FLOOR, PlacementKind.forObjectId(GridObject.COBBLESTONE_OBJECT_ID))
        assertEquals(PlacementKind.OBJECT, PlacementKind.forObjectId(GridObject.FENCE_OBJECT_ID))
        assertEquals(PlacementKind.OBJECT, PlacementKind.forObjectId("plant_tulip"))
        assertEquals(PlacementKind.OBJECT, PlacementKind.forObjectId("struct_hut"))

        assertTrue(path("p", 0, 0).isFloor)
        assertFalse(path("p", 0, 0).isTerrain || path("p", 0, 0).isPlant)
        assertFalse(fence("f", 0, 0).isFloor || fence("f", 0, 0).isTerrain || fence("f", 0, 0).isPlant)
        assertEquals(Footprint.SINGLE, fence("f", 0, 0).footprint)
    }

    // --- The two layers of a cell ---

    @Test
    fun `a cell holds paving and something standing on it at the same time`() {
        val paving = path("p", 3, 3)
        val post = fence("f", 3, 3)
        val grid = gridOf(paving, post)

        assertSame(paving, grid.floorAt(3, 3))
        assertSame(post, grid.occupantAt(3, 3))
        // A tap means the thing on top ...
        assertSame(post, grid.objectAt(3, 3))
        // ... and the paving itself once nothing stands on it.
        assertSame(paving, gridOf(paving).objectAt(3, 3))
        assertNull(gridOf(paving).occupantAt(3, 3))
    }

    @Test
    fun `anything can be stood on paving`() {
        val grid = gridOf(path("p", 3, 3), path("q", 4, 3), path("r", 3, 4), path("s", 4, 4))

        assertEquals(PlacementCheck.OK, grid.checkPlacement(Footprint.SINGLE, 3, 3))
        // A whole hut on a paved square.
        assertEquals(PlacementCheck.OK, grid.checkPlacement(Footprint(2, 2), 3, 3))
    }

    @Test
    fun `paving can be slipped under something already standing there`() {
        val grid = gridOf(tulip("t", 2, 2))
        assertEquals(PlacementCheck.OK, grid.checkPlacement(Footprint.SINGLE, 2, 2, PlacementKind.FLOOR))
    }

    @Test
    fun `one cell takes one paving and one occupant`() {
        val grid = gridOf(path("p", 3, 3), fence("f", 3, 3))

        assertEquals(PlacementCheck.OCCUPIED, grid.checkPlacement(Footprint.SINGLE, 3, 3, PlacementKind.FLOOR))
        assertEquals(PlacementCheck.OCCUPIED, grid.checkPlacement(Footprint.SINGLE, 3, 3, PlacementKind.OBJECT))
    }

    @Test
    fun `river and paving never share a cell`() {
        val grid = gridOf(river("r", 1, 1), path("p", 5, 5))

        // No paving on water ...
        assertEquals(PlacementCheck.NOT_BUILDABLE, grid.checkPlacement(Footprint.SINGLE, 1, 1, PlacementKind.FLOOR))
        // ... and no water through a paved cell.
        assertEquals(PlacementCheck.OCCUPIED, grid.checkPlacement(Footprint.SINGLE, 5, 5, PlacementKind.TERRAIN))
        assertEquals(PlacementCheck.OK, grid.checkPlacement(Footprint.SINGLE, 5, 6, PlacementKind.TERRAIN))
    }

    // --- Through the game actions ---

    @Test
    fun `a paved path with a fence on it, and no flowers in the paving`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.FLOOR_COBBLESTONE.itemId, 3))
        repo.putSlot(InventorySlot(1, GardenItem.STRUCT_FENCE.itemId, 1))
        repo.putSlot(InventorySlot(2, GardenItem.SEED_TULIP.itemId, 1))

        for (x in 0..2) assertEquals(ActionResult.SUCCESS, place(0, x, 4, t0))
        assertEquals(ActionResult.SUCCESS, place(1, 0, 4, t0)) // fence on the first stone
        // A seed needs tilled soil, and a paved cell cannot be tilled.
        assertEquals(ActionResult.NEEDS_TILLED_SOIL, place(2, 2, 4, t0))
        assertEquals(ActionResult.TILE_OCCUPIED, HoeUseCase(repo, GardenMap.DEFAULT)(2, 4, t0))

        val grid = GardenGrid(GardenMap.DEFAULT, repo.getGridObjects())
        assertEquals(4, repo.objects.size)
        assertEquals(GridObject.FENCE_OBJECT_ID, grid.objectAt(0, 4)?.objectId)
        assertEquals(GridObject.COBBLESTONE_OBJECT_ID, grid.objectAt(1, 4)?.objectId)
        assertEquals(GridObject.COBBLESTONE_OBJECT_ID, grid.objectAt(2, 4)?.objectId)
        assertEquals(1, repo.slots.getValue(2).amount) // the seed was not used up
    }

    @Test
    fun `refused placements use up nothing`() = runBlocking {
        repo.put(path("p", 3, 3))
        repo.put(river("r", 6, 6))
        repo.put(fence("f", 1, 1))
        repo.putSlot(InventorySlot(0, GardenItem.FLOOR_COBBLESTONE.itemId, 1))
        repo.putSlot(InventorySlot(1, GardenItem.TILE_RIVER.itemId, 1))
        repo.putSlot(InventorySlot(2, GardenItem.STRUCT_FENCE.itemId, 1))

        assertEquals(ActionResult.TILE_OCCUPIED, place(0, 3, 3, t0))      // paving on paving
        assertEquals(ActionResult.TILE_NOT_BUILDABLE, place(0, 6, 6, t0)) // paving on river
        assertEquals(ActionResult.TILE_OCCUPIED, place(1, 3, 3, t0))      // river through paving
        assertEquals(ActionResult.TILE_OCCUPIED, place(2, 1, 1, t0))      // fence on fence
        assertEquals(ActionResult.TILE_NOT_BUILDABLE, place(2, 6, 6, t0)) // fence in river

        assertEquals(3, repo.objects.size)
        assertTrue((0..2).all { repo.slots.getValue(it).amount == 1 })
    }

    @Test
    fun `removing what stands on paving leaves the paving, and the paving can then be lifted`() = runBlocking {
        repo.put(path("p", 3, 3))
        repo.put(fence("f", 3, 3))

        val onTop = GardenGrid(GardenMap.DEFAULT, repo.getGridObjects()).objectAt(3, 3)!!
        assertEquals(ActionResult.SUCCESS, remove(onTop.instanceId))
        val left = GardenGrid(GardenMap.DEFAULT, repo.getGridObjects()).objectAt(3, 3)!!
        assertEquals(GridObject.COBBLESTONE_OBJECT_ID, left.objectId)

        assertEquals(ActionResult.SUCCESS, remove(left.instanceId))
        assertTrue(repo.objects.isEmpty())
    }

    @Test
    fun `paving and fences are not plants`() {
        for (thing in listOf(path("p", 0, 0), fence("f", 1, 0))) {
            val later = calc(thing.copy(moistureLevel = 0.1f), t0 + 100 * 3_600_000L, WeatherType.SUNNY)
            assertEquals(0.1f, later.moistureLevel, 0f)
            assertEquals(0f, later.stressRate, 0f)
            assertEquals(0f, later.growthProgress, 0f)
        }
    }

    // --- Shop and encyclopedia ---

    @Test
    fun `the new items are on the right shelves and for sale from the start`() {
        assertEquals(ItemCategory.DECORATION, GardenItem.FLOOR_COBBLESTONE.category)
        assertEquals(ItemCategory.STRUCTURE, GardenItem.STRUCT_FENCE.category)
        val newPlayer = UserProfile(coins = UserProfile.STARTING_COINS)
        for (item in listOf(GardenItem.FLOOR_COBBLESTONE, GardenItem.STRUCT_FENCE)) {
            assertTrue(item.isUnlockedFor(newPlayer.stats))
            assertTrue(item.price <= newPlayer.coins)
            assertEquals(0, item.placeXp)
        }
    }


    /** Finishes and unveils the building just placed: when it earns, since #50. */
    private suspend fun finishAndUnveil(objectId: String, at: Long = t0) {
        val site = repo.objects.values.first { it.objectId == objectId && it.isWorking }
        repo.put(site.copy(workKind = com.studioguu.miniaturegarden.domain.model.WorkKind.READY))
        UnveilBuildingUseCase(repo, RecordCollectionUseCase(repo))(site.instanceId, at)
    }

    @Test
    fun `first placements make the new encyclopedia pages ready`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.FLOOR_COBBLESTONE.itemId, 1))
        repo.putSlot(InventorySlot(1, GardenItem.STRUCT_FENCE.itemId, 1))

        place(0, 0, 0, t0)
        place(1, 0, 0, t0)

        assertEquals(1, repo.collection[GridObject.COBBLESTONE_OBJECT_ID]?.count)
        // A fence takes a moment to put up: its page comes when it is unveiled (#50).
        assertEquals(null, repo.collection[GridObject.FENCE_OBJECT_ID])
        finishAndUnveil(GridObject.FENCE_OBJECT_ID)
        assertEquals(1, repo.collection[GridObject.FENCE_OBJECT_ID]?.count)
        assertEquals(100L, repo.profile?.coins) // the coins come when the pages are filled in
    }

    // --- Fences ---

    @Test
    fun `a new fence is stored as a plain fence, whatever stands next to it`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_FENCE.itemId, 3))

        for (x in 3..5) assertEquals(ActionResult.SUCCESS, place(0, x, 3, t0))

        // How fences join is worked out when they are drawn; nothing about it is stored.
        assertEquals(3, repo.objects.size)
        assertTrue(repo.objects.values.all { it.objectId == FencePart.SECTION.objectId && !it.isFlipped })
        assertEquals(100L, repo.profile?.coins)
    }

    @Test
    fun `there is one fence item, and it is what every fence id belongs to`() {
        val fenceItems = GardenItem.entries.filter { it.objectId.startsWith(GridObject.FENCE_OBJECT_ID) }
        assertEquals(listOf(GardenItem.STRUCT_FENCE), fenceItems)
        for (part in FencePart.entries) {
            val stored = GridObject(instanceId = part.name, gridX = 0, gridY = 0, objectId = part.objectId, lastUpdatedTimestamp = t0)
            assertEquals(part.name, GardenItem.STRUCT_FENCE, StoreObjectUseCase.itemFor(stored))
        }
    }

    @Test
    fun `every fence part is a fence, takes one cell and stands on paving like any object`() {
        for (part in FencePart.entries) {
            val fence = GridObject(instanceId = part.name, gridX = 0, gridY = 0, objectId = part.objectId, lastUpdatedTimestamp = t0)
            assertTrue(part.name, fence.isFence)
            assertFalse(part.name, fence.isPlant || fence.isFloor || fence.isTerrain)
            assertEquals(part.name, Footprint.SINGLE, fence.footprint)
            assertEquals(part.name, PlacementKind.OBJECT, PlacementKind.forObjectId(part.objectId))
            assertTrue(part.name, part.objectId in TileSetDefinition.objectIds)
        }
        assertEquals(3, FencePart.entries.map { it.objectId }.toSet().size)
        assertFalse(GridObject(instanceId = "r", gridX = 0, gridY = 0, objectId = "deco_rock", lastUpdatedTimestamp = t0).isFence)
    }

    // --- River animation frames ---

    // --- River autotiling ---

    private fun maskOf(x: Int, y: Int, vararg rivers: Pair<Int, Int>): Int =
        RiverAutotile.maskAt(x, y) { cx, cy -> (cx to cy) in rivers }

    @Test
    fun `each neighbour sets its own bit`() {
        assertEquals(0, maskOf(4, 4))
        assertEquals(RiverAutotile.NORTH, maskOf(4, 4, 4 to 3))
        assertEquals(RiverAutotile.EAST, maskOf(4, 4, 5 to 4))
        assertEquals(RiverAutotile.SOUTH, maskOf(4, 4, 4 to 5))
        assertEquals(RiverAutotile.WEST, maskOf(4, 4, 3 to 4))
        assertEquals(listOf(1, 2, 4, 8), listOf(RiverAutotile.NORTH, RiverAutotile.EAST, RiverAutotile.SOUTH, RiverAutotile.WEST))
    }

    @Test
    fun `neighbours combine into the sixteen masks and diagonals do not count`() {
        assertEquals(5, maskOf(4, 4, 4 to 3, 4 to 5))                    // straight north-south
        assertEquals(10, maskOf(4, 4, 5 to 4, 3 to 4))                   // straight east-west
        assertEquals(6, maskOf(4, 4, 5 to 4, 4 to 5))                    // corner east + south
        assertEquals(15, maskOf(4, 4, 4 to 3, 5 to 4, 4 to 5, 3 to 4))   // crossing
        assertEquals(0, maskOf(4, 4, 3 to 3, 5 to 3, 3 to 5, 5 to 5))    // diagonals only
        // The tile itself being river makes no difference to its own mask.
        assertEquals(RiverAutotile.EAST, maskOf(4, 4, 4 to 4, 5 to 4))
    }

    @Test
    fun `a bend in a river gets the matching pieces`() {
        // (2,2) - (3,2) - (3,3): west end, corner, south end.
        val river = arrayOf(2 to 2, 3 to 2, 3 to 3)
        assertEquals(RiverAutotile.EAST, maskOf(2, 2, *river))
        assertEquals(RiverAutotile.WEST or RiverAutotile.SOUTH, maskOf(3, 2, *river))
        assertEquals(RiverAutotile.NORTH, maskOf(3, 3, *river))
    }
}
