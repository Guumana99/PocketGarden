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
    fun `every item is sold in exactly one shop, in some season`() {
        for (item in GardenItem.entries) {
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
    fun `things are set out a few to a shelf, in the master's order`() {
        val stock = ShopShelves.stock(ShopStage.SUNDRIES, Season.SPRING, veteran, 10_000, false)
        val shelves = ShopShelves.shelves(stock)
        assertTrue(shelves.all { it.size in 1..ShopShelves.PER_SHELF })
        assertTrue(shelves.dropLast(1).all { it.size == ShopShelves.PER_SHELF })
        assertEquals(stock, shelves.flatten())
        assertEquals(emptyList<List<ShelfItem>>(), ShopShelves.shelves(emptyList()))
    }
}
