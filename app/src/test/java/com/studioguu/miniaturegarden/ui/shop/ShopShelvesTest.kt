package com.studioguu.miniaturegarden.ui.shop

import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.LevelTable
import com.studioguu.miniaturegarden.domain.model.PlayerStats
import com.studioguu.miniaturegarden.domain.model.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What stands on the shelves of the three shops, and how each thing is tagged. */
class ShopShelvesTest {

    private fun level(n: Int) = PlayerStats(n, LevelTable.xpForLevel(n))
    private val newcomer = level(1)
    private val veteran = level(20).copy(collectionPages = 99)

    @Test
    fun `there are three shops, one for each shelf of the item master, and the seed shop is the way in`() {
        assertEquals(3, ShopStage.entries.size)
        assertEquals(ItemCategory.entries.toSet(), ShopStage.entries.map { it.category }.toSet())
        assertEquals(ShopStage.SEEDS, ShopStage.ENTRANCE)
    }

    @Test
    fun `every item on sale is sold in exactly one shop, in some season`() {
        // The rocks and fences but the first are on no shelf: they are in its list.
        for (item in GardenItem.entries.filter { it.inShop && it !in GardenItem.OFF_THE_SHELF }) {
            val shops = ShopStage.entries.filter { stage ->
                Season.entries.any { season -> ShopShelves.stock(stage, season, veteran, 10_000, false).any { it.item == item } }
            }
            assertEquals(item.name, listOf(ShopShelves.stageOf(item)), shops)
        }
    }

    @Test
    fun `the seed shop only stocks what is in season`() {
        val spring = ShopShelves.stock(ShopStage.SEEDS, Season.SPRING, veteran, 10_000, false).map { it.item }
        assertTrue(GardenItem.SEED_TULIP in spring)
        assertFalse(GardenItem.SEED_SUNFLOWER in spring)
        assertTrue(GardenItem.SEED_MINT in spring)
        assertEquals(GardenItem.shelf(ItemCategory.SEED, Season.SPRING), spring)
    }

    @Test
    fun `each thing is tagged for the player - locked, short of coins, for sale`() {
        val stock = ShopShelves.stock(ShopStage.WORKSHOP, Season.SPRING, newcomer, coins = 15, ownsSymbol = false).associate { it.item to it.tag }
        assertEquals(ShelfTag.FOR_SALE, stock[GardenItem.STRUCT_FENCE])   // 15 coins, from the start
        assertEquals(ShelfTag.LOCKED, stock[GardenItem.STRUCT_WELL])      // Lv.3
        assertEquals(ShelfTag.LOCKED, stock[GardenItem.STRUCT_COTTAGE])

        val poorer = ShopShelves.stock(ShopStage.WORKSHOP, Season.SPRING, newcomer, coins = 14, ownsSymbol = false)
        assertEquals(ShelfTag.SHORT_OF_COINS, poorer.first { it.item == GardenItem.STRUCT_FENCE }.tag)
    }

    @Test
    fun `the cottage stays on the shelf as owned once it is the player's, rather than locked or for sale`() {
        val owned = ShopShelves.stock(ShopStage.WORKSHOP, Season.SPRING, veteran, 10_000, ownsSymbol = true)
        assertEquals(ShelfTag.OWNED, owned.first { it.item == GardenItem.STRUCT_COTTAGE }.tag)
        // Only the one-of-a-kind building is affected.
        assertEquals(ShelfTag.FOR_SALE, owned.first { it.item == GardenItem.STRUCT_HUT }.tag)
        // A cottage the player could not have bought yet is simply locked.
        assertEquals(ShelfTag.LOCKED, ShopShelves.stock(ShopStage.WORKSHOP, Season.SPRING, newcomer, 10_000, true).first { it.item == GardenItem.STRUCT_COTTAGE }.tag)
    }

    @Test
    fun `the shelves know how many of each thing are in the bag, over all its slots`() {
        val slots = listOf(
            com.studioguu.miniaturegarden.domain.model.InventorySlot(0, GardenItem.SEED_TULIP.itemId, 5),
            com.studioguu.miniaturegarden.domain.model.InventorySlot(1, GardenItem.STRUCT_FENCE.itemId, 2),
            com.studioguu.miniaturegarden.domain.model.InventorySlot(2, GardenItem.SEED_TULIP.itemId, 3),
            com.studioguu.miniaturegarden.domain.model.InventorySlot(3),
        )
        val held = ShopShelves.heldIn(slots)
        assertEquals(mapOf(GardenItem.SEED_TULIP to 8, GardenItem.STRUCT_FENCE to 2), held)
        val stock = ShopShelves.stock(ShopStage.SEEDS, Season.SPRING, veteran, 100, false, held)
        assertEquals(8, stock.first { it.item == GardenItem.SEED_TULIP }.held)
        assertEquals(0, stock.first { it.item == GardenItem.SEED_MINT }.held)
    }

    @Test
    fun `the one fence or rock on the shelf counts every kind of it in the bag, and each kind in the list its own`() {
        val held = mapOf(GardenItem.STRUCT_FENCE to 2, GardenItem.STRUCT_FENCE_WHITE to 3, GardenItem.STRUCT_FENCE_BAMBOO to 4, GardenItem.DECO_ROCK_MOSSY to 5)
        val fence = ShopShelves.stock(ShopStage.WORKSHOP, Season.SPRING, veteran, 100, false, held).first { it.item == GardenItem.STRUCT_FENCE }
        assertEquals(9, fence.heldOnShelf)
        assertEquals(2, fence.held)
        val rock = ShopShelves.stock(ShopStage.SUNDRIES, Season.SPRING, veteran, 100, false, held).first { it.item == GardenItem.DECO_ROCK }
        assertEquals(5, rock.heldOnShelf)
        assertEquals(0, rock.held)
        assertEquals(listOf(2, 3, 4), ShopShelves.fenceKinds(veteran, 100, held).map { it.held })
        assertEquals(listOf(0, 0, 5, 0, 0), ShopShelves.rockKinds(veteran, 100, held).map { it.held })
    }

