package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherForecast
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.usecase.CalculateOfflineProgressUseCase.Companion.OVERRIPE_HOURS
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WitherAndWeatherTest {

    private val hour = 3_600_000L
    private val day = WeatherType.DAY_MILLIS

    /** Midnight UTC, so that whole days line up with the changes of the weather. */
    private val t0 = 19_700L * day
    private val sunny = WeatherType.SUNNY
    private val rainy = WeatherType.RAINY

    private val calc = CalculateOfflineProgressUseCase()
    private val repo = FakeGardenRepository().apply { profile = UserProfile(coins = 100) }
    private val record = RecordCollectionUseCase(repo)
    private val care = CareForPlantUseCase(repo, calc)
    private val place = PlaceItemUseCase(repo, GardenMap.DEFAULT, record)
    private val harvest = HarvestPlantUseCase(repo, calc, record)
    private val remove = RemoveObjectUseCase(repo)
    private val sync = SyncGardenUseCase(repo, VerifyIntegrityUseCase(repo, AlwaysTrusted), calc)

    private fun tulip(
        id: String = "p",
        growth: Float = 0f,
        moisture: Float = 1f,
        nutrition: Float = 1f,
        stress: Float = 0f,
        status: PlantStatus = PlantStatus.HEALTHY,
        x: Int = 0,
        at: Long = t0,
    ) = GridObject(
        instanceId = id, gridX = x, gridY = 0, objectId = PlantSpecies.TULIP.objectId,
        growthProgress = growth, moistureLevel = moisture, nutritionLevel = nutrition,
        stressRate = stress, status = status, lastUpdatedTimestamp = at,
    )

    /** A forecast that reads its weather off a list, one entry per day from [t0] on. */
    private fun days(vararg weather: WeatherType) = WeatherForecast { time ->
        weather[Math.floorDiv(time - t0, day).toInt().coerceIn(0, weather.lastIndex)]
    }

    // --- Going over after full bloom ---

    @Test
    fun `a plant in full bloom is not gone or withered straight away`() {
        val bloom = tulip(growth = 1f)

        val later = calc(bloom, t0 + 6 * hour, sunny)

        assertEquals(1f, later.growthProgress, 0f)
        assertEquals(PlantStatus.HEALTHY, later.status)
        assertEquals(6f / OVERRIPE_HOURS, later.stressRate, 1e-4f)
    }

    @Test
    fun `left standing in bloom for long enough it withers, and it is still there`() {
        // Kept watered and fed, so that nothing but the waiting is at work.
        var plant = tulip(growth = 1f)
        var now = t0
        repeat(OVERRIPE_HOURS / 12 - 1) {
            now += 12 * hour
            plant = calc(plant, now, sunny).copy(moistureLevel = 1f, nutritionLevel = 1f)
            assertFalse(plant.status == PlantStatus.WITHERED)
        }

        plant = calc(plant, now + 12 * hour, sunny)

        assertEquals(PlantStatus.WITHERED, plant.status)
        assertEquals(1f, plant.stressRate, 0f)
        assertEquals(1f, plant.growthProgress, 0f) // withered, not removed and not reset
    }

    @Test
    fun `the time to go over is counted from the bloom, not from the planting`() {
        // 0.5 grown: 24 healthy hours short of bloom for a tulip. It dries out long before that
        // unless it is watered, so it is, here.
        val halfway = tulip(growth = 0.5f)
        val atBloom = calc(halfway, t0 + 12 * hour, sunny).copy(moistureLevel = 1f)
            .let { calc(it, t0 + 24 * hour, sunny) }
        assertEquals(1f, atBloom.growthProgress, 1e-4f)
        assertEquals(0f, atBloom.stressRate, 1e-3f)

        val twoHoursOn = calc(atBloom.copy(moistureLevel = 1f), t0 + 26 * hour, sunny)
        assertEquals(2f / OVERRIPE_HOURS, twoHoursOn.stressRate, 1e-3f)
    }

    @Test
    fun `a growing plant still shakes off old hardship, one in bloom does not`() {
        val growing = calc(tulip(growth = 0.1f, stress = 0.5f), t0 + 6 * hour, sunny)
        val inBloom = calc(tulip(growth = 1f, stress = 0.5f), t0 + 6 * hour, sunny)

        assertTrue(growing.stressRate < 0.5f)
        assertTrue(inBloom.stressRate > 0.5f)
    }

    @Test
    fun `a harvest in time is unaffected, and too late it is refused`() = runBlocking {
        repo.put(tulip("soon", growth = 1f))
        repo.put(tulip("late", growth = 1f, x = 1))

        assertEquals(ActionResult.SUCCESS, harvest("soon", t0 + 10 * hour, sunny))
        assertEquals(150L, repo.profile?.coins)

        val tooLate = t0 + (OVERRIPE_HOURS + 1) * hour
        assertEquals(ActionResult.WITHERED, harvest("late", tooLate, sunny))
        assertNotNull(repo.objects["late"]) // still standing
        assertEquals(150L, repo.profile?.coins)
    }

    @Test
    fun `a withered plant stays as it is however much time passes`() {
        val withered = tulip(growth = 0.6f, moisture = 0f, stress = 1f, status = PlantStatus.WITHERED)

        val muchLater = calc(withered, t0 + 6 * day, days(sunny, rainy, sunny, rainy, sunny, sunny, sunny))

        assertEquals(withered.copy(lastUpdatedTimestamp = muchLater.lastUpdatedTimestamp), muchLater)
        assertEquals(PlantStatus.WITHERED, muchLater.status)
    }

    // --- Reviving ---

    @Test
    fun `reviving costs coins and brings the plant back to health at the growth it had`() = runBlocking {
        repo.put(tulip(growth = 0.6f, moisture = 0f, nutrition = 0.05f, stress = 1f, status = PlantStatus.WITHERED))

        assertEquals(ActionResult.SUCCESS, care.revive("p", t0 + hour, sunny))

        val revived = repo.objects.getValue("p")
        assertEquals(PlantStatus.HEALTHY, revived.status)
        assertEquals(0f, revived.stressRate, 0f)
        assertEquals(1f, revived.moistureLevel, 0f)
        assertEquals(1f, revived.nutritionLevel, 0f)
        assertEquals(0.6f, revived.growthProgress, 0f)
        assertEquals(t0 + hour, revived.lastUpdatedTimestamp)
        assertEquals(100L - CareForPlantUseCase.REVIVE_COST, repo.profile?.coins)
        assertEquals(1, repo.profileSaves)

        // And from there it grows again.
        val grownOn = calc(revived, t0 + 7 * hour, sunny)
        assertTrue(grownOn.growthProgress > 0.6f)
        assertEquals(PlantStatus.HEALTHY, grownOn.status)
    }

    @Test
    fun `a plant that went over in bloom can be revived and then harvested`() = runBlocking {
        repo.put(tulip(growth = 1f, stress = 1f, status = PlantStatus.WITHERED))

        assertEquals(ActionResult.WITHERED, harvest("p", t0, sunny))
        assertEquals(ActionResult.SUCCESS, care.revive("p", t0, sunny))
        assertEquals(ActionResult.SUCCESS, harvest("p", t0 + hour, sunny))

        assertEquals(100L - CareForPlantUseCase.REVIVE_COST + PlantSpecies.TULIP.harvestCoins, repo.profile?.coins)
    }

    @Test
    fun `reviving is refused without the coins, and for plants that are not withered`() = runBlocking {
        repo.put(tulip("dead", stress = 1f, status = PlantStatus.WITHERED))
        repo.put(tulip("fine", x = 1))
        repo.put(GridObject(instanceId = "rock", gridX = 2, gridY = 0, objectId = "deco_rock", lastUpdatedTimestamp = t0))

        assertEquals(ActionResult.NOT_WITHERED, care.revive("fine", t0, sunny))
        assertEquals(ActionResult.NOT_A_PLANT, care.revive("rock", t0, sunny))
        assertEquals(ActionResult.NOT_FOUND, care.revive("nothing", t0, sunny))
        assertEquals(100L, repo.profile?.coins)

        repo.profile = UserProfile(coins = CareForPlantUseCase.REVIVE_COST - 1L)
        assertEquals(ActionResult.NOT_ENOUGH_COINS, care.revive("dead", t0, sunny))
        assertEquals(PlantStatus.WITHERED, repo.objects.getValue("dead").status)
        assertEquals(CareForPlantUseCase.REVIVE_COST - 1L, repo.profile?.coins)
    }

    @Test
    fun `a plant that withered while the app was away can be revived on return`() = runBlocking {
        // Stored as healthy; five dry days later it is not.
        repo.put(tulip(growth = 0.3f))

        assertEquals(ActionResult.SUCCESS, care.revive("p", t0 + 5 * day, sunny))

        assertEquals(PlantStatus.HEALTHY, repo.objects.getValue("p").status)
    }

    @Test
    fun `watering and fertilizing still do nothing for a withered plant`() = runBlocking {
        repo.put(tulip(moisture = 0f, stress = 1f, status = PlantStatus.WITHERED))

        assertEquals(ActionResult.WITHERED, care.water("p", t0, sunny))
        assertEquals(ActionResult.WITHERED, care.fertilize("p", t0, sunny))
        assertEquals(100L, repo.profile?.coins)
    }

    // --- Clearing a withered plant away: compost ---

    @Test
    fun `a withered plant that is cleared away becomes compost in its bed`() = runBlocking {
        repo.till(0, 0)
        repo.put(tulip(stress = 1f, status = PlantStatus.WITHERED))
        val soil = repo.objects.getValue("soil_0_0")
        assertFalse(soil.isComposted)

        assertEquals(ActionResult.SUCCESS, remove("p"))

        assertTrue(repo.plants.isEmpty())
        assertTrue(repo.objects.getValue("soil_0_0").isComposted)
        assertEquals(100L, repo.profile?.coins) // free, and nothing refunded
    }

    @Test
    fun `the next seed in composted soil starts ahead and uses the compost up`() = runBlocking {
        repo.till(0, 0)
        repo.put(tulip(stress = 1f, status = PlantStatus.WITHERED))
        repo.putSlot(InventorySlot(0, GardenItem.SEED_SUNFLOWER.itemId, 2))
        remove("p")

        assertEquals(ActionResult.SUCCESS, place(0, 0, 0, t0))

        val first = repo.plants.single()
        assertEquals(GridObject.COMPOST_GROWTH_BONUS, first.growthProgress, 0f)
        assertFalse(repo.objects.getValue("soil_0_0").isComposted)

        // The one after that starts from nothing again.
        repo.put(first.copy(growthProgress = 1f))
        harvest(first.instanceId, t0, sunny)
        place(0, 0, 0, t0)
        assertEquals(0f, repo.plants.single().growthProgress, 0f)
    }

    @Test
    fun `only withered plants make compost`() = runBlocking {
        repo.till(0, 0)
        repo.till(1, 0)
        repo.put(tulip("alive", growth = 0.4f))
        repo.put(GridObject(instanceId = "rock", gridX = 1, gridY = 0, objectId = "deco_rock", lastUpdatedTimestamp = t0))

        remove("alive")
        remove("rock")

        assertFalse(repo.objects.getValue("soil_0_0").isComposted)
        assertFalse(repo.objects.getValue("soil_1_0").isComposted)
        assertTrue(RemoveObjectUseCase.leavesCompost(tulip(stress = 1f, status = PlantStatus.WITHERED)))
        assertFalse(RemoveObjectUseCase.leavesCompost(tulip()))
    }

    @Test
    fun `a withered plant with no bed under it is simply cleared away`() = runBlocking {
        repo.put(tulip(stress = 1f, status = PlantStatus.WITHERED))

        assertEquals(ActionResult.SUCCESS, remove("p"))

        assertTrue(repo.objects.isEmpty())
    }

    @Test
    fun `compost keeps, and does nothing for things that are not seeds`() = runBlocking {
        repo.till(0, 0)
        repo.put(tulip(stress = 1f, status = PlantStatus.WITHERED))
        remove("p")
        repo.putSlot(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 1))

        // A month goes by.
        val soil = calc(repo.objects.getValue("soil_0_0"), t0 + 30 * day, WeatherForecast.Daily)
        assertTrue(soil.isComposted)

        place(0, 0, 0, t0)
        assertTrue(repo.objects.getValue("soil_0_0").isComposted)
    }

    // --- Rain ---

    @Test
    fun `rain waters a dry plant`() {
        val dry = tulip(moisture = 0.05f, status = PlantStatus.THIRSTY)

        val afterRain = calc(dry, t0 + 2 * hour, rainy)

        assertEquals(1f, afterRain.moistureLevel, 0f)
        assertEquals(PlantStatus.HEALTHY, afterRain.status)
        // Watered at once, so it has been growing for the two hours as well.
        assertTrue(afterRain.growthProgress > 0f)
    }

    @Test
    fun `nothing moves closer to withering while it rains`() {
        val thirsty = tulip("a", moisture = 0f, stress = 0.9f, status = PlantStatus.THIRSTY)
        val hungry = tulip("b", nutrition = 0.05f, stress = 0.9f, status = PlantStatus.UNNOURISHED)
        val inBloom = tulip("c", growth = 1f, stress = 0.9f)

        for (plant in listOf(thirsty, hungry, inBloom)) {
            val wet = calc(plant, t0 + 3 * day, rainy)
            val dry = calc(plant, t0 + 3 * day, sunny)
            assertTrue(plant.instanceId, wet.stressRate <= 0.9f)
            assertFalse(plant.instanceId, wet.status == PlantStatus.WITHERED)
            assertEquals(plant.instanceId, PlantStatus.WITHERED, dry.status)
        }
    }

    @Test
    fun `rain changes how fast plants grow only a little`() {
        val wet = calc(tulip(), t0 + 10 * hour, rainy).growthProgress
        val dry = calc(tulip(), t0 + 10 * hour, sunny).growthProgress

        assertTrue(wet >= dry)
        assertTrue(wet <= dry * 1.25f)
    }

    @Test
    fun `rain does not bring a withered plant back`() {
        val withered = tulip(moisture = 0f, stress = 1f, status = PlantStatus.WITHERED)

        val afterRain = calc(withered, t0 + day, rainy)

        assertEquals(PlantStatus.WITHERED, afterRain.status)
        assertEquals(0f, afterRain.moistureLevel, 0f)
    }

    // --- Time away, through changing weather ---

    @Test
    fun `one kind of weather throughout gives the same result either way`() {
        val start = tulip(growth = 0.2f, moisture = 0.7f)
        for (weather in WeatherType.entries) {
            val asType = calc(start, t0 + 3 * day + 7 * hour, weather)
            val asForecast = calc(start, t0 + 3 * day + 7 * hour, WeatherForecast { weather })

            assertEquals(weather.name, asType.growthProgress, asForecast.growthProgress, 1e-4f)
            assertEquals(weather.name, asType.moistureLevel, asForecast.moistureLevel, 1e-4f)
            assertEquals(weather.name, asType.stressRate, asForecast.stressRate, 1e-4f)
            assertEquals(weather.name, asType.status, asForecast.status)
            assertEquals(weather.name, asType.lastUpdatedTimestamp, asForecast.lastUpdatedTimestamp)
        }
    }

    @Test
    fun `time away is weighed day by day with the weather each day had`() {
        val start = tulip(growth = 0f)
        val forecast = days(sunny, rainy, sunny)

        val result = calc(start, t0 + 2 * day + 12 * hour, forecast)

        // The same thing, done by hand one day at a time.
        val afterDay1 = calc(start, t0 + day, sunny)
        val afterDay2 = calc(afterDay1, t0 + 2 * day, rainy)
        val expected = calc(afterDay2, t0 + 2 * day + 12 * hour, sunny)
        assertEquals(expected, result)
        // And it is not what either weather alone would have given.
        assertFalse(result == calc(start, t0 + 2 * day + 12 * hour, sunny))
        assertFalse(result == calc(start, t0 + 2 * day + 12 * hour, rainy))
    }

    @Test
    fun `a rainy day in the middle of an absence saves a plant that the sun alone would kill`() {
        // A tulip dries out in a day and withers a day after it has gone thirsty.
        val start = tulip(growth = 0.1f)

        val allSun = calc(start, t0 + 3 * day, days(sunny, sunny, sunny))
        val rainOnDay2 = calc(start, t0 + 3 * day, days(sunny, rainy, sunny))

        assertEquals(PlantStatus.WITHERED, allSun.status)
        assertFalse(rainOnDay2.status == PlantStatus.WITHERED)
        // Watered by the rain, then one more day of sun.
        assertTrue(rainOnDay2.stressRate < 1f)
        assertTrue(rainOnDay2.growthProgress > allSun.growthProgress)
    }

    @Test
    fun `rain that comes after the plant has withered is too late`() {
        val start = tulip(growth = 0.1f)

        val result = calc(start, t0 + 4 * day, days(sunny, sunny, sunny, rainy))

        assertEquals(PlantStatus.WITHERED, result.status)
    }

    @Test
    fun `the part of a minute left at a change of weather is not lost`() {
        // Starts 30 s before midnight and ends 30 s after: one whole minute, across the change.
        val start = tulip(at = t0 + day - 30_000)

        val result = calc(start, t0 + day + 30_000, days(sunny, rainy))

        assertEquals(t0 + day + 30_000, result.lastUpdatedTimestamp)
        assertTrue(result.growthProgress > 0f)
    }

    @Test
    fun `many short visits add up to the same as one long absence`() {
        val start = tulip(growth = 0.05f)
        val forecast = days(sunny, rainy, sunny, sunny)
        val end = t0 + 3 * day + 5 * hour

        var stepped = start
        var now = t0
        while (now < end) {
            now = minOf(now + 37 * 60_000L + 13_000L, end)
            stepped = calc(stepped, now, forecast)
        }
        val atOnce = calc(start, end, forecast)

        assertEquals(atOnce.growthProgress, stepped.growthProgress, 2e-3f)
        assertEquals(atOnce.moistureLevel, stepped.moistureLevel, 2e-3f)
        assertEquals(atOnce.stressRate, stepped.stressRate, 2e-3f)
        assertEquals(atOnce.status, stepped.status)
    }

    @Test
    fun `only the last seven days of a long absence count, with their own weather`() {
        val start = tulip(growth = 1f, at = t0)
        // Day 0..2 would be sunny; they fall outside the window. The seven days that count are rainy.
        val forecast = days(sunny, sunny, sunny, rainy, rainy, rainy, rainy, rainy, rainy, rainy)

        val result = calc(start, t0 + 10 * day, forecast)

        assertEquals(t0 + 10 * day, result.lastUpdatedTimestamp)
        // Seven days of rain: hungry by now, but watered and no closer to withering.
        assertEquals(0f, result.stressRate, 0f)
        assertEquals(1f, result.moistureLevel, 0f)
        assertEquals(PlantStatus.UNNOURISHED, result.status)
        // Had the three sunny days counted as well, it would have gone over a little.
        assertTrue(calc(start, t0 + 10 * day, days(sunny)).stressRate > 0f)
    }

    @Test
    fun `nothing happens when no time has passed, whatever the forecast`() {
        val start = tulip(growth = 0.4f, moisture = 0.1f)

        assertSame(start, calc(start, t0, WeatherForecast.Daily))
        assertSame(start, calc(start, t0 + 20_000, days(rainy)))
    }

    @Test
    fun `a clock set back is repaired under a forecast too`() {
        val fromTheFuture = tulip(at = t0 + 5 * hour)

        val result = calc(fromTheFuture, t0, WeatherForecast.Daily)

        assertEquals(t0, result.lastUpdatedTimestamp)
        assertEquals(0f, result.growthProgress, 0f)
    }

    @Test
    fun `the daily forecast is the game's own weather`() {
        for (d in 0 until 60) {
            val time = t0 + d * day + 5 * hour
            assertEquals(WeatherType.forTime(time), WeatherForecast.Daily.at(time))
        }
        // A kind of weather is a forecast of itself.
        assertEquals(rainy, rainy.at(t0))
        assertTrue(rainy.isProtective)
        assertFalse(sunny.isProtective)
        assertFalse(WeatherType.CLOUDY.isProtective)
    }

    @Test
    fun `coming back to the garden brings every plant up to date with the days that passed`() = runBlocking {
        repo.put(tulip("a", growth = 0.1f))
        repo.put(tulip("b", growth = 1f, x = 1))
        val forecast = days(sunny, rainy, sunny)

        sync(t0 + 2 * day + 6 * hour, forecast)

        for (id in listOf("a", "b")) {
            val before = if (id == "a") tulip("a", growth = 0.1f) else tulip("b", growth = 1f, x = 1)
            assertEquals(calc(before, t0 + 2 * day + 6 * hour, forecast), repo.objects.getValue(id))
        }
        // "a" was watered by the rain on the second day and is alive; "b" has been in bloom all along.
        assertFalse(repo.objects.getValue("a").status == PlantStatus.WITHERED)
        assertTrue(repo.objects.getValue("b").stressRate > 0f)
    }
}

/** A device whose clock and save data are as they should be. */
private object AlwaysTrusted : com.studioguu.miniaturegarden.domain.repository.SecurityRepository {
    override fun isSignatureValid(profile: UserProfile): Boolean = true
    override fun isTimeTampered(profile: UserProfile): Boolean = false
    override fun isTamperFlagged(): Boolean = false
    override fun setTamperFlag(flagged: Boolean) = Unit
}
