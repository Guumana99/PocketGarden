package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.GardenExpansion
import com.studioguu.miniaturegarden.domain.model.GardenFeature
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PlacementCheck
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.StarterGarden
import com.studioguu.miniaturegarden.domain.model.StructureData
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

    private val hoe = HoeUseCase(repo, GardenMap.DEFAULT)

    private val step get() = repo.progress?.tutorialStep

    /** Every object can be put where it is with the ones before it already in place. */
    private fun assertNoOverlaps(objects: List<GridObject>, map: GardenMap = GardenMap.DEFAULT) {
        val placed = mutableListOf<GridObject>()
        for (obj in objects) {
            val check = GardenGrid(map, placed)
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
    fun `the starter garden is bare lawn with only the crate in the middle`() {
        val objects = StarterGarden.objects(t0)
        assertNoOverlaps(objects)
        val crate = objects.single()
        assertTrue(crate.isStarterCrate)
        assertEquals(GridCell(4, 4), GridCell(crate.gridX, crate.gridY))
        // No fences and no tilled soil: the garden is the player's to make.
        assertTrue(objects.none { it.isTilledSoil || it.isFence || it.isWorking || it.isPlant })
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
        assertEquals(listOf(InventorySlot(0, GardenItem.SEED_TULIP.itemId, 1)), seeds.map { it.contents() })
        assertEquals(TutorialStep.TILL_SOIL, step)
        // Only once: the crate is gone.
        assertEquals(ActionResult.NOT_FOUND, openCrate(StarterGarden.STARTER_CRATE_INSTANCE_ID))
        assertEquals(1, repo.slots.values.sumOf { it.amount })
    }

    @Test
    fun `the seed joins tulip seeds already in the bag`() = runBlocking {
        repo.putSlot(InventorySlot(3, GardenItem.SEED_TULIP.itemId, 2))

        assertEquals(ActionResult.SUCCESS, openCrate(StarterGarden.STARTER_CRATE_INSTANCE_ID))

        assertEquals(InventorySlot(3, GardenItem.SEED_TULIP.itemId, 3), repo.slots[3]!!.contents())
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
        repo.put(GridObject("a_fence", 1, 1, objectId = GridObject.FENCE_OBJECT_ID, lastUpdatedTimestamp = t0))
        assertEquals(ActionResult.NOT_FOUND, openCrate("a_fence"))
        assertTrue(repo.objects.containsKey("a_fence"))
    }

    // --- The whole way through ---

    @Test
    fun `crate, tilling, sowing and watering lead to the closing board`() = runBlocking {
        openCrate(StarterGarden.STARTER_CRATE_INSTANCE_ID)
        assertEquals(TutorialStep.TILL_SOIL, follow())

        // The bare lawn where the crate stood sparkles: that is where to till.
        val patch = TutorialGuide.hintCells(TutorialStep.TILL_SOIL, repo.objects.values.toList()).single()
        assertEquals(StarterGarden.CRATE, patch)
        assertEquals(ActionResult.SUCCESS, hoe(patch.x, patch.y, t0))
        assertEquals(TutorialStep.PLANT_SEED, follow())

        val bed = TutorialGuide.hintCells(TutorialStep.PLANT_SEED, repo.objects.values.toList()).single()
        assertEquals(patch, bed)
        assertEquals(ActionResult.SUCCESS, place(0, bed.x, bed.y, t0))
        assertEquals(TutorialStep.WATER_SEEDLING, follow())

        // The seed is a little dry, so that the watering the next step asks for can be done.
        val seedling = repo.plants.single()
        assertTrue(seedling.moistureLevel < CareForPlantUseCase.WATERING_THRESHOLD)
        assertEquals(setOf(bed), TutorialGuide.hintCells(TutorialStep.WATER_SEEDLING, repo.objects.values.toList()))

        assertEquals(ActionResult.SUCCESS, care.water(seedling.instanceId, t0 + 60_000L, sunny))
        assertTrue(follow.wateringDone())
        assertEquals(TutorialStep.FINISHED, step)
        assertTrue(TutorialGuide.hintCells(TutorialStep.FINISHED, repo.objects.values.toList()).isEmpty())

        // The garden itself does not move the board on, and watering again changes nothing.
        assertFalse(follow.wateringDone())
        assertEquals(TutorialStep.FINISHED, follow())
    }

    @Test
    fun `sowing waits for tilled soil`() = runBlocking {
        openCrate(StarterGarden.STARTER_CRATE_INSTANCE_ID)
        // Without tilled soil the seed cannot go in, so the step stays where it is.
        assertEquals(ActionResult.NEEDS_TILLED_SOIL, place(0, 4, 4, t0))
        assertEquals(TutorialStep.TILL_SOIL, follow())
        assertTrue(TutorialGuide.hintCells(TutorialStep.PLANT_SEED, repo.objects.values.toList()).isEmpty())
    }

    @Test
    fun `tilling before the crate is opened skips straight to sowing`() = runBlocking {
        repo.till(1, 1)
        assertEquals(TutorialStep.OPEN_CRATE, follow())
        openCrate(StarterGarden.STARTER_CRATE_INSTANCE_ID)
        assertEquals(TutorialStep.PLANT_SEED, follow())
    }

    // --- Handing over the rest, one by one ---

    @Test
    fun `nothing else is in hand during the first steps`() {
        for (guided in listOf(TutorialStep.OPEN_CRATE, TutorialStep.TILL_SOIL, TutorialStep.PLANT_SEED, TutorialStep.WATER_SEEDLING, TutorialStep.FINISHED)) {
            assertTrue(GardenFeature.entries.none { guided.reveals(it) })
            assertEquals(null, guided.introducing)
        }
        assertTrue(GardenFeature.entries.all { TutorialStep.DONE.reveals(it) })
        assertEquals(null, TutorialStep.DONE.introducing)
        // A garden from before there were first steps has everything.
        assertTrue(GardenFeature.entries.all { GardenProgress.EXISTING_GARDEN.tutorialStep.reveals(it) })
    }

    @Test
    fun `after the closing board the features come one at a time, each at the player's tap`() = runBlocking {
        repo.progress = repo.progress!!.copy(tutorialStep = TutorialStep.FINISHED)

        val handedOver = mutableListOf<GardenFeature>()
        var seen = TutorialStep.FINISHED
        while (seen != TutorialStep.DONE) {
            val now = follow.acknowledge(seen)
            now.introducing?.let { feature ->
                handedOver += feature
                // Only this one and those before it are in hand; the rest are still put away.
                assertTrue(now.reveals(feature))
                assertTrue(GardenFeature.entries.filter { it.ordinal > feature.ordinal }.none { now.reveals(it) })
            }
            seen = now
        }
        assertEquals(listOf(GardenFeature.BLESSED_RAIN, GardenFeature.COLLECTION, GardenFeature.CAMERA, GardenFeature.POST), handedOver)
        assertEquals(TutorialStep.DONE, step)
    }

    @Test
    fun `a tap on a board that is no longer showing changes nothing`() = runBlocking {
        repo.progress = repo.progress!!.copy(tutorialStep = TutorialStep.REVEAL_COLLECTION)
        assertEquals(TutorialStep.REVEAL_COLLECTION, follow.acknowledge(TutorialStep.REVEAL_RAIN))
        assertEquals(TutorialStep.REVEAL_COLLECTION, step)
        // Nor does one during the guided steps, or after them.
        repo.progress = repo.progress!!.copy(tutorialStep = TutorialStep.PLANT_SEED)
        assertEquals(TutorialStep.PLANT_SEED, follow.acknowledge(TutorialStep.PLANT_SEED))
        repo.progress = repo.progress!!.copy(tutorialStep = TutorialStep.DONE)
        assertEquals(TutorialStep.DONE, follow.acknowledge(TutorialStep.DONE))
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
    fun `a seedling cleared away before its watering ends the guided steps`() = runBlocking {
        openCrate(StarterGarden.STARTER_CRATE_INSTANCE_ID)
        hoe(4, 5, t0)
        place(0, 4, 5, t0)
        assertEquals(TutorialStep.WATER_SEEDLING, follow())

        assertEquals(ActionResult.SUCCESS, remove(repo.plants.single().instanceId))

        // On to the closing board, so that the rest is still handed over.
        assertEquals(TutorialStep.FINISHED, follow())
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
        val map = TitleSampleGarden.MAP
        for (beat in 0 until TitleSampleGarden.CYCLE_BEATS) {
            val objects = TitleSampleGarden.objects(beat)
            assertNoOverlaps(objects.filter { !it.isPlant }, map)
            assertTrue(objects.all { map.isInside(it.gridX, it.gridY) })
            assertTrue(objects.all { it.objectId in TileSetDefinition.objectIds })
            // Its flowers all stand in tilled soil, as real ones must.
            val grid = GardenGrid(map, objects)
            assertTrue(objects.filter { it.isPlant }.all { grid.isTilled(it.gridX, it.gridY) })
            assertEquals(objects.size, objects.map { it.instanceId }.toSet().size)
        }
    }

    @Test
    fun `the sample garden shows off, as large as a grown garden and with every kind of thing in it`() {
        // As large as a garden grows once its cottage stands.
        assertEquals(GardenExpansion.STAGES.first().size, TitleSampleGarden.SIZE)
        val ids = TitleSampleGarden.objects(0).map { it.objectId }.toSet()
        assertTrue(StructureData.entries.all { it.objectId in ids })
        // Every crop that has its artwork; the ones drawn in code wait for theirs.
        assertTrue(listOf(PlantSpecies.TULIP, PlantSpecies.SUNFLOWER, PlantSpecies.ROSE).all { it.objectId in ids })
        assertTrue(TitleSampleGarden.visitors.isNotEmpty())
        // Most flowers are in bloom at any moment.
        val plants = TitleSampleGarden.objects(5).filter { it.isPlant }
        assertTrue(plants.count { it.growthProgress == 1f } * 2 > plants.size)
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
