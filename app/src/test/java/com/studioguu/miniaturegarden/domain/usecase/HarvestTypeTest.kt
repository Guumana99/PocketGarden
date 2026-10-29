package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.data.local.mapper.toDomain
import com.studioguu.miniaturegarden.data.local.mapper.toEntity
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.HarvestReward
import com.studioguu.miniaturegarden.domain.model.HarvestType
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.UnlockCondition
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Crops harvested once and crops that bear again, and what becomes of a withered one. */
class HarvestTypeTest {

    private val t0 = 1_700_000_000_000L
    private val hour = 3_600_000L
    private val sunny = WeatherType.SUNNY

    private val repo = FakeGardenRepository().apply { profile = UserProfile(coins = 0) }
    private val calc = CalculateOfflineProgressUseCase()
    private val harvest = HarvestPlantUseCase(repo, calc, RecordCollectionUseCase(repo))
    private val care = CareForPlantUseCase(repo, calc)
    private val remove = RemoveObjectUseCase(repo)

    private fun plant(
        species: PlantSpecies,
        id: String = "p1",
        growth: Float = 1f,
        moisture: Float = 1f,
        stress: Float = 0f,
        status: PlantStatus = PlantStatus.HEALTHY,
        harvests: Int = 0,
    ) = GridObject(
        id, 0, 0, objectId = species.objectId, growthProgress = growth, moistureLevel = moisture,
        stressRate = stress, status = status, lastUpdatedTimestamp = t0, harvestCount = harvests,
    )

    private fun soil(id: String = "soil") =
        GridObject(id, 0, 0, objectId = GridObject.TILLED_SOIL_OBJECT_ID, lastUpdatedTimestamp = t0)

    // --- Which crop is which ---

    @Test
    fun `daikon, tulip and sunflower are harvested once, tomato, strawberry and paprika again and again`() {
        for (single in listOf(PlantSpecies.DAIKON, PlantSpecies.TULIP, PlantSpecies.SUNFLOWER, PlantSpecies.ROSE)) {
            assertEquals(single.name, HarvestType.SINGLE_HARVEST, single.harvestType)
        }
        for (repeatable in listOf(PlantSpecies.TOMATO, PlantSpecies.STRAWBERRY, PlantSpecies.PAPRIKA)) {
            assertEquals(repeatable.name, HarvestType.REPEATABLE_HARVEST, repeatable.harvestType)
        }
    }

    @Test
    fun `the four new crops are on sale, in the book and named`() {
        assertEquals(PlantSpecies.PAPRIKA.objectId, GardenItem.SEED_PAPRIKA.objectId)
        assertEquals(PlantSpecies.DAIKON.objectId, GardenItem.SEED_DAIKON.objectId)
        assertEquals(UnlockCondition.PlayerLevel(11), GardenItem.SEED_PAPRIKA.unlock)
        assertEquals(UnlockCondition.PlayerLevel(4), GardenItem.SEED_DAIKON.unlock)
        assertEquals(setOf(Season.SUMMER), GardenItem.SEED_PAPRIKA.seasons)
        assertEquals(setOf(Season.AUTUMN, Season.WINTER), GardenItem.SEED_DAIKON.seasons)
        for (species in listOf(PlantSpecies.TOMATO, PlantSpecies.STRAWBERRY, PlantSpecies.PAPRIKA, PlantSpecies.DAIKON)) {
            assertTrue(species.name, CollectionEntry.entries.any { it.objectId == species.objectId })
        }
        val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val strings = main.resolve("res/values/strings.xml").readText()
        for (name in listOf("object_plant_paprika", "object_plant_daikon", "item_seed_paprika", "item_seed_daikon", "flavor_paprika", "flavor_daikon")) {
            assertTrue(name, strings.contains("name=\"$name\""))
        }
    }

    // --- Stages and sprites ---

