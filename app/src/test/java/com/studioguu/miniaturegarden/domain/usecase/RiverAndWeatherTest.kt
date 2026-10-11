package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.PlacementCheck
import com.studioguu.miniaturegarden.domain.model.Footprint
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.TileType
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiverAndWeatherTest {

    private val t0 = 1_700_000_000_000L
    private val hour = 3_600_000L

    private val repo = FakeGardenRepository().apply { profile = UserProfile(coins = 100) }
    private val calc = CalculateOfflineProgressUseCase()
    private val record = RecordCollectionUseCase(repo)
    private val place = PlaceItemUseCase(repo, GardenMap.DEFAULT, record)
    private val remove = RemoveObjectUseCase(repo)
    private val care = CareForPlantUseCase(repo, calc)

    private fun river(id: String, x: Int, y: Int) =
        GridObject(instanceId = id, gridX = x, gridY = y, objectId = GridObject.RIVER_OBJECT_ID, lastUpdatedTimestamp = t0)

    private fun tulip(id: String, x: Int, y: Int) =
        GridObject(instanceId = id, gridX = x, gridY = y, objectId = PlantSpecies.TULIP.objectId, lastUpdatedTimestamp = t0)

    // --- The river is laid by the player ---

    @Test
    fun `a new garden has no river anywhere`() {
        assertTrue(GardenMap.DEFAULT.cellsOf(TileType.RIVER).isEmpty())
        val grid = GardenGrid(GardenMap.DEFAULT, emptyList())
        for (x in 0 until 9) for (y in 0 until 9) {
            assertEquals(PlacementCheck.OK, grid.checkPlacement(Footprint.SINGLE, x, y))
        }
    }

    @Test
    fun `a river tile can be laid on any free tile`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.TILE_RIVER.itemId, 3))

        assertEquals(ActionResult.SUCCESS, place(0, 6, 2, t0))
        assertEquals(ActionResult.SUCCESS, place(0, 0, 0, t0))
        assertEquals(ActionResult.SUCCESS, place(0, 8, 8, t0))

        assertEquals(3, repo.objects.size)
        assertTrue(repo.objects.values.all { it.isTerrain && !it.isPlant })
        assertTrue(repo.slots.getValue(0).isEmpty)
    }

    @Test
    fun `nothing can be planted or built on a river tile`() = runBlocking {
        repo.put(river("r", 4, 4))
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TULIP.itemId, 1))
        repo.putSlot(InventorySlot(1, GardenItem.STRUCT_HUT.itemId, 1))
        repo.putSlot(InventorySlot(2, GardenItem.TILE_RIVER.itemId, 1))

        assertEquals(ActionResult.TILE_NOT_BUILDABLE, place(0, 4, 4, t0))
        // The hut's far corner would be in the water.
        assertEquals(ActionResult.TILE_NOT_BUILDABLE, place(1, 3, 3, t0))
        // River on river is refused too, and the tile is not used up.
        assertEquals(ActionResult.TILE_NOT_BUILDABLE, place(2, 4, 4, t0))

        assertEquals(1, repo.objects.size)
        assertEquals(1, repo.slots.getValue(2).amount)
        // Right beside it is fine.
        assertEquals(ActionResult.SUCCESS, place(0, 5, 4, t0))
    }

    @Test
    fun `a river tile cannot be laid under something that is already there`() = runBlocking {
        repo.put(tulip("p", 2, 2))
        repo.putSlot(InventorySlot(0, GardenItem.TILE_RIVER.itemId, 1))

        assertEquals(ActionResult.TILE_OCCUPIED, place(0, 2, 2, t0))
        assertEquals(1, repo.slots.getValue(0).amount)
    }

    @Test
    fun `removing a river tile turns the tile back into lawn`() = runBlocking {
        repo.put(river("r", 4, 4))
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TULIP.itemId, 1))

        assertEquals(ActionResult.SUCCESS, remove("r"))
        assertEquals(ActionResult.SUCCESS, place(0, 4, 4, t0))
    }

    @Test
    fun `river tiles are not plants`() = runBlocking {
        repo.put(river("r", 4, 4).copy(moistureLevel = 0.1f))

        assertEquals(ActionResult.NOT_A_PLANT, care.water("r", t0 + hour, WeatherType.SUNNY))
        val later = calc(repo.objects.getValue("r"), t0 + 72 * hour, WeatherType.SUNNY)
        assertEquals(0.1f, later.moistureLevel, 0f)
        assertEquals(0f, later.stressRate, 0f)
    }

    @Test
    fun `the first river tile makes its page of the encyclopedia ready`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.TILE_RIVER.itemId, 2))

        place(0, 1, 1, t0)
        place(0, 1, 2, t0)

        assertEquals(2, repo.collection[GridObject.RIVER_OBJECT_ID]?.count)
        assertEquals(false, repo.collection[GridObject.RIVER_OBJECT_ID]?.revealed)
        assertEquals(100L, repo.profile?.coins) // the coins come when the page is filled in
    }

    // --- Shop shelves ---

    @Test
    fun `every shelf has something on it and seeds are exactly the plants`() {
        for (category in ItemCategory.entries) {
            assertTrue(category.name, GardenItem.entries.any { it.category == category })
        }
        for (item in GardenItem.entries) {
            val isSeed = item.objectId.startsWith(GridObject.PLANT_ID_PREFIX)
            assertEquals(item.name, isSeed, item.category == ItemCategory.SEED)
        }
        assertEquals(ItemCategory.STRUCTURE, GardenItem.STRUCT_HUT.category)
        assertEquals(ItemCategory.STRUCTURE, GardenItem.STRUCT_WELL.category)
        assertEquals(ItemCategory.DECORATION, GardenItem.TILE_RIVER.category)
    }

    @Test
    fun `the river tile is for sale from the start`() {
        assertTrue(GardenItem.TILE_RIVER.isUnlockedFor(UserProfile().stats))
        assertTrue(GardenItem.TILE_RIVER.price <= UserProfile.STARTING_COINS)
    }

    // --- Weather ---

    @Test
    fun `rain makes plants grow a fifth faster and keeps the soil moist`() {
        val start = tulip("p", 0, 0)

        val sunny = calc(start, t0 + 10 * hour, WeatherType.SUNNY)
        val rainy = calc(start, t0 + 10 * hour, WeatherType.RAINY)

        assertEquals(10f / 48f, sunny.growthProgress, 1e-4f)
        assertEquals(10f / 48f * 1.2f, rainy.growthProgress, 1e-4f)
        assertEquals(1f, rainy.moistureLevel, 0f)
        assertTrue(sunny.moistureLevel < 1f)
    }

    @Test
    fun `clouds slow the drying but not the growing`() {
        val start = tulip("p", 0, 0)

        val sunny = calc(start, t0 + 10 * hour, WeatherType.SUNNY)
        val cloudy = calc(start, t0 + 10 * hour, WeatherType.CLOUDY)

        assertEquals(sunny.growthProgress, cloudy.growthProgress, 1e-6f)
        assertTrue(cloudy.moistureLevel > sunny.moistureLevel)
    }

    @Test
    fun `rain does not help a plant that is already suffering`() {
        val starving = tulip("p", 0, 0).copy(nutritionLevel = 0.1f)

        val rainy = calc(starving, t0 + 10 * hour, WeatherType.RAINY)

        assertEquals(0f, rainy.growthProgress, 0f)
        assertFalse(rainy.stressRate == 0f)
    }

    @Test
    fun `no weather is worse for growth than a sunny day`() {
        assertTrue(WeatherType.entries.all { it.growthMultiplier >= WeatherType.SUNNY.growthMultiplier })
        assertEquals(WeatherType.RAINY, WeatherType.entries.maxBy { it.growthMultiplier })
    }
}
