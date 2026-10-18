package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.ExpansionCheck
import com.studioguu.miniaturegarden.domain.model.Footprint
import com.studioguu.miniaturegarden.domain.model.GardenExpansion
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.LevelTable
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WorkKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolBuildingTest {

    private val t0 = 1_700_000_000_000L

    private val repo = FakeGardenRepository().apply {
        profile = UserProfile(coins = 1_000, playerLevel = 6, currentXp = LevelTable.xpForLevel(6))
        progress = GardenProgress()
        (0 until UserProfile.DEFAULT_INVENTORY_CAPACITY).forEach { putSlot(InventorySlot(it)) }
    }
    private val storedMap = StoredGardenMap(repo)
    private val purchase = PurchaseItemUseCase(repo)
    private val place = PlaceItemUseCase(repo, storedMap, RecordCollectionUseCase(repo))
    private val move = MoveObjectUseCase(repo, storedMap)
    private val store = StoreObjectUseCase(repo)
    private val expand = ExpandGardenUseCase(repo)

    private fun cottage(x: Int, y: Int, work: WorkKind = WorkKind.NONE) = GridObject(
        instanceId = "cottage", gridX = x, gridY = y, objectId = StructureData.COTTAGE.objectId,
        lastUpdatedTimestamp = t0, workKind = work,
    )

    private val cottageSlots get() = repo.slots.values.filter { it.itemId == GardenItem.STRUCT_COTTAGE.itemId && !it.isEmpty }

    // --- The cottage itself ---

    @Test
    fun `the cottage is a 3x3 symbol building sold among the structures`() {
        assertEquals(Footprint(3, 3), StructureData.COTTAGE.footprint)
        assertTrue(StructureData.COTTAGE.isSymbol)
        assertEquals(listOf(StructureData.COTTAGE), StructureData.entries.filter { it.isSymbol })
        assertTrue(GardenItem.STRUCT_COTTAGE.isSymbol)
        assertEquals(ItemCategory.STRUCTURE, GardenItem.STRUCT_COTTAGE.category)
        assertTrue(GardenItem.STRUCT_COTTAGE.buildSeconds > 0)
        assertTrue(GardenItem.STRUCT_COTTAGE.objectId in TileSetDefinition.objectIds)
        assertEquals(CollectionEntry.COTTAGE, CollectionEntry.fromObjectId(StructureData.COTTAGE.objectId))
        assertFalse(GardenItem.STRUCT_HUT.isSymbol)
    }

    // --- One per garden ---

    @Test
    fun `a cottage can be bought once, and not again while it is in the bag`() = runBlocking {
        assertEquals(ActionResult.SUCCESS, purchase(GardenItem.STRUCT_COTTAGE))
        assertEquals(ActionResult.ONLY_ONE_SYMBOL, purchase(GardenItem.STRUCT_COTTAGE))
        assertEquals(1, cottageSlots.sumOf { it.amount })
        assertEquals(1_000L - GardenItem.STRUCT_COTTAGE.price, repo.profile?.coins)
    }

    @Test
    fun `nor while one stands in the garden, built or still going up`() = runBlocking {
        repo.put(cottage(2, 2, WorkKind.BUILDING))
        assertEquals(ActionResult.ONLY_ONE_SYMBOL, purchase(GardenItem.STRUCT_COTTAGE))
        repo.put(cottage(2, 2))
        assertEquals(ActionResult.ONLY_ONE_SYMBOL, purchase(GardenItem.STRUCT_COTTAGE))
        assertTrue(cottageSlots.isEmpty())
    }

    @Test
    fun `the shop shows the cottage as owned while it is in the bag or in the garden`() {
        val emptyBag = listOf(InventorySlot(0))
        assertFalse(ownsSymbolBuilding(emptyList(), emptyBag))
        assertTrue(ownsSymbolBuilding(emptyList(), listOf(InventorySlot(0, GardenItem.STRUCT_COTTAGE.itemId, 1))))
        assertTrue(ownsSymbolBuilding(listOf(cottage(2, 2, WorkKind.BUILDING)), emptyBag))
        assertTrue(ownsSymbolBuilding(listOf(cottage(2, 2)), emptyBag))
        // Other buildings are not one of a kind.
        val hut = GridObject(instanceId = "hut", gridX = 0, gridY = 0, objectId = StructureData.HUT.objectId, lastUpdatedTimestamp = t0)
        assertFalse(ownsSymbolBuilding(listOf(hut), listOf(InventorySlot(0, GardenItem.STRUCT_HUT.itemId, 1))))
    }

    @Test
    fun `an owned cottage stays on the shelf, marked as owned instead of for sale`() {
        val main = listOf(java.io.File("src/main"), java.io.File("app/src/main")).first { it.isDirectory }
        val shop = java.io.File(main, "java/com/studioguu/miniaturegarden/ui/shop/ShopDialog.kt").readText()
        assertTrue(shop.contains("owned = item.isSymbol && ownsSymbol"))
        assertTrue(shop.contains("R.string.shop_owned"))
        val strings = java.io.File(main, "res/values/strings.xml").readText()
        assertTrue(strings.contains("<string name=\"shop_owned\">"))
    }

    @Test
    fun `once it is gone another can be bought`() = runBlocking {
        repo.put(cottage(2, 2))
        repo.deleteGridObject("cottage")
        assertEquals(ActionResult.SUCCESS, purchase(GardenItem.STRUCT_COTTAGE))
    }

    @Test
    fun `a second cottage is never placed, even with two in the bag`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_COTTAGE.itemId, 2))

        assertEquals(ActionResult.SUCCESS, place(0, 0, 0, t0))
        assertEquals(ActionResult.ONLY_ONE_SYMBOL, place(0, 5, 5, t0))

        val standing = repo.objects.values.filter { it.isSymbol }
        assertEquals(1, standing.size)
        // It goes up over its building time, covering all nine cells.
        assertEquals(WorkKind.BUILDING, standing.single().workKind)
        assertEquals(9, standing.single().occupiedCells().size)
        assertEquals(1, repo.slots[0]?.amount)
    }

    @Test
    fun `a finished cottage can be moved and put back in the bag`() = runBlocking {
        repo.put(cottage(0, 0))

        assertEquals(ActionResult.SUCCESS, move("cottage", 5, 5))
        assertEquals(5 to 5, repo.objects["cottage"]!!.let { it.gridX to it.gridY })
        // Its whole footprint has to fit: from (7, 7) it would hang over the edge.
        assertEquals(ActionResult.OUT_OF_BOUNDS, move("cottage", 7, 7))

        assertEquals(ActionResult.SUCCESS, store("cottage"))
        assertTrue(repo.objects.values.none { it.isSymbol })
        assertEquals(1, cottageSlots.sumOf { it.amount })
    }

    // --- Growing the garden ---

    @Test
    fun `the garden grows 9 to 11 to 13 and no further`() {
        assertEquals(GridObject.GRID_SIZE, GardenProgress().gardenSize)
        assertEquals(11, GardenExpansion.nextStage(9)?.size)
        assertEquals(13, GardenExpansion.nextStage(11)?.size)
        assertNull(GardenExpansion.nextStage(13))
        assertEquals(13, GardenExpansion.MAX_SIZE)
    }

    @Test
    fun `growing needs a finished symbol building, and the last step a higher level`() {
        val built = listOf(cottage(3, 3))
        assertEquals(ExpansionCheck.NEEDS_SYMBOL, GardenExpansion.check(9, emptyList(), 99))
        assertEquals(ExpansionCheck.NEEDS_SYMBOL, GardenExpansion.check(9, listOf(cottage(3, 3, WorkKind.BUILDING)), 99))
        assertEquals(ExpansionCheck.NEEDS_SYMBOL, GardenExpansion.check(9, listOf(cottage(3, 3, WorkKind.READY)), 99))
        // A hut is a building, but not a symbol.
        assertEquals(
            ExpansionCheck.NEEDS_SYMBOL,
            GardenExpansion.check(9, listOf(cottage(3, 3).copy(objectId = StructureData.HUT.objectId)), 99),
        )
        assertEquals(ExpansionCheck.READY, GardenExpansion.check(9, built, 1))
        assertEquals(ExpansionCheck.NEEDS_LEVEL, GardenExpansion.check(11, built, 7))
        assertEquals(ExpansionCheck.READY, GardenExpansion.check(11, built, 8))
        assertEquals(ExpansionCheck.AT_MAX_SIZE, GardenExpansion.check(13, built, 99))
    }

    @Test
    fun `growing adds a ring all round and moves everything one cell along`() = runBlocking {
        repo.put(cottage(0, 0))
        repo.till(8, 8)

        assertEquals(ActionResult.SUCCESS, expand())

        assertEquals(11, repo.progress?.gardenSize)
        assertEquals(11, storedMap.current().size)
        assertEquals(1 to 1, repo.objects["cottage"]!!.let { it.gridX to it.gridY })
        assertEquals(9 to 9, repo.objects["soil_8_8"]!!.let { it.gridX to it.gridY })
        // The new outer ring is lawn that can be used straight away.
        val hoe = HoeUseCase(repo, storedMap)
        assertEquals(ActionResult.SUCCESS, hoe(0, 10, t0))
        assertEquals(ActionResult.SUCCESS, hoe(10, 0, t0))
        assertEquals(ActionResult.OUT_OF_BOUNDS, hoe(11, 0, t0))
    }

    @Test
    fun `the garden does not grow before it may`() = runBlocking {
        repo.till(4, 4)
        assertEquals(ActionResult.EXPANSION_LOCKED, expand())
        assertEquals(9, repo.progress?.gardenSize)
        assertEquals(4 to 4, repo.objects["soil_4_4"]!!.let { it.gridX to it.gridY })

        repo.put(cottage(0, 0))
        assertEquals(ActionResult.SUCCESS, expand())
        // Lv.6 is not enough for 13 x 13.
        assertEquals(ActionResult.EXPANSION_LOCKED, expand())
        assertEquals(11, repo.progress?.gardenSize)

        repo.profile = repo.profile!!.copy(playerLevel = 8, currentXp = LevelTable.xpForLevel(8))
        assertEquals(ActionResult.SUCCESS, expand())
        assertEquals(ActionResult.GARDEN_AT_MAX_SIZE, expand())
        assertEquals(13, repo.progress?.gardenSize)
        assertEquals(2 to 2, repo.objects["cottage"]!!.let { it.gridX to it.gridY })
    }

    @Test
    fun `a garden without stored progress is the starting size`() = runBlocking {
        repo.progress = null
        assertEquals(GardenMap.DEFAULT.size, storedMap.current().size)
    }
}
