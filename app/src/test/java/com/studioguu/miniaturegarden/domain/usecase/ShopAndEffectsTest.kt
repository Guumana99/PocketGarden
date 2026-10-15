package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.PlantSurroundings
import com.studioguu.miniaturegarden.domain.model.PlayerStats
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.StructureEffect
import com.studioguu.miniaturegarden.domain.model.UnlockCondition
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.model.WorkKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopAndEffectsTest {

    private val t0 = 1_700_000_000_000L
    private val hour = 3_600_000L
    private val sunny = WeatherType.SUNNY

    private val calc = CalculateOfflineProgressUseCase()
    private val repo = FakeGardenRepository().apply {
        profile = UserProfile(coins = 1_000)
        repeat(16) { putSlot(InventorySlot(it)) }
    }
    private val purchase = PurchaseItemUseCase(repo)
    private val place = PlaceItemUseCase(repo, GardenMap.DEFAULT, RecordCollectionUseCase(repo))

    private fun obj(id: String, objectId: String, x: Int, y: Int) =
        GridObject(instanceId = id, gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = t0)

    private fun tulip(id: String, x: Int, y: Int) = obj(id, PlantSpecies.TULIP.objectId, x, y)

    // --- The catalogue ---

    @Test
    fun `every shelf of the shop is stocked and every item is on exactly one`() {
        val shelves = ItemCategory.entries.associateWith(GardenItem::shelf)
        assertTrue(shelves.values.none { it.isEmpty() })
        assertEquals(GardenItem.entries.toSet(), shelves.values.flatten().toSet())
        assertEquals(GardenItem.entries.size, shelves.values.sumOf { it.size })

        assertEquals(6, shelves.getValue(ItemCategory.SEED).size)
        assertEquals(5, shelves.getValue(ItemCategory.STRUCTURE).size)
        assertEquals(9, shelves.getValue(ItemCategory.DECORATION).size)
    }

    @Test
    fun `item ids and object ids are unique, and every object can be drawn and has a page`() {
        assertEquals(GardenItem.entries.size, GardenItem.entries.map { it.itemId }.toSet().size)
        assertEquals(GardenItem.entries.size, GardenItem.entries.map { it.objectId }.toSet().size)
        for (item in GardenItem.entries) {
            assertEquals(item.name, item, GardenItem.fromItemId(item.itemId))
            assertTrue(item.name, item.objectId in TileSetDefinition.objectIds)
            assertTrue(item.name, CollectionEntry.fromObjectId(item.objectId) != null)
            assertTrue(item.name, item.price > 0)
        }
    }

    @Test
    fun `a shelf shows what is for sale soonest first, and the cheaper of two that open together`() {
        val decorations = GardenItem.shelf(ItemCategory.DECORATION)
        // From the start, cheapest first ...
        val fromStart = decorations.takeWhile { it.unlock == UnlockCondition.None }
        assertEquals(5, fromStart.size)
        assertEquals(fromStart.map { it.price }.sorted(), fromStart.map { it.price })
        // ... then what a level opens, then what the encyclopedia opens.
        assertEquals(listOf(GardenItem.DECO_ROCK, GardenItem.DECO_BENCH), decorations.subList(5, 7))
        assertEquals(listOf(GardenItem.FLOOR_WOOD, GardenItem.DECO_LANTERN), decorations.subList(7, 9))

        assertEquals(
            listOf(GardenItem.STRUCT_FENCE, GardenItem.STRUCT_WELL, GardenItem.STRUCT_HUT, GardenItem.STRUCT_COTTAGE, GardenItem.FLOOR_GREENHOUSE),
            GardenItem.shelf(ItemCategory.STRUCTURE),
        )
        assertEquals(
            listOf(
                GardenItem.SEED_MINT, GardenItem.SEED_TULIP, GardenItem.SEED_COSMOS, GardenItem.SEED_SNOWDROP,
                GardenItem.SEED_SUNFLOWER, GardenItem.SEED_ROSE,
            ),
            GardenItem.shelf(ItemCategory.SEED),
        )
    }

    @Test
    fun `the new things go into the right layer of a cell`() {
        assertEquals(PlacementKind.FLOOR, PlacementKind.forObjectId(GardenItem.FLOOR_WOOD.objectId))
        assertEquals(PlacementKind.OBJECT, PlacementKind.forObjectId(GardenItem.GROUND_FLOWERS.objectId))
        assertEquals(PlacementKind.OBJECT, PlacementKind.forObjectId(GardenItem.DECO_BENCH.objectId))
        assertEquals(PlacementKind.OBJECT, PlacementKind.forObjectId(GardenItem.DECO_LANTERN.objectId))
        // None of them takes time to build or pays experience: they are simply put down.
        for (item in listOf(GardenItem.FLOOR_WOOD, GardenItem.GROUND_FLOWERS, GardenItem.DECO_BENCH, GardenItem.DECO_LANTERN)) {
            assertEquals(0, item.buildSeconds)
            assertEquals(0, item.placeXp)
        }
    }

    @Test
    fun `decking is paving - things stand on it, and seeds do not go into it`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.FLOOR_WOOD.itemId, 2))
        repo.putSlot(InventorySlot(1, GardenItem.DECO_BENCH.itemId, 1))
        repo.putSlot(InventorySlot(2, GardenItem.SEED_TULIP.itemId, 1))

        assertEquals(ActionResult.SUCCESS, place(0, 2, 2, t0))
        assertEquals(ActionResult.SUCCESS, place(1, 2, 2, t0))             // a bench on the deck
        assertEquals(ActionResult.TILE_OCCUPIED, place(0, 2, 2, t0))       // no second deck under it
        assertEquals(ActionResult.SUCCESS, place(0, 3, 2, t0))
        assertEquals(ActionResult.NEEDS_TILLED_SOIL, place(2, 3, 2, t0))
        assertEquals(ActionResult.TILE_OCCUPIED, HoeUseCase(repo, GardenMap.DEFAULT)(3, 2, t0))
    }

    // --- Prices and unlocks ---

    @Test
    fun `a new player can shape a first garden with what they start with`() {
        val newPlayer = PlayerStats(level = 1, totalXp = 0)
        val forSale = GardenItem.entries.filter { it.isUnlockedFor(newPlayer) }
        // A crop, a structure and several decorations are open from the start.
        assertTrue(forSale.any { it.category == ItemCategory.SEED })
        assertTrue(forSale.any { it.category == ItemCategory.STRUCTURE })
        assertTrue(forSale.count { it.category == ItemCategory.DECORATION } >= 4)
        // Each costs a fifth of the starting coins at most.
        assertTrue(forSale.all { it.price * 5 <= UserProfile.STARTING_COINS })
    }

    @Test
    fun `what opens later costs more than what opens earlier, shelf by shelf`() {
        for (category in ItemCategory.entries) {
            val byLevel = GardenItem.shelf(category).filter { it.unlock !is UnlockCondition.CollectionPages }
            val dearestSoFar = byLevel.runningFold(0) { dearest, item -> maxOf(dearest, item.price) }.drop(1)
            // Going along the shelf, the dearest item so far only ever gets dearer at a new level.
            byLevel.zipWithNext().forEachIndexed { i, (earlier, later) ->
                if (later.unlockLevel > earlier.unlockLevel) assertTrue("${later.name} after ${earlier.name}", later.price >= dearestSoFar[i])
            }
        }
        // Nothing costs more than the symbol building (the late greenhouse costs as much); after
        // those two come the two buildings that pay for themselves.
        val byPrice = GardenItem.entries.sortedByDescending { it.price }
        assertTrue(GardenItem.entries.all { it.price <= GardenItem.STRUCT_COTTAGE.price })
        assertEquals(setOf(GardenItem.STRUCT_COTTAGE, GardenItem.FLOOR_GREENHOUSE), byPrice.take(2).toSet())
        assertEquals(setOf(GardenItem.STRUCT_HUT, GardenItem.STRUCT_WELL), byPrice.drop(2).take(2).toSet())
    }

    @Test
    fun `the encyclopedia opens items that no level opens`() {
        val pageItems = GardenItem.entries.filter { it.unlock is UnlockCondition.CollectionPages }
        assertEquals(setOf(GardenItem.FLOOR_WOOD, GardenItem.DECO_LANTERN), pageItems.toSet())

        val veteran = PlayerStats(level = 10, totalXp = 99_999)
        assertFalse(GardenItem.FLOOR_WOOD.isUnlockedFor(veteran))
        assertFalse(GardenItem.DECO_LANTERN.isUnlockedFor(veteran))

        val beginner = PlayerStats(level = 1, totalXp = 0)
        assertFalse(GardenItem.FLOOR_WOOD.isUnlockedFor(beginner.copy(collectionPages = 1)))
        assertTrue(GardenItem.FLOOR_WOOD.isUnlockedFor(beginner.copy(collectionPages = 2)))
        assertFalse(GardenItem.DECO_LANTERN.isUnlockedFor(beginner.copy(collectionPages = 3)))
        assertTrue(GardenItem.DECO_LANTERN.isUnlockedFor(beginner.copy(collectionPages = 4)))
    }

    @Test
    fun `both can be reached on the first day, with the pages of what is for sale from the start`() {
        val newPlayer = PlayerStats(level = 1, totalXp = 0)
        val pagesWithinReach = GardenItem.entries
            .filter { it.isUnlockedFor(newPlayer) && it.category != ItemCategory.SEED }
            .count { CollectionEntry.fromObjectId(it.objectId) != null }
        val needed = GardenItem.entries.mapNotNull { (it.unlock as? UnlockCondition.CollectionPages)?.pages }.max()
        assertTrue("$pagesWithinReach pages within reach, $needed needed", pagesWithinReach >= needed)
    }

    @Test
    fun `the shop sells a page-locked item only once the pages are filled in, not merely ready`() = runBlocking {
        assertEquals(ActionResult.ITEM_LOCKED, purchase(GardenItem.FLOOR_WOOD))
        assertEquals(1_000L, repo.profile?.coins)

        // Two things placed: their pages are ready, but nobody has filled them in.
        repo.discover(CollectionEntry.GRASS, revealed = false)
        repo.discover(CollectionEntry.PEBBLE, revealed = false)
        assertEquals(ActionResult.ITEM_LOCKED, purchase(GardenItem.FLOOR_WOOD))

        repo.discover(CollectionEntry.GRASS, revealed = true)
        assertEquals(ActionResult.ITEM_LOCKED, purchase(GardenItem.FLOOR_WOOD))
        repo.discover(CollectionEntry.PEBBLE, revealed = true)
        assertEquals(ActionResult.SUCCESS, purchase(GardenItem.FLOOR_WOOD))
        assertEquals(1_000L - GardenItem.FLOOR_WOOD.price, repo.profile?.coins)
        assertEquals(InventorySlot(0, GardenItem.FLOOR_WOOD.itemId, 1), repo.slots.getValue(0).contents())

        // The lantern needs four.
        assertEquals(ActionResult.ITEM_LOCKED, purchase(GardenItem.DECO_LANTERN))
        repo.discover(CollectionEntry.RIVER, revealed = true)
        repo.discover(CollectionEntry.PATH, revealed = true)
        assertEquals(ActionResult.SUCCESS, purchase(GardenItem.DECO_LANTERN))
    }

    @Test
    fun `level-locked items are as before, whatever the encyclopedia holds`() = runBlocking {
        CollectionEntry.entries.forEach { repo.discover(it, revealed = true) }

        assertEquals(ActionResult.ITEM_LOCKED, purchase(GardenItem.DECO_BENCH))   // Lv.2
        assertEquals(ActionResult.ITEM_LOCKED, purchase(GardenItem.STRUCT_WELL))  // Lv.3

        repo.profile = UserProfile(coins = 1_000).gainXp(100) // Lv.2
        assertEquals(ActionResult.SUCCESS, purchase(GardenItem.DECO_BENCH))
        assertEquals(ActionResult.ITEM_LOCKED, purchase(GardenItem.STRUCT_WELL))
    }

    // --- What buildings do for the plants round them ---

    @Test
    fun `a well reaches the ring of cells round it and no further`() {
        // A well is one cell wide and two deep; at (4,4) it stands on (4,4) and (4,5).
        val reach = StructureEffect.WELL.cellsInReach(4, 4)

        assertEquals(10, reach.size) // a 3 x 4 block without the well's own two cells
        assertFalse(GridCell(4, 4) in reach || GridCell(4, 5) in reach)
        for (cell in listOf(GridCell(3, 3), GridCell(5, 3), GridCell(3, 6), GridCell(5, 6), GridCell(3, 4), GridCell(5, 5))) {
            assertTrue("$cell", cell in reach)
        }
        for (cell in listOf(GridCell(2, 4), GridCell(6, 5), GridCell(4, 2), GridCell(4, 7))) {
            assertFalse("$cell", cell in reach)
        }
    }

    @Test
    fun `a hut reaches the ring round its four cells`() {
        val reach = StructureEffect.HUT.cellsInReach(2, 2)
        assertEquals(12, reach.size) // 4 x 4 without the 2 x 2 hut
        assertTrue(GridCell(1, 1) in reach && GridCell(4, 4) in reach && GridCell(1, 3) in reach)
        assertFalse(GridCell(2, 2) in reach || GridCell(3, 3) in reach)
        assertFalse(GridCell(0, 2) in reach || GridCell(5, 2) in reach)
    }

    @Test
    fun `every building has its effect and nothing else has one`() {
        // A symbol building does something else instead: it lets the garden grow.
        for (structure in StructureData.entries.filter { !it.isSymbol }) assertTrue(structure.name, StructureEffect.of(structure) != null)
        assertNull(StructureEffect.of(StructureData.COTTAGE))
        assertEquals(StructureEffect.WELL, StructureEffect.forObjectId("struct_well"))
        assertEquals(StructureEffect.HUT, StructureEffect.forObjectId("struct_hut"))
        assertNull(StructureEffect.forObjectId("struct_fence"))
        assertNull(StructureEffect.forObjectId("deco_lantern"))
        // An effect is a help, never a hindrance.
        for (effect in StructureEffect.entries) {
            assertTrue(effect.moistureDrainFactor in 0.1f..1f)
            assertTrue(effect.growthFactor in 1f..1.5f)
            assertTrue(effect.range >= 1)
        }
    }

    @Test
    fun `a plant beside a well dries out half as fast`() {
        val well = obj("well", "struct_well", 4, 4)
        val near = tulip("near", 3, 4)
        val far = tulip("far", 0, 0)
        val garden = listOf(well, near, far)

        val (_, nearLater, farLater) = calc(garden, t0 + 6 * hour, sunny)

        // A tulip's soil lasts 24 h: 6 h take a quarter of it, or an eighth beside the well.
        assertEquals(0.75f, farLater.moistureLevel, 1e-3f)
        assertEquals(0.875f, nearLater.moistureLevel, 1e-3f)
        // It grows as fast as any other.
        assertEquals(farLater.growthProgress, nearLater.growthProgress, 1e-6f)
    }

    @Test
    fun `a well keeps a plant alive that the sun would have withered`() {
        val garden = listOf(obj("well", "struct_well", 4, 4), tulip("near", 5, 5), tulip("far", 8, 8))

        val (_, near, far) = calc(garden, t0 + 44 * hour, sunny)

        // In the open: dry after 19 h, withered 24 h after that. Beside the well: only just thirsty.
        assertEquals(PlantStatus.WITHERED, far.status)
        assertFalse(near.status == PlantStatus.WITHERED)
        assertTrue(near.stressRate < 0.3f)
        assertTrue(near.growthProgress > far.growthProgress)
    }

    @Test
    fun `a plant beside a hut grows a tenth faster and dries as usual`() {
        val garden = listOf(obj("hut", "struct_hut", 2, 2), tulip("near", 4, 3), tulip("far", 7, 7))

        val (_, near, far) = calc(garden, t0 + 10 * hour, sunny)

        assertEquals(far.growthProgress * 1.1f, near.growthProgress, 1e-4f)
        assertEquals(far.moistureLevel, near.moistureLevel, 1e-6f)
    }

    @Test
    fun `two wells are no better than one, but a well and a hut both count`() {
        val plant = tulip("p", 4, 3)
        val oneWell = listOf(obj("w1", "struct_well", 3, 2), plant)
        val twoWells = oneWell + obj("w2", "struct_well", 5, 3)
        val wellAndHut = oneWell + obj("hut", "struct_hut", 5, 3)

        assertEquals(PlantSurroundings(moistureDrainFactor = 0.5f), PlantSurroundings.of(plant, oneWell))
        assertEquals(PlantSurroundings.of(plant, oneWell), PlantSurroundings.of(plant, twoWells))
        assertEquals(PlantSurroundings(moistureDrainFactor = 0.5f, growthFactor = 1.1f), PlantSurroundings.of(plant, wellAndHut))
        assertTrue(PlantSurroundings.of(plant, oneWell).helps)
    }

    @Test
    fun `only finished buildings help`() {
        val plant = tulip("p", 3, 4)
        for (kind in listOf(WorkKind.BUILDING, WorkKind.READY, WorkKind.DEMOLISHING, WorkKind.PACKING)) {
            val site = obj("well", "struct_well", 4, 4).copy(workKind = kind, workStartedAt = t0, workEndsAt = t0 + 120_000)
            assertSame(kind.name, PlantSurroundings.OPEN_GROUND, PlantSurroundings.of(plant, listOf(site, plant)))
        }
        assertTrue(PlantSurroundings.of(plant, listOf(obj("well", "struct_well", 4, 4), plant)).helps)
    }

    @Test
    fun `things that are not plants, and plants in the open, are unaffected`() {
        val well = obj("well", "struct_well", 4, 4)
        val rock = obj("rock", "deco_rock", 3, 4)
        assertSame(PlantSurroundings.OPEN_GROUND, PlantSurroundings.of(rock, listOf(well, rock)))
        assertSame(PlantSurroundings.OPEN_GROUND, PlantSurroundings.of(tulip("p", 0, 0), listOf(well)))
        assertSame(PlantSurroundings.OPEN_GROUND, PlantSurroundings.of(tulip("p", 0, 0), emptyList()))
        assertFalse(PlantSurroundings.OPEN_GROUND.helps)

        // And without a garden to look at, a plant is reckoned as standing in the open, as before.
        val alone = tulip("p", 3, 4)
        assertEquals(calc(alone, t0 + 6 * hour, sunny), calc(alone, t0 + 6 * hour, sunny, listOf(alone)))
    }

    @Test
    fun `the rain still does what it does, well or no well`() {
        val garden = listOf(obj("well", "struct_well", 4, 4), tulip("near", 3, 4).copy(moistureLevel = 0.1f))

        val (_, near) = calc(garden, t0 + 5 * hour, WeatherType.RAINY)

        assertEquals(1f, near.moistureLevel, 0f)
        assertEquals(0f, near.stressRate, 0f)
    }

    @Test
    fun `watering and harvesting reckon with the well too`() = runBlocking {
        val care = CareForPlantUseCase(repo, calc)
        repo.put(obj("well", "struct_well", 4, 4))
        repo.put(tulip("near", 3, 4))
        repo.put(tulip("far", 0, 0))

        // After 6 h the plant in the open has dried to 75 % and may be watered; the one beside
        // the well is still at 87.5 %, above the mark below which watering is allowed.
        assertEquals(ActionResult.SUCCESS, care.water("far", t0 + 6 * hour, sunny))
        assertEquals(ActionResult.ALREADY_MOIST, care.water("near", t0 + 6 * hour, sunny))
    }

    @Test
    fun `a sync of the whole garden gives each plant the surroundings it stands in`() = runBlocking {
        val sync = SyncGardenUseCase(repo, VerifyIntegrityUseCase(repo, Honest), calc)
        repo.put(obj("well", "struct_well", 4, 4))
        repo.put(tulip("near", 3, 4))
        repo.put(tulip("far", 0, 0))

        sync(t0 + 6 * hour, sunny)

        assertEquals(0.875f, repo.objects.getValue("near").moistureLevel, 1e-3f)
        assertEquals(0.75f, repo.objects.getValue("far").moistureLevel, 1e-3f)
    }

    private object Honest : com.studioguu.miniaturegarden.domain.repository.SecurityRepository {
        override fun isSignatureValid(profile: UserProfile) = true
        override fun isTimeTampered(profile: UserProfile) = false
        override fun isTamperFlagged() = false
        override fun setTamperFlag(flagged: Boolean) = Unit
    }
}
