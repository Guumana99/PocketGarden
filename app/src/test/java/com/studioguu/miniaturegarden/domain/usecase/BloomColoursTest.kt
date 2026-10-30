package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.BloomColours
import com.studioguu.miniaturegarden.domain.model.BloomRarity
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.SoilGrade
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.repository.SecurityRepository
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A tulip's colour is decided as it opens, from the soil it stands in then. */
class BloomColoursTest {
    private val t0 = 1_700_000_000_000L

    private class Trusted : SecurityRepository {
        override fun isSignatureValid(profile: UserProfile) = true
        override fun isTamperFlagged() = false
        override fun setTamperFlag(flagged: Boolean) = Unit
    }

    private fun colours(grade: SoilGrade, level: Int, season: Season): List<String> {
        val random = Random(7)
        return List(20_000) { BloomColours.pick(PlantSpecies.TULIP, grade, level, season, random)!! }
    }

    private fun share(of: List<String>, rarity: BloomRarity) =
        of.count { BloomColours.rarityOf(PlantSpecies.TULIP, it) == rarity } / of.size.toFloat()

    @Test
    fun `plain soil only has the common three`() {
        assertEquals(setOf("red", "pink", "yellow"), colours(SoilGrade.PLAIN, 30, Season.SPRING).toSet())
    }

    @Test
    fun `good soil adds the rare two, at 15 percent between them`() {
        val good = colours(SoilGrade.GOOD, 30, Season.SPRING)
        assertEquals(setOf("red", "pink", "yellow", "white", "purple"), good.toSet())
        assertEquals(0.15f, share(good, BloomRarity.RARE), 0.01f)
    }

    @Test
    fun `blue takes fluffy soil, Lv 10 and spring, and then comes 3 percent of the time`() {
        val blue = colours(SoilGrade.FLUFFY, 10, Season.SPRING)
        assertEquals(0.03f, share(blue, BloomRarity.VERY_RARE), 0.006f)
        assertTrue("blue" !in colours(SoilGrade.FLUFFY, 9, Season.SPRING))
        assertTrue("blue" !in colours(SoilGrade.FLUFFY, 10, Season.SUMMER))
        assertTrue("blue" !in colours(SoilGrade.GOOD, 10, Season.SPRING))
    }

    @Test
    fun `a crop of one colour has none to decide`() {
        assertNull(BloomColours.pick(PlantSpecies.SUNFLOWER, SoilGrade.FLUFFY, 30, Season.SPRING, Random(1)))
    }

    @Test
    fun `the colour comes at the sync that finds it open, and stays when the soil changes`() = runBlocking {
        val repo = FakeGardenRepository().apply {
            profile = UserProfile(coins = 100)
            progress = GardenProgress(tutorialStep = TutorialStep.DONE)
        }
        val sync = SyncGardenUseCase(repo, VerifyIntegrityUseCase(repo, Trusted()), CalculateOfflineProgressUseCase())
        repo.put(GridObject("soil", 0, 0, objectId = GridObject.TILLED_SOIL_OBJECT_ID, lastUpdatedTimestamp = t0))
        repo.put(GridObject("t", 0, 0, objectId = PlantSpecies.TULIP.objectId, growthProgress = 0.5f, lastUpdatedTimestamp = t0))

        sync(t0, WeatherType.SUNNY)
        assertNull(repo.objects.getValue("t").bloomColour)

        repo.put(repo.objects.getValue("t").copy(growthProgress = 1f))
        sync(t0, WeatherType.SUNNY)
        val colour = repo.objects.getValue("t").bloomColour
        assertNotNull(colour)
        assertTrue(colour in setOf("red", "pink", "yellow"))

        repo.put(repo.objects.getValue("soil").copy(soilQuality = 2))
        sync(t0, WeatherType.SUNNY)
        assertEquals(colour, repo.objects.getValue("t").bloomColour)
    }

    @Test
    fun `a tulip that was in bloom before keeps the colour its id gave it`() {
        val old = GridObject("old", 0, 0, objectId = PlantSpecies.TULIP.objectId, growthProgress = 1f, lastUpdatedTimestamp = t0, bloomColour = BloomColours.BY_ID)
        assertEquals(PlantSpecies.TULIP.bloomColourOf("old"), BloomColours.colourOf(old))
        assertEquals(old, BloomColours.settled(old, listOf(old), 30, Season.SPRING, Random(1)))
    }

    @Test
    fun `the harvest writes down the colour it opened in`() = runBlocking {
        val repo = FakeGardenRepository().apply {
            profile = UserProfile(coins = 100)
            progress = GardenProgress(tutorialStep = TutorialStep.DONE)
        }
        repo.put(GridObject("t", 0, 0, objectId = PlantSpecies.TULIP.objectId, growthProgress = 1f, lastUpdatedTimestamp = t0, bloomColour = "purple"))
        HarvestPlantUseCase(repo, CalculateOfflineProgressUseCase(), RecordCollectionUseCase(repo))("t", t0, WeatherType.SUNNY)
        assertNotNull(repo.collection[CollectionEntry.colourRecordId("plant_tulip", "purple")])
    }
}
