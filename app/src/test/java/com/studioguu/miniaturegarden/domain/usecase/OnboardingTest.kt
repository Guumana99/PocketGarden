package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.data.repository.PracticeGardenRepository
import com.studioguu.miniaturegarden.domain.model.GardenExpansion
import com.studioguu.miniaturegarden.domain.model.GardenFeature
import com.studioguu.miniaturegarden.domain.model.Gift
import com.studioguu.miniaturegarden.domain.model.GiftItem
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.PracticeGarden
import com.studioguu.miniaturegarden.domain.model.TutorialTool
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
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.TutorialGuide
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.repository.PracticeMode
import com.studioguu.miniaturegarden.ui.title.TitleSampleGarden
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePractice : PracticeMode {
    override val practising = MutableStateFlow(true)
    override fun enter() {
        practising.value = true
    }

    override fun leave() {
        practising.value = false
    }
}

class OnboardingTest {

    private val t0 = 1_700_000_000_000L
    private val sunny = WeatherType.SUNNY

    private val repo = FakeGardenRepository().apply {
        profile = UserProfile(coins = UserProfile.STARTING_COINS)
        progress = GardenProgress.NEW_GARDEN
        (0 until UserProfile.DEFAULT_INVENTORY_CAPACITY).forEach { putSlot(InventorySlot(it)) }
    }
    private val follow = FollowTutorialUseCase(repo)

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

    // --- The first steps, played in a practice garden ---

    @Test
    fun `a new garden starts bare, in spring, with the first steps ahead`() {
        assertEquals(Season.SPRING, GardenProgress.NEW_GARDEN.season)
        assertEquals(TutorialStep.WELCOME, GardenProgress.NEW_GARDEN.tutorialStep)
        // A garden from before there were first steps is not walked through them again.
        assertEquals(TutorialStep.DONE, GardenProgress.EXISTING_GARDEN.tutorialStep)
        assertEquals(Season.SPRING, GardenProgress.EXISTING_GARDEN.season)
        assertTrue(runBlocking { PracticeGardenRepository().getGridObjects() }.isEmpty())
        // A garden that was waiting at the old crate has them ahead of it too.
        assertEquals(TutorialStep.WELCOME, TutorialStep.fromName("OPEN_CRATE"))
    }

    @Test
    fun `the first steps go from the shining cell to the harvest, with the seed and the fertilizer free`() = runBlocking {
        val garden = PracticeGardenRepository()
        val follow = FollowTutorialUseCase(garden)
        val hoe = HoeUseCase(garden, GardenMap.DEFAULT)
        val offline = CalculateOfflineProgressUseCase()
        val care = CareForPlantUseCase(garden, offline)
        val plot = PracticeGarden.PLOT
        suspend fun step() = garden.getProgress()?.tutorialStep

        follow.welcomed()
        assertEquals(TutorialStep.TILL_SOIL, step())
        assertEquals(setOf(plot), TutorialGuide.hintCells(TutorialStep.TILL_SOIL, emptyList()))
        // Only the cell that shines, and twice over twice.
        assertEquals(ActionResult.NOT_THIS_STEP, hoe(plot.x + 1, plot.y, t0))
        assertEquals(ActionResult.HOE_STRUCK, hoe(plot.x, plot.y, t0, strikes = 0))
        assertEquals(ActionResult.SUCCESS, hoe(plot.x, plot.y, t0, strikes = 1))
        assertEquals(TutorialStep.TILL_SOIL, follow())
        assertTrue(garden.getGridObjects().single().isLooseSoil)
        assertEquals(ActionResult.HOE_STRUCK, hoe(plot.x, plot.y, t0, strikes = 0))
        assertEquals(ActionResult.SUCCESS, hoe(plot.x, plot.y, t0, strikes = 1))
        assertEquals(TutorialStep.BUY_SEED, follow())

        val coins = garden.getUserProfile()!!.coins
        assertEquals(ActionResult.SUCCESS, PurchaseItemUseCase(garden)(PracticeGarden.SEED))
        assertEquals(coins, garden.getUserProfile()!!.coins)
        assertEquals(TutorialStep.PLANT_SEED, follow())

        val slot = garden.getInventory().first { !it.isEmpty }.slotIndex
        assertEquals(ActionResult.SUCCESS, PlaceItemUseCase(garden, GardenMap.DEFAULT, RecordCollectionUseCase(garden))(slot, plot.x, plot.y, t0))
        assertEquals(TutorialStep.WATER_SEEDLING, follow())
        val plant = garden.getGridObjects().single { it.isPlant }

        assertEquals(ActionResult.SUCCESS, care.water(plant.instanceId, t0, sunny))
        assertTrue(follow.wateringDone())
        assertEquals(TutorialStep.FEED_SEEDLING, step())
        assertEquals(PlantStatus.UNNOURISHED, garden.getGridObjects().single { it.isPlant }.status)

        assertEquals(ActionResult.SUCCESS, care.fertilize(plant.instanceId, t0, sunny))
        assertEquals(coins, garden.getUserProfile()!!.coins)
        val fed = garden.getGridObjects().single { it.isPlant }
        assertEquals(PlantStatus.HEALTHY, fed.status)
        assertEquals(plant.growthProgress, fed.growthProgress, 0f)
        follow.fed()
        assertEquals(TutorialStep.GROW, step())

        follow.grow(0.5f)
        assertEquals(TutorialStep.GROW, step())
        follow.grow(1f)
        assertEquals(TutorialStep.HARVEST, step())
        assertEquals(setOf(plot), TutorialGuide.hintCells(TutorialStep.HARVEST, garden.getGridObjects()))

        val harvest = HarvestPlantUseCase(garden, offline, RecordCollectionUseCase(garden))
        assertEquals(ActionResult.SUCCESS, harvest(plant.instanceId, t0, sunny))
        assertTrue(garden.getUserProfile()!!.coins > coins)
        assertEquals(TutorialStep.PRACTICE_DONE, follow())
    }

