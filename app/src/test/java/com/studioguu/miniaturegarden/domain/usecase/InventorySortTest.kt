package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.local.mapper.toDomain
import com.studioguu.miniaturegarden.data.local.mapper.toEntity
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.Gift
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventoryFilter
import com.studioguu.miniaturegarden.domain.model.InventoryGroup
import com.studioguu.miniaturegarden.domain.model.InventoryOrder
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.InventorySort
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.UserProfile
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventorySortTest {

    private fun slot(index: Int, item: GardenItem, amount: Int = 1, acquired: Long = 0L) =
        InventorySlot(index, item.itemId, amount, acquired)

    /**
     * A bag of six things and two empty slots. Bought in this order: rock, tulip, cobblestone,
     * grass, fence, sunflower (the sunflower last).
     */
    private val bag = listOf(
        slot(0, GardenItem.SEED_TULIP, amount = 3, acquired = 2),
        slot(1, GardenItem.DECO_ROCK, amount = 1, acquired = 1),
        InventorySlot(2),
        slot(3, GardenItem.FLOOR_COBBLESTONE, amount = 12, acquired = 3),
        slot(4, GardenItem.GROUND_GRASS, amount = 5, acquired = 4),
        slot(5, GardenItem.STRUCT_FENCE, amount = 8, acquired = 5),
        InventorySlot(6),
        slot(7, GardenItem.SEED_SUNFLOWER, amount = 2, acquired = 6),
    )

    private fun items(slots: List<InventorySlot>): List<GardenItem?> = slots.map { GardenItem.fromItemId(it.itemId) }

    private fun arranged(sort: InventorySort, filter: InventoryFilter = InventoryFilter.ALL, season: Season = Season.SPRING) =
        InventoryOrder.arrange(bag, sort, filter, season)

    // --- Each order ---

    @Test
    fun `by default the newest things come first`() {
        assertEquals(InventorySort.ACQUIRED, InventorySort.DEFAULT)
        assertEquals(
            // The sunflower came last, but it is spring: its seeds wait at the back.
            listOf(GardenItem.STRUCT_FENCE, GardenItem.GROUND_GRASS, GardenItem.FLOOR_COBBLESTONE, GardenItem.SEED_TULIP, GardenItem.DECO_ROCK, GardenItem.SEED_SUNFLOWER, null, null),
            items(arranged(InventorySort.ACQUIRED)),
        )
    }

    @Test
    fun `by kind, crops then furniture then greenery then ground, newest first within each`() {
        assertEquals(
            listOf(GardenItem.SEED_TULIP, GardenItem.STRUCT_FENCE, GardenItem.DECO_ROCK, GardenItem.GROUND_GRASS, GardenItem.FLOOR_COBBLESTONE, GardenItem.SEED_SUNFLOWER, null, null),
            items(arranged(InventorySort.KIND)),
        )
    }

    @Test
    fun `by amount, the biggest stacks first`() {
        val sorted = arranged(InventorySort.AMOUNT).filter { !it.isEmpty }
        assertEquals(listOf(12, 8, 5, 3, 1, 2), sorted.map { it.amount })
    }

    @Test
    fun `by season, what is in season first, then all year, then the rest by how soon their season comes`() {
        // Spring: tulip in season; the rest is good all year except the sunflower (summer).
        val spring = items(arranged(InventorySort.SEASON, season = Season.SPRING))
        assertEquals(GardenItem.SEED_TULIP, spring.first())
        assertEquals(GardenItem.SEED_SUNFLOWER, spring.filterNotNull().last())
        // Summer: the sunflower comes to the front and the tulip goes to the back.
        val summer = items(arranged(InventorySort.SEASON, season = Season.SUMMER))
        assertEquals(GardenItem.SEED_SUNFLOWER, summer.first())
        assertEquals(GardenItem.SEED_TULIP, summer.filterNotNull().last())
        // Of two out-of-season things, the one whose season is nearer comes first.
        assertTrue(
            InventoryOrder.seasonRank(GardenItem.SEED_SUNFLOWER, Season.SPRING) <
                InventoryOrder.seasonRank(GardenItem.SEED_TULIP, Season.AUTUMN),
        )
        // The rose has two seasons and is in season in either.
        assertEquals(0, InventoryOrder.seasonRank(GardenItem.SEED_ROSE, Season.AUTUMN))
        assertEquals(1, InventoryOrder.seasonRank(GardenItem.DECO_ROCK, Season.WINTER))
    }

    @Test
    fun `by level, what opens earliest first`() {
        val sorted = items(arranged(InventorySort.LEVEL)).filterNotNull()
        assertEquals(sorted.sortedBy { it.unlockLevel }, sorted)
        // From the start: newest first among them; then the Lv.2 rock, then the Lv.3 sunflower.
        assertEquals(
            listOf(GardenItem.STRUCT_FENCE, GardenItem.GROUND_GRASS, GardenItem.FLOOR_COBBLESTONE, GardenItem.SEED_TULIP, GardenItem.DECO_ROCK, GardenItem.SEED_SUNFLOWER),
            sorted,
        )
        // What the encyclopedia opens comes after everything a level opens.
        assertTrue(GardenItem.DECO_LANTERN.openingRank > GardenItem.STRUCT_COTTAGE.openingRank)
    }

    // --- Tabs ---

    @Test
    fun `a tab narrows the bag down and the order applies to what is left`() {
        assertEquals(
            listOf(GardenItem.SEED_TULIP, GardenItem.SEED_SUNFLOWER),
            items(arranged(InventorySort.ACQUIRED, InventoryFilter.SEEDS)).filterNotNull(),
        )
        assertEquals(
            listOf(GardenItem.SEED_TULIP, GardenItem.SEED_SUNFLOWER),
            items(arranged(InventorySort.SEASON, InventoryFilter.SEEDS, Season.SPRING)).filterNotNull(),
        )
        assertEquals(
            listOf(GardenItem.STRUCT_FENCE, GardenItem.DECO_ROCK),
            items(arranged(InventorySort.AMOUNT, InventoryFilter.FURNITURE)).filterNotNull(),
        )
        assertEquals(
            listOf(GardenItem.FLOOR_COBBLESTONE, GardenItem.GROUND_GRASS),
            items(arranged(InventorySort.AMOUNT, InventoryFilter.OTHER)).filterNotNull(),
        )
    }

    @Test
    fun `the empty slots stay at the end whatever the tab, so the bag keeps its size`() {
        for (filter in InventoryFilter.entries) for (sort in InventorySort.entries) {
            val shown = arranged(sort, filter)
            assertEquals(listOf(2, 6), shown.filter { it.isEmpty }.map { it.slotIndex })
            assertTrue(shown.dropWhile { !it.isEmpty }.all { it.isEmpty })
        }
        // Under "all" nothing goes missing and nothing is shown twice.
        for (sort in InventorySort.entries) {
            assertEquals(bag.map { it.slotIndex }.sorted(), arranged(sort).map { it.slotIndex }.sorted())
        }
    }

    @Test
    fun `every item belongs to a group and to exactly one tab besides all`() {
        for (item in GardenItem.entries) {
            InventoryGroup.of(item)
            assertEquals(item.name, 1, InventoryFilter.entries.filter { it != InventoryFilter.ALL }.count { it.matches(item) })
        }
    }

    @Test
    fun `ties keep a steady order`() {
        val old = listOf(slot(3, GardenItem.GROUND_GRASS), slot(1, GardenItem.GROUND_PEBBLE), slot(2, GardenItem.FLOOR_COBBLESTONE))
        // Slots filled before acquisition was recorded all count as 0: they stay in slot order.
        assertEquals(listOf(1, 2, 3), InventoryOrder.arrange(old, InventorySort.ACQUIRED).map { it.slotIndex })
        assertEquals(listOf(1, 2, 3), InventoryOrder.arrange(old, InventorySort.LEVEL).map { it.slotIndex })
    }

    @Test
    fun `an unknown stored order falls back to the default`() {
        assertEquals(InventorySort.ACQUIRED, InventorySort.fromName("BY_COLOUR"))
        assertEquals(InventorySort.ACQUIRED, InventorySort.fromName(null))
        InventorySort.entries.forEach { assertEquals(it, InventorySort.fromName(it.name)) }
    }

    // --- Recording what came in last ---

    private val repo = FakeGardenRepository().apply {
        profile = UserProfile(coins = 1_000)
        (0 until UserProfile.DEFAULT_INVENTORY_CAPACITY).forEach { putSlot(InventorySlot(it)) }
    }

    private fun newest(): String = InventoryOrder.arrange(repo.slots.values.toList(), InventorySort.ACQUIRED).first().itemId

    @Test
    fun `buying, putting back and opening a gift each make that the newest thing`() = runBlocking {
        val purchase = PurchaseItemUseCase(repo)
        purchase(GardenItem.STRUCT_FENCE)
        assertEquals(GardenItem.STRUCT_FENCE.itemId, newest())
        purchase(GardenItem.SEED_TULIP)
        assertEquals(GardenItem.SEED_TULIP.itemId, newest())

        // Topping up an older stack brings it back to the front.
        purchase(GardenItem.STRUCT_FENCE)
        assertEquals(GardenItem.STRUCT_FENCE.itemId, newest())

        repo.put(GridObject("rock", 1, 1, objectId = GardenItem.DECO_ROCK.objectId, lastUpdatedTimestamp = 0L))
        assertEquals(ActionResult.SUCCESS, StoreObjectUseCase(repo)("rock"))
        assertEquals(GardenItem.DECO_ROCK.itemId, newest())

        repo.put(GridObject("box", 0, 0, objectId = GridObject.GIFT_BOX_OBJECT_ID, lastUpdatedTimestamp = 0L))
        assertEquals(ActionResult.SUCCESS, OpenGiftUseCase(repo)("box"))
        assertEquals(Gift.FIRST.items.single().itemId, newest())
    }

    @Test
    fun `taking something out of the bag does not make it newer`() = runBlocking {
        val purchase = PurchaseItemUseCase(repo)
        purchase(GardenItem.STRUCT_FENCE)
        purchase(GardenItem.STRUCT_FENCE)
        purchase(GardenItem.GROUND_GRASS)
        val fence = repo.slots.values.single { it.itemId == GardenItem.STRUCT_FENCE.itemId }
        PlaceItemUseCase(repo, com.studioguu.miniaturegarden.domain.model.GardenMap.DEFAULT, RecordCollectionUseCase(repo))(fence.slotIndex, 0, 0, 0L)
        assertEquals(fence.acquiredOrder, repo.slots[fence.slotIndex]!!.acquiredOrder)
        assertEquals(GardenItem.GROUND_GRASS.itemId, newest())
    }

    // --- Storage ---

    @Test
    fun `a slot goes into the database and comes back with when it came in`() {
        val stored = slot(4, GardenItem.SEED_ROSE, amount = 2, acquired = 17)
        assertEquals(17L, stored.toEntity().acquiredOrder)
        assertEquals(stored, stored.toEntity().toDomain())
    }

    @Test
    fun `the v9 migration adds the column Room expects`() {
        val schemas = listOf(File("schemas"), File("app/schemas")).first { it.isDirectory }
        val v9 = File(schemas, "com.studioguu.miniaturegarden.data.local.AppDatabase/9.json").readText()
        val table = v9.substringAfter("\"tableName\": \"inventory_slots\"").substringBefore("\"tableName\"")
        assertTrue(table.contains("`acquiredOrder` INTEGER NOT NULL DEFAULT 0"))
        val source = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
            .resolve("java/com/studioguu/miniaturegarden/data/local/AppDatabase.kt").readText()
        assertTrue(source.contains("ALTER TABLE inventory_slots ADD COLUMN acquiredOrder INTEGER NOT NULL DEFAULT 0"))
        assertTrue(
            source.contains("MIGRATION_8_9") &&
                listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
                    .resolve("java/com/studioguu/miniaturegarden/di/DatabaseModule.kt").readText().contains("MIGRATION_8_9"),
        )
    }
}
