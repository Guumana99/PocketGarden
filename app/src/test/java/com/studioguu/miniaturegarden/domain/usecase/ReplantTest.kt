package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.WeatherType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Moving a crop with the pencil ([MoveObjectUseCase.replant]). */
class ReplantTest {

    private val now = 1_700_000_000_000L
    private val repo = FakeGardenRepository().apply { progress = GardenProgress(season = Season.AUTUMN) }
    private val move = MoveObjectUseCase(repo, GardenMap.DEFAULT, CalculateOfflineProgressUseCase())

    private fun plant(x: Int, y: Int, species: PlantSpecies = PlantSpecies.TOMATO, status: PlantStatus = PlantStatus.HEALTHY) = GridObject(
        instanceId = "plant",
        gridX = x,
        gridY = y,
        objectId = species.objectId,
        growthProgress = 0.6f,
        moistureLevel = 0.7f,
        nutritionLevel = 0.9f,
        stressRate = 0.2f,
        harvestCount = 2,
        bloomColour = "pink",
        status = status,
        lastUpdatedTimestamp = now,
    )

    private fun bed(x: Int, y: Int, objectId: String = GridObject.TILLED_SOIL_OBJECT_ID, food: Float = 0.9f, grade: Int = 0) = GridObject(
        instanceId = "bed_${x}_$y",
        gridX = x,
        gridY = y,
        objectId = objectId,
        nutritionLevel = food,
        soilQuality = grade,
        soilProgress = grade * 3,
        lastCropId = "plant_mint".takeIf { grade > 0 },
        lastUpdatedTimestamp = now,
    )

    private fun put(vararg things: GridObject) = things.forEach { repo.objects[it.instanceId] = it }

    private suspend fun replant(x: Int, y: Int) = move.replant("plant", x, y, now, WeatherType.SUNNY)

    @Test
    fun `what is the plant's goes with it, and it takes on the food of its new soil`() = runBlocking {
        val from = bed(1, 1)
        val to = bed(2, 2, food = 0.4f, grade = 2)
        put(from, to, plant(1, 1))

        assertEquals(ActionResult.SUCCESS, replant(2, 2))

        val moved = repo.objects.getValue("plant")
        assertEquals(GridCell(2, 2), GridCell(moved.gridX, moved.gridY))
        assertEquals(0.6f, moved.growthProgress, 0f)
        assertEquals(2, moved.harvestCount)
        assertEquals(0.7f, moved.moistureLevel, 0f)
        assertEquals(0.2f, moved.stressRate, 0f)
        assertEquals("pink", moved.bloomColour)
        assertEquals(0.4f, moved.nutritionLevel, 0f)
        // The soils are left as they were, each with its own grade, food and record.
        assertEquals(from, repo.objects.getValue(from.instanceId))
        assertEquals(to, repo.objects.getValue(to.instanceId))
    }

    @Test
    fun `moved onto soil with no food in it, a crop shows it, and is well again on soil that has some`() = runBlocking {
        put(bed(1, 1), bed(2, 2, food = 0f), bed(3, 3), plant(1, 1))
        assertEquals(ActionResult.SUCCESS, replant(2, 2))
        assertEquals(PlantStatus.UNNOURISHED, repo.objects.getValue("plant").status)
        assertEquals(ActionResult.SUCCESS, replant(3, 3))
        assertEquals(PlantStatus.HEALTHY, repo.objects.getValue("plant").status)
    }

    @Test
    fun `a crop only goes where it could be planted`() = runBlocking {
        val bench = GridObject(instanceId = "bench", gridX = 4, gridY = 4, objectId = "deco_bench", lastUpdatedTimestamp = now)
        val other = plant(5, 5).copy(instanceId = "other")
        put(bed(1, 1), bed(2, 2, GridObject.LOOSE_SOIL_OBJECT_ID), bed(4, 4), bench, bed(5, 5), other, plant(1, 1))

        assertEquals(ActionResult.NEEDS_RIDGE, replant(2, 2)) // A vegetable on tilled earth.
        assertEquals(ActionResult.NEEDS_RIDGE, replant(3, 3)) // On bare ground.
        assertEquals(ActionResult.TILE_OCCUPIED, replant(4, 4))
        assertEquals(ActionResult.TILE_OCCUPIED, replant(5, 5))
        assertEquals(ActionResult.OUT_OF_BOUNDS, replant(-1, 0))
        assertEquals(GridCell(1, 1), repo.objects.getValue("plant").let { GridCell(it.gridX, it.gridY) })

        val open = MoveObjectUseCase.cellsFor(plant(1, 1), repo.objects.values.toList(), GardenMap.DEFAULT)
        assertEquals(setOf(GridCell(1, 1)), open)
    }

    @Test
    fun `a crop comes out from under glass and goes under it, out of season too`() = runBlocking {
        // Tomatoes are not an autumn crop: they could not be sown out here now.
        assertFalse(GardenItem.SEED_TOMATO.isInSeason(Season.AUTUMN))
        put(bed(1, 1, GridObject.GREENHOUSE_OBJECT_ID), bed(2, 2), plant(1, 1))
        assertEquals(ActionResult.SUCCESS, replant(2, 2))
        assertEquals(ActionResult.SUCCESS, replant(1, 1))
    }

    @Test
    fun `a withered crop stays, and one that has withered since it was looked at too`() = runBlocking {
        put(bed(1, 1), bed(2, 2), plant(1, 1, status = PlantStatus.WITHERED))
        assertEquals(ActionResult.NOT_MOVABLE, replant(2, 2))

        // Dry and all but gone, a long time ago: brought up to now, it has withered.
        put(plant(1, 1).copy(moistureLevel = 0f, stressRate = 0.99f, lastUpdatedTimestamp = now - 30L * 24 * 3_600_000))
        assertEquals(ActionResult.NOT_MOVABLE, replant(2, 2))
        assertEquals(GridCell(1, 1), repo.objects.getValue("plant").let { GridCell(it.gridX, it.gridY) })
    }

    @Test
    fun `replanting takes the pencil, and nothing else in the ground moves`() {
        val crop = plant(1, 1)
        assertEquals(LayoutCheck.OK, GardenLayout.check(LayoutAction.MOVE, crop, editing = true))
        assertEquals(LayoutCheck.NEEDS_EDIT_MODE, GardenLayout.check(LayoutAction.MOVE, crop, editing = false))
        assertEquals(LayoutCheck.IN_THE_GROUND, GardenLayout.check(LayoutAction.STORE, crop, editing = true))
        val withered = crop.copy(status = PlantStatus.WITHERED)
        assertFalse(GardenLayout.isMovable(withered))
        assertEquals(LayoutCheck.IN_THE_GROUND, GardenLayout.check(LayoutAction.MOVE, withered, editing = true))
        assertEquals(LayoutCheck.IN_THE_GROUND, GardenLayout.check(LayoutAction.MOVE, bed(1, 1), editing = true))
        assertTrue(GardenLayout.isMovable(crop))
    }

    @Test
    fun `a crop moved like a piece of the layout does not skip the rules for planting`() = runBlocking {
        put(bed(1, 1), plant(1, 1))
        assertEquals(ActionResult.NOT_MOVABLE, move("plant", 2, 2))
    }
}
