package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.FarmTool
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.HoeSkill
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantSurroundings
import com.studioguu.miniaturegarden.domain.model.SoilHardness
import com.studioguu.miniaturegarden.domain.model.SoilQuality
import com.studioguu.miniaturegarden.domain.model.Sprinkler
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WateringCanSkill
import com.studioguu.miniaturegarden.domain.model.WeatherType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tools get better with use (no stamina), the ground is not all alike, a better hoe makes
 * richer beds, a better can pours more, and the sprinkler is a thing in the garden that can be
 * switched off.
 */
class FarmToolsTest {

    private val t0 = 1_700_000_000_000L
    private val hour = 3_600_000L
    private val sunny = WeatherType.SUNNY

    private val repo = FakeGardenRepository().apply {
        profile = UserProfile(coins = 100)
        progress = GardenProgress(tutorialStep = TutorialStep.DONE)
    }
    private val calc = CalculateOfflineProgressUseCase()
    private val hoe = HoeUseCase(repo, GardenMap.DEFAULT)
    private val care = CareForPlantUseCase(repo, calc)
    private val harvest = HarvestPlantUseCase(repo, calc, RecordCollectionUseCase(repo))
    private val toggle = ToggleSprinklerUseCase(repo, calc)

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    private fun obj(id: String, x: Int, y: Int, objectId: String, quality: Int = 0) =
        GridObject(id, x, y, objectId = objectId, lastUpdatedTimestamp = t0, soilQuality = quality)

    private fun cellOfHardness(hardness: Int): Pair<Int, Int> =
        (0 until 9).flatMap { x -> (0 until 9).map { y -> x to y } }.first { (x, y) -> SoilHardness.at(x, y) == hardness }

    // --- No stamina ---

    @Test
    fun `nothing in the game counts stamina`() {
        // In code, that is: the comments may say there is none.
        val sources = File(main, "java").walkTopDown().filter { it.extension == "kt" }
        for (file in sources) {
            val code = file.readLines().map { it.trim() }.filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            assertFalse(file.name, code.any { it.contains("stamina", ignoreCase = true) })
        }
        assertFalse(File(main, "res/values/strings.xml").readText().contains("スタミナ"))
    }

    // --- Levels ---

    @Test
    fun `a tool's level comes from its uses`() {
        assertEquals(1, FarmTool.HOE.levelFor(0))
        assertEquals(1, FarmTool.HOE.levelFor(14))
        assertEquals(2, FarmTool.HOE.levelFor(15))
        assertEquals(3, FarmTool.HOE.levelFor(50))
        assertEquals(5, FarmTool.HOE.levelFor(180))
        assertEquals(5, FarmTool.HOE.levelFor(10_000))
        assertEquals(15, FarmTool.HOE.usesToNextLevel(0))
        assertEquals(null, FarmTool.HOE.usesToNextLevel(180))
        assertEquals(1, FarmTool.WATERING_CAN.levelFor(24))
        assertEquals(2, FarmTool.WATERING_CAN.levelFor(25))
        assertEquals(FarmTool.WATERING_CAN.maxLevel, FarmTool.WATERING_CAN.levelFor(300))
        assertEquals(2, GardenProgress(hoeXp = 15).hoeLevel)
        assertEquals(1, GardenProgress().wateringCanLevel)
    }

    // --- Hard ground and the hoe ---

    @Test
    fun `the ground's hardness is fixed by the place, and mixed`() {
        val all = (0 until 13).flatMap { x -> (0 until 13).map { y -> SoilHardness.at(x, y) } }
        assertTrue(all.all { it in 1..SoilHardness.MAX })
        assertEquals(setOf(1, 2, 3), all.toSet())
        assertEquals(SoilHardness.at(4, 7), SoilHardness.at(4, 7))
    }