    @Test
    fun `a three-stage crop goes back to the start of its second stage`() {
        for (species in listOf(PlantSpecies.TOMATO, PlantSpecies.STRAWBERRY, PlantSpecies.PAPRIKA)) {
            assertEquals(0.5f, species.regrowFrom, 0f)
            assertEquals(2, TileSetDefinition.growthStage(species.regrowFrom, species.growthStages))
            assertEquals(species.growthHours / 2f, species.regrowHours, 1e-4f)
        }
        assertEquals("plant_tomato_stage1", TileSetDefinition.assetIdFor(plant(PlantSpecies.TOMATO, growth = 0.2f)))
        assertEquals("plant_tomato_stage2", TileSetDefinition.assetIdFor(plant(PlantSpecies.TOMATO, growth = 0.5f)))
        assertEquals("plant_tomato_stage3", TileSetDefinition.assetIdFor(plant(PlantSpecies.TOMATO, growth = 1f)))
    }

    @Test
    fun `a withered crop wears its own withered picture`() {
        for (species in listOf(PlantSpecies.TOMATO, PlantSpecies.STRAWBERRY, PlantSpecies.PAPRIKA, PlantSpecies.DAIKON)) {
            val withered = plant(species, growth = 0.7f, stress = 1f, status = PlantStatus.WITHERED)
            assertEquals("${species.objectId}_withered", TileSetDefinition.assetIdFor(withered))
        }
    }

    // --- Harvesting ---

    @Test
    fun `a single-harvest crop goes, and the tilled soil under it is free again`() = runBlocking {
        repo.put(soil())
        repo.put(plant(PlantSpecies.DAIKON))

        assertEquals(ActionResult.SUCCESS, harvest("p1", t0, sunny))

        assertNull(repo.objects["p1"])
        assertTrue(repo.objects.getValue("soil").isTilledSoil)
        assertEquals(PlantSpecies.DAIKON.harvestCoins.toLong(), repo.profile?.coins)
    }

    @Test
    fun `a repeatable crop stays, back at its second stage, and ripens again`() = runBlocking {
        repo.put(soil())
        repo.put(plant(PlantSpecies.TOMATO, moisture = 0.6f))

        assertEquals(ActionResult.SUCCESS, harvest("p1", t0, sunny))

        val after = repo.objects.getValue("p1")
        assertEquals(0.5f, after.growthProgress, 0f)
        assertEquals(2, TileSetDefinition.growthStage(after.growthProgress, PlantSpecies.TOMATO.growthStages))
        assertEquals(1, after.harvestCount)
        assertEquals(0.6f, after.moistureLevel, 0f) // the care it had stays with it
        assertEquals(t0, after.lastUpdatedTimestamp)
        assertEquals(PlantSpecies.TOMATO.harvestCoins.toLong(), repo.profile?.coins) // the first harvest pays in full
        // Not ready again at once...
        assertEquals(ActionResult.NOT_READY, harvest("p1", t0, sunny))
        // ...but half its growing time (4 h) later, with the soil kept moist (by rain here), it is.
        assertEquals(ActionResult.NOT_READY, harvest("p1", t0 + 3 * hour, WeatherType.RAINY))
        assertEquals(ActionResult.SUCCESS, harvest("p1", t0 + 4 * hour, WeatherType.RAINY))
        assertEquals(2, repo.objects.getValue("p1").harvestCount)
        assertEquals((PlantSpecies.TOMATO.harvestCoins + PlantSpecies.TOMATO.regrowCoins).toLong(), repo.profile?.coins)
    }

    @Test
    fun `later harvests of a repeatable crop pay its smaller regrowth reward`() {
        assertEquals(HarvestReward(20, 15), PlantSpecies.TOMATO.harvestReward(0))
        assertEquals(HarvestReward(9, 8), PlantSpecies.TOMATO.harvestReward(1))
        assertEquals(HarvestReward(9, 8), PlantSpecies.TOMATO.harvestReward(5))
        // A single-harvest crop is never harvested twice, but would pay in full if it were.
        assertEquals(HarvestReward(50, 23), PlantSpecies.DAIKON.harvestReward(0))
        assertEquals(HarvestReward(50, 23), PlantSpecies.DAIKON.harvestReward(1))
    }

