package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.CollectionProgress
import com.studioguu.miniaturegarden.domain.model.CollectionRecord
import com.studioguu.miniaturegarden.domain.model.CollectionReward
import com.studioguu.miniaturegarden.domain.model.CollectionTrigger
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionTest {

    private val t0 = 1_700_000_000_000L
    private val sunny = WeatherType.SUNNY

    private val repo = FakeGardenRepository().apply { profile = UserProfile(coins = 100) }
    private val record = RecordCollectionUseCase(repo)
    private val place = PlaceItemUseCase(repo, GardenMap.allGrass(), record)
    private val harvest = HarvestPlantUseCase(repo, CalculateOfflineProgressUseCase(), record)

    private fun grown(id: String, species: PlantSpecies, x: Int = 0) = GridObject(
        instanceId = id, gridX = x, gridY = 0, objectId = species.objectId,
        growthProgress = 1f, lastUpdatedTimestamp = t0,
    )

    // --- Catalog ---

    @Test
    fun `the encyclopedia has a page for every object in the shop, keyed by object id`() {
        val shopObjects = GardenItem.entries.map { it.objectId }.toSet()
        assertEquals(shopObjects, CollectionEntry.entries.map { it.objectId }.toSet())
        assertEquals(CollectionEntry.entries.size, CollectionEntry.entries.map { it.objectId }.toSet().size)
    }

    @Test
    fun `plants are collected by harvesting and everything else by placing`() {
        for (entry in CollectionEntry.entries) {
            val isPlant = entry.objectId.startsWith(GridObject.PLANT_ID_PREFIX)
            assertEquals(entry.name, if (isPlant) CollectionTrigger.HARVEST else CollectionTrigger.PLACEMENT, entry.trigger)
            assertTrue(entry.name, entry.discoveryCoins > 0)
        }
    }

    // --- Recording ---

    @Test
    fun `the first time fills in the page and pays its discovery reward`() = runBlocking {
        val reward = record(PlantSpecies.TULIP.objectId, CollectionTrigger.HARVEST, t0)

        assertEquals(CollectionReward(coins = 20, discovered = CollectionEntry.TULIP), reward)
        assertEquals(CollectionRecord("plant_tulip", count = 1, firstObtainedAt = t0), repo.collection["plant_tulip"])
    }

    @Test
    fun `later times only count up and keep the first date`() = runBlocking {
        record(PlantSpecies.TULIP.objectId, CollectionTrigger.HARVEST, t0)

        val second = record(PlantSpecies.TULIP.objectId, CollectionTrigger.HARVEST, t0 + 5_000)
        val third = record(PlantSpecies.TULIP.objectId, CollectionTrigger.HARVEST, t0 + 9_000)

        assertEquals(CollectionReward.NONE, second)
        assertEquals(CollectionReward.NONE, third)
        assertEquals(CollectionRecord("plant_tulip", count = 3, firstObtainedAt = t0), repo.collection["plant_tulip"])
    }

    @Test
    fun `the wrong kind of event and unknown objects are ignored`() = runBlocking {
        // Planting a tulip is not harvesting it; a hut cannot be harvested.
        assertEquals(CollectionReward.NONE, record(PlantSpecies.TULIP.objectId, CollectionTrigger.PLACEMENT, t0))
        assertEquals(CollectionReward.NONE, record("struct_hut", CollectionTrigger.HARVEST, t0))
        assertEquals(CollectionReward.NONE, record("plant_unknown", CollectionTrigger.HARVEST, t0))
        assertTrue(repo.collection.isEmpty())
    }

    @Test
    fun `filling in the last page adds the completion reward, once`() = runBlocking {
        CollectionEntry.entries.filter { it != CollectionEntry.HUT }.forEach(repo::discover)

        val last = record("struct_hut", CollectionTrigger.PLACEMENT, t0)
        assertEquals(
            CollectionReward(
                coins = CollectionEntry.HUT.discoveryCoins + CollectionEntry.COMPLETION_COINS,
                xp = CollectionEntry.COMPLETION_XP,
                discovered = CollectionEntry.HUT,
                completed = true,
            ),
            last,
        )

        assertEquals(CollectionReward.NONE, record("struct_hut", CollectionTrigger.PLACEMENT, t0))
        assertEquals(CollectionReward.NONE, record("plant_rose", CollectionTrigger.HARVEST, t0))
    }

    @Test
    fun `completion is not paid while any page is missing`() = runBlocking {
        CollectionEntry.entries.filter { it != CollectionEntry.HUT && it != CollectionEntry.ROSE }.forEach(repo::discover)

        val reward = record("struct_hut", CollectionTrigger.PLACEMENT, t0)

        assertFalse(reward.completed)
        assertEquals(CollectionEntry.HUT.discoveryCoins, reward.coins)
        assertEquals(0, reward.xp)
    }

    // --- Progress ---

    @Test
    fun `progress counts discovered pages`() {
        val none = CollectionProgress(emptyList())
        assertEquals(0, none.discovered)
        assertEquals(CollectionEntry.entries.size, none.total)
        assertEquals(0, none.percent)
        assertFalse(none.isComplete)
        assertNull(none.recordOf(CollectionEntry.TULIP))

        val three = CollectionProgress(
            listOf(
                CollectionRecord("plant_tulip", 4, t0),
                CollectionRecord("deco_rock", 1, t0),
                CollectionRecord("ground_grass", 2, t0),
                // A record for something no longer in the catalog must not inflate the figure.
                CollectionRecord("plant_retired", 9, t0),
            )
        )
        assertEquals(3, three.discovered)
        assertEquals(27, three.percent) // 3 of 11, rounded down
        assertTrue(three.isDiscovered(CollectionEntry.ROCK))
        assertFalse(three.isDiscovered(CollectionEntry.HUT))
        assertEquals(4, three.recordOf(CollectionEntry.TULIP)?.count)
    }

    @Test
    fun `one hundred percent means every page`() {
        val all = CollectionEntry.entries.map { CollectionRecord(it.objectId, 1, t0) }
        assertTrue(CollectionProgress(all).isComplete)
        assertEquals(100, CollectionProgress(all).percent)

        val allButOne = CollectionProgress(all.drop(1))
        assertFalse(allButOne.isComplete)
        assertEquals(90, allButOne.percent)
    }

    // --- Through the game actions ---

    @Test
    fun `a first harvest pays the harvest and the discovery together, a second only the harvest`() = runBlocking {
        repo.profile = UserProfile(coins = 0)
        repo.put(grown("a", PlantSpecies.TULIP))
        repo.put(grown("b", PlantSpecies.TULIP, x = 1))

        harvest("a", t0, sunny)
        assertEquals(50L + 20L, repo.profile?.coins)
        assertEquals(50L, repo.profile?.currentXp)
        assertEquals(1, repo.profileSaves) // one signed save, not two

        harvest("b", t0, sunny)
        assertEquals(70L + 50L, repo.profile?.coins)
        assertEquals(2, repo.collection["plant_tulip"]?.count)
    }

    @Test
    fun `a harvest that is refused records nothing`() = runBlocking {
        repo.put(grown("a", PlantSpecies.TULIP).copy(growthProgress = 0.5f))

        assertEquals(ActionResult.NOT_READY, harvest("a", t0, sunny))

        assertTrue(repo.collection.isEmpty())
        assertEquals(100L, repo.profile?.coins)
    }

    @Test
    fun `placing a decoration for the first time pays its discovery coins`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 2))

        place(0, 0, 0, t0)
        assertEquals(115L, repo.profile?.coins)
        assertEquals(0L, repo.profile?.currentXp)

        place(0, 1, 0, t0)
        assertEquals(115L, repo.profile?.coins)
        assertEquals(2, repo.collection["deco_rock"]?.count)
    }

    @Test
    fun `planting a seed does not fill in the plant's page`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TULIP.itemId, 1))

        place(0, 0, 0, t0)

        assertTrue(repo.collection.isEmpty())
        assertEquals(0, repo.profileSaves)
    }

    @Test
    fun `a placement that is refused records nothing`() = runBlocking {
        repo.put(grown("a", PlantSpecies.TULIP))
        repo.putSlot(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 1))

        assertEquals(ActionResult.TILE_OCCUPIED, place(0, 0, 0, t0))

        assertTrue(repo.collection.isEmpty())
        assertEquals(100L, repo.profile?.coins)
    }

    @Test
    fun `building the last missing structure pays placement, discovery and completion in one save`() = runBlocking {
        CollectionEntry.entries.filter { it != CollectionEntry.HUT }.forEach(repo::discover)
        repo.profile = UserProfile(coins = 0, currentXp = 0)
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_HUT.itemId, 1))

        place(0, 2, 2, t0)

        assertEquals((80 + 300).toLong(), repo.profile?.coins)
        assertEquals((60 + 300).toLong(), repo.profile?.currentXp) // hut's own XP + completion
        assertEquals(3, repo.profile?.playerLevel)                 // 360 XP is past the 300 for Lv.3
        assertEquals(1, repo.profileSaves)
        assertTrue(CollectionProgress(repo.getCollection()).isComplete)
    }
}