    @Test
    fun `a better hoe needs fewer strikes - three come down to one`() {
        assertEquals(3, HoeSkill.strikesNeeded(hardness = 3, level = 1))
        assertEquals(2, HoeSkill.strikesNeeded(hardness = 3, level = 2))
        assertEquals(1, HoeSkill.strikesNeeded(hardness = 3, level = 3))
        assertEquals(1, HoeSkill.strikesNeeded(hardness = 1, level = 1))
        for (level in 1..5) for (hardness in 1..3) assertTrue(HoeSkill.strikesNeeded(hardness, level) >= 1)
    }

    @Test
    fun `hard ground takes its strikes, and only the last one makes the bed`() = runBlocking {
        val (x, y) = cellOfHardness(3)
        assertEquals(ActionResult.HOE_STRUCK, hoe(x, y, t0, strikes = 0))
        assertEquals(ActionResult.HOE_STRUCK, hoe(x, y, t0, strikes = 1))
        assertTrue(repo.objects.isEmpty())
        assertEquals(0, repo.progress?.hoeXp)
        assertEquals(ActionResult.SUCCESS, hoe(x, y, t0, strikes = 2))
        assertEquals(GridObject.TILLED_SOIL_OBJECT_ID, repo.objects.values.single().objectId)
        assertEquals(1, repo.progress?.hoeXp)
        // Soft ground goes at one strike.
        val (sx, sy) = cellOfHardness(1)
        assertEquals(ActionResult.SUCCESS, hoe(sx, sy, t0))
    }

    @Test
    fun `the first steps' one cell goes at one strike, wherever it is`() = runBlocking {
        repo.progress = GardenProgress(tutorialStep = TutorialStep.TILL_SOIL)
        val (x, y) = cellOfHardness(3)
        assertEquals(ActionResult.SUCCESS, hoe(x, y, t0))
        assertEquals(TutorialStep.PLANT_SEED, repo.progress?.tutorialStep)
    }

    @Test
    fun `a better hoe makes richer beds, whose crops fetch more`() = runBlocking {
        repo.progress = GardenProgress(hoeXp = 40) // level 3
        val (x, y) = cellOfHardness(3)
        assertEquals(ActionResult.SUCCESS, hoe(x, y, t0)) // one strike at level 3
        val bed = repo.objects.values.single()
        assertEquals(2, bed.soilQuality)
        assertEquals(0, HoeSkill.soilQualityFor(1))
        assertEquals(SoilQuality.MAX, HoeSkill.soilQualityFor(99))

        // A tulip grown in it pays 10% more.
        repo.put(GridObject("t", x, y, objectId = "plant_tulip", growthProgress = 1f, lastUpdatedTimestamp = t0))
        assertEquals(ActionResult.SUCCESS, harvest("t", t0, sunny))
        assertEquals(100L + SoilQuality.harvestCoins(PlantSpecies.TULIP.harvestCoins, 2), repo.profile?.coins)
        assertEquals(55, SoilQuality.harvestCoins(50, 2))
        assertEquals(50, SoilQuality.harvestCoins(50, 0))
        assertEquals(20, SoilQuality.bonusPercent(SoilQuality.MAX))
    }

    // --- The watering can ---

    @Test
    fun `a better can pours more, so fewer pours are needed`() = runBlocking {
        assertEquals(0.35f, WateringCanSkill.waterAmount(1), 1e-6f)
        assertEquals(0.75f, WateringCanSkill.waterAmount(5), 1e-6f)
        assertEquals(0.35f, CareForPlantUseCase.wateredMoisture(0f), 1e-6f)
        assertEquals(0.75f, CareForPlantUseCase.wateredMoisture(0f, canLevel = 5), 1e-6f)
        // From bone dry to past the watering line: three pours from a new can, one from the best.
        fun poursFromDry(level: Int): Int {
            var moisture = 0f
            var pours = 0
            while (moisture < CareForPlantUseCase.WATERING_THRESHOLD) {
                moisture = CareForPlantUseCase.wateredMoisture(moisture, level)
                pours++
            }
            return pours
        }
        assertEquals(3, poursFromDry(1))
        assertEquals(2, poursFromDry(3))
        assertEquals(2, poursFromDry(5))

        // Each pour counts for the can, and its level sets the pour.
        repo.progress = GardenProgress(wateringCanXp = 70) // level 3
        repo.put(GridObject("p", 0, 0, objectId = "plant_tulip", moistureLevel = 0.1f, lastUpdatedTimestamp = t0))
        assertEquals(ActionResult.SUCCESS, care.water("p", t0, sunny))
        assertEquals(0.1f + WateringCanSkill.waterAmount(3), repo.objects.getValue("p").moistureLevel, 1e-4f)
        assertEquals(71, repo.progress?.wateringCanXp)
    }

