package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.Footprint
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoeTest {

    private val t0 = 1_700_000_000_000L
    private val sunny = WeatherType.SUNNY

    private val repo = FakeGardenRepository().apply { profile = UserProfile(coins = 100) }
    private val record = RecordCollectionUseCase(repo)
    private val hoe = HoeUseCase(repo, GardenMap.DEFAULT)
    private val place = PlaceItemUseCase(repo, GardenMap.DEFAULT, record)
    private val harvest = HarvestPlantUseCase(repo, CalculateOfflineProgressUseCase(), record)
    private val remove = RemoveObjectUseCase(repo)

    private fun obj(id: String, x: Int, y: Int, objectId: String) =
        GridObject(instanceId = id, gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = t0)

    private fun grid() = GardenGrid(GardenMap.DEFAULT, repo.objects.values.toList())

    // --- Tilling ---

    @Test
    fun `the hoe turns open ground into tilled soil, for free`() = runBlocking {
        assertFalse(grid().isTilled(3, 4))

        assertEquals(ActionResult.SUCCESS, hoe.till(3, 4, t0))

        val soil = repo.objects.values.single()
        assertEquals(GridObject.TILLED_SOIL_OBJECT_ID, soil.objectId)
        assertEquals(3 to 4, soil.gridX to soil.gridY)
        assertEquals(t0, soil.lastUpdatedTimestamp)
        assertTrue(grid().isTilled(3, 4))
        assertFalse(grid().isTilled(4, 4))
        // A tool, not an item: no coins, no XP, no profile save, nothing taken from the bag.
        assertEquals(100L, repo.profile?.coins)
        assertEquals(0, repo.profileSaves)
    }

    @Test
    fun `tilled soil is ground, not a plant or a thing in the way`() {
        val soil = obj("s", 0, 0, GridObject.TILLED_SOIL_OBJECT_ID)
        assertTrue(soil.isTilledSoil)
        assertTrue(soil.isFloor)
        assertFalse(soil.isPlant || soil.isTerrain || soil.isFence)
        assertEquals(Footprint.SINGLE, soil.footprint)
        assertEquals(PlacementKind.FLOOR, PlacementKind.forObjectId(soil.objectId))
        assertTrue(soil.objectId in TileSetDefinition.objectIds)
        assertFalse(obj("c", 0, 0, GridObject.COBBLESTONE_OBJECT_ID).isTilledSoil)
    }

    @Test
    fun `only empty ground can be tilled`() = runBlocking {
        repo.put(obj("river", 1, 1, GridObject.RIVER_OBJECT_ID))
        repo.put(obj("stone", 2, 2, GridObject.COBBLESTONE_OBJECT_ID))
        repo.put(obj("rock", 3, 3, "deco_rock"))
        repo.put(obj("hut", 5, 5, "struct_hut"))
        val before = repo.objects.size

        assertEquals(ActionResult.TILE_NOT_BUILDABLE, hoe.till(1, 1, t0))
        assertEquals(ActionResult.TILE_OCCUPIED, hoe.till(2, 2, t0))
        assertEquals(ActionResult.TILE_OCCUPIED, hoe.till(3, 3, t0))
        assertEquals(ActionResult.TILE_OCCUPIED, hoe.till(6, 6, t0)) // any cell of the hut
        assertEquals(ActionResult.OUT_OF_BOUNDS, hoe.till(GridObject.GRID_SIZE, 0, t0))
        assertEquals(ActionResult.OUT_OF_BOUNDS, hoe.till(0, -1, t0))

        assertEquals(before, repo.objects.size)
    }

    @Test
    fun `tilling the same cell twice changes nothing the second time`() = runBlocking {
        assertEquals(ActionResult.SUCCESS, hoe.till(4, 4, t0))
        assertEquals(ActionResult.TILE_OCCUPIED, hoe.till(4, 4, t0 + 1))
        assertEquals(1, repo.objects.size)
    }

    @Test
    fun `the river on the map cannot be tilled`() = runBlocking {
        val riverMap = GardenMap.parse(*Array(9) { "......~.." })
        val hoeOnRiverMap = HoeUseCase(repo, riverMap)

        assertEquals(ActionResult.TILE_NOT_BUILDABLE, hoeOnRiverMap(6, 2, t0))
        assertEquals(ActionResult.SUCCESS, hoeOnRiverMap.till(5, 2, t0))
    }

    // --- Planting ---

    @Test
    fun `seeds and seedlings go into tilled soil and nowhere else`() = runBlocking {
        val seeds = GardenItem.entries.filter { it.category == ItemCategory.SEED }
        assertEquals(14, seeds.size)
        seeds.forEachIndexed { slot, seed -> repo.putSlot(InventorySlot(slot, seed.itemId, 2)) }
        // Two rows of tilled soil along the top, nine to a row.
        seeds.indices.forEach { hoe.till(it % 9, it / 9, t0) }

        seeds.indices.forEach { slot ->
            assertEquals(ActionResult.NEEDS_TILLED_SOIL, place(slot, slot % 9, 5 + slot / 9, t0)) // open ground
            assertEquals(2, repo.slots.getValue(slot).amount)                                      // nothing used up
            assertEquals(ActionResult.SUCCESS, place(slot, slot % 9, slot / 9, t0))               // tilled
            assertEquals(1, repo.slots.getValue(slot).amount)
        }

        assertEquals(PlantSpecies.entries.map { it.objectId }.toSet(), repo.plants.map { it.objectId }.toSet())
        assertTrue(repo.plants.all { it.gridY <= 1 })
    }

    @Test
    fun `a plant stands on its soil and the two share the cell`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TULIP.itemId, 2))
        hoe.till(2, 2, t0)
        place(0, 2, 2, t0)

        assertEquals(PlantSpecies.TULIP.objectId, grid().objectAt(2, 2)?.objectId)
        assertTrue(grid().isTilled(2, 2))
        // One plant per bed.
        assertEquals(ActionResult.TILE_OCCUPIED, place(0, 2, 2, t0))
        assertEquals(1, repo.plants.size)
    }

    @Test
    fun `things that are not seeds do not need tilled soil, and can stand on it`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 2))
        repo.putSlot(InventorySlot(1, GardenItem.STRUCT_FENCE.itemId, 1))
        repo.putSlot(InventorySlot(2, GardenItem.GROUND_GRASS.itemId, 1))
        hoe.till(1, 1, t0)

        assertEquals(ActionResult.SUCCESS, place(0, 0, 0, t0)) // open ground
        assertEquals(ActionResult.SUCCESS, place(0, 1, 1, t0)) // on a bed
        assertEquals(ActionResult.SUCCESS, place(1, 3, 3, t0))
        assertEquals(ActionResult.SUCCESS, place(2, 4, 4, t0))
    }

    @Test
    fun `paving and river cannot be laid over a bed`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.FLOOR_COBBLESTONE.itemId, 1))
        repo.putSlot(InventorySlot(1, GardenItem.TILE_RIVER.itemId, 1))
        hoe.till(2, 2, t0)

        assertEquals(ActionResult.TILE_OCCUPIED, place(0, 2, 2, t0))
        assertEquals(ActionResult.TILE_OCCUPIED, place(1, 2, 2, t0))
        assertTrue(grid().isTilled(2, 2))
    }

    // --- The life of a bed ---

    @Test
    fun `after the harvest the bed is still tilled and can be planted again`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TULIP.itemId, 2))
        hoe.till(2, 2, t0)
        place(0, 2, 2, t0)
        val tulip = repo.plants.single()
        repo.put(tulip.copy(growthProgress = 1f))

        assertEquals(ActionResult.SUCCESS, harvest(tulip.instanceId, t0, sunny))

        assertTrue(repo.plants.isEmpty())
        assertTrue(grid().isTilled(2, 2))
        assertEquals(ActionResult.SUCCESS, place(0, 2, 2, t0))
    }

    @Test
    fun `a tap on a planted bed means the plant, and on an empty one the soil`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TULIP.itemId, 1))
        hoe.till(2, 2, t0)
        assertTrue(grid().objectAt(2, 2)!!.isTilledSoil)

        place(0, 2, 2, t0)
        assertTrue(grid().objectAt(2, 2)!!.isPlant)

        // Pulling the plant up leaves the bed; removing the bed turns it back into open ground.
        assertEquals(ActionResult.SUCCESS, remove(grid().objectAt(2, 2)!!.instanceId))
        val soil = grid().objectAt(2, 2)!!
        assertTrue(soil.isTilledSoil)
        assertEquals(ActionResult.SUCCESS, remove(soil.instanceId))
        assertNull(grid().objectAt(2, 2))
        assertFalse(grid().isTilled(2, 2))
    }

    @Test
    fun `time does nothing to a bed`() = runBlocking {
        hoe.till(2, 2, t0)
        val soil = repo.objects.values.single()

        val later = CalculateOfflineProgressUseCase()(soil, t0 + 30L * 24 * 3_600_000, sunny)

        assertEquals(soil.copy(lastUpdatedTimestamp = later.lastUpdatedTimestamp), later)
        assertEquals(GridObject.TILLED_SOIL_OBJECT_ID, later.objectId)
    }

    // --- Gardens from before the hoe ---

    @Test
    fun `plants from an older save get soil put under them`() = runBlocking {
        repo.put(obj("tulip", 1, 1, PlantSpecies.TULIP.objectId))
        repo.put(obj("rose", 4, 6, PlantSpecies.ROSE.objectId))
        repo.put(obj("rock", 7, 7, "deco_rock"))
        // One that was planted on paving: it keeps its paving.
        repo.put(obj("stone", 2, 2, GridObject.COBBLESTONE_OBJECT_ID))
        repo.put(obj("onStone", 2, 2, PlantSpecies.TULIP.objectId))

        hoe.tillUnderExistingPlants(t0)

        assertTrue(grid().isTilled(1, 1))
        assertTrue(grid().isTilled(4, 6))
        assertFalse(grid().isTilled(7, 7))
        assertFalse(grid().isTilled(2, 2))
        assertEquals(GridObject.COBBLESTONE_OBJECT_ID, grid().floorAt(2, 2)?.objectId)
        assertEquals(7, repo.objects.size)
        // The plants themselves are untouched.
        assertEquals(obj("tulip", 1, 1, PlantSpecies.TULIP.objectId), repo.objects["tulip"])

        // Doing it again adds nothing.
        hoe.tillUnderExistingPlants(t0 + 1)
        assertEquals(7, repo.objects.size)
    }
}