    @Test
    fun `tapping again and again gets one seed of the first steps, and costs nothing`() = runBlocking {
        repo.progress = GardenProgress(tutorialStep = TutorialStep.BUY_SEED)
        val buy = PurchaseItemUseCase(repo)
        assertEquals(ActionResult.SUCCESS, buy(PracticeGarden.SEED))
        // Before the step has moved on, and after.
        assertEquals(ActionResult.NOT_THIS_STEP, buy(PracticeGarden.SEED))
        assertEquals(TutorialStep.PLANT_SEED, follow())
        assertEquals(ActionResult.NOT_THIS_STEP, buy(PracticeGarden.SEED))
        assertEquals(1, repo.getInventory().filter { it.itemId == PracticeGarden.SEED.itemId }.sumOf { it.amount })
        assertEquals(UserProfile.STARTING_COINS, repo.profile!!.coins)
    }

    @Test
    fun `leaving the practice garden the first time brings the closing board and the first gift`() = runBlocking {
        val practice = FakePractice()
        val leave = LeavePracticeUseCase(repo, practice, PlaceGiftUseCase(repo, GardenMap.DEFAULT))
        val coins = repo.profile!!.coins
        assertEquals(TutorialStep.FINISHED, leave(t0))
        assertFalse(practice.practising.value)
        assertEquals(TutorialStep.FINISHED, step)
        val box = repo.objects.values.single()
        assertTrue(box.isGiftBox)
        assertEquals(Gift.FIRST, Gift.inside(box))
        assertEquals(coins, repo.profile!!.coins)
        // Played again from the settings: nothing changes in the saved garden, and no second gift.
        repo.progress = repo.progress!!.copy(tutorialStep = TutorialStep.DONE)
        assertEquals(TutorialStep.DONE, leave(t0))
        assertEquals(1, repo.objects.size)
        assertEquals(TutorialStep.DONE, step)
    }

    @Test
    fun `a gift box holds what it was given, and gives it to the bag`() = runBlocking {
        val place = PlaceGiftUseCase(repo, GardenMap.DEFAULT)
        val gift = Gift("spring_note", listOf(GiftItem(GardenItem.SEED_MINT.itemId, 2), GiftItem(GardenItem.SEED_TULIP.itemId, 5)), coins = 50, title = "おたより")
        assertEquals(ActionResult.SUCCESS, place(gift, t0))
        val box = repo.objects.values.single()
        assertEquals(gift, Gift.inside(box))
        assertEquals(PracticeGarden.PLOT, GridCell(box.gridX, box.gridY))

        val coins = repo.profile!!.coins
        assertEquals(ActionResult.SUCCESS, OpenGiftUseCase(repo)(box.instanceId))
        assertTrue(repo.objects.isEmpty())
        assertEquals(coins + 50, repo.profile!!.coins)
        assertEquals(2, repo.slots.values.single { it.itemId == GardenItem.SEED_MINT.itemId }.amount)
        assertEquals(5, repo.slots.values.single { it.itemId == GardenItem.SEED_TULIP.itemId }.amount)
    }

    @Test
    fun `with no room in the bag a gift box stays shut, and a box from before gifts holds the first gift`() = runBlocking {
        (0 until UserProfile.DEFAULT_INVENTORY_CAPACITY).forEach { repo.putSlot(InventorySlot(it, GardenItem.SEED_MINT.itemId, 1)) }
        val old = GridObject("starter_crate", 5, 5, objectId = GridObject.GIFT_BOX_OBJECT_ID, lastUpdatedTimestamp = t0)
        repo.put(old)
        assertEquals(Gift.FIRST, Gift.inside(old))
        assertEquals(ActionResult.INVENTORY_FULL, OpenGiftUseCase(repo)(old.instanceId))
        assertTrue(repo.objects.containsKey(old.instanceId))
        // It is the first gift's box: leaving the practice garden sets down no other.
        assertEquals(ActionResult.SUCCESS, PlaceGiftUseCase(repo, GardenMap.DEFAULT)(Gift.FIRST, t0))
        assertEquals(1, repo.objects.size)
    }

