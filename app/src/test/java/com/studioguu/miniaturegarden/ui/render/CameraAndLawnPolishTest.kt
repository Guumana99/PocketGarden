package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.studioguu.miniaturegarden.ui.debug.zoomText
import com.studioguu.miniaturegarden.ui.shop.ShopFades
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A new or grown garden opens at the starting zoom, the debug read-out shows the zoom, and the
 * lawn, the stream and the shop's changes of room look the way they should.
 */
class CameraAndLawnPolishTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private fun source(path: String) = File(main, "java/com/studioguu/miniaturegarden/$path").readText().replace("\r\n", "\n")

    private val phone = Size(411f, 640f)
    private fun metrics(gridSize: Int) = IsometricMetrics(tileWidth = 128f, tileHeight = 64f, heightStep = 16f, gridSize = gridSize)

    // --- The camera ---

    @Test
    fun `every size of garden starts at the same zoom on a given screen, centred`() {
        for (screen in listOf(phone, Size(1280f, 800f), Size(2000f, 2000f))) {
            val first = GardenCamera.starting(screen, metrics(9)).scale
            for (size in listOf(9, 11, 13)) {
                val start = GardenCamera.starting(screen, metrics(size))
                assertEquals("$size x $size on $screen", first, start.scale, 0f)
                assertEquals(Offset.Zero, start.pan)
            }
        }
        assertEquals(com.studioguu.miniaturegarden.domain.model.GardenExpansion.MAX_SIZE, GardenCamera.LARGEST_GRID)
        // On a phone, finger-sized tiles.
        assertEquals(GardenCamera.START_SCALE, GardenCamera.starting(phone, metrics(9)).scale, 0f)
    }

    @Test
    fun `a garden that grows goes back to the starting zoom, not to the zoom it had`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        val growing = canvas.substringAfter("if (fittedGridSize != metrics.gridSize").substringBefore("}")
        assertTrue(growing.contains("GardenCamera.starting(viewport, metrics)"))
        assertFalse(growing.contains("GardenCamera.fitting"))
        // Even after the player has zoomed right in, the grown garden opens where a new one would.
        val zoomedIn = GardenCamera.starting(phone, metrics(9)).transformedBy(phone.center(), Offset.Zero, 4f, phone, metrics(9))
        assertTrue(zoomedIn.scale > GardenCamera.START_SCALE)
        assertEquals(GardenCamera.START_SCALE, GardenCamera.starting(phone, metrics(11)).scale, 0f)
    }

    @Test
    fun `starting a new garden throws away the view kept from the last one`() {
        val model = source("ui/title/TitleViewModel.kt")
        val startOver = model.substringAfter("fun startOver(").substringBefore("\n    }\n")
        assertTrue(startOver.contains("deleteSavedGarden()"))
        assertTrue(startOver.contains("gardenView = null"))
        // "Start" with no garden saved goes the same way, so no zoom is carried over either.
        val title = source("ui/title/TitleScreen.kt")
        assertTrue(title.contains("else viewModel.startOver(onEnterGarden)"))
    }

    // --- The debug read-out ---

    @Test
    fun `the zoom reads as a multiplier with two places`() {
        assertEquals("×0.55", zoomText(GardenCamera.START_SCALE))
        assertEquals("×1.00", zoomText(1f))
        assertEquals("×2.50", zoomText(GardenCamera.MAX_SCALE))
    }

    @Test
    fun `the garden screen shows the zoom in debug builds only, as it changes`() {
        val screen = source("ui/GardenScreen.kt")
        assertTrue(screen.contains("onZoomChanged = if (BuildConfig.DEBUG) ({ debugZoom = it }) else null"))
        assertTrue(screen.contains("DebugZoomReadout(debugZoom)"))
        assertTrue(screen.contains("zoomScale = debugZoom"))
        val canvas = source("ui/components/GardenCanvas.kt")
        assertTrue(canvas.contains("LaunchedEffect(camera?.scale)"))
    }

    // --- The lawn ---

    @Test
    fun `a feathered cell of grass is whole over itself and fades out smoothly beyond it`() {
        assertEquals(1f, LawnShade.featherAlpha(0f), 0f)
        assertEquals(1f, LawnShade.featherAlpha(LawnShade.FEATHER_START), 0f)
        assertEquals(0f, LawnShade.featherAlpha(1f), 0f)
        assertEquals(0f, LawnShade.featherAlpha(1.4f), 0f)
        // Falling all the way, never rising again.
        var last = 1f
        for (i in 0..100) {
            val alpha = LawnShade.featherAlpha(LawnShade.FEATHER_START + (1f - LawnShade.FEATHER_START) * i / 100f)
            assertTrue(alpha <= last + 1e-6f)
            last = alpha
        }
        // Drawn larger, the solid middle covers nearly all of the cell, and the fade reaches well
        // over the neighbours: no hard line where two cells meet.
        val solidReach = LawnShade.FEATHER_START * LawnShade.FEATHER_OVERLAP
        assertTrue("solid to $solidReach", solidReach in 0.95f..1f)
        assertTrue(LawnShade.FEATHER_OVERLAP in 1.2f..1.5f)
    }

    @Test
    fun `feathering keeps the top face and leaves out the side of the slab`() {
        // A 9 x 7 opaque picture, its top face a diamond 4 by 2 round (4.5, 3.5).
        val width = 9
        val height = 7
        val pixels = IntArray(width * height) { 0xFF336633.toInt() }
        val out = LawnShade.feather(pixels, width, centerX = 4.5f, centerY = 3.5f, halfWidth = 4f, halfHeight = 2f)
        fun alphaAt(x: Int, y: Int) = out[y * width + x] ushr 24
        assertEquals(255, alphaAt(4, 3))                 // the middle
        assertEquals(0, alphaAt(0, 6))                   // a corner, outside the face
        assertEquals(0, alphaAt(4, 6))                   // below the face: the slab's side
        assertTrue(alphaAt(7, 3) in 1..254)              // in the fade
        // The colour is untouched.
        assertEquals(0x336633, out[3 * width + 4] and 0xFFFFFF)
        assertEquals(0xFF336633.toInt(), LawnShade.meanColour(pixels, width, 4.5f, 3.5f, 4f, 2f))
    }

    @Test
    fun `the lawn is laid feathered, under one colour, and kept inside the garden`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        assertTrue(canvas.contains("FeatheredLawn.from(atlas, GroundTile.GRASS.assetId)"))
        val terrain = canvas.substringAfter("private fun DrawScope.drawTerrain(").substringBefore("\n}\n")
        assertTrue(terrain.contains("drawPath(lawnPath, lawn.base)"))
        assertTrue(terrain.contains("clipPath(lawnPath)"))
        assertTrue(terrain.contains("LawnShade.FEATHER_OVERLAP"))
    }

    // --- The stream ---

    @Test
    fun `the water is shaded under its far banks and lit at its near ones`() {
        val alone = 0
        assertEquals(listOf(RiverAutotile.NORTH, RiverAutotile.WEST), RiverAutotile.shadedSides(alone))
        assertEquals(listOf(RiverAutotile.SOUTH, RiverAutotile.EAST), RiverAutotile.litSides(alone))
        // A straight run north to south has banks only to the east and west.
        val run = RiverAutotile.NORTH or RiverAutotile.SOUTH
        assertEquals(listOf(RiverAutotile.WEST), RiverAutotile.shadedSides(run))
        assertEquals(listOf(RiverAutotile.EAST), RiverAutotile.litSides(run))
        // Water all round: no bank at all.
        val all = RiverAutotile.NORTH or RiverAutotile.EAST or RiverAutotile.SOUTH or RiverAutotile.WEST
        assertTrue(RiverAutotile.shadedSides(all).isEmpty() && RiverAutotile.litSides(all).isEmpty())
        // The shade starts at the water's edge, inside the bank, and stays off the cell's middle.
        assertTrue(RiverAutotile.BANK_EDGE in 0.15f..0.25f)
        assertTrue(RiverAutotile.BANK_EDGE + RiverAutotile.BANK_SHADE_WIDTH < 0.5f)
    }

    @Test
    fun `the garden box's rim lies over the stream and the beds at its edge`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        assertTrue(canvas.contains("pipeline.submit(RenderLayer.BASE_TERRAIN, Float.MAX_VALUE) { drawFrameLip("))
        val terrain = canvas.substringAfter("private fun DrawScope.drawTerrain(").substringBefore("\n}\n")
        assertFalse(terrain.contains("FRAME_LINE"))
    }

    @Test
    fun `the stream kit has a cosy name, and no frame in it`() {
        val strings = File(main, "res/values/strings.xml").readText()
        fun string(name: String) = Regex("<string name=\"$name\">(.*?)</string>").find(strings)?.groupValues?.get(1)
        assertEquals("小川のかけら", string("item_tile_river"))
        assertEquals("せせらぎ", string("object_terrain_river"))
        assertFalse(strings.contains("せせらぎの木枠"))
    }

    // --- The shop and the crate ---

    @Test
    fun `the shop changes room quickly, never with two rooms at half strength`() {
        assertTrue(ShopFades.ROOM_IN_MILLIS <= 160)
        assertTrue(ShopFades.SHELVES_OUT_MILLIS + ShopFades.SHELVES_IN_MILLIS <= 220)
        val shop = source("ui/shop/ShopStageScreen.kt")
        assertFalse(shop.contains("fadeIn() togetherWith fadeOut()"))
        // The new shelves wait until the old ones have gone.
        assertTrue(shop.contains("delayMillis = SHELVES_OUT_MILLIS"))
    }

    @Test
    fun `the gift box's two buttons are of one make`() {
        val crate = source("ui/onboarding/FirstSteps.kt").substringAfter("fun GiftDialog(").substringBefore("\n}\n")
        assertFalse(crate.contains("CarvedButton"))
        assertEquals(2, Regex("DialogAction\\(").findAll(crate).count())
    }

    private fun Size.center(): Offset = Offset(width / 2f, height / 2f)
}
