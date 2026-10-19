package com.studioguu.miniaturegarden.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {

    // --- Level curve ---

    @Test
    fun `level thresholds follow 50 x N x (N - 1)`() {
        assertEquals(0L, LevelTable.xpForLevel(1))
        assertEquals(100L, LevelTable.xpForLevel(2))
        assertEquals(300L, LevelTable.xpForLevel(3))
        assertEquals(600L, LevelTable.xpForLevel(4))
        assertEquals(1_000L, LevelTable.xpForLevel(5))
        assertEquals(4_500L, LevelTable.xpForLevel(10))
        // Nothing below level 1, nothing above the cap.
        assertEquals(0L, LevelTable.xpForLevel(0))
        assertEquals(LevelTable.xpForLevel(LevelTable.MAX_LEVEL), LevelTable.xpForLevel(LevelTable.MAX_LEVEL + 5))
    }

    @Test
    fun `every level costs more than the one before`() {
        val costs = (1 until LevelTable.MAX_LEVEL).map { LevelTable.xpForLevel(it + 1) - LevelTable.xpForLevel(it) }
        assertEquals(100L, costs.first())
        assertTrue(costs.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `xp maps to the highest level whose threshold it has reached`() {
        assertEquals(1, LevelTable.levelForXp(0))
        assertEquals(1, LevelTable.levelForXp(99))
        assertEquals(2, LevelTable.levelForXp(100))
        assertEquals(2, LevelTable.levelForXp(299))
        assertEquals(3, LevelTable.levelForXp(300))
        assertEquals(5, LevelTable.levelForXp(1_499))
        assertEquals(1, LevelTable.levelForXp(-50))
        assertEquals(LevelTable.MAX_LEVEL, LevelTable.levelForXp(Long.MAX_VALUE))
        for (level in 1..LevelTable.MAX_LEVEL) {
            assertEquals(level, LevelTable.levelForXp(LevelTable.xpForLevel(level)))
        }
    }

    // --- Gaining XP ---

    @Test
    fun `gaining experience raises the level exactly at the threshold`() {
        val almost = UserProfile(currentXp = 95)
        assertEquals(1, almost.gainXp(4).playerLevel)
        assertEquals(2, almost.gainXp(5).playerLevel)
        assertEquals(100L, almost.gainXp(5).currentXp)
    }

    @Test
    fun `one large gain can cross several levels`() {
        val leveled = UserProfile().gainXp(650)
        assertEquals(4, leveled.playerLevel)
        assertEquals(650L, leveled.currentXp)
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
        val stats = UserProfile(playerLevel = 3, currentXp = 375).stats
        assertEquals(PlayerStats(level = 3, totalXp = 375), stats)
        assertEquals(75L, stats.xpIntoLevel)      // 375 - 300
        assertEquals(300L, stats.xpSpanOfLevel)   // 600 - 300
        assertEquals(225L, stats.xpToNextLevel)
        assertEquals(0.25f, stats.progress, 1e-6f)
        assertFalse(stats.isMaxLevel)
    }

    @Test
    fun `a new player is at the very start of level 1`() {
        val stats = UserProfile().stats
        assertEquals(1, stats.level)
        assertEquals(0L, stats.xpIntoLevel)
        assertEquals(100L, stats.xpToNextLevel)
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
        fun unlockedAt(level: Int) = GardenItem.entries.filter { it.isUnlockedFor(statsAt(level)) }.toSet()

        assertEquals(
            setOf(
                GardenItem.SEED_TULIP, GardenItem.GROUND_GRASS, GardenItem.GROUND_PEBBLE, GardenItem.TILE_RIVER,
                GardenItem.FLOOR_COBBLESTONE, GardenItem.STRUCT_FENCE, GardenItem.GROUND_FLOWERS,
                GardenItem.SEED_COSMOS, GardenItem.SEED_SNOWDROP, GardenItem.SEED_MINT,
                GardenItem.SEED_TOMATO, GardenItem.SEED_STRAWBERRY,
            ),
            unlockedAt(1),
        )
        assertEquals(
            unlockedAt(1) + GardenItem.SEED_SUNFLOWER + GardenItem.DECO_ROCK + GardenItem.DECO_BENCH + GardenItem.SEED_DAIKON,
            unlockedAt(2),
        )
        assertEquals(unlockedAt(2) + GardenItem.STRUCT_WELL + GardenItem.SEED_WATERMELON + GardenItem.SEED_LAVENDER + GardenItem.DECO_SPRINKLER, unlockedAt(3))
        assertEquals(unlockedAt(3) + GardenItem.SEED_ROSE + GardenItem.SEED_PAPRIKA + GardenItem.STRUCT_WINDMILL + GardenItem.DECO_POND, unlockedAt(4))
        // Level opens everything but the two that the encyclopedia opens.
        val byPages = setOf(GardenItem.FLOOR_WOOD, GardenItem.DECO_LANTERN)
        // The symbol building comes at Lv.6, the greenhouse much later, at Lv.18.
        assertEquals(GardenItem.entries.toSet() - byPages - GardenItem.STRUCT_COTTAGE - GardenItem.FLOOR_GREENHOUSE, unlockedAt(5))
        assertEquals(GardenItem.entries.toSet() - byPages - GardenItem.FLOOR_GREENHOUSE, unlockedAt(17))
        assertEquals(GardenItem.entries.toSet() - byPages, unlockedAt(18))
        assertEquals(GardenItem.entries.toSet(), GardenItem.entries.filter { it.isUnlockedFor(statsAt(18).copy(collectionPages = 4)) }.toSet())
    }

    @Test
    fun `unlockedBetween lists what a level-up opens, including skipped levels`() {
        assertEquals(
            setOf(GardenItem.SEED_SUNFLOWER, GardenItem.DECO_ROCK, GardenItem.DECO_BENCH, GardenItem.SEED_DAIKON),
            GardenItem.unlockedBetween(1, 2).toSet(),
        )
        assertEquals(
            setOf(
                GardenItem.STRUCT_WELL, GardenItem.SEED_WATERMELON, GardenItem.SEED_LAVENDER, GardenItem.SEED_ROSE, GardenItem.SEED_PAPRIKA,
                GardenItem.STRUCT_WINDMILL, GardenItem.DECO_SPRINKLER, GardenItem.DECO_POND,
            ),
            GardenItem.unlockedBetween(2, 4).toSet(),
        )
        assertEquals(setOf(GardenItem.STRUCT_COTTAGE), GardenItem.unlockedBetween(5, 6).toSet())
        assertTrue(GardenItem.unlockedBetween(6, 7).isEmpty())
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
        assertTrue(LevelUpNotice(6, 7).unlockedItems.isEmpty())
    }

    // --- Economy ---

    private fun seedFor(species: PlantSpecies) = GardenItem.entries.single { it.objectId == species.objectId }

    @Test
    fun `a new player can afford and buy the starter seed`() {
        val newPlayer = UserProfile(coins = UserProfile.STARTING_COINS)
        assertTrue(GardenItem.SEED_TULIP.isUnlockedFor(newPlayer.stats))
        assertTrue(GardenItem.SEED_TULIP.price * 3 <= newPlayer.coins)
    }

    @Test
    fun `every plant sells for more than its seed costs`() {
        for (species in PlantSpecies.entries) {
            assertTrue(species.name, species.harvestCoins > seedFor(species).price)
        }
    }

    @Test
    fun `plants that unlock later earn more per hour, in coins and in experience`() {
        fun level(species: PlantSpecies) = (seedFor(species).unlock as? UnlockCondition.PlayerLevel)?.level ?: 1
        fun coinsPerHour(species: PlantSpecies) = (species.harvestCoins - seedFor(species).price).toDouble() / species.growthHours
        fun xpPerHour(species: PlantSpecies) = species.harvestXp.toDouble() / species.growthHours
        assertEquals(
            listOf(
                PlantSpecies.SUNFLOWER, PlantSpecies.DAIKON, PlantSpecies.WATERMELON, PlantSpecies.LAVENDER, PlantSpecies.ROSE,
                PlantSpecies.PAPRIKA, PlantSpecies.PUMPKIN, PlantSpecies.POINSETTIA,
            ),
            PlantSpecies.entries.filter { level(it) > 1 }.sortedBy(::level),
        )

        // Whatever opens at a higher level beats everything that opens lower, the seasonal crops
        // of the first level included.
        for (later in PlantSpecies.entries) for (earlier in PlantSpecies.entries) {
            if (level(later) <= level(earlier)) continue
            assertTrue("$later over $earlier in coins", coinsPerHour(later) > coinsPerHour(earlier))
            assertTrue("$later over $earlier in XP", xpPerHour(later) > xpPerHour(earlier))
        }
    }

    @Test
    fun `a crop that bears again earns at its first harvest's rate, never past a later level's`() {
        fun level(species: PlantSpecies) = (seedFor(species).unlock as? UnlockCondition.PlayerLevel)?.level ?: 1
        val repeatable = PlantSpecies.entries.filter { it.isRepeatable }
        assertEquals(setOf(PlantSpecies.TOMATO, PlantSpecies.STRAWBERRY, PlantSpecies.PAPRIKA), repeatable.toSet())
        for (crop in repeatable) {
            // Later harvests pay less, at no more per hour than the first (seed and all).
            val firstCoins = (crop.harvestCoins - seedFor(crop).price).toDouble() / crop.growthHours
            val firstXp = crop.harvestXp.toDouble() / crop.growthHours
            val againCoins = crop.regrowCoins / crop.regrowHours.toDouble()
            val againXp = crop.regrowXp / crop.regrowHours.toDouble()
            assertTrue("$crop coins", crop.regrowCoins in 1 until crop.harvestCoins)
            assertTrue("$crop coins per hour", againCoins <= firstCoins + 1e-9)
            assertTrue("$crop XP per hour", againXp <= firstXp + 1e-9)
            // So a bush that keeps bearing never outdoes what a higher level opens.
            for (later in PlantSpecies.entries.filter { level(it) > level(crop) }) {
                assertTrue("$later over $crop", (later.harvestCoins - seedFor(later).price).toDouble() / later.growthHours > againCoins)
                assertTrue("$later over $crop in XP", later.harvestXp.toDouble() / later.growthHours > againXp)
            }
        }
        for (crop in PlantSpecies.entries - repeatable.toSet()) {
            assertEquals(0, crop.regrowCoins)
            assertEquals(0, crop.regrowXp)
        }
    }

    @Test
    fun `the starter seeds' first harvest reaches level 2`() {
        val firstHarvest = STARTER_SEEDS * PlantSpecies.TULIP.harvestXp
        assertTrue(firstHarvest >= LevelTable.xpForLevel(2))
        assertTrue(firstHarvest < LevelTable.xpForLevel(3))
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
        assertTrue(GardenItem.STRUCT_HUT.placeXp > GardenItem.STRUCT_WELL.placeXp)
        // Buying a structure for its XP is never cheaper than growing tulips for it.
        for (item in paying) {
            val coinsPerXp = item.price.toDouble() / item.placeXp
            assertTrue(item.name, coinsPerXp > GardenItem.SEED_TULIP.price.toDouble() / PlantSpecies.TULIP.harvestXp)
        }
    }

    private companion object {
        /** Seeds a new garden starts with (see GardenRepositoryImpl). */
        const val STARTER_SEEDS = 3
    }
}
