package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.Ground
import com.studioguu.miniaturegarden.domain.model.InventoryFilter
import com.studioguu.miniaturegarden.domain.model.InventoryOrder
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.InventorySort
import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.PlantGround
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.PlantSurroundings
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.Soil
import com.studioguu.miniaturegarden.domain.model.SoilGrade
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.repository.SecurityRepository
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ground and what the hoe does to it, and the food and grade of the soil (device feedback ②). */
class SoilTest {

    private val t0 = 1_700_000_000_000L
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val sunny = WeatherType.SUNNY

    private val repo = FakeGardenRepository().apply {
        profile = UserProfile(coins = 1_000)
        progress = GardenProgress(tutorialStep = TutorialStep.DONE, season = Season.SUMMER)
    }
    private val calc = CalculateOfflineProgressUseCase()
    private val hoe = HoeUseCase(repo, GardenMap.DEFAULT)
    private val care = CareForPlantUseCase(repo, calc)
    private val harvest = HarvestPlantUseCase(repo, calc, RecordCollectionUseCase(repo))
    private val place = PlaceItemUseCase(repo, GardenMap.DEFAULT, RecordCollectionUseCase(repo))
    private val remove = RemoveObjectUseCase(repo)
    private val sync = SyncGardenUseCase(repo, VerifyIntegrityUseCase(repo, Trusted), calc, RecordCollectionUseCase(repo))

    private fun soil(id: String = "soil", objectId: String = GridObject.TILLED_SOIL_OBJECT_ID, quality: Int = 0, food: Float = 1f) =
        GridObject(id, 2, 2, objectId = objectId, soilQuality = quality, nutritionLevel = food, lastUpdatedTimestamp = t0)

    private fun plant(species: PlantSpecies, id: String = "p", growth: Float = 1f, food: Float = 1f, status: PlantStatus = PlantStatus.HEALTHY) =
        GridObject(id, 2, 2, objectId = species.objectId, growthProgress = growth, nutritionLevel = food, status = status, lastUpdatedTimestamp = t0)

    private val stored get() = repo.objects.getValue("soil")

    /** A harvest that is never a good one. */
    private val noLuck = object : Random() {
        override fun nextBits(bitCount: Int) = (1 shl bitCount) - 1
    }

    /** A harvest that is always a good one, when it can be. */
    private val luck = object : Random() {
        override fun nextBits(bitCount: Int) = 0
    }

    private suspend fun strike(x: Int, y: Int, strikes: Int) = hoe(x, y, t0, strikes)

    // --- The hoe ---

    @Test
    fun `two strikes till the ground, two more ridge it, and that is as far as it goes`() = runBlocking {
        assertEquals(ActionResult.HOE_STRUCK, strike(2, 2, 0))
        assertTrue(repo.objects.isEmpty())
        assertEquals(ActionResult.SUCCESS, strike(2, 2, 1))
        val tilled = repo.objects.values.single()
        assertTrue(tilled.isLooseSoil)
        assertEquals(ActionResult.HOE_STRUCK, strike(2, 2, 0))
        assertTrue(repo.objects.values.single().isLooseSoil)
        assertEquals(ActionResult.SUCCESS, strike(2, 2, 1))
        assertTrue(repo.objects.values.single().isTilledSoil)
        assertEquals(tilled.instanceId, repo.objects.values.single().instanceId)
        // Nothing more to be done with the hoe: it does not go back either.
        assertEquals(ActionResult.TILE_OCCUPIED, strike(2, 2, 1))
        assertTrue(repo.objects.values.single().isTilledSoil)
    }

    @Test
    fun `a ridged bed keeps its food and grade, and the hoe leaves tilled earth with a flower on it alone`() = runBlocking {
        repo.put(soil(objectId = GridObject.LOOSE_SOIL_OBJECT_ID, quality = 2, food = 0.5f))
        assertEquals(ActionResult.SUCCESS, strike(2, 2, 1))
        assertEquals(2, stored.soilQuality)
        assertEquals(0.5f, stored.nutritionLevel, 0f)
        assertTrue(stored.isTilledSoil)

        repo.put(stored.copy(objectId = GridObject.LOOSE_SOIL_OBJECT_ID))
        repo.put(plant(PlantSpecies.TULIP, growth = 0.2f))
        assertEquals(ActionResult.TILE_OCCUPIED, strike(2, 2, 1))
        assertTrue(stored.isLooseSoil)
    }

