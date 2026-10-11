package com.studioguu.miniaturegarden.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            setOf(GardenItem.SEED_TULIP, GardenItem.GROUND_GRASS, GardenItem.GROUND_PEBBLE),
            unlockedAt(1),
        )
        assertEquals(unlockedAt(1) + GardenItem.SEED_SUNFLOWER + GardenItem.DECO_ROCK, unlockedAt(2))
        assertEquals(unlockedAt(2) + GardenItem.STRUCT_WELL, unlockedAt(3))
        assertEquals(unlockedAt(3) + GardenItem.SEED_ROSE, unlockedAt(4))
        assertEquals(GardenItem.entries.toSet(), unlockedAt(5))
    }

    @Test
    fun `unlockedBetween lists what a level-up opens, including skipped levels`() {
        assertEquals(
            setOf(GardenItem.SEED_SUNFLOWER, GardenItem.DECO_ROCK),
            GardenItem.unlockedBetween(1, 2).toSet(),
        )
        assertEquals(
            setOf(GardenItem.STRUCT_WELL, GardenItem.SEED_ROSE),
            GardenItem.unlockedBetween(2, 4).toSet(),
        )
        assertTrue(GardenItem.unlockedBetween(5, 6).isEmpty())
        assertTrue(GardenItem.unlockedBetween(3, 3).isEmpty())
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
        val byUnlock = PlantSpecies.entries.sortedBy { (seedFor(it).unlock as? UnlockCondition.PlayerLevel)?.level ?: 1 }
        assertEquals(listOf(PlantSpecies.TULIP, PlantSpecies.SUNFLOWER, PlantSpecies.ROSE), byUnlock)

        val coinsPerHour = byUnlock.map { (it.harvestCoins - seedFor(it).price).toDouble() / it.growthHours }
        val xpPerHour = byUnlock.map { it.harvestXp.toDouble() / it.growthHours }
        assertTrue("$coinsPerHour", coinsPerHour.zipWithNext().all { (a, b) -> b > a })
        assertTrue("$xpPerHour", xpPerHour.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `the starter seeds' first harvest reaches level 2`() {
        val firstHarvest = STARTER_SEEDS * PlantSpecies.TULIP.harvestXp
        assertTrue(firstHarvest >= LevelTable.xpForLevel(2))
        assertTrue(firstHarvest < LevelTable.xpForLevel(3))
    }

    @Test
    fun `only structures give experience for being placed`() {
        val paying = GardenItem.entries.filter { it.placeXp > 0 }
        assertEquals(setOf(GardenItem.STRUCT_HUT, GardenItem.STRUCT_WELL), paying.toSet())
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