    // --- The sprinkler ---

    @Test
    fun `the sprinkler is a thing to put down, not a tool on the bar`() {
        val item = GardenItem.entries.single { it.objectId == Sprinkler.OBJECT_ID }
        assertEquals(ItemCategory.DECORATION, item.category)
        assertEquals(0, item.buildSeconds)
        assertTrue(CollectionEntry.entries.any { it.objectId == Sprinkler.OBJECT_ID })
        val bar = File(main, "java/com/studioguu/miniaturegarden/ui/hud/QuickActionBar.kt").readText()
        assertFalse(bar.contains("sprinkler", ignoreCase = true))
    }

    @Test
    fun `a running sprinkler keeps the beds round it moist, a stopped one does nothing`() {
        val plant = GridObject("p", 4, 4, objectId = "plant_tulip", lastUpdatedTimestamp = t0)
        val running = obj("s", 5, 5, Sprinkler.OBJECT_ID)
        assertEquals(Sprinkler.MOISTURE_DRAIN_FACTOR, PlantSurroundings.of(plant, listOf(plant, running)).moistureDrainFactor, 0f)
        assertEquals(1f, PlantSurroundings.of(plant, listOf(plant, running.copy(isOn = false))).moistureDrainFactor, 0f)
        // Out of its reach, nothing.
        val far = obj("f", 7, 7, Sprinkler.OBJECT_ID)
        assertEquals(1f, PlantSurroundings.of(plant, listOf(plant, far)).moistureDrainFactor, 0f)
        assertEquals(8, Sprinkler.cellsInReach(5, 5).size)
    }

    @Test
    fun `switching the sprinkler counts the time before under the old setting`() = runBlocking {
        repo.put(obj("s", 5, 5, Sprinkler.OBJECT_ID))
        repo.put(GridObject("p", 4, 4, objectId = "plant_tulip", lastUpdatedTimestamp = t0))
        assertEquals(ActionResult.SUCCESS, toggle("s", t0 + 12 * hour, sunny))
        assertFalse(repo.objects.getValue("s").isOn)
        // Twelve hours under a running sprinkler: a tulip dries over a day, four times slower here.
        val plant = repo.objects.getValue("p")
        assertEquals(t0 + 12 * hour, plant.lastUpdatedTimestamp)
        assertEquals(1f - 12f / 24f * Sprinkler.MOISTURE_DRAIN_FACTOR, plant.moistureLevel, 0.02f)
        assertEquals(ActionResult.SUCCESS, toggle("s", t0 + 13 * hour, sunny))
        assertTrue(repo.objects.getValue("s").isOn)
        // Only the sprinkler is switched.
        assertEquals(ActionResult.NOT_SWITCHABLE, toggle("p", t0, sunny))
        assertEquals(ActionResult.NOT_FOUND, toggle("nope", t0, sunny))
    }

    @Test
    fun `the sprinkler's running and stopped states are drawn and offered`() {
        val panel = File(main, "java/com/studioguu/miniaturegarden/ui/hud/ObjectStatusDialog.kt").readText()
        assertTrue(panel.contains("onClick = onToggleSprinkler"))
        val canvas = File(main, "java/com/studioguu/miniaturegarden/ui/components/GardenCanvas.kt").readText()
        assertTrue(canvas.contains("if (obj.isSprinkler && obj.isOn && !obj.isWorking)"))
        assertTrue(canvas.contains("SprinklerSpray.frameAt("))
    }
}