    // --- Where things go ---

    @Test
    fun `vegetables go on ridges, flowers on tilled earth too, and the log where it did`() {
        val vegetables = listOf(
            PlantSpecies.TOMATO, PlantSpecies.STRAWBERRY, PlantSpecies.CARROT, PlantSpecies.DAIKON, PlantSpecies.EGGPLANT, PlantSpecies.WHEAT,
            PlantSpecies.WATERMELON, PlantSpecies.HAKUSAI, PlantSpecies.PAPRIKA, PlantSpecies.CORN, PlantSpecies.MINT,
        )
        val flowers = listOf(
            PlantSpecies.TULIP, PlantSpecies.COSMOS, PlantSpecies.SNOWDROP, PlantSpecies.SUNFLOWER, PlantSpecies.LAVENDER, PlantSpecies.ROSE,
            PlantSpecies.POINSETTIA, PlantSpecies.NANOHANA,
        )
        assertEquals(PlantSpecies.entries.toSet() - PlantSpecies.SHIITAKE, (vegetables + flowers).toSet())
        for (crop in vegetables) {
            assertEquals(crop.name, PlantGround.BED, crop.ground)
            assertTrue(crop.ground.allows(Ground.TILLED_SOIL))
            assertFalse(crop.ground.allows(Ground.LOOSE_SOIL))
        }
        for (crop in flowers) {
            assertEquals(crop.name, PlantGround.FLOWER_BED, crop.ground)
            assertTrue(crop.ground.allows(Ground.TILLED_SOIL) && crop.ground.allows(Ground.LOOSE_SOIL))
            assertFalse(crop.ground.allows(Ground.BARE_EARTH))
        }
        val log = PlantSpecies.SHIITAKE.ground
        assertTrue(log.allows(Ground.BARE_EARTH) && log.allows(Ground.LOOSE_SOIL) && log.allows(Ground.TILLED_SOIL))
        assertEquals(Ground.LOOSE_SOIL, GardenGrid(GardenMap.DEFAULT, listOf(soil(objectId = GridObject.LOOSE_SOIL_OBJECT_ID))).groundAt(2, 2))
        assertEquals(Ground.TILLED_SOIL, GardenGrid(GardenMap.DEFAULT, listOf(soil())).groundAt(2, 2))
    }

