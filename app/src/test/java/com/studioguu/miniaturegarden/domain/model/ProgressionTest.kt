package com.studioguu.miniaturegarden.domain.model

import com.studioguu.miniaturegarden.domain.usecase.ActionResult
import com.studioguu.miniaturegarden.domain.usecase.CalculateOfflineProgressUseCase
import com.studioguu.miniaturegarden.domain.usecase.ExpandGardenUseCase
import com.studioguu.miniaturegarden.domain.usecase.FakeGardenRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {

    // --- Level curve ---

    @Test
    fun `each level costs what the table says, and the last step from Lv_21 on`() {
        assertEquals(
            listOf(80L, 300, 700, 1_500, 2_200, 2_900, 3_600, 4_500, 5_300, 6_000, 6_600, 7_200, 7_900, 8_800, 9_500, 10_200, 11_000, 12_000, 13_000),
            (1..19).map { LevelTable.xpForLevel(it + 1) - LevelTable.xpForLevel(it) },
        )
        assertEquals(0L, LevelTable.xpForLevel(1))
        assertEquals(80L, LevelTable.xpForLevel(2))
        assertEquals(380L, LevelTable.xpForLevel(3))
        assertEquals(21_080L, LevelTable.xpForLevel(10))
        assertEquals(113_280L, LevelTable.xpForLevel(20))
        for (level in 20 until LevelTable.MAX_LEVEL) {
            assertEquals(13_000L, LevelTable.xpForLevel(level + 1) - LevelTable.xpForLevel(level))
        }
        // Nothing below level 1, nothing above the cap, which is still 99.
        assertEquals(99, LevelTable.MAX_LEVEL)
        assertEquals(0L, LevelTable.xpForLevel(0))
        assertEquals(LevelTable.xpForLevel(LevelTable.MAX_LEVEL), LevelTable.xpForLevel(LevelTable.MAX_LEVEL + 5))
    }

    @Test
    fun `no level costs less than the one before`() {
        val costs = (1 until LevelTable.MAX_LEVEL).map { LevelTable.xpForLevel(it + 1) - LevelTable.xpForLevel(it) }
        assertTrue(costs.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `xp maps to the highest level whose threshold it has reached`() {
        assertEquals(1, LevelTable.levelForXp(0))
        assertEquals(1, LevelTable.levelForXp(79))
        assertEquals(2, LevelTable.levelForXp(80))
        assertEquals(2, LevelTable.levelForXp(379))
        assertEquals(3, LevelTable.levelForXp(380))
        assertEquals(1, LevelTable.levelForXp(-50))
        assertEquals(LevelTable.MAX_LEVEL, LevelTable.levelForXp(Long.MAX_VALUE))
        for (level in 1..LevelTable.MAX_LEVEL) {
            assertEquals(level, LevelTable.levelForXp(LevelTable.xpForLevel(level)))
        }
    }

    // --- Gaining XP ---

    @Test
    fun `gaining experience raises the level exactly at the threshold`() {
        val almost = UserProfile(currentXp = 75)
        assertEquals(1, almost.gainXp(4).playerLevel)
        assertEquals(2, almost.gainXp(5).playerLevel)
        assertEquals(80L, almost.gainXp(5).currentXp)
    }

    @Test
    fun `one large gain can cross several levels`() {
        val leveled = UserProfile().gainXp(1_100)
        assertEquals(4, leveled.playerLevel)
        assertEquals(1_100L, leveled.currentXp)
    }

    @Test
    fun `gaining experience never lowers a level and leaves everything else alone`() {
        // A level recorded under an older, easier curve is kept.
        val veteran = UserProfile(coins = 77, playerLevel = 6, currentXp = 10, inventoryCapacity = 20)
        val after = veteran.gainXp(5)
        assertEquals(6, after.playerLevel)
        assertEquals(veteran.copy(currentXp = 15), after)
    }

    // --- PlayerStats ---

    @Test
    fun `stats describe progress through the current level`() {
        val stats = UserProfile(playerLevel = 3, currentXp = 555).stats
        assertEquals(PlayerStats(level = 3, totalXp = 555), stats)
        assertEquals(175L, stats.xpIntoLevel)     // 555 - 380
        assertEquals(700L, stats.xpSpanOfLevel)   // 1,080 - 380
        assertEquals(525L, stats.xpToNextLevel)
        assertEquals(0.25f, stats.progress, 1e-6f)
        assertFalse(stats.isMaxLevel)
    }

    @Test
    fun `a new player is at the very start of level 1`() {
        val stats = UserProfile().stats
        assertEquals(1, stats.level)
        assertEquals(0L, stats.xpIntoLevel)
        assertEquals(80L, stats.xpToNextLevel)
        assertEquals(0f, stats.progress, 0f)
    }

    @Test
    fun `stats at the level cap show a full bar and nothing left to earn`() {
        val stats = PlayerStats(LevelTable.MAX_LEVEL, LevelTable.xpForLevel(LevelTable.MAX_LEVEL) + 12_345)
        assertTrue(stats.isMaxLevel)
        assertEquals(0L, stats.xpToNextLevel)
        assertEquals(1f, stats.progress, 0f)
    }

    // --- Unlock conditions ---

    private fun statsAt(level: Int) = PlayerStats(level, LevelTable.xpForLevel(level))

    @Test
    fun `a level condition is met at that level and above`() {
        val condition = UnlockCondition.PlayerLevel(3)
        assertFalse(condition.isMetBy(statsAt(1)))
        assertFalse(condition.isMetBy(statsAt(2)))
        assertTrue(condition.isMetBy(statsAt(3)))
        assertTrue(condition.isMetBy(statsAt(9)))
        assertTrue(UnlockCondition.None.isMetBy(statsAt(1)))
    }

    @Test
    fun `items unlock at their own level`() {
        val expected = mapOf(
            1 to setOf(
                GardenItem.SEED_MINT, GardenItem.SEED_TULIP, GardenItem.SEED_STRAWBERRY, GardenItem.SEED_TOMATO,
                GardenItem.SEED_COSMOS, GardenItem.SEED_SNOWDROP,
                GardenItem.GROUND_GRASS, GardenItem.GROUND_PEBBLE, GardenItem.TILE_RIVER, GardenItem.FLOOR_COBBLESTONE,
                GardenItem.STRUCT_FENCE, GardenItem.GROUND_FLOWERS,
            ),
            2 to setOf(GardenItem.SEED_CARROT, GardenItem.DECO_ROCK),
            3 to setOf(GardenItem.SEED_SUNFLOWER, GardenItem.DECO_BENCH, GardenItem.STRUCT_COTTAGE),
            4 to setOf(GardenItem.SEED_DAIKON, GardenItem.DECO_POND, GardenItem.DECO_ROCK_FLAT),
            5 to setOf(GardenItem.SEED_LAVENDER, GardenItem.STRUCT_WELL, GardenItem.STRUCT_FENCE_WHITE),
            6 to setOf(GardenItem.SEED_EGGPLANT, GardenItem.DECO_WEATHERVANE),
            7 to setOf(GardenItem.SEED_WHEAT, GardenItem.DECO_SPRINKLER, GardenItem.DECO_ROCK_MOSSY),
            8 to setOf(GardenItem.SEED_ROSE, GardenItem.STRUCT_FENCE_BAMBOO),
            9 to setOf(GardenItem.SEED_WATERMELON, GardenItem.STRUCT_WINDMILL),
            10 to setOf(GardenItem.SEED_HAKUSAI, GardenItem.DECO_ROCK_STANDING),
            11 to setOf(GardenItem.SEED_PAPRIKA, GardenItem.STRUCT_HUT),
            12 to setOf(GardenItem.SEED_NANOHANA),
            13 to setOf(GardenItem.SEED_CORN, GardenItem.DECO_ROCK_PEBBLES),
            14 to setOf(GardenItem.SEED_SHIITAKE),
            15 to setOf(GardenItem.SEED_POINSETTIA),
            16 to setOf(GardenItem.STRUCT_EDGING_BRICK),
            17 to setOf(GardenItem.STRUCT_BRIDGE),
            18 to setOf(GardenItem.FLOOR_GREENHOUSE),
        )
        val byLevel = GardenItem.entries.filter { it.unlock !is UnlockCondition.CollectionPages }
            .groupBy { it.unlockLevel }
            .mapValues { it.value.toSet() }
        assertEquals(expected, byLevel)
        // The lantern and the decking are still opened by the encyclopedia.
        assertEquals(UnlockCondition.CollectionPages(4), GardenItem.DECO_LANTERN.unlock)
        assertEquals(UnlockCondition.CollectionPages(2), GardenItem.FLOOR_WOOD.unlock)
    }

    @Test
    fun `unlockedBetween lists what a level-up puts in the shop, including skipped levels`() {
        assertEquals(setOf(GardenItem.SEED_CARROT, GardenItem.DECO_ROCK), GardenItem.unlockedBetween(1, 2).toSet())
        assertEquals(
            setOf(GardenItem.SEED_SUNFLOWER, GardenItem.DECO_BENCH, GardenItem.STRUCT_COTTAGE, GardenItem.SEED_DAIKON, GardenItem.DECO_POND, GardenItem.DECO_ROCK_FLAT),
            GardenItem.unlockedBetween(2, 4).toSet(),
        )
        assertEquals(setOf(GardenItem.SEED_EGGPLANT, GardenItem.DECO_WEATHERVANE), GardenItem.unlockedBetween(5, 6).toSet())
        assertEquals(setOf(GardenItem.DECO_ROCK_STANDING, GardenItem.SEED_HAKUSAI), GardenItem.unlockedBetween(9, 10).toSet())
        assertEquals(setOf(GardenItem.SEED_NANOHANA), GardenItem.unlockedBetween(11, 12).toSet())
        assertEquals(setOf(GardenItem.STRUCT_BRIDGE), GardenItem.unlockedBetween(15, 17).toSet())
        assertTrue(GardenItem.unlockedBetween(3, 3).isEmpty())
    }

    @Test
    fun `a level-up waits as one notice, and levels gained meanwhile join it`() {
        val first = LevelUpNotice.after(pending = null, previous = 1, level = 2)
        assertEquals(LevelUpNotice(1, 2), first)
        // Still on screen when the level rises again: one board, from where it started.
        assertEquals(LevelUpNotice(1, 4), LevelUpNotice.after(first, previous = 2, level = 4))
        // The level did not rise: nothing new to say.
        assertNull(LevelUpNotice.after(pending = null, previous = 3, level = 3))
        assertEquals(first, LevelUpNotice.after(first, previous = 2, level = 2))
    }

    @Test
    fun `the level-up notice names what the shop just opened`() {
        assertEquals(GardenItem.unlockedBetween(1, 4).toSet(), LevelUpNotice(1, 4).unlockedItems.toSet())
        assertTrue(LevelUpNotice(1, 2).unlockedItems.isNotEmpty())
        // A level that opens nothing: the board does not speak of new items.
        assertTrue(LevelUpNotice(15, 16).unlockedItems.isEmpty())
    }

    // --- What is registered and not on sale ---

    @Test
    fun `the withdrawn edging is registered but never on a shelf`() {
        val waiting = setOf(GardenItem.STRUCT_EDGING_BRICK)
        assertEquals(waiting, GardenItem.entries.filter { !it.inShop }.toSet())
        for (category in ItemCategory.entries) {
            for (season in Season.entries) assertTrue(GardenItem.shelf(category, season).none { it in waiting })
            assertTrue(GardenItem.shelf(category).none { it in waiting })
        }
        assertEquals(setOf(Season.WINTER), GardenItem.SEED_HAKUSAI.seasons)
        assertEquals(setOf(Season.SPRING), GardenItem.SEED_NANOHANA.seasons)
        assertEquals(500, GardenItem.DECO_WEATHERVANE.price)
        assertEquals(WeatherOutlook.WEATHERVANE_OBJECT_ID, GardenItem.DECO_WEATHERVANE.objectId)
    }

    @Test
    fun `the weathervane is what lets tomorrow be read`() {
        val vane = GridObject(instanceId = "v", gridX = 1, gridY = 1, objectId = GardenItem.DECO_WEATHERVANE.objectId, lastUpdatedTimestamp = 0L)
        assertTrue(WeatherOutlook.isReadable(listOf(vane)))
        assertFalse(WeatherOutlook.isReadable(emptyList()))
    }

    // --- Crops ---

    private fun seedFor(species: PlantSpecies) = StandardPlayer.seedOf(species)

    /** Hours, seed, coins, XP, and for a crop that bears again its later hours, coins and XP. */
    private data class Row(val hours: Int, val seed: Int, val coins: Int, val xp: Int, val again: Triple<Int, Int, Int>? = null)

    @Test
    fun `crops grow, cost and pay what the table says`() {
        val table = mapOf(
            PlantSpecies.MINT to Row(4, 5, 13, 7),
            PlantSpecies.TULIP to Row(6, 10, 22, 10),
            PlantSpecies.COSMOS to Row(10, 10, 22, 10),
            PlantSpecies.SNOWDROP to Row(10, 10, 22, 10),
            PlantSpecies.STRAWBERRY to Row(6, 5, 16, 14, Triple(3, 8, 7)),
            PlantSpecies.TOMATO to Row(8, 10, 20, 15, Triple(4, 9, 8)),
            PlantSpecies.CARROT to Row(16, 20, 47, 22),
            PlantSpecies.SUNFLOWER to Row(20, 20, 50, 22),
            PlantSpecies.DAIKON to Row(18, 20, 50, 23),
            PlantSpecies.LAVENDER to Row(24, 20, 52, 24),
            PlantSpecies.EGGPLANT to Row(22, 15, 34, 25, Triple(11, 17, 12)),
            PlantSpecies.WHEAT to Row(30, 35, 90, 39),
            PlantSpecies.ROSE to Row(36, 40, ROSE_COINS, 40),
            PlantSpecies.WATERMELON to Row(30, 40, 100, 42),
            PlantSpecies.HAKUSAI to Row(30, 40, 105, 43),
            PlantSpecies.PAPRIKA to Row(32, 40, 90, 58, Triple(16, 45, 29)),
            PlantSpecies.NANOHANA to Row(32, 45, 115, 45),
            PlantSpecies.CORN to Row(42, 65, 165, 63),
            PlantSpecies.SHIITAKE to Row(48, 45, 100, 65, Triple(24, 50, 33)),
            PlantSpecies.POINSETTIA to Row(40, 70, 175, 67),
        )
        assertEquals(PlantSpecies.entries.toSet(), table.keys)
        for ((species, row) in table) {
            val again = if (species.isRepeatable) Triple(species.regrowHours.toInt(), species.regrowCoins, species.regrowXp) else null
            assertEquals(species.name, row, Row(species.growthHours, seedFor(species).price, species.harvestCoins, species.harvestXp, again))
            if (species.isRepeatable) assertEquals(species.name, species.regrowHours, species.regrowHours.toInt().toFloat())
        }
    }

    @Test
    fun `soil never dries out in under 12 hours, and a crop in full bloom still lasts 72`() {
        assertEquals(12, PlantSpecies.MIN_DRY_OUT_HOURS)
        for (species in PlantSpecies.entries) assertTrue(species.name, species.dryOutHours >= PlantSpecies.MIN_DRY_OUT_HOURS)
        assertEquals(72, CalculateOfflineProgressUseCase.OVERRIPE_HOURS)
    }

    @Test
    fun `hakusai grows like the daikon and nanohana like the cosmos`() {
        assertEquals(PlantSpecies.DAIKON.growthStages, PlantSpecies.HAKUSAI.growthStages)
        assertEquals(PlantSpecies.DAIKON.dryOutHours, PlantSpecies.HAKUSAI.dryOutHours)
        assertEquals(PlantSpecies.DAIKON.ground, PlantSpecies.HAKUSAI.ground)
        assertEquals(PlantSpecies.COSMOS.growthStages, PlantSpecies.NANOHANA.growthStages)
        assertEquals(PlantSpecies.COSMOS.dryOutHours, PlantSpecies.NANOHANA.dryOutHours)
        assertEquals(PlantSpecies.COSMOS.ground, PlantSpecies.NANOHANA.ground)
    }

    @Test
    fun `a crop opened later earns no less for a bed and a day than any sown before it in its season`() {
        // For the standard player (three visits a day); a crop that bears again by its later harvests.
        for (season in Season.entries) {
            val sown = PlantSpecies.entries.filter { season in StandardPlayer.seasonsOf(it) }
            for (later in sown) for (earlier in sown) {
                if (StandardPlayer.unlockLevel(later) <= StandardPlayer.unlockLevel(earlier)) continue
                assertTrue(
                    "$season: $later (Lv.${StandardPlayer.unlockLevel(later)}, ${StandardPlayer.coinsPerDay(later)}) " +
                        "under $earlier (Lv.${StandardPlayer.unlockLevel(earlier)}, ${StandardPlayer.coinsPerDay(earlier)})",
                    StandardPlayer.coinsPerDay(later) >= StandardPlayer.coinsPerDay(earlier) - 1e-9,
                )
            }
        }
    }

    @Test
    fun `a crop that bears again pays less each later time, in half the time`() {
        val repeatable = PlantSpecies.entries.filter { it.isRepeatable }
        assertEquals(
            setOf(PlantSpecies.TOMATO, PlantSpecies.STRAWBERRY, PlantSpecies.PAPRIKA, PlantSpecies.EGGPLANT, PlantSpecies.SHIITAKE),
            repeatable.toSet(),
        )
        for (crop in repeatable) {
            assertTrue("$crop coins", crop.regrowCoins in 1 until crop.harvestCoins)
            assertTrue("$crop XP", crop.regrowXp in 1 until crop.harvestXp)
            assertEquals(crop.growthHours / 2f, crop.regrowHours, 1e-4f)
        }
        for (crop in PlantSpecies.entries - repeatable.toSet()) {
            assertEquals(0, crop.regrowCoins)
            assertEquals(0, crop.regrowXp)
        }
    }

    @Test
    fun `every plant sells for more than its seed costs`() {
        for (species in PlantSpecies.entries) {
            assertTrue(species.name, species.harvestCoins > seedFor(species).price)
        }
    }

    @Test
    fun `a new player can afford and buy the starter seed`() {
        val newPlayer = UserProfile(coins = UserProfile.STARTING_COINS)
        assertTrue(GardenItem.SEED_TULIP.isUnlockedFor(newPlayer.stats))
        assertTrue(GardenItem.SEED_TULIP.price * 3 <= newPlayer.coins)
    }

    // --- Furniture and buildings ---

    @Test
    fun `furniture and buildings cost what the table says, and the ground stays as it was`() {
        assertEquals(
            mapOf(
                GardenItem.DECO_ROCK to 60, GardenItem.DECO_BENCH to 100, GardenItem.STRUCT_COTTAGE to 800,
                GardenItem.DECO_POND to 120, GardenItem.STRUCT_WELL to 400, GardenItem.DECO_WEATHERVANE to 500,
                GardenItem.DECO_SPRINKLER to 450, GardenItem.STRUCT_WINDMILL to 1_200, GardenItem.STRUCT_HUT to 2_000,
                GardenItem.FLOOR_GREENHOUSE to 3_000,
                GardenItem.DECO_ROCK_FLAT to 80, GardenItem.DECO_ROCK_MOSSY to 150, GardenItem.DECO_ROCK_STANDING to 250,
                GardenItem.DECO_ROCK_PEBBLES to 300, GardenItem.STRUCT_BRIDGE to 3_500, GardenItem.STRUCT_FENCE_WHITE to 25, GardenItem.STRUCT_FENCE_BAMBOO to 35,
                GardenItem.STRUCT_EDGING_BRICK to 60,
                GardenItem.GROUND_GRASS to 10, GardenItem.GROUND_PEBBLE to 10, GardenItem.FLOOR_COBBLESTONE to 10,
                GardenItem.TILE_RIVER to 10, GardenItem.GROUND_FLOWERS to 15, GardenItem.STRUCT_FENCE to 15,
                GardenItem.FLOOR_WOOD to 12, GardenItem.DECO_LANTERN to 60,
            ),
            GardenItem.entries.filter { it.category != ItemCategory.SEED }.associateWith { it.price },
        )
    }

    @Test
    fun `only structures, and the pond, give experience for being placed`() {
        val paying = GardenItem.entries.filter { it.placeXp > 0 }
        assertEquals(
            setOf(
                GardenItem.STRUCT_HUT, GardenItem.STRUCT_WELL, GardenItem.STRUCT_COTTAGE, GardenItem.STRUCT_WINDMILL, GardenItem.FLOOR_GREENHOUSE,
                GardenItem.DECO_POND,
            ),
            paying.toSet(),
        )
        // Buying a structure for its XP is never cheaper than growing tulips for it.
        for (item in paying) {
            val coinsPerXp = item.price.toDouble() / item.placeXp
            assertTrue(item.name, coinsPerXp > GardenItem.SEED_TULIP.price.toDouble() / PlantSpecies.TULIP.harvestXp)
        }
    }

    // --- Growing the garden ---

    @Test
    fun `the garden grows for its level and its price, and the planned sizes are not open yet`() {
        assertEquals(
            listOf(
                ExpansionStage(11, playerLevel = 1, price = 0),
                ExpansionStage(13, playerLevel = 8, price = 3_000),
                ExpansionStage(15, playerLevel = 14, price = 10_000, open = false),
                ExpansionStage(17, playerLevel = 20, price = 20_000, open = false),
            ),
            GardenExpansion.STAGES,
        )
        assertEquals(13, GardenExpansion.MAX_SIZE)
        assertNull(GardenExpansion.nextStage(13))
        assertEquals(ExpansionCheck.AT_MAX_SIZE, GardenExpansion.check(13, 99, 1_000_000))
    }

    @Test
    fun `growing the garden takes the level and the coins and nothing else, a cottage not among them`() = runBlocking {
        // check() is not even told what stands in the garden.
        assertEquals(ExpansionCheck.READY, GardenExpansion.check(11, 8, 3_000))
        assertEquals(ExpansionCheck.NEEDS_LEVEL, GardenExpansion.check(11, 7, 3_000))
        assertEquals(ExpansionCheck.NEEDS_COINS, GardenExpansion.check(11, 8, 2_999))
        // An empty garden, no cottage anywhere: it grows, and the price is paid.
        val repo = FakeGardenRepository().apply {
            profile = UserProfile(coins = 3_000, playerLevel = 8, currentXp = LevelTable.xpForLevel(8))
            progress = GardenProgress(gardenSize = 11)
        }
        assertEquals(ActionResult.SUCCESS, ExpandGardenUseCase(repo)())
        assertEquals(13, repo.progress?.gardenSize)
        assertEquals(0L, repo.profile?.coins)
    }

    // --- Saves from before ---

    @Test
    fun `a save from the old curve keeps its level, however low its experience now reads`() {
        // Lv.10 under the old curve took 4,500 XP; the new one asks 21,080.
        val veteran = UserProfile(coins = 1_234, playerLevel = 10, currentXp = 4_600)
        assertEquals(5, LevelTable.levelForXp(veteran.currentXp))
        assertEquals(10, veteran.gainXp(5).playerLevel)
        assertEquals(10, veteran.gainXp(16_000).playerLevel)
        assertEquals(10, veteran.loseXp(100).playerLevel)
        // It rises again only once the new curve's experience for the next level is there.
        assertEquals(11, veteran.gainXp((LevelTable.xpForLevel(11) - 4_600).toInt()).playerLevel)
        // The gauge simply waits at empty meanwhile, and what is for sale is the level's.
        assertEquals(0f, veteran.stats.progress, 0f)
        assertTrue(GardenItem.STRUCT_WINDMILL.isUnlockedFor(veteran.stats))
    }

    @Test
    fun `a growing plant keeps its share of growth when the hours change`() {
        // Growth is kept as a share of the whole (0..1), never as hours: nothing goes backwards.
        val plant = GridObject(
            instanceId = "rose", gridX = 0, gridY = 0, objectId = PlantSpecies.ROSE.objectId,
            growthProgress = 0.6f, lastUpdatedTimestamp = 0L, moistureLevel = 1f, nutritionLevel = 1f,
        )
        assertEquals(0.6f, CalculateOfflineProgressUseCase()(plant, 0L).growthProgress, 0f)
    }

    private companion object {
        /** 100 in the plan: 105 so that it is not behind the wheat of the level before it in autumn. */
        const val ROSE_COINS = 105
    }
}
