package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PlacementCheck
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.StarterGarden
import com.studioguu.miniaturegarden.domain.model.TutorialGuide
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.title.TitleSampleGarden
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {

    private val t0 = 1_700_000_000_000L
    private val sunny = WeatherType.SUNNY

    private val repo = FakeGardenRepository().apply {
        profile = UserProfile(coins = UserProfile.STARTING_COINS)
        progress = GardenProgress.NEW_GARDEN
        StarterGarden.objects(t0).forEach(::put)
        (0 until UserProfile.DEFAULT_INVENTORY_CAPACITY).forEach { putSlot(InventorySlot(it)) }
    }
    private val openCrate = OpenStarterCrateUseCase(repo)
    private val follow = FollowTutorialUseCase(repo)
    private val place = PlaceItemUseCase(repo, GardenMap.DEFAULT, RecordCollectionUseCase(repo))
    private val care = CareForPlantUseCase(repo, CalculateOfflineProgressUseCase())
    private val remove = RemoveObjectUseCase(repo)

    private val step get() = repo.progress?.tutorialStep

    /** Every object can be put where it is with the ones before it already in place. */
    private fun assertNoOverlaps(objects: List<GridObject>) {
        val placed = mutableListOf<GridObject>()
        for (obj in objects) {
            val check = GardenGrid(GardenMap.DEFAULT, placed)
                .checkPlacement(obj.footprint, obj.gridX, obj.gridY, PlacementKind.forObjectId(obj.objectId))
            assertEquals("${obj.objectId} at ${obj.gridX},${obj.gridY}", PlacementCheck.OK, check)
            placed += obj
        }
    }

    // --- The starter garden ---

    @Test
    fun `a new garden starts in spring with the first steps ahead`() {
        assertEquals(Season.SPRING, GardenProgress.NEW_GARDEN.season)
        assertEquals(TutorialStep.OPEN_CRATE, GardenProgress.NEW_GARDEN.tutorialStep)
        // A garden from before there were first steps is not walked through them again.
        assertEquals(TutorialStep.DONE, GardenProgress.EXISTING_GARDEN.tutorialStep)
        assertEquals(Season.SPRING, GardenProgress.EXISTING_GARDEN.season)
    }

    @Test
    fun `the starter garden is a fenced plot with a tilled bed and the crate in the middle`() {
        val objects = StarterGarden.objects(t0)
        assertNoOverlaps(objects)
        assertTrue(objects.all { GardenMap.DEFAULT.isInside(it.gridX, it.gridY) })
        assertEquals(objects.size, objects.map { it.instanceId }.toSet().size)

        val crate = objects.single { it.isStarterCrate }
        assertEquals(GridCell(4, 4), GridCell(crate.gridX, crate.gridY))
        assertEquals(6, objects.count { it.isTilledSoil })
        assertEquals(11, objects.count { it.isFence })
        // Everything stands finished: nothing to wait for, nothing growing yet.
        assertTrue(objects.none { it.isWorking || it.isPlant })
        assertTrue(objects.all { it.objectId in TileSetDefinition.objectIds })
    }

    @Test
    fun `the crate cannot be moved, put away or bought`() {
        val crate = StarterGarden.objects(t0).single { it.isStarterCrate }
        assertFalse(MoveObjectUseCase.canMove(crate))
        assertEquals(null, StoreObjectUseCase.itemFor(crate))
        assertTrue(GardenItem.entries.none { it.objectId == GridObject.STARTER_CRATE_OBJECT_ID })
    }

    // --- Opening the crate ---

    @Test
    fun `opening the crate puts one tulip seed in the bag and takes the crate away`() = runBlocking {
        assertEquals(ActionResult.SUCCESS, openCrate(StarterGarden.STARTER_CRATE_INSTANCE_ID))

        assertTrue(repo.objects.values.none { it.isStarterCrate })
        val seeds = repo.slots.values.filter { !it.isEmpty }
        assertEquals(listOf(InventorySlot(0, GardenItem.SEED_TULIP.itemId, 1)), seeds)
        assertEquals(TutorialStep.PLANT_SEED, step)
        // Only once: the crate is gone.
        assertEquals(ActionResult.NOT_FOUND, openCrate(StarterGarden.STARTER_CRATE_INSTANCE_ID))
        assertEquals(1, repo.slots.values.sumOf { it.amount })
    }

    @Test
    fun `the seed joins tulip seeds already in the bag`() = runBlocking {
        repo.putSlot(InventorySlot(3, GardenItem.SEED_TULIP.itemId, 2))

        assertEquals(ActionResult.SUCCESS, openCrate(StarterGarden.STARTER_CRATE_INSTANCE_ID))

        assertEquals(InventorySlot(3, GardenItem.SEED_TULIP.itemId, 3), repo.slots[3])
        assertTrue(repo.slots[0]!!.isEmpty)
    }

    @Test
    fun `with the bag full the crate stays shut and in place`() = runBlocking {
        (0 until UserProfile.DEFAULT_INVENTORY_CAPACITY).forEach { repo.putSlot(InventorySlot(it, GardenItem.DECO_ROCK.itemId, 1)) }

        assertEquals(ActionResult.INVENTORY_FULL, openCrate(StarterGarden.STARTER_CRATE_INSTANCE_ID))

        assertTrue(repo.objects.values.any { it.isStarterCrate })
        assertEquals(TutorialStep.OPEN_CRATE, step)
    }

    @Test
    fun `only the crate opens like a crate`() = runBlocking {
        assertEquals(ActionResult.NOT_FOUND, openCrate("starter_fence_2_3"))
        assertTrue(repo.objects.containsKey("starter_fence_2_3"))
    }

    // --- The whole way through ---

    @Test
    fun `crate, sowing and watering lead to free play`() = runBlocking {
        openCrate(StarterGarden.STARTER_CRATE_INSTANCE_ID)
        val bed = TutorialGuide.hintCells(TutorialStep.PLANT_SEED, repo.objects.values.toList()).single()
        assertTrue(bed in StarterGarden.BED)

        assertEquals(ActionResult.SUCCESS, place(0, bed.x, bed.y, t0))
        assertEquals(TutorialStep.WATER_SEEDLING, follow())

        // The seed is a little dry, so that the watering the next step asks for can be done.
        val seedling = repo.plants.single()
        assertTrue(seedling.moistureLevel < CareForPlantUseCase.WATERING_THRESHOLD)
        assertEquals(setOf(bed), TutorialGuide.hintCells(TutorialStep.WATER_SEEDLING, repo.objects.values.toList()))

        assertEquals(ActionResult.SUCCESS, care.water(seedling.instanceId, t0 + 60_000L, sunny))
        assertTrue(follow.wateringDone())
        assertEquals(TutorialStep.DONE, step)
        assertTrue(TutorialGuide.hintCells(TutorialStep.DONE, repo.objects.values.toList()).isEmpty())

        // Once done it stays done.
        assertFalse(follow.wateringDone())
        assertEquals(TutorialStep.DONE, follow())
    }

    @Test
    fun `watering only finishes the first steps when watering is the step`() = runBlocking {
        assertFalse(follow.wateringDone())
        assertEquals(TutorialStep.OPEN_CRATE, step)
    }

    @Test
    fun `nothing moves on before the crate is opened, even with a plant in the garden`() = runBlocking {
        repo.till(0, 8)
        repo.putSlot(InventorySlot(5, GardenItem.SEED_TULIP.itemId, 1))
        place(5, 0, 8, t0)

        assertEquals(TutorialStep.OPEN_CRATE, follow())

        // Opened afterwards: the seed is in the ground already, so watering is next.
        openCrate(StarterGarden.STARTER_CRATE_INSTANCE_ID)
        assertEquals(TutorialStep.WATER_SEEDLING, follow())
    }

    @Test
    fun `a seedling cleared away before its watering lets the first steps go`() = runBlocking {
        openCrate(StarterGarden.STARTER_CRATE_INSTANCE_ID)
        place(0, 4, 5, t0)
        follow()

        assertEquals(ActionResult.SUCCESS, remove(repo.plants.single().instanceId))

        assertEquals(TutorialStep.DONE, follow())
    }

    @Test
    fun `a garden without stored progress has no first steps`() = runBlocking {
        repo.progress = null
        assertEquals(TutorialStep.DONE, follow())
        assertFalse(follow.wateringDone())
    }

    @Test
    fun `the crate is what sparkles first`() {
        assertEquals(setOf(StarterGarden.CRATE), TutorialGuide.hintCells(TutorialStep.OPEN_CRATE, StarterGarden.objects(t0)))
    }

    @Test
    fun `unknown stored values fall back to spring and to no first steps`() {
        assertEquals(Season.SPRING, Season.fromName("MONSOON"))
        assertEquals(TutorialStep.DONE, TutorialStep.fromName("SOMETHING_NEW"))
        Season.entries.forEach { assertEquals(it, Season.fromName(it.name)) }
        TutorialStep.entries.forEach { assertEquals(it, TutorialStep.fromName(it.name)) }
    }

    // --- The title screen's sample garden ---

    @Test
    fun `the sample garden is a garden that could really be built`() {
        for (beat in 0 until TitleSampleGarden.CYCLE_BEATS) {
            val objects = TitleSampleGarden.objects(beat)
            assertNoOverlaps(objects.filter { !it.isPlant })
            assertTrue(objects.all { it.objectId in TileSetDefinition.objectIds })
            // Its flowers all stand in tilled soil, as real ones must.
            val grid = GardenGrid(GardenMap.DEFAULT, objects)
            assertTrue(objects.filter { it.isPlant }.all { grid.isTilled(it.gridX, it.gridY) })
            assertEquals(objects.size, objects.map { it.instanceId }.toSet().size)
        }
    }

    @Test
    fun `the sample flowers grow from seed to bloom, stay a while, and start over`() {
        val growth = (0 until TitleSampleGarden.CYCLE_BEATS * 2).map { TitleSampleGarden.growthAt(it, phase = 0) }
        assertEquals(0f, growth[0])
        assertEquals(1f, growth[TitleSampleGarden.GROW_BEATS])
        assertEquals(1f, growth[TitleSampleGarden.CYCLE_BEATS - 1])
        assertEquals(0f, growth[TitleSampleGarden.CYCLE_BEATS])
        assertTrue((1..TitleSampleGarden.GROW_BEATS).all { growth[it] > growth[it - 1] })
        // Not all in step: at any beat some flowers are young and some in bloom.
        val plants = TitleSampleGarden.objects(3).filter { it.isPlant }
        assertTrue(plants.any { it.growthProgress < 0.5f })
        assertTrue(plants.any { it.growthProgress == 1f })
        assertNotNull(plants.firstOrNull())
    }
}