    @Test
    fun `a vegetable on tilled earth is told it wants a ridge, a flower on bare ground that it wants tilling`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TOMATO.itemId, 2))
        repo.putSlot(InventorySlot(1, GardenItem.SEED_SUNFLOWER.itemId, 2))
        repo.put(soil(objectId = GridObject.LOOSE_SOIL_OBJECT_ID))
        assertEquals(ActionResult.NEEDS_RIDGE, place(0, 2, 2, t0))
        assertEquals(ActionResult.NEEDS_TILLED_SOIL, place(1, 5, 5, t0))
        assertEquals(ActionResult.SUCCESS, place(1, 2, 2, t0))
        assertEquals(2, repo.slots.getValue(0).amount)
    }

    @Test
    fun `a new plant knows how much food its cell has`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TOMATO.itemId, 1))
        repo.put(soil(food = 0f))
        assertEquals(ActionResult.SUCCESS, place(0, 2, 2, t0))
        val tomato = repo.plants.single()
        assertEquals(0f, tomato.nutritionLevel, 0f)
        assertEquals(PlantStatus.UNNOURISHED, tomato.status)
        assertEquals(0f, tomato.growthProgress, 0f)
    }

    // --- Turning the ground back ---

    @Test
    fun `clearing ridges takes them back a step at a time, for nothing and at once`() = runBlocking {
        repo.put(soil(quality = 1, food = 0.5f))
        assertEquals(ActionResult.SUCCESS, remove("soil"))
        assertTrue(stored.isLooseSoil)
        assertEquals(1, stored.soilQuality)
        assertEquals(ActionResult.SUCCESS, remove("soil"))
        assertTrue(repo.objects.isEmpty())
        assertEquals(1_000L, repo.profile?.coins)
    }

    @Test
    fun `a vegetable goes with the ridges, a flower stays on the tilled earth and goes with it`() = runBlocking {
        val veg = plant(PlantSpecies.TOMATO)
        repo.put(soil())
        repo.put(veg)
        assertTrue(Soil.takesPlantBack(stored, veg))
        assertEquals(ActionResult.SUCCESS, remove.turnBackUnder("p"))
        assertNull(repo.objects["p"])
        assertTrue(stored.isLooseSoil)

        val flower = plant(PlantSpecies.TULIP, id = "f")
        repo.put(soil())
        repo.put(flower)
        assertFalse(Soil.takesPlantBack(stored, flower))
        assertEquals(ActionResult.SUCCESS, remove.turnBackUnder("f"))
        assertTrue(repo.objects.containsKey("f"))
        assertTrue(stored.isLooseSoil)

        assertTrue(Soil.takesPlantBack(stored, flower))
        assertEquals(ActionResult.SUCCESS, remove.turnBackUnder("f"))
        assertFalse(repo.objects.containsKey("f"))
        assertFalse(repo.objects.containsKey("soil"))
    }

    @Test
    fun `a log stays through it all`() = runBlocking {
        val log = plant(PlantSpecies.SHIITAKE, id = "log")
        repo.put(soil())
        repo.put(log)
        assertFalse(Soil.takesPlantBack(stored, log))
        remove.turnBackUnder("log")
        assertFalse(Soil.takesPlantBack(stored, log))
        remove.turnBackUnder("log")
        assertFalse(repo.objects.containsKey("soil"))
        assertTrue(repo.objects.containsKey("log"))
    }

    // --- Food ---

    @Test
    fun `a full cell feeds three crops, and with none left a crop is weak but can be harvested`() = runBlocking {
        repo.put(soil())
        repeat(3) { i ->
            repo.put(plant(PlantSpecies.CARROT))
            assertEquals(ActionResult.SUCCESS, harvest("p", t0, sunny, noLuck))
            assertEquals(1f - (i + 1) / 3f, stored.nutritionLevel, 1e-4f)
        }
        assertEquals(0f, stored.nutritionLevel, 0f)
        repo.put(plant(PlantSpecies.CARROT, food = 0f, status = PlantStatus.UNNOURISHED))
        val before = repo.profile!!.coins
        assertEquals(ActionResult.SUCCESS, harvest("p", t0, sunny, luck))
        assertEquals(before + PlantSpecies.CARROT.harvestCoins, repo.profile!!.coins)
    }

    @Test
    fun `food running out neither withers a crop nor slows it`() {
        val fed = plant(PlantSpecies.CORN, growth = 0.2f, food = 1f).copy(moistureLevel = 1f)
        val weak = fed.copy(nutritionLevel = 0f, status = PlantStatus.UNNOURISHED)
        val later = t0 + 6 * hour
        assertEquals(calc(fed, later, sunny).growthProgress, calc(weak, later, sunny).growthProgress, 0f)
        val long = calc(weak.copy(moistureLevel = 1f), t0 + 10 * hour, WeatherType.RAINY)
        assertEquals(PlantStatus.UNNOURISHED, long.status)
        assertEquals(0f, long.stressRate, 0f)
        // Its food is the soil's and stays where it is, however long.
        assertEquals(1f, calc(fed, t0 + 6 * day, WeatherType.RAINY).nutritionLevel, 0f)
    }

    @Test
    fun `growing time is the same on any soil, fed or not`() {
        val hours = 5 * hour
        fun grown(soil: GridObject): Float {
            val tomato = plant(PlantSpecies.CORN, growth = 0f)
            return calc(tomato, t0 + hours, sunny, listOf(tomato, soil)).growthProgress
        }
        val plain = grown(soil())
        assertEquals(plain, grown(soil(quality = 1)), 0f)
        assertEquals(plain, grown(soil(quality = 2, food = 0f)), 0f)
        assertEquals(plain, grown(soil(food = 0.3f)), 0f)
        assertEquals(5f / PlantSpecies.CORN.growthHours, plain, 1e-4f)
    }

    @Test
    fun `fertilizer and compost grow nothing`() = runBlocking {
        repo.put(soil(food = 0f))
        repo.put(plant(PlantSpecies.CORN, growth = 0.3f, food = 0f, status = PlantStatus.UNNOURISHED))
        care.fertilize("p", t0, sunny)
        assertEquals(0.3f, repo.objects.getValue("p").growthProgress, 0f)
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TOMATO.itemId, 1))
        remove("p")
        assertEquals(ActionResult.SUCCESS, place(0, 2, 2, t0))
        assertEquals(0f, repo.plants.single().growthProgress, 0f)
    }

    @Test
    fun `fertilizer fills the cell and costs 10 plus 5 a level`() = runBlocking {
        assertEquals(15, Soil.fertilizerCost(1))
        assertEquals(60, Soil.fertilizerCost(10))
        repo.profile = UserProfile(coins = 100, playerLevel = 4)
        repo.put(soil(food = 0f))
        repo.put(plant(PlantSpecies.CARROT, growth = 0.5f, food = 0f, status = PlantStatus.UNNOURISHED))

        assertEquals(ActionResult.SUCCESS, care.fertilize("p", t0, sunny))
        assertEquals(1f, stored.nutritionLevel, 0f)
        assertEquals(1f, repo.objects.getValue("p").nutritionLevel, 0f)
        assertEquals(PlantStatus.HEALTHY, repo.objects.getValue("p").status)
        assertEquals(70L, repo.profile?.coins)
        assertEquals(CareForPlantUseCase.FERTILIZE_XP.toLong(), repo.profile?.currentXp)

        // Full: no more to take. And the bed can be fed with nothing on it.
        assertEquals(ActionResult.ALREADY_FED, care.fertilize("p", t0, sunny))
        assertEquals(70L, repo.profile?.coins)
        repo.objects.remove("p")
        repo.put(stored.copy(nutritionLevel = 0.5f))
        assertEquals(ActionResult.SUCCESS, care.fertilize("soil", t0, sunny))
        assertEquals(1f, stored.nutritionLevel, 0f)
    }

    @Test
    fun `there is no feeding a log, or too little money`() = runBlocking {
        repo.put(plant(PlantSpecies.SHIITAKE, id = "log"))
        assertEquals(ActionResult.NO_SOIL, care.fertilize("log", t0, sunny))
        repo.objects.remove("log")
        repo.profile = UserProfile(coins = 14)
        repo.put(soil(food = 0.5f))
        assertEquals(ActionResult.NOT_ENOUGH_COINS, care.fertilize("soil", t0, sunny))
        assertEquals(0.5f, stored.nutritionLevel, 0f)
    }

    @Test
    fun `a withered crop pulled up puts a serving back, up to full, and a growing one nothing`() = runBlocking {
        repo.put(soil(food = 0f))
        repo.put(plant(PlantSpecies.CARROT, growth = 0.4f))
        remove("p")
        assertEquals(0f, stored.nutritionLevel, 0f)
        repo.put(plant(PlantSpecies.CARROT, growth = 0.4f).copy(status = PlantStatus.WITHERED))
        remove("p")
        assertEquals(1f / 3f, stored.nutritionLevel, 1e-4f)
        repo.put(stored.copy(nutritionLevel = 0.9f))
        repo.put(plant(PlantSpecies.CARROT, growth = 0.4f).copy(status = PlantStatus.WITHERED))
        remove("p")
        assertEquals(1f, stored.nutritionLevel, 0f)
        // A log gives nothing, and nothing is left as a thing on the ground.
        repo.put(stored.copy(nutritionLevel = 0f))
        repo.put(plant(PlantSpecies.SHIITAKE, id = "log"))
        remove("log")
        assertEquals(0f, stored.nutritionLevel, 0f)
        assertEquals(1, repo.objects.size)
    }

    // --- Grade ---

    private suspend fun harvests(times: Int, vararg crops: PlantSpecies) {
        repeat(times) { i ->
            repo.put(stored.copy(nutritionLevel = 1f))
            repo.put(plant(crops[i % crops.size]))
            assertEquals(ActionResult.SUCCESS, harvest("p", t0, sunny, noLuck))
        }
    }

    @Test
    fun `five harvests of one crop raise the grade, four when the crops change`() = runBlocking {
        repo.put(soil())
        harvests(4, PlantSpecies.CARROT)
        assertEquals(0, stored.soilQuality)
        harvests(1, PlantSpecies.CARROT)
        assertEquals(1, stored.soilQuality)

        repo.put(soil())
        // The first harvest has nothing to differ from: it is the next ones that are told apart.
        harvests(5, PlantSpecies.CARROT, PlantSpecies.TOMATO)
        assertEquals(1, stored.soilQuality)
        assertEquals(24 + 30 * 4, stored.soilProgress + Soil.POINTS_PER_GRADE)
    }

    @Test
    fun `a crop's later harvests take half the food and bring half the points`() = runBlocking {
        for (grade in SoilGrade.entries) {
            repo.put(soil().copy(soilQuality = grade.ordinal))
            repo.put(plant(PlantSpecies.TOMATO).copy(harvestCount = 1))
            assertEquals(ActionResult.SUCCESS, harvest("p", t0, sunny, noLuck))
            assertEquals(grade.name, 1f - 0.5f * grade.nutritionPerHarvest / Soil.SERVINGS, stored.nutritionLevel, 1e-4f)
        }
        repo.put(soil())
        repo.put(plant(PlantSpecies.TOMATO).copy(harvestCount = 1))
        harvest("p", t0, sunny, noLuck)
        assertEquals(Soil.SAME_CROP_POINTS / 2, stored.soilProgress)
        repo.put(stored.copy(lastCropId = PlantSpecies.CARROT.objectId, soilProgress = 0))
        repo.put(plant(PlantSpecies.TOMATO).copy(harvestCount = 3))
        harvest("p", t0, sunny, noLuck)
        assertEquals(Soil.NEW_CROP_POINTS / 2, stored.soilProgress)
    }

    @Test
    fun `with no food a harvest does nothing for the grade, and takes nothing from it`() = runBlocking {
        repo.put(soil(quality = 1, food = 0f))
        repeat(6) {
            repo.put(plant(PlantSpecies.CARROT, food = 0f, status = PlantStatus.UNNOURISHED))
            harvest("p", t0, sunny, noLuck)
        }
        assertEquals(1, stored.soilQuality)
        assertEquals(0, stored.soilProgress)
    }

    @Test
    fun `the grade stops at fluffy, which takes more out of the soil`() = runBlocking {
        repo.put(soil(quality = 2))
        harvests(6, PlantSpecies.CARROT)
        assertEquals(2, stored.soilQuality)
        repo.put(soil(quality = 2))
        repo.put(plant(PlantSpecies.CARROT))
        harvest("p", t0, sunny, noLuck)
        assertEquals(1f - 1.5f / 3f, stored.nutritionLevel, 1e-4f)
        repo.put(soil(quality = 1))
        repo.put(plant(PlantSpecies.CARROT))
        harvest("p", t0, sunny, noLuck)
        assertEquals(1f - 1.25f / 3f, stored.nutritionLevel, 1e-4f)
    }

    private suspend fun absent(from: Long, days: Int): Long {
        val back = from + days * day
        sync(back, sunny)
        return back
    }

    @Test
    fun `left alone for two weeks soil loses a grade, but one absence alone costs none`() = runBlocking {
        repo.put(soil(quality = 2))
        // However long it lasts: seven days count.
        var now = absent(t0, 30)
        assertEquals(2, stored.soilQuality)
        assertEquals(7 * day, stored.idleMillis)
        now = absent(now, 6)
        assertEquals(2, stored.soilQuality)
        now = absent(now, 1)
        assertEquals(1, stored.soilQuality)
        assertEquals(0L, stored.idleMillis)
        // Plain soil has nothing left to lose.
        repo.put(stored.copy(soilQuality = 0))
        now = absent(now, 7)
        absent(now, 7)
        assertEquals(0, stored.soilQuality)
    }

    @Test
    fun `a withered plant counts as nothing growing, a living one stops the count`() = runBlocking {
        repo.put(soil(quality = 1))
        repo.put(plant(PlantSpecies.ROSE, growth = 0.2f).copy(status = PlantStatus.WITHERED, stressRate = 1f))
        var now = absent(t0, 7)
        assertEquals(7 * day, stored.idleMillis)
        now = absent(now, 7)
        assertEquals(0, stored.soilQuality)

        repo.objects.remove("p")
        repo.put(stored.copy(soilQuality = 1, idleMillis = 13 * day, lastUpdatedTimestamp = now))
        repo.put(plant(PlantSpecies.ROSE, growth = 0.2f).copy(lastUpdatedTimestamp = now))
        absent(now, 3)
        assertEquals(1, stored.soilQuality)
        assertEquals(0L, stored.idleMillis)
    }

    @Test
    fun `better soil dries out later`() {
        val rose = plant(PlantSpecies.ROSE, growth = 0.2f)
        fun moisture(quality: Int) = calc(rose, t0 + 18 * hour, sunny, listOf(rose, soil(quality = quality))).moistureLevel
        assertEquals(1f - 18f / 36f, moisture(0), 1e-3f)
        assertEquals(1f - 18f / (36f * 1.15f), moisture(1), 1e-3f)
        assertEquals(1f - 18f / (36f * 1.3f), moisture(2), 1e-3f)
        assertEquals(1f, PlantSurroundings.of(rose, listOf(rose, soil(quality = 2))).moistureDrainFactor, 0f)
        assertEquals(1f, PlantSurroundings.of(rose, listOf(rose, soil(quality = 2))).growthFactor, 0f)
    }

    @Test
    fun `good harvests pay half as much again, on good soil with food in it`() = runBlocking {
        assertEquals(0f, SoilGrade.PLAIN.bountyChance, 0f)
        assertEquals(0.1f, SoilGrade.GOOD.bountyChance, 0f)
        assertEquals(0.2f, SoilGrade.FLUFFY.bountyChance, 0f)
        repo.put(soil(quality = 2))
        repo.put(plant(PlantSpecies.CARROT))
        var paid = 0
        var good = false
        harvest("p", t0, sunny, luck) { reward, bounty ->
            paid = reward.coins
            good = bounty
        }
        assertTrue(good)
        assertEquals(Math.round(PlantSpecies.CARROT.harvestCoins * 1.5f), paid)
        assertEquals(1_000L + paid, repo.profile?.coins)

        // Plain soil never; nor with no food left.
        repo.put(soil(quality = 0))
        repo.put(plant(PlantSpecies.CARROT))
        harvest("p", t0, sunny, luck) { _, bounty -> good = bounty }
        assertFalse(good)
        repo.put(soil(quality = 2, food = 0f))
        repo.put(plant(PlantSpecies.CARROT, food = 0f))
        harvest("p", t0, sunny, luck) { _, bounty -> good = bounty }
        assertFalse(good)
    }

    // --- Seasons ---

    @Test
    fun `a crop that bears again keeps bearing in its season and under glass, and ends past it`() {
        val tomato = plant(PlantSpecies.TOMATO)
        assertFalse(HarvestPlantUseCase.endsAtHarvest(tomato, Season.SUMMER, underGlass = false))
        assertTrue(HarvestPlantUseCase.endsAtHarvest(tomato, Season.AUTUMN, underGlass = false))
        assertFalse(HarvestPlantUseCase.endsAtHarvest(tomato, Season.AUTUMN, underGlass = true))
        // One harvest is all a carrot has anyway; a crop of every season has no end.
        assertFalse(HarvestPlantUseCase.endsAtHarvest(plant(PlantSpecies.CARROT), Season.SUMMER, underGlass = false))
        assertFalse(HarvestPlantUseCase.endsAtHarvest(plant(PlantSpecies.MINT), Season.WINTER, underGlass = false))
    }

    @Test
    fun `its last harvest pays, and the plant goes into the soil as a serving of food`() = runBlocking {
        repo.progress = repo.progress!!.copy(season = Season.AUTUMN)
        repo.put(soil(food = 2f / 3f))
        repo.put(plant(PlantSpecies.TOMATO).copy(harvestCount = 2))
        assertEquals(ActionResult.SUCCESS, harvest("p", t0, sunny, noLuck))
        assertNull(repo.objects["p"])
        // A later harvest takes half a serving, the compost gives a whole one.
        assertEquals(2f / 3f + 0.5f / 3f, stored.nutritionLevel, 1e-4f)
        assertEquals(1_000L + PlantSpecies.TOMATO.regrowCoins, repo.profile?.coins)
    }

    @Test
    fun `in season it stays, knowing what food is left`() = runBlocking {
        repo.put(soil(food = 1f / 3f))
        repo.put(plant(PlantSpecies.TOMATO, food = 1f / 3f))
        assertEquals(ActionResult.SUCCESS, harvest("p", t0, sunny, noLuck))
        val tomato = repo.objects.getValue("p")
        assertEquals(0f, tomato.nutritionLevel, 0f)
        assertEquals(PlantStatus.UNNOURISHED, tomato.status)
    }

    @Test
    fun `a plant out of its season stays where it is and goes on growing`() {
        val rose = plant(PlantSpecies.ROSE, growth = 0.2f)
        assertTrue(calc(rose, t0 + 5 * hour, sunny).growthProgress > 0.2f)
    }

    @Test
    fun `a seed out of its season cannot be sown, and stays in the bag at the back`() = runBlocking {
        repo.progress = repo.progress!!.copy(season = Season.SUMMER)
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TULIP.itemId, 3, acquiredOrder = 9))
        repo.putSlot(InventorySlot(1, GardenItem.SEED_TOMATO.itemId, 1, acquiredOrder = 1))
        repo.putSlot(InventorySlot(2, GardenItem.DECO_ROCK.itemId, 1, acquiredOrder = 2))
        repo.put(soil())
        assertEquals(ActionResult.OUT_OF_SEASON, place(0, 2, 2, t0))
        assertEquals(3, repo.slots.getValue(0).amount)

        val slots = repo.slots.values.toList()
        for (sort in InventorySort.entries) {
            val order = InventoryOrder.arrange(slots, sort, InventoryFilter.ALL, Season.SUMMER)
            assertEquals(sort.name, GardenItem.SEED_TULIP.itemId, order.last().itemId)
        }
        assertTrue(InventoryOrder.isOutOfSeason(GardenItem.SEED_TULIP, Season.SUMMER))
        assertFalse(InventoryOrder.isOutOfSeason(GardenItem.DECO_ROCK, Season.SUMMER))
        assertFalse(InventoryOrder.isOutOfSeason(GardenItem.SEED_MINT, Season.SUMMER))
        assertEquals(Season.SPRING, InventoryOrder.nextSeason(GardenItem.SEED_TULIP, Season.SUMMER))
        assertEquals(Season.AUTUMN, InventoryOrder.nextSeason(GardenItem.SEED_ROSE, Season.SUMMER))
    }

    @Test
    fun `the season's letter names what has had its last, and a crop in season goes unnamed`() = runBlocking {
        repo.put(soil())
        repo.put(plant(PlantSpecies.TOMATO))
        repo.put(GridObject("m", 3, 3, objectId = PlantSpecies.MINT.objectId, lastUpdatedTimestamp = t0))
        repo.progress = GardenProgress(tutorialStep = TutorialStep.DONE, season = Season.SUMMER, seasonStartedAt = t0)
        val turn = t0 + 10 * day
        AdvanceSeasonUseCase(repo)(turn)
        val letter = repo.getLetters().single { it.endingCrops.isNotEmpty() }
        assertEquals(listOf(PlantSpecies.TOMATO.objectId), letter.endingCrops)
        assertTrue(Letter.arrived(Season.WINTER, t0).endingCrops.isEmpty())
    }
}

private object Trusted : SecurityRepository {
    override fun isSignatureValid(profile: UserProfile) = true
    override fun isTamperFlagged() = false
    override fun setTamperFlag(flagged: Boolean) = Unit
}
