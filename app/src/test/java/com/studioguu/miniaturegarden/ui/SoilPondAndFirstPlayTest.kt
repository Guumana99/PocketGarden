package com.studioguu.miniaturegarden.ui

import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.WeatherForecast
import com.studioguu.miniaturegarden.domain.model.WeatherDiary
import com.studioguu.miniaturegarden.domain.model.SeasonClock
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.render.PondJoin
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #61: the broken ground between the wasteland and a bed (soil_revived.png), the open water in
 * the middle of a pool (pond_center.png), and a new game that opens on a sunny spring day.
 */
class SoilPondAndFirstPlayTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    private fun source(path: String) = File(main, "java/com/studioguu/miniaturegarden/$path").readText()

    // --- First play: sunny spring ---

    @Test
    fun `a new game starts in spring, and is sunny all through its first play`() {
        assertEquals(Season.SPRING, GardenProgress.NEW_GARDEN.season)
        assertEquals(TutorialStep.OPEN_CRATE, GardenProgress.NEW_GARDEN.tutorialStep)
        // Whatever the day's own weather, every moment of the first play is sunny.
        val day = SeasonClock.DAY_MILLIS
        val rainyDay = 20_000L * day
        for (step in TutorialStep.entries.filter { it <= TutorialStep.FINISHED }) {
            assertTrue(step.name, step.isFirstPlay)
            val forecast = WeatherForecast.forGarden(step, WeatherType.RAINY)
            assertEquals(step.name, WeatherType.SUNNY, forecast.at(rainyDay))
            assertEquals(step.name, WeatherType.SUNNY, forecast.at(rainyDay + 5 * day))
        }
    }

    @Test
    fun `after the first play the weather goes by the day again`() {
        val diary = WeatherDiary(mapOf(1L to WeatherType.RAINY))
        for (step in listOf(TutorialStep.REVEAL_EDIT, TutorialStep.REVEAL_POST, TutorialStep.DONE)) {
            assertFalse(step.name, step.isFirstPlay)
            assertSame(diary, WeatherForecast.forGarden(step, diary))
        }
        // Not read yet: no guess.
        assertSame(diary, WeatherForecast.forGarden(null, diary))
    }

    @Test
    fun `the garden screen runs on the first play's weather`() {
        val viewModel = source("ui/GardenViewModel.kt")
        assertTrue(viewModel.contains("WeatherForecast.forGarden(progress.value?.tutorialStep, diary)"))
        assertFalse(viewModel.contains("debugWeather ?: diary"))
        assertTrue(viewModel.contains("watchFirstPlayWeather()"))
    }

    // --- Broken ground ---

    @Test
    fun `ground the hoe has struck shows the next ground in patches until it is that ground`() {
        val assets = source("data/asset/AssetRepository.kt")
        assertTrue(assets.contains("val broken = async(Dispatchers.Default) { bedFace(R.drawable.soil_revived, GroundSlab.REVIVED) }"))
        assertFalse(assets.contains("composted ?: tilled"))
        val viewModel = source("ui/GardenViewModel.kt")
        assertTrue(viewModel.contains("val brokenGround: StateFlow<Set<GridCell>> = hoeStrikes.map { it.keys }"))
        assertTrue(viewModel.contains("if (it == ActionResult.HOE_STRUCK) all + (cell to strikes + 1) else all - cell"))
        assertTrue(source("ui/GardenScreen.kt").contains("brokenCells = brokenGround,"))
        val canvas = source("ui/components/GardenCanvas.kt")
        assertTrue(canvas.contains("drawHalfTurnedGround(transform, cell.x, cell.y, if (floor == null) terrain.broken else terrain.tilled)"))
        // Not over ridges or anything else that has been made there since.
        assertTrue(canvas.contains("(floor != null && !floor.isLooseSoil)) continue"))
        // Tilled earth is the flat picture, ridges the furrowed one; better soil is deeper in colour.
        assertTrue(canvas.contains("face = if (obj.isLooseSoil) terrain.broken else terrain.tilled"))
        assertTrue(canvas.contains("soilTint(quality)?.let { drawPath(bed, it, blendMode = BlendMode.Multiply) }"))
    }

    // --- The middle of a pool ---

    private fun ponds(vararg xy: Pair<Int, Int>): (Int, Int) -> Boolean {
        val cells = xy.map { GridCell(it.first, it.second) }.toSet()
        return { x, y -> GridCell(x, y) in cells }
    }

    @Test
    fun `only a pond with ponds on all four sides is the middle of a pool`() {
        val square = ponds(*(0..2).flatMap { x -> (0..2).map { y -> x to y } }.toTypedArray())
        assertTrue(PondJoin.isCentre(1, 1, square))
        for ((x, y) in listOf(0 to 0, 1 to 0, 2 to 1, 0 to 2)) assertFalse("$x,$y", PondJoin.isCentre(x, y, square))
        val plus = ponds(1 to 1, 1 to 0, 2 to 1, 1 to 2, 0 to 1)
        assertTrue(PondJoin.isCentre(1, 1, plus))
        val twoByTwo = ponds(0 to 0, 1 to 0, 0 to 1, 1 to 1)
        for ((x, y) in listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)) assertFalse(PondJoin.isCentre(x, y, twoByTwo))
        assertEquals(PondJoin.ALL, PondJoin.NORTH or PondJoin.EAST or PondJoin.SOUTH or PondJoin.WEST)
    }

    @Test
    fun `the middle of a pool is pond_center's open water`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        assertTrue(canvas.contains("PondJoin.isCentre(obj.gridX, obj.gridY, isPondHere)"))
        assertTrue(canvas.contains("fillWithTexture(water, transform, transform.tilePath(obj.gridX, obj.gridY), POND_WATER_CELLS)"))
        assertTrue(source("data/asset/AssetRepository.kt").contains("decodeUnscaled(R.drawable.pond_center)"))
    }

    // --- The shop's list in the docs ---

    @Test
    fun `every item in the shop is in the docs' list, with whether its picture is final`() {
        val docs = File(main, "../../../docs/ASSETS.md").readText()
        val list = docs.substringAfter("### ショップの取扱一覧").substringBefore("\n### ")
        val rows = list.lines().filter { it.startsWith("| 種 |") || it.startsWith("| 建物 |") || it.startsWith("| 飾り |") }
        assertEquals(GardenItem.entries.size, rows.size)
        assertTrue(list.contains("全 ${GardenItem.entries.size} 品"))
        for (item in GardenItem.entries) {
            val row = rows.singleOrNull { it.contains("（`${item.itemId}`）") }
            assertTrue(item.itemId, row != null)
            assertTrue(item.itemId, row!!.contains("| ${item.price} |"))
            assertTrue(item.itemId, row.contains("アセット") || row.contains("コード描画（仮置き）"))
        }
    }
}