    @Test
    fun `afterHarvest keeps the stress on the record and does not start the overripe clock`() {
        val ripe = plant(PlantSpecies.PAPRIKA, stress = 0.3f, harvests = 2)
        val after = HarvestPlantUseCase.afterHarvest(ripe, PlantSpecies.PAPRIKA, t0 + hour)!!
        assertEquals(0.3f, after.stressRate, 0f)
        assertEquals(3, after.harvestCount)
        assertEquals(t0 + hour, after.lastUpdatedTimestamp)
        // Below full growth, standing about earns no overripe stress.
        val later = calc(after, t0 + 10 * hour, sunny)
        assertTrue(later.growthProgress < 1f)
        assertNull(HarvestPlantUseCase.afterHarvest(plant(PlantSpecies.DAIKON), PlantSpecies.DAIKON, t0))
    }

    // --- Withering ---

    @Test
    fun `a repeatable crop left dry withers like any other`() = runBlocking {
        repo.put(plant(PlantSpecies.STRAWBERRY, growth = 0.6f, moisture = 0f, stress = 0.9f, status = PlantStatus.THIRSTY, harvests = 1))

        val later = calc(repo.objects.getValue("p1"), t0 + 24 * hour, sunny)

        assertEquals(PlantStatus.WITHERED, later.status)
        assertEquals("plant_strawberry_withered", TileSetDefinition.assetIdFor(later))
    }

    @Test
    fun `a withered crop cannot be harvested, watered or fed, and does not go away by itself`() = runBlocking {
        repo.put(soil())
        repo.put(plant(PlantSpecies.TOMATO, stress = 1f, status = PlantStatus.WITHERED, harvests = 1))
        repo.profile = UserProfile(coins = 100)

        assertEquals(ActionResult.WITHERED, harvest("p1", t0, sunny))
        assertEquals(ActionResult.WITHERED, care.water("p1", t0, sunny))
        assertEquals(ActionResult.WITHERED, care.fertilize("p1", t0, sunny))
        // Days later it is still there, still withered.
        calc(repo.objects.getValue("p1"), t0 + 72 * hour, sunny).let {
            assertEquals(PlantStatus.WITHERED, it.status)
        }
        assertEquals(PlantStatus.WITHERED, repo.objects.getValue("p1").status)
        assertEquals(100L, repo.profile?.coins)
    }

    @Test
    fun `clearing a withered crop away is what frees the soil, with compost in it`() = runBlocking {
        repo.put(soil())
        repo.put(plant(PlantSpecies.PAPRIKA, growth = 0.7f, stress = 1f, status = PlantStatus.WITHERED, harvests = 3))

        assertEquals(ActionResult.SUCCESS, remove("p1"))

        assertNull(repo.objects["p1"])
        val freed = repo.objects.getValue("soil")
        assertTrue(freed.isTilledSoil)
        assertTrue(freed.isComposted)
    }

    // --- Storage ---

    @Test
    fun `the harvest count goes into the database and comes back`() {
        val bush = plant(PlantSpecies.TOMATO, growth = 0.5f, harvests = 4)
        assertEquals(bush, bush.toEntity().toDomain())
        assertEquals(0, plant(PlantSpecies.DAIKON).toEntity().harvestCount)
    }

    @Test
    fun `the move to schema 13 adds the harvest count, as Room expects it`() {
        val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val database = main.resolve("java/com/studioguu/miniaturegarden/data/local/AppDatabase.kt").readText()
        // At 13 or later (v14 added the tools' experience): the move to 13 stays in the chain.
        val version = Regex("""version = (\d+)""").find(database)!!.groupValues[1].toInt()
        assertTrue("version $version", version >= 13)
        assertTrue(database.contains("ALTER TABLE grid_objects ADD COLUMN harvestCount INTEGER NOT NULL DEFAULT 0"))
        assertTrue(main.resolve("java/com/studioguu/miniaturegarden/di/DatabaseModule.kt").readText().contains("MIGRATION_12_13"))
        val schemas = listOf(File("schemas"), File("app/schemas")).first { it.isDirectory }
        val v13 = File(schemas, "com.studioguu.miniaturegarden.data.local.AppDatabase/13.json").readText()
        val createSql = Regex("\"tableName\": \"grid_objects\",\\s*\"createSql\": \"([^\"]+)\"").find(v13)?.groupValues?.get(1)
        assertTrue(createSql, createSql!!.contains("`harvestCount` INTEGER NOT NULL DEFAULT 0"))
        assertFalse(database.contains("version = 12,"))
    }
}
