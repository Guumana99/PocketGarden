package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.PlantSurroundings
import com.studioguu.miniaturegarden.domain.model.Sprinkler
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sprinkler is a thing in the garden that can be switched off; and nothing counts stamina. */
class SprinklerTest {

    private val t0 = 1_700_000_000_000L
    private val hour = 3_600_000L
    private val sunny = WeatherType.SUNNY

    private val repo = FakeGardenRepository().apply {
        profile = UserProfile(coins = 100)
        progress = GardenProgress(tutorialStep = TutorialStep.DONE)
    }
    private val toggle = ToggleSprinklerUseCase(repo, CalculateOfflineProgressUseCase())

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    private fun obj(id: String, x: Int, y: Int, objectId: String) =
        GridObject(id, x, y, objectId = objectId, lastUpdatedTimestamp = t0)

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