    @Test
    fun `during the first steps only the tool the step asks for can be picked up`() {
        fun allowed(step: TutorialStep) = TutorialTool.entries.filter { step.allows(it) }.toSet()
        for (step in listOf(TutorialStep.WELCOME, TutorialStep.GROW, TutorialStep.HARVEST, TutorialStep.PRACTICE_DONE)) {
            assertEquals(emptySet<TutorialTool>(), allowed(step))
        }
        assertEquals(setOf(TutorialTool.HOE), allowed(TutorialStep.TILL_SOIL))
        assertEquals(setOf(TutorialTool.SHOP), allowed(TutorialStep.BUY_SEED))
        assertEquals(setOf(TutorialTool.BAG), allowed(TutorialStep.PLANT_SEED))
        assertEquals(setOf(TutorialTool.WATER), allowed(TutorialStep.WATER_SEEDLING))
        assertEquals(setOf(TutorialTool.FERTILIZE), allowed(TutorialStep.FEED_SEEDLING))
        // From the closing board on, and in a garden from before the first steps, everything.
        for (free in listOf(TutorialStep.FINISHED, TutorialStep.REVEAL_EDIT, TutorialStep.REVEAL_POST, TutorialStep.DONE)) {
            assertEquals(TutorialTool.entries.toSet(), allowed(free))
        }
    }

    @Test
    fun `the screen locks the tool bar and keeps edit mode put away until it is handed over`() {
        val main = listOf(java.io.File("src/main"), java.io.File("app/src/main")).first { it.isDirectory }
        fun source(path: String) = java.io.File(main, "java/com/studioguu/miniaturegarden/$path").readText()
        val screen = source("ui/GardenScreen.kt")
        assertTrue(screen.contains("(tutorialStep?.allows(tool) ?: true) && (!editMode || GardenLayout.toolAllowedInEditMode(tool))"))
        assertTrue(screen.contains("visible = tutorialStep?.reveals(GardenFeature.EDIT_MODE)"))
        val bar = source("ui/hud/QuickActionBar.kt")
        // Watering has no button on the bar: it is the plant panel's own (see QuickActionBar).
        for (tool in TutorialTool.entries - TutorialTool.WATER) assertTrue(tool.name, bar.contains("allows(TutorialTool.${tool.name})"))
        assertFalse(bar.contains("TutorialTool.WATER"))
        // A gift is received with a soft tap, not the till's coins.
        val model = source("ui/GardenViewModel.kt")
        val openGift = model.substringAfter("fun openGift()").substringBefore("fun closeGift()")
        assertTrue(openGift.contains("SoundEffect.TAP"))
        assertFalse(openGift.contains("SoundEffect.COIN"))
        // Opening a box is the everyday garden: no edit mode, so no grid.
        assertTrue(model.substringBefore("_openingGiftId.value = gridObjects.value.objectAt(tile)").takeLast(400).contains("_editMode.value = false"))
    }

    @Test
    fun `the first steps speak to the player, and never of how long things take`() {
        val main = listOf(java.io.File("src/main"), java.io.File("app/src/main")).first { it.isDirectory }
        val strings = java.io.File(main, "res/values/strings.xml").readText()
        val lines = Regex("<string name=\"(tutorial_[a-z_]+)\">(.*?)</string>").findAll(strings).toList()
        assertTrue(lines.size >= 15)
        for (line in lines) {
            val text = line.groupValues[2]
            assertFalse(line.groupValues[1], Regex("[0-9０-９]+ *(時間|分|秒|日)|数時間").containsMatchIn(text))
            assertFalse(line.groupValues[1], text.contains("しましょう"))
        }
    }

    // --- Handing over the rest, one by one ---

    @Test
    fun `nothing else is in hand during the first steps`() {
        for (guided in TutorialStep.entries.filter { it <= TutorialStep.FINISHED }) {
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
        // Edit mode (where the grid shows) first, then the rest as before.
        assertEquals(
            listOf(GardenFeature.EDIT_MODE, GardenFeature.BLESSED_RAIN, GardenFeature.COLLECTION, GardenFeature.CAMERA, GardenFeature.POST),
            handedOver,
        )
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
    fun `a garden without stored progress has no first steps`() = runBlocking {
        repo.progress = null
        assertEquals(TutorialStep.DONE, follow())
        assertFalse(follow.wateringDone())
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
        // All but the bridge, which wants a river two cells wide.
        assertTrue(StructureData.entries.filter { it != StructureData.BRIDGE }.all { it.objectId in ids })
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
