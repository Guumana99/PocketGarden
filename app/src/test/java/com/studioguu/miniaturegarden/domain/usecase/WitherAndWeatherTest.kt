package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherForecast
import com.studioguu.miniaturegarden.domain.model.WeatherDiary
import com.studioguu.miniaturegarden.domain.model.SeasonClock
import com.studioguu.miniaturegarden.domain.model.GameDay
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.usecase.CalculateOfflineProgressUseCase.Companion.OVERRIPE_HOURS
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WitherAndWeatherTest {

    private val hour = 3_600_000L
    private val day = SeasonClock.DAY_MILLIS

    /** Midnight UTC, the start of the days that [days] changes the weather on. */
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
    private val sync = SyncGardenUseCase(repo, VerifyIntegrityUseCase(repo, AlwaysTrusted), calc, RecordCollectionUseCase(repo))

    private fun flower(
        id: String = "p",
        growth: Float = 0f,
        moisture: Float = 1f,
        nutrition: Float = 1f,
        stress: Float = 0f,
        status: PlantStatus = PlantStatus.HEALTHY,
        x: Int = 0,
        at: Long = t0,
    ) = GridObject(
        instanceId = id, gridX = x, gridY = 0, objectId = PlantSpecies.SUNFLOWER.objectId,
        growthProgress = growth, moistureLevel = moisture, nutritionLevel = nutrition,
        stressRate = stress, status = status, lastUpdatedTimestamp = at,
    )

    /** A forecast that reads its weather off a list, one entry per day from [t0] on. */
    private fun days(vararg weather: WeatherType) = object : WeatherForecast {
        override fun at(epochMillis: Long) = weather[Math.floorDiv(epochMillis - t0, day).toInt().coerceIn(0, weather.lastIndex)]
        override fun nextChangeAfter(epochMillis: Long) = t0 + (Math.floorDiv(epochMillis - t0, day) + 1) * day
    }

    // --- Going over after full bloom ---

    @Test
    fun `a plant in full bloom is not gone or withered straight away`() {
        val bloom = flower(growth = 1f)

        val later = calc(bloom, t0 + 6 * hour, sunny)

        assertEquals(1f, later.growthProgress, 0f)
        assertEquals(PlantStatus.HEALTHY, later.status)
        assertEquals(6f / OVERRIPE_HOURS, later.stressRate, 1e-4f)
    }

    @Test
    fun `left standing in bloom for long enough it withers, and it is still there`() {
        // Kept watered and fed, so that nothing but the waiting is at work.
        var plant = flower(growth = 1f)
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
        // 0.5 grown: 10 healthy hours short of bloom for a sunflower, before its soil goes dry.
        val halfway = flower(growth = 0.5f)
        val atBloom = calc(halfway, t0 + 10 * hour, sunny)
        assertEquals(1f, atBloom.growthProgress, 1e-4f)
        assertEquals(0f, atBloom.stressRate, 1e-3f)

        val twoHoursOn = calc(atBloom.copy(moistureLevel = 1f), t0 + 12 * hour, sunny)
        assertEquals(2f / OVERRIPE_HOURS, twoHoursOn.stressRate, 1e-3f)
    }

    @Test
    fun `a growing plant still shakes off old hardship, one in bloom does not`() {
        val growing = calc(flower(growth = 0.1f, stress = 0.5f), t0 + 6 * hour, sunny)
        val inBloom = calc(flower(growth = 1f, stress = 0.5f), t0 + 6 * hour, sunny)

        assertTrue(growing.stressRate < 0.5f)
        assertTrue(inBloom.stressRate > 0.5f)
    }

    @Test
    fun `a harvest in time is unaffected, and too late it is refused`() = runBlocking {
        repo.put(flower("soon", growth = 1f))
        repo.put(flower("late", growth = 1f, x = 1))

        assertEquals(ActionResult.SUCCESS, harvest("soon", t0 + 10 * hour, sunny))
        assertEquals(100L + PlantSpecies.SUNFLOWER.harvestCoins, repo.profile?.coins)

        val tooLate = t0 + (OVERRIPE_HOURS + 1) * hour
        assertEquals(ActionResult.WITHERED, harvest("late", tooLate, sunny))
        assertNotNull(repo.objects["late"]) // still standing
        assertEquals(100L + PlantSpecies.SUNFLOWER.harvestCoins, repo.profile?.coins)
    }

    @Test
    fun `a withered plant stays as it is however much time passes`() {
        val withered = flower(growth = 0.6f, moisture = 0f, stress = 1f, status = PlantStatus.WITHERED)

        val muchLater = calc(withered, t0 + 6 * day, days(sunny, rainy, sunny, rainy, sunny, sunny, sunny))

        assertEquals(withered.copy(lastUpdatedTimestamp = muchLater.lastUpdatedTimestamp), muchLater)
        assertEquals(PlantStatus.WITHERED, muchLater.status)
    }

    // --- Reviving ---

    @Test
    fun `reviving costs coins and brings the plant back to health at the growth it had`() = runBlocking {
        repo.put(flower(growth = 0.6f, moisture = 0f, nutrition = 0.05f, stress = 1f, status = PlantStatus.WITHERED))

        assertEquals(ActionResult.SUCCESS, care.revive("p", t0 + hour, sunny))

        val revived = repo.objects.getValue("p")
        assertEquals(PlantStatus.HEALTHY, revived.status)
        assertEquals(0f, revived.stressRate, 0f)
        assertEquals(1f, revived.moistureLevel, 0f)
        // The food is the soil's: reviving a plant does not fill it.
        assertEquals(0.05f, revived.nutritionLevel, 0f)
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
        repo.put(flower(growth = 1f, stress = 1f, status = PlantStatus.WITHERED))

        assertEquals(ActionResult.WITHERED, harvest("p", t0, sunny))
        assertEquals(ActionResult.SUCCESS, care.revive("p", t0, sunny))
        assertEquals(ActionResult.SUCCESS, harvest("p", t0 + hour, sunny))

        assertEquals(100L - CareForPlantUseCase.REVIVE_COST + PlantSpecies.SUNFLOWER.harvestCoins, repo.profile?.coins)
    }

    @Test
    fun `reviving is refused without the coins, and for plants that are not withered`() = runBlocking {
        repo.put(flower("dead", stress = 1f, status = PlantStatus.WITHERED))
        repo.put(flower("fine", x = 1))
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
        repo.put(flower(growth = 0.3f))

        assertEquals(ActionResult.SUCCESS, care.revive("p", t0 + 5 * day, sunny))

        assertEquals(PlantStatus.HEALTHY, repo.objects.getValue("p").status)
    }

    @Test
    fun `watering and fertilizing still do nothing for a withered plant`() = runBlocking {
        repo.put(flower(moisture = 0f, stress = 1f, status = PlantStatus.WITHERED))

        assertEquals(ActionResult.WITHERED, care.water("p", t0, sunny))
        assertEquals(100L, repo.profile?.coins)
    }

    // --- Clearing a withered plant away: compost ---

    @Test
    fun `a withered plant that is cleared away becomes compost in its bed`() = runBlocking {
        repo.till(0, 0)
        repo.put(repo.objects.getValue("soil_0_0").copy(nutritionLevel = 0f))
        repo.put(flower(stress = 1f, status = PlantStatus.WITHERED))

        assertEquals(ActionResult.SUCCESS, remove("p"))

        assertTrue(repo.plants.isEmpty())
        assertEquals(1f / 3f, repo.objects.getValue("soil_0_0").nutritionLevel, 1e-4f)
        assertEquals(100L, repo.profile?.coins) // free, and nothing refunded
    }

    @Test
    fun `only withered crops make compost`() = runBlocking {
        repo.till(0, 0)
        repo.till(1, 0)
        repo.put(repo.objects.getValue("soil_0_0").copy(nutritionLevel = 0f))
        repo.put(repo.objects.getValue("soil_1_0").copy(nutritionLevel = 0f))
        repo.put(flower("alive", growth = 0.4f))
        repo.put(GridObject(instanceId = "rock", gridX = 1, gridY = 0, objectId = "deco_rock", lastUpdatedTimestamp = t0))

        remove("alive")
        remove("rock")

        assertEquals(0f, repo.objects.getValue("soil_0_0").nutritionLevel, 0f)
        assertEquals(0f, repo.objects.getValue("soil_1_0").nutritionLevel, 0f)
        assertTrue(RemoveObjectUseCase.leavesCompost(flower(stress = 1f, status = PlantStatus.WITHERED)))
        assertFalse(RemoveObjectUseCase.leavesCompost(flower()))
    }

    @Test
    fun `a withered plant with no bed under it is simply cleared away`() = runBlocking {
        repo.put(flower(stress = 1f, status = PlantStatus.WITHERED))

        assertEquals(ActionResult.SUCCESS, remove("p"))

        assertTrue(repo.objects.isEmpty())
    }

    // --- Rain ---

    @Test
    fun `rain waters a dry plant`() {
        val dry = flower(moisture = 0.05f, status = PlantStatus.THIRSTY)

        val afterRain = calc(dry, t0 + 2 * hour, rainy)

        assertEquals(1f, afterRain.moistureLevel, 0f)
        assertEquals(PlantStatus.HEALTHY, afterRain.status)
        // Watered at once, so it has been growing for the two hours as well.
        assertTrue(afterRain.growthProgress > 0f)
    }

    @Test
    fun `nothing moves closer to withering while it rains`() {
        val thirsty = flower("a", moisture = 0f, stress = 0.9f, status = PlantStatus.THIRSTY)
        val hungry = flower("b", nutrition = 0.05f, stress = 0.9f, status = PlantStatus.UNNOURISHED)
        val inBloom = flower("c", growth = 1f, stress = 0.9f)

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
        val wet = calc(flower(), t0 + 10 * hour, rainy).growthProgress
        val dry = calc(flower(), t0 + 10 * hour, sunny).growthProgress

        assertTrue(wet >= dry)
        assertTrue(wet <= dry * 1.25f)
    }

    @Test
    fun `rain does not bring a withered plant back`() {
        val withered = flower(moisture = 0f, stress = 1f, status = PlantStatus.WITHERED)

        val afterRain = calc(withered, t0 + day, rainy)

        assertEquals(PlantStatus.WITHERED, afterRain.status)
        assertEquals(0f, afterRain.moistureLevel, 0f)
    }

    // --- Time away, through changing weather ---

    @Test
    fun `one kind of weather throughout gives the same result either way`() {
        val start = flower(growth = 0.2f, moisture = 0.7f)
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
        val start = flower(growth = 0f)
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
        // A rose dries out in 36 hours and withers a day after it has gone thirsty.
        val start = flower(growth = 0.1f).copy(objectId = PlantSpecies.ROSE.objectId)

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
        val start = flower(growth = 0.1f)

        val result = calc(start, t0 + 4 * day, days(sunny, sunny, sunny, rainy))

        assertEquals(PlantStatus.WITHERED, result.status)
    }

    @Test
    fun `the part of a minute left at a change of weather is not lost`() {
        // Starts 30 s before midnight and ends 30 s after: one whole minute, across the change.
        val start = flower(at = t0 + day - 30_000)

        val result = calc(start, t0 + day + 30_000, days(sunny, rainy))

        assertEquals(t0 + day + 30_000, result.lastUpdatedTimestamp)
        assertTrue(result.growthProgress > 0f)
    }

    @Test
    fun `many short visits add up to the same as one long absence`() {
        val start = flower(growth = 0.05f)
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
        val start = flower(growth = 1f, at = t0)
        // Day 0..2 would be sunny; they fall outside the window. The seven days that count are rainy.
        val forecast = days(sunny, sunny, sunny, rainy, rainy, rainy, rainy, rainy, rainy, rainy)

        val result = calc(start, t0 + 10 * day, forecast)

        assertEquals(t0 + 10 * day, result.lastUpdatedTimestamp)
        // Seven days of rain: watered and no closer to withering.
        assertEquals(0f, result.stressRate, 0f)
        assertEquals(1f, result.moistureLevel, 0f)
        assertEquals(PlantStatus.HEALTHY, result.status)
        // Had the three sunny days counted as well, it would have gone over a little.
        assertTrue(calc(start, t0 + 10 * day, days(sunny)).stressRate > 0f)
    }

    @Test
    fun `nothing happens when no time has passed, whatever the forecast`() {
        val start = flower(growth = 0.4f, moisture = 0.1f)

        assertSame(start, calc(start, t0, days(sunny, rainy)))
        assertSame(start, calc(start, t0 + 20_000, days(rainy)))
    }

    @Test
    fun `a clock set back is repaired under a forecast too`() {
        val fromTheFuture = flower(at = t0 + 5 * hour)

        val result = calc(fromTheFuture, t0, days(sunny, rainy))

        assertEquals(t0, result.lastUpdatedTimestamp)
        assertEquals(0f, result.growthProgress, 0f)
    }

    @Test
    fun `the diary is the garden's own weather, changing at five in the morning`() {
        val utc = ZoneOffset.UTC
        val today = GameDay.of(t0 + 12 * hour, utc)
        val diary = WeatherDiary(mapOf(today to rainy, today + 1 to sunny), utc)
        assertEquals(rainy, diary.at(t0 + 5 * hour))
        assertEquals(rainy, diary.at(t0 + day + 4 * hour + 59 * 60_000L))
        assertEquals(sunny, diary.at(t0 + day + 5 * hour))
        assertEquals(t0 + day + 5 * hour, diary.nextChangeAfter(t0 + 12 * hour))
        // Before five it is still the day before, which is not in the diary: fine weather.
        assertEquals(sunny, diary.at(t0 + 4 * hour))
        // A kind of weather is a forecast of itself, and never changes.
        assertEquals(rainy, rainy.at(t0))
        assertEquals(Long.MAX_VALUE, rainy.nextChangeAfter(t0))
        assertTrue(rainy.isProtective)
        assertTrue(WeatherType.SNOWY.isProtective)
        assertFalse(sunny.isProtective)
        assertFalse(WeatherType.CLOUDY.isProtective)
    }

    @Test
    fun `snow keeps plants watered and unharmed, but brings them on no faster`() {
        val start = flower(growth = 0.1f, moisture = 0.3f)
        val snowed = calc(start, t0 + 6 * hour, WeatherType.SNOWY)
        val sunnyHours = calc(start.copy(moistureLevel = 1f), t0 + 6 * hour, sunny)
        val rained = calc(start, t0 + 6 * hour, rainy)

        assertEquals(1f, snowed.moistureLevel, 0f)
        assertEquals(0f, snowed.stressRate, 0f)
        // As fast as a healthy plant on a fine day; the rain is a fifth faster.
        assertEquals(sunnyHours.growthProgress, snowed.growthProgress, 1e-4f)
        assertEquals((rained.growthProgress - 0.1f) / 1.2f, snowed.growthProgress - 0.1f, 1e-4f)
    }

    @Test
    fun `coming back to the garden brings every plant up to date with the days that passed`() = runBlocking {
        repo.put(flower("a", growth = 0.1f))
        repo.put(flower("b", growth = 1f, x = 1))
        val forecast = days(sunny, rainy, sunny)

        sync(t0 + 2 * day + 6 * hour, forecast)

        for (id in listOf("a", "b")) {
            val before = if (id == "a") flower("a", growth = 0.1f) else flower("b", growth = 1f, x = 1)
            assertEquals(calc(before, t0 + 2 * day + 6 * hour, forecast), repo.objects.getValue(id))
        }
        // "a" was watered by the rain on the second day and is alive; "b" has been in bloom all along.
        assertFalse(repo.objects.getValue("a").status == PlantStatus.WITHERED)
        assertTrue(repo.objects.getValue("b").stressRate > 0f)
    }

    // --- The tulip never withers (#58) ---

    private fun tulip(growth: Float = 0f, stress: Float = 0f, status: PlantStatus = PlantStatus.HEALTHY) =
        flower(growth = growth, stress = stress, status = status).copy(objectId = PlantSpecies.TULIP.objectId)

    @Test
    fun `a tulip left dry, unfed or in bloom for a week does not wither`() {
        assertFalse(PlantSpecies.TULIP.canWither)
        assertTrue(PlantSpecies.entries.filter { it != PlantSpecies.TULIP }.all { it.canWither })
        for (start in listOf(tulip(), tulip(growth = 1f))) {
            val week = calc(start.copy(moistureLevel = 0f, nutritionLevel = 0f), t0 + 7 * day, sunny)
            assertFalse(week.status == PlantStatus.WITHERED)
            assertTrue(week.stressRate < 1f)
        }
    }

    @Test
    fun `a tulip saved withered comes back on the next update`() {
        val saved = tulip(growth = 0.6f, stress = 1f, status = PlantStatus.WITHERED)
        val later = calc(saved, t0 + hour, sunny)
        assertFalse(later.status == PlantStatus.WITHERED)
        assertTrue(later.stressRate < 1f)
    }

}

/** A device whose clock and save data are as they should be. */
private object AlwaysTrusted : com.studioguu.miniaturegarden.domain.repository.SecurityRepository {
    override fun isSignatureValid(profile: UserProfile): Boolean = true
    override fun isTamperFlagged(): Boolean = false
    override fun setTamperFlag(flagged: Boolean) = Unit
}