    @Test
    fun `everything else on a shelf says on its tag what it has in the bag`() {
        val everything = GardenItem.entries.filter { it.inShop }.associateWith { 1 }
        for (stage in ShopStage.entries) for (season in Season.entries) {
            for (thing in ShopShelves.stock(stage, season, veteran, 100, false, everything)) {
                val expected = GardenItem.kindsUnder(thing.item)?.size ?: 1
                assertEquals(thing.item.name, expected, thing.heldOnShelf)
            }
        }
    }

    @Test
    fun `the things opened by encyclopedia pages are the deck and the lantern, and they are meant to be there`() {
        // Their tags read "図鑑で解放", and their cards how many pages: what opens them, not stray data.
        val byPages = GardenItem.entries.filter { it.unlock is com.studioguu.miniaturegarden.domain.model.UnlockCondition.CollectionPages }
        assertEquals(setOf(GardenItem.FLOOR_WOOD, GardenItem.DECO_LANTERN), byPages.toSet())
        val newcomer = ShopShelves.stock(ShopStage.SUNDRIES, Season.SPRING, newcomer, 10_000, false)
        assertTrue(byPages.all { item -> newcomer.first { it.item == item }.tag == ShelfTag.LOCKED })
    }

    @Test
    fun `the shop's words are short - no season named on the shelf note, and the river is a cosy kit`() {
        val main = listOf(java.io.File("src/main"), java.io.File("app/src/main")).first { it.isDirectory }
        val strings = java.io.File(main, "res/values/strings.xml").readText()
        fun string(name: String) = Regex("<string name=\"$name\">(.*?)</string>").find(strings)?.groupValues?.get(1)
        assertEquals("季節が変わると入れ替わります", string("shop_season_caption"))
        assertEquals(null, string("shop_card_seasons"))
        assertEquals("小川", string("item_tile_river"))
        assertTrue(string("shop_locked_pages")!!.contains("図鑑"))
        assertTrue(string("shop_held")!!.startsWith("所持数"))
    }

    @Test
    fun `things are set out a few to a shelf, in the master's order`() {
        val stock = ShopShelves.stock(ShopStage.SUNDRIES, Season.SPRING, veteran, 10_000, false)
        val shelves = ShopShelves.shelves(stock)
        assertTrue(shelves.all { it.size in 1..ShopShelves.PER_SHELF })
        assertTrue(shelves.dropLast(1).all { it.size == ShopShelves.PER_SHELF })
        assertEquals(stock, shelves.flatten())
        assertEquals(emptyList<List<ShelfItem>>(), ShopShelves.shelves(emptyList()))
    }

    @Test
    fun `the names on the shelves are all one size, small enough for the longest to fit on one line`() {
        // Device feedback 11/03: at 360dp wide with text at 1.3 times, the lavender's and the
        // shiitake's names were cut short.
        val main = listOf(java.io.File("src/main"), java.io.File("app/src/main")).first { it.isDirectory }
        val shop = java.io.File(main, "java/com/studioguu/miniaturegarden/ui/shop/ShopStageScreen.kt").readText().replace("\r\n", "\n")
        assertTrue(shop.contains("val nameStyle = shelfNameStyle(maxWidth / ShopShelves.PER_SHELF)"))
        assertTrue(shop.contains("            style = nameStyle,\n            color = GardenColors.Ink,\n            maxLines = 1,\n            softWrap = false,"))
        // Measured over every name that can be on a shelf, never larger than the theme's size.
        assertTrue(shop.contains("ItemCategory.entries.flatMap { GardenItem.shelf(it) }"))
        assertTrue(shop.contains("if (widest <= room) base else base.copy(fontSize = base.fontSize * (room / widest * SHELF_NAME_FIT))"))
        // Each thing has a third of the shelf.
        assertTrue(shop.contains("ShelfThing(thing, picked = thing.item == inHand, nameStyle, Modifier.weight(1f), onInspect)"))
        assertFalse(shop.contains("ITEM_WIDTH"))
    }

    @Test
    fun `the shop's words are a size up, and held to a ceiling however large the device's setting`() {
        // Device feedback 11/03: a little small at the usual setting, far too large at 1.3 times.
        assertEquals(1.1f, ShopText.fontScale(1f), 1e-4f)
        assertEquals(ShopText.MAX_SCALE, ShopText.fontScale(1.3f), 1e-4f)
        assertEquals(ShopText.MAX_SCALE, ShopText.fontScale(2f), 1e-4f)
        assertEquals(0.85f * 1.1f, ShopText.fontScale(0.85f), 1e-4f)
        assertEquals(1.15f, ShopText.MAX_SCALE)
        val main = listOf(java.io.File("src/main"), java.io.File("app/src/main")).first { it.isDirectory }
        val shop = java.io.File(main, "java/com/studioguu/miniaturegarden/ui/shop/ShopStageScreen.kt").readText().replace("\r\n", "\n")
        // The whole stage is drawn at it, the shelf names measured at it too.
        assertTrue(shop.contains("val shopDensity = remember(device) { Density(device.density, ShopText.fontScale(device.fontScale)) }"))
        assertTrue(shop.contains("CompositionLocalProvider(LocalDensity provides shopDensity) {\n        Box(modifier = modifier.fillMaxSize().testTag(SHOP_STAGE_TAG)) {"))
    }
}
