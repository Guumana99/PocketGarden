package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.CollectionCategory
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.CollectionProgress
import com.studioguu.miniaturegarden.domain.model.CollectionRecord
import com.studioguu.miniaturegarden.domain.model.CollectionTrigger
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PageState
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
    private val reveal = RevealCollectionEntryUseCase(repo)
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

    @Test
    fun `every page belongs to one chapter and no chapter is empty`() {
        val byCategory = CollectionCategory.entries.associateWith(CollectionEntry::inCategory)
        assertTrue(byCategory.values.none { it.isEmpty() })
        assertEquals(CollectionEntry.entries.size, byCategory.values.sumOf { it.size })

        assertEquals(
            listOf(
                CollectionEntry.TULIP, CollectionEntry.SUNFLOWER, CollectionEntry.ROSE, CollectionEntry.COSMOS, CollectionEntry.SNOWDROP, CollectionEntry.MINT,
                CollectionEntry.TOMATO, CollectionEntry.STRAWBERRY, CollectionEntry.WATERMELON, CollectionEntry.LAVENDER,
                CollectionEntry.PUMPKIN, CollectionEntry.POINSETTIA, CollectionEntry.PAPRIKA, CollectionEntry.DAIKON,
            ),
            byCategory[CollectionCategory.CROPS],
        )
        assertEquals(
            listOf(
                CollectionEntry.GRASS, CollectionEntry.PEBBLE, CollectionEntry.RIVER, CollectionEntry.PATH,
                CollectionEntry.FLOWERS, CollectionEntry.DECK,
            ),
            byCategory[CollectionCategory.TERRAIN],
        )
        assertEquals(
            listOf(
                CollectionEntry.ROCK, CollectionEntry.FENCE, CollectionEntry.BENCH, CollectionEntry.LANTERN,
                CollectionEntry.WELL, CollectionEntry.HUT, CollectionEntry.COTTAGE, CollectionEntry.GREENHOUSE,
            ),
            byCategory[CollectionCategory.STRUCTURES],
        )
    }

    // --- Recording: meeting the condition ---

    @Test
    fun `the first time makes the page ready but does not fill it in`() = runBlocking {
        assertEquals(CollectionEntry.TULIP, record(PlantSpecies.TULIP.objectId, CollectionTrigger.HARVEST, t0))

        assertEquals(CollectionRecord("plant_tulip", count = 1, firstObtainedAt = t0, revealed = false), repo.collection["plant_tulip"])
        assertEquals(PageState.READY, CollectionProgress(repo.getCollection()).stateOf(CollectionEntry.TULIP))
        assertEquals(0, repo.profileSaves) // nothing is paid for meeting the condition
    }

    @Test
    fun `later times only count up`() = runBlocking {
        record("deco_rock", CollectionTrigger.PLACEMENT, t0)
        assertNull(record("deco_rock", CollectionTrigger.PLACEMENT, t0 + 1))
        assertNull(record("deco_rock", CollectionTrigger.PLACEMENT, t0 + 2))

        assertEquals(CollectionRecord("deco_rock", count = 3, firstObtainedAt = t0), repo.collection["deco_rock"])
    }

    @Test
    fun `counting up leaves a filled-in page filled in`() = runBlocking {
        repo.discover(CollectionEntry.ROCK, revealed = true)

        record("deco_rock", CollectionTrigger.PLACEMENT, t0)

        assertEquals(2, repo.collection["deco_rock"]?.count)
        assertTrue(repo.collection.getValue("deco_rock").revealed)
    }

    @Test
    fun `the wrong kind of action and unknown things record nothing`() = runBlocking {
        assertNull(record(PlantSpecies.TULIP.objectId, CollectionTrigger.PLACEMENT, t0))
        assertNull(record("struct_hut", CollectionTrigger.HARVEST, t0))
        assertNull(record("plant_unknown", CollectionTrigger.HARVEST, t0))
        assertTrue(repo.collection.isEmpty())
    }

    // --- Revealing: the player's tap ---

    @Test
    fun `revealing a ready page fills it in and pays its discovery coins`() = runBlocking {
        repo.discover(CollectionEntry.SUNFLOWER, revealed = false)

        assertEquals(40, reveal(CollectionEntry.SUNFLOWER))

        assertTrue(repo.collection.getValue("plant_sunflower").revealed)
        assertEquals(140L, repo.profile?.coins)
        assertEquals(1, repo.profileSaves)
        assertEquals(PageState.REVEALED, CollectionProgress(repo.getCollection()).stateOf(CollectionEntry.SUNFLOWER))
    }

    @Test
    fun `a page pays only once however often it is tapped`() = runBlocking {
        repo.discover(CollectionEntry.HUT, revealed = false)

        assertEquals(80, reveal(CollectionEntry.HUT))
        assertNull(reveal(CollectionEntry.HUT))
        assertNull(reveal(CollectionEntry.HUT))

        assertEquals(180L, repo.profile?.coins)
        assertEquals(1, repo.profileSaves)
    }

    @Test
    fun `a locked page cannot be revealed`() = runBlocking {
        assertNull(reveal(CollectionEntry.ROSE))

        assertTrue(repo.collection.isEmpty())
        assertEquals(100L, repo.profile?.coins)
        assertEquals(0, repo.profileSaves)
    }

    @Test
    fun `revealing keeps the count and the date`() = runBlocking {
        repo.collection["deco_rock"] = CollectionRecord("deco_rock", count = 7, firstObtainedAt = t0)

        reveal(CollectionEntry.ROCK)

        assertEquals(CollectionRecord("deco_rock", count = 7, firstObtainedAt = t0, revealed = true), repo.collection["deco_rock"])
    }

    @Test
    fun `filling in every page pays nothing beyond the pages themselves`() = runBlocking {
        CollectionEntry.entries.forEach { repo.discover(it, revealed = false) }
        repo.profile = UserProfile(coins = 0)

        CollectionEntry.entries.forEach { reveal(it) }

        assertEquals(CollectionEntry.entries.sumOf { it.discoveryCoins }.toLong(), repo.profile?.coins)
        assertEquals(0L, repo.profile?.currentXp)
        assertEquals(100, CollectionProgress(repo.getCollection()).percent)
    }

    // --- Progress ---

    @Test
    fun `progress tells locked, ready and revealed pages apart`() {
        val none = CollectionProgress(emptyList())
        assertEquals(0, none.revealed)
        assertEquals(0, none.ready)
        assertEquals(0, none.percent)
        assertEquals(28, none.total)
        assertEquals(PageState.LOCKED, none.stateOf(CollectionEntry.TULIP))

        val some = CollectionProgress(
            listOf(
                CollectionRecord("plant_tulip", 4, t0, revealed = true),
                CollectionRecord("deco_rock", 1, t0, revealed = true),
                CollectionRecord("struct_well", 1, t0),
                CollectionRecord("something_removed_from_the_game", 9, t0, revealed = true),
            )
        )
        assertEquals(2, some.revealed)
        assertEquals(1, some.ready)
        assertEquals(7, some.percent) // 2 of 28, rounded down; ready pages do not count yet
        assertEquals(PageState.REVEALED, some.stateOf(CollectionEntry.TULIP))
        assertEquals(PageState.READY, some.stateOf(CollectionEntry.WELL))
        assertEquals(PageState.LOCKED, some.stateOf(CollectionEntry.HUT))
        assertEquals(4, some.recordOf(CollectionEntry.TULIP)?.count)

        assertEquals(0, some.readyIn(CollectionCategory.CROPS))
        assertEquals(0, some.readyIn(CollectionCategory.TERRAIN))
        assertEquals(1, some.readyIn(CollectionCategory.STRUCTURES))
    }

    @Test
    fun `one hundred percent means every page filled in`() {
        val all = CollectionEntry.entries.map { CollectionRecord(it.objectId, 1, t0, revealed = true) }
        assertEquals(100, CollectionProgress(all).percent)
        assertEquals(96, CollectionProgress(all.drop(1)).percent) // 27 of 28, rounded down

        val allReady = CollectionEntry.entries.map { CollectionRecord(it.objectId, 1, t0) }
        assertEquals(0, CollectionProgress(allReady).percent)
        assertEquals(28, CollectionProgress(allReady).ready)
    }

    // --- Through the game actions ---

    @Test
    fun `a first harvest pays the harvest only and makes the page ready`() = runBlocking {
        repo.profile = UserProfile(coins = 0)
        repo.put(grown("a", PlantSpecies.TULIP))
        repo.put(grown("b", PlantSpecies.TULIP, x = 1))

        harvest("a", t0, sunny)
        assertEquals(50L, repo.profile?.coins)
        assertEquals(50L, repo.profile?.currentXp)
        assertEquals(1, repo.profileSaves)
        assertFalse(repo.collection.getValue("plant_tulip").revealed)

        harvest("b", t0, sunny)
        assertEquals(100L, repo.profile?.coins)
        assertEquals(2, repo.collection["plant_tulip"]?.count)

        // The discovery coins come when the player fills the page in.
        assertEquals(20, reveal(CollectionEntry.TULIP))
        assertEquals(120L, repo.profile?.coins)
    }

    @Test
    fun `a harvest that is refused records nothing`() = runBlocking {
        repo.put(grown("a", PlantSpecies.TULIP).copy(growthProgress = 0.5f))

        assertEquals(ActionResult.NOT_READY, harvest("a", t0, sunny))

        assertTrue(repo.collection.isEmpty())
        assertEquals(100L, repo.profile?.coins)
    }

    @Test
    fun `placing a decoration makes its page ready and pays nothing`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 2))

        place(0, 0, 0, t0)
        place(0, 1, 0, t0)

        assertEquals(100L, repo.profile?.coins)
        assertEquals(0, repo.profileSaves)
        assertEquals(CollectionRecord("deco_rock", count = 2, firstObtainedAt = t0), repo.collection["deco_rock"])
    }

    @Test
    fun `planting a seed does not open the plant's page`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TULIP.itemId, 1))
        repo.till(0, 0)

        assertEquals(ActionResult.SUCCESS, place(0, 0, 0, t0))

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
    fun `building the last missing structure earns its own XP and no completion bonus`() = runBlocking {
        CollectionEntry.entries.filter { it != CollectionEntry.HUT }.forEach { repo.discover(it, revealed = true) }
        repo.profile = UserProfile(coins = 0, currentXp = 0)
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_HUT.itemId, 1))

        place(0, 2, 2, t0)
        reveal(CollectionEntry.HUT)

        assertEquals(80L, repo.profile?.coins)      // the hut's page, nothing more
        assertEquals(60L, repo.profile?.currentXp)  // the hut's own placement XP
        assertEquals(100, CollectionProgress(repo.getCollection()).percent)
    }
}
