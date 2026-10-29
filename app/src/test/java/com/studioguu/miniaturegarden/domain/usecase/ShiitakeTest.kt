package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.Ground
import com.studioguu.miniaturegarden.domain.model.HarvestType
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PlantGround
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.UnlockCondition
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shiitake (#68): a log laid on the earth, in the pumpkin's place. */
class ShiitakeTest {

    private val t0 = 1_700_000_000_000L
    private val hour = 3_600_000L

    private val repo = FakeGardenRepository().apply {
        profile = UserProfile(coins = 1_000, playerLevel = 5)
        progress = GardenProgress(Season.AUTUMN, TutorialStep.DONE)
        for (i in 0 until 16) putSlot(InventorySlot(i))
    }
    private val place = PlaceItemUseCase(repo, GardenMap.DEFAULT, RecordCollectionUseCase(repo))
    private val harvest = HarvestPlantUseCase(repo, CalculateOfflineProgressUseCase(), RecordCollectionUseCase(repo))

    private fun at(id: String, x: Int, y: Int, objectId: String, growth: Float = 0f) =
        GridObject(id, x, y, objectId = objectId, growthProgress = growth, lastUpdatedTimestamp = t0)

    private fun lay(item: GardenItem, x: Int, y: Int): ActionResult = runBlocking {
        val slot = repo.slots.values.first { it.isEmpty }.slotIndex
        repo.putSlot(InventorySlot(slot, item.itemId, 1))
        place(slot, x, y, t0).also { if (it != ActionResult.SUCCESS) repo.putSlot(InventorySlot(slot)) }
    }

    // --- What it is ---

    @Test
    fun `it takes the pumpkin's place in the shop, the book and its numbers`() {
        val shiitake = PlantSpecies.SHIITAKE
        assertEquals("plant_shiitake", shiitake.objectId)
        assertEquals(listOf(48, 30, 100, 65), listOf(shiitake.growthHours, shiitake.dryOutHours, shiitake.harvestCoins, shiitake.harvestXp))
        assertEquals(3, shiitake.growthStages)
        assertEquals(HarvestType.REPEATABLE_HARVEST, shiitake.harvestType)
        assertEquals(PlantGround.LOG, shiitake.ground)
        val seed = GardenItem.SEED_SHIITAKE
        assertEquals("seed_shiitake", seed.itemId)
        assertEquals(45, seed.price)
        assertEquals(UnlockCondition.PlayerLevel(14), seed.unlock)
        assertEquals(setOf(Season.AUTUMN), seed.seasons)
        assertEquals(90, CollectionEntry.SHIITAKE.discoveryCoins)
        assertNull(PlantSpecies.fromObjectId("plant_pumpkin"))
        assertNull(GardenItem.fromItemId("seed_pumpkin"))
        // Its later harvests pay like the other crops that bear again: about half, in half the time.
        assertEquals(50, shiitake.regrowCoins)
        assertEquals(33, shiitake.regrowXp)
    }

    @Test
    fun `every other crop is sown in a bed, as before`() {
        for (species in PlantSpecies.entries - PlantSpecies.SHIITAKE) assertEquals(species.name, PlantGround.BED, species.ground)
        assertEquals(setOf(Ground.TILLED_SOIL, Ground.GREENHOUSE), PlantGround.BED.grounds)
        assertEquals(setOf(Ground.BARE_EARTH, Ground.TILLED_SOIL), PlantGround.LOG.grounds)
    }

    // --- Where it can go ---

    @Test
    fun `the ground of a cell is read from what is laid on it`() {
        val grid = GardenGrid(
            GardenMap.DEFAULT,
            listOf(
                at("t", 1, 1, GridObject.TILLED_SOIL_OBJECT_ID),
                at("g", 2, 1, GridObject.GREENHOUSE_OBJECT_ID),
                at("c", 3, 1, GridObject.COBBLESTONE_OBJECT_ID),
                at("w", 4, 1, GridObject.WOOD_DECK_OBJECT_ID),
                at("l", 5, 1, "ground_grass", growth = 0.2f),
                at("p", 6, 1, "ground_pebble"),
                at("f", 7, 1, "ground_flowers"),
                at("r", 8, 1, GridObject.RIVER_OBJECT_ID),
                at("o", 9, 1, GridObject.POND_OBJECT_ID),
                at("b", 1, 2, "deco_bench"),
            ),
        )
        assertEquals(Ground.TILLED_SOIL, grid.groundAt(1, 1))
        assertEquals(Ground.GREENHOUSE, grid.groundAt(2, 1))
        assertEquals(Ground.PAVING, grid.groundAt(3, 1))
        assertEquals(Ground.PAVING, grid.groundAt(4, 1))
        assertEquals(Ground.LAWN, grid.groundAt(5, 1))
        assertEquals(Ground.GROUND_COVER, grid.groundAt(6, 1))
        assertEquals(Ground.GROUND_COVER, grid.groundAt(7, 1))
        assertEquals(Ground.WATER, grid.groundAt(8, 1))
        assertEquals(Ground.WATER, grid.groundAt(9, 1))
        // Open earth: the wasteland, broken ground (which is not kept in the garden), and under a bench.
        assertEquals(Ground.BARE_EARTH, grid.groundAt(1, 2))
        assertEquals(Ground.BARE_EARTH, grid.groundAt(5, 5))
    }

    @Test
    fun `a log goes on open earth and on a bed, composted or not`() {
        assertEquals(ActionResult.SUCCESS, lay(GardenItem.SEED_SHIITAKE, 5, 5))
        repo.till(6, 6)
        assertEquals(ActionResult.SUCCESS, lay(GardenItem.SEED_SHIITAKE, 6, 6))
        repo.till(7, 7)
        repo.put(repo.objects.values.single { it.isTilledSoil && it.gridX == 7 }.copy(growthProgress = 1f))
        assertEquals(ActionResult.SUCCESS, lay(GardenItem.SEED_SHIITAKE, 7, 7))
        assertEquals(3, repo.plants.count { it.objectId == PlantSpecies.SHIITAKE.objectId })
    }

    @Test
    fun `a log does not go on a lawn, ground cover, paving, water or under glass, and says where it can`() {
        repo.put(at("l", 1, 1, "ground_grass", growth = 0.3f))
        repo.put(at("L", 2, 1, "ground_grass", growth = 1f))
        repo.put(at("p", 3, 1, "ground_pebble"))
        repo.put(at("f", 4, 1, "ground_flowers"))
        repo.put(at("c", 5, 1, GridObject.COBBLESTONE_OBJECT_ID))
        repo.put(at("w", 6, 1, GridObject.WOOD_DECK_OBJECT_ID))
        repo.put(at("r", 7, 1, GridObject.RIVER_OBJECT_ID))
        repo.put(at("o", 8, 1, GridObject.POND_OBJECT_ID))
        repo.put(at("g", 9, 1, GridObject.GREENHOUSE_OBJECT_ID))
        // The river keeps its own word ("not on the river"), which says as much.
        for (x in (1..9) - 7) assertEquals("x=$x", ActionResult.NEEDS_OPEN_GROUND, lay(GardenItem.SEED_SHIITAKE, x, 1))
        assertEquals(ActionResult.TILE_NOT_BUILDABLE, lay(GardenItem.SEED_SHIITAKE, 7, 1))
        assertTrue(repo.plants.isEmpty())
        // Outside the garden is still outside the garden; something standing there is still in the way.
        assertEquals(ActionResult.OUT_OF_BOUNDS, lay(GardenItem.SEED_SHIITAKE, -1, 0))
        repo.put(at("b", 4, 4, "deco_bench"))
        assertEquals(ActionResult.TILE_OCCUPIED, lay(GardenItem.SEED_SHIITAKE, 4, 4))
    }

    @Test
    fun `a crop sown in a bed still wants a bed, wherever else it is put`() {
        repo.progress = repo.progress!!.copy(season = Season.SPRING)
        assertEquals(ActionResult.NEEDS_TILLED_SOIL, lay(GardenItem.SEED_TULIP, 5, 5))
        repo.put(at("l", 1, 1, "ground_grass", growth = 1f))
        assertEquals(ActionResult.NEEDS_TILLED_SOIL, lay(GardenItem.SEED_TULIP, 1, 1))
        repo.till(6, 6)
        assertEquals(ActionResult.SUCCESS, lay(GardenItem.SEED_TULIP, 6, 6))
    }

    @Test
    fun `out of autumn a log cannot be laid, not even on a bed`() {
        repo.progress = repo.progress!!.copy(season = Season.SPRING)
        assertEquals(ActionResult.OUT_OF_SEASON, lay(GardenItem.SEED_SHIITAKE, 5, 5))
        repo.till(6, 6)
        assertEquals(ActionResult.OUT_OF_SEASON, lay(GardenItem.SEED_SHIITAKE, 6, 6))
    }

    @Test
    fun `a log laid on the open earth stays there when the garden opens again`() = runBlocking {
        assertEquals(ActionResult.SUCCESS, lay(GardenItem.SEED_SHIITAKE, 5, 5))
        repo.put(at("tulip", 6, 6, PlantSpecies.TULIP.objectId))
        // Run at every start, for gardens from before the hoe: it tills under the tulip only.
        HoeUseCase(repo, GardenMap.DEFAULT).tillUnderExistingPlants(t0)
        assertTrue(repo.objects.values.any { it.isTilledSoil && it.gridX == 6 && it.gridY == 6 })
        assertFalse(repo.objects.values.any { it.isTilledSoil && it.gridX == 5 && it.gridY == 5 })
    }

    // --- The soil ---

    @Test
    fun `a log on a composted bed gets no head start and leaves the compost be`() {
        repo.till(5, 5)
        val bed = repo.objects.values.single { it.isTilledSoil }
        repo.put(bed.copy(growthProgress = 1f))
        assertEquals(ActionResult.SUCCESS, lay(GardenItem.SEED_SHIITAKE, 5, 5))
        assertEquals(0f, repo.plants.single().growthProgress, 0f)
        assertTrue(repo.objects.getValue(bed.instanceId).isComposted)
    }

    @Test
    fun `a withered log is only taken away, and leaves no compost, unlike a crop of the bed`() = runBlocking {
        repo.till(5, 5)
        val bed = repo.objects.values.single { it.isTilledSoil }
        repo.put(at("log", 5, 5, PlantSpecies.SHIITAKE.objectId, growth = 0.7f).copy(status = PlantStatus.WITHERED))
        assertFalse(RemoveObjectUseCase.leavesCompost(repo.objects.getValue("log")))
        assertEquals(ActionResult.SUCCESS, RemoveObjectUseCase(repo)("log"))
        assertTrue(repo.plants.isEmpty())
        assertFalse(repo.objects.getValue(bed.instanceId).isComposted)
        // A withered tomato on the same bed still does.
        repo.put(at("tomato", 5, 5, PlantSpecies.TOMATO.objectId).copy(status = PlantStatus.WITHERED))
        assertEquals(ActionResult.SUCCESS, RemoveObjectUseCase(repo)("tomato"))
        assertTrue(repo.objects.getValue(bed.instanceId).isComposted)
    }

    @Test
    fun `a withered log's panel and its clearing do not promise compost`() {
        val main = listOf(java.io.File("src/main"), java.io.File("app/src/main")).first { it.isDirectory }
        val dialog = java.io.File(main, "java/com/studioguu/miniaturegarden/ui/hud/ObjectStatusDialog.kt").readText()
        assertTrue(dialog.contains("ground.usesSoil) R.string.hint_withered else R.string.hint_withered_log"))
        val strings = java.io.File(main, "res/values/strings.xml").readText()
        assertTrue(strings.contains("<string name=\"hint_withered_log\">"))
        assertFalse(strings.substringAfter("<string name=\"hint_withered_log\">").substringBefore("</string>").contains("たい肥になり、"))
        val remove = java.io.File(main, "java/com/studioguu/miniaturegarden/ui/hud/RemoveConfirmDialog.kt").readText()
        assertTrue(remove.contains("RemoveObjectUseCase.leavesCompost(gridObject) -> R.string.remove_text_withered"))
        assertTrue(remove.contains("gridObject.status == PlantStatus.WITHERED -> R.string.remove_text_withered_log"))
    }

    // --- Harvests ---

    @Test
    fun `a ripe log is picked and goes back to stage 2, on open earth too`() = runBlocking {
        repo.put(at("log", 5, 5, PlantSpecies.SHIITAKE.objectId, growth = 1f))
        val before = repo.profile!!.coins
        assertEquals(ActionResult.SUCCESS, harvest("log", t0, WeatherType.SUNNY))
        val log = repo.objects.getValue("log")
        assertEquals(PlantSpecies.SHIITAKE.regrowFrom, log.growthProgress, 1e-6f)
        assertEquals(0.5f, log.growthProgress, 1e-6f)
        assertEquals(before + 100, repo.profile!!.coins)
        // The next crop on it pays the smaller amount.
        repo.put(log.copy(growthProgress = 1f, lastUpdatedTimestamp = t0 + hour))
        assertEquals(ActionResult.SUCCESS, harvest("log", t0 + hour, WeatherType.SUNNY))
        assertEquals(before + 100 + 50, repo.profile!!.coins)
    }

    // --- Saved gardens from before ---

    @Test
    fun `a saved garden's pumpkins are taken up and their seeds paid back, once`() = runBlocking {
        repo.till(5, 5)
        repo.put(at("pumpkin", 5, 5, "plant_pumpkin", growth = 0.4f))
        repo.put(at("tulip", 6, 6, PlantSpecies.TULIP.objectId))
        repo.putSlot(InventorySlot(3, "seed_pumpkin", 2))
        repo.putSlot(InventorySlot(4, GardenItem.SEED_MINT.itemId, 1))
        val before = repo.profile!!.coins
        val retire = RetiredCropsUseCase(repo)

        assertEquals(140L, retire())
        assertFalse(repo.objects.containsKey("pumpkin"))
        assertTrue(repo.objects.containsKey("tulip"))
        assertTrue("its bed stays", repo.objects.values.any { it.isTilledSoil && it.gridX == 5 && it.gridY == 5 })
        assertTrue(repo.slots.getValue(3).isEmpty)
        assertEquals(1, repo.slots.getValue(4).amount)
        assertEquals(before + 140, repo.profile!!.coins)

        // Run again at the next start: nothing left to do, nothing paid twice.
        assertEquals(0L, retire())
        assertEquals(before + 140, repo.profile!!.coins)
    }
}
