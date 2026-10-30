package com.studioguu.miniaturegarden.ui

import com.studioguu.miniaturegarden.data.asset.FlatGround
import com.studioguu.miniaturegarden.data.asset.GroundSlab
import com.studioguu.miniaturegarden.data.asset.TestPng
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.LawnGrowth
import com.studioguu.miniaturegarden.domain.model.LawnStage
import com.studioguu.miniaturegarden.ui.collection.BookTurn
import com.studioguu.miniaturegarden.ui.hud.HEADER_GAP
import com.studioguu.miniaturegarden.ui.render.FLAT_GROUND_CELLS
import com.studioguu.miniaturegarden.ui.render.GROUND_VARIATION_CELLS
import com.studioguu.miniaturegarden.ui.render.PondJoin
import com.studioguu.miniaturegarden.ui.render.SoilPalette
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The batch of #54: dialogs and panels set straight, the book's way back to its contents, the
 * completion plaque without its experience, no watering can on the bar, faster artwork at the
 * title, a silent hard ground, the sprinkler's water, the wasteland without a grid, beds in the
 * wasteland's tones, ponds that join into one pool, and the lawn made ready to grow in stages.
 */
class UiBalanceAndGroundTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private fun source(path: String) = File(main, "java/com/studioguu/miniaturegarden/$path").readText().replace("\r\n", "\n")

    // --- 1.1 The start-over dialog ---

    @Test
    fun `the start-over dialog's two buttons are side by side in the middle`() {
        val dialog = source("ui/title/TitleScreen.kt").substringAfter("if (confirmStartOver) {")
        assertTrue(dialog.contains("Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)"))
        assertTrue(dialog.contains("Modifier.fillMaxWidth()"))
        // Both in one centred row: no separate dismiss button pushed to the right by the dialog.
        assertFalse(dialog.contains("dismissButton"))
        // Cancel first, as in the other wooden dialogs.
        assertTrue(dialog.indexOf("R.string.action_cancel") < dialog.indexOf("R.string.title_reset_ok"))
    }

    // --- 1.2 The panel's header ---

    @Test
    fun `the panel's state tag has room between it and the close button`() {
        assertTrue(HEADER_GAP.value >= 12f)
        val panel = source("ui/hud/ObjectStatusDialog.kt")
        val header = panel.substringAfter("// The header:").substringBefore("if (gridObject.isWorking) WorkRow")
        assertTrue(header.contains("horizontalArrangement = Arrangement.spacedBy(HEADER_GAP)"))
        assertTrue(header.contains("StatusTag(gridObject.status)"))
        assertTrue(panel.contains("modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)"))
    }

    // --- 1.3 Back to the contents ---

    @Test
    fun `the book jumps straight to a far page, and slides only to the next one`() {
        assertTrue(BookTurn.slides(3, 4))
        assertTrue(BookTurn.slides(4, 3))
        assertFalse(BookTurn.slides(12, 0))
        assertFalse(BookTurn.slides(0, 7))
        val footer = source("ui/collection/BookParts.kt").substringAfter("internal fun BookFooter(").substringBefore("\n}\n")
        assertFalse(footer.contains("animateScrollToPage(0)"))
        assertTrue(footer.contains("pagerState.turnTo(0)"))
        for (path in listOf("ui/collection/CollectionDialog.kt", "ui/collection/NatureNotesChapter.kt")) {
            assertFalse(path, source(path).contains("animateScrollToPage(index + 1)"))
        }
    }

    // --- 1.5 and 2.3 ---

    @Test
    fun `the bar has no watering can, and the tools have no levels`() {
        val bar = source("ui/hud/QuickActionBar.kt")
        assertFalse(bar.contains("\"💧\""))
        assertFalse(bar.contains("onWater"))
        val panel = source("ui/hud/ObjectStatusDialog.kt")
        assertFalse(panel.contains("toolStars"))
        assertFalse(source("ui/GardenScreen.kt").contains("toolLevels"))
    }

    @Test
    fun `hard ground makes no sound`() {
        val act = source("ui/GardenViewModel.kt").substringAfter("private fun act(").substringBefore("actionResultMessage(result)")
        assertTrue(act.contains("ActionResult.HOE_STRUCK -> Unit"))
        assertFalse(act.contains("HOE_STRUCK -> audio.play"))
    }

    // --- 2.2 The title's artwork ---

    @Test
    fun `the artwork is read from the app's start, the atlas and the ground side by side`() {
        val repository = source("data/asset/AssetRepository.kt")
        assertTrue(repository.contains("private val atlasMutex = Mutex()"))
        assertTrue(repository.contains("private val terrainMutex = Mutex()"))
        assertFalse(repository.contains("private val mutex = Mutex()"))
        assertTrue(repository.contains("fun prefetch(scope: CoroutineScope)"))
        val app = source("PocketGardenApplication.kt")
        val onCreate = app.substringAfter("override fun onCreate()")
        assertTrue(onCreate.contains("assets.prefetch("))
        // Started before the rest of the set-up (since #56 the settings are read off the main
        // thread, after it; there is no runBlocking any more).
        assertFalse(onCreate.contains("runBlocking"))
        assertTrue(onCreate.indexOf("assets.prefetch(") < onCreate.indexOf("audio.holdUntilSettings()"))
        // The title's grown-over garden does not wait for the wasteland, the slowest to make.
        assertTrue(source("ui/title/TitleViewModel.kt").contains("loadTerrainTextures(withWasteland = false)"))
        assertTrue(source("ui/GardenViewModel.kt").contains("assetRepository.loadTerrainTextures()"))
        // Made once for each installed build and the way it is made, then read back from the cache.
        assertTrue(repository.contains("\"wasteland-\${FlatGround.FIELD_VERSION}-\$installed.png\""))
        assertTrue(repository.contains("readCachedField(file) ?: decodeUnscaled(R.drawable.soil_wasteland)"))
        assertTrue(FlatGround.FIELD_VERSION >= 1)
    }

    // --- 3.1 The wasteland, seamless ---

    @Test
    fun `the blend's weights rise smoothly from the tile's edge, and the two always add up to one`() {
        assertEquals(0.0, FlatGround.weight(0.0), 1e-9)
        assertEquals(0.5, FlatGround.weight(0.5), 1e-9)
        assertEquals(1.0, FlatGround.weight(1.0), 1e-9)
        for (k in 0..20) {
            val t = k / 20.0
            assertEquals(1.0, FlatGround.weight(t) + (1 - FlatGround.weight(t)), 1e-9)
            if (k > 0) assertTrue(FlatGround.weight(t) >= FlatGround.weight(t - 0.05))
        }
        assertEquals(FlatGround.TILE_SIZE, FlatGround.tileSize(FlatGround.SIZE))
    }

    private fun noise(size: Int, seed0: Int): IntArray {
        var seed = seed0
        return IntArray(size * size) {
            seed = seed * 1103515245 + 12345
            val v = 64 + ((seed ushr 16) and 0x7F)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    private fun spread(pixels: IntArray, size: Int, columns: IntRange): Double {
        val values = (0 until size).flatMap { y -> columns.map { x -> (pixels[y * size + x] and 0xFF).toDouble() } }
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    @Test
    fun `blending keeps the earth's grain as strong in the blended strip as elsewhere`() {
        // Noise: averaging would flatten it into a smooth strip; the blend keeps its contrast.
        val size = 128
        val out = FlatGround.seamless(noise(size, 7), size)
        val tile = FlatGround.tileSize(size)
        val margin = size - tile
        val strip = spread(out, tile, (margin / 2 - 2)..(margin / 2 + 2))
        val middle = spread(out, tile, (tile / 2 - 2)..(tile / 2 + 2))
        assertTrue("$strip against $middle", strip > middle * 0.85)
    }

    @Test
    fun `evening out takes away the light and dark larger than a clod, and keeps it tiling`() {
        // Broad light and dark across the tile (a whole wave of it), with grain over it: bands, once repeated.
        val size = 256
        val grain = noise(size, 3)
        val banded = IntArray(size * size) { i ->
            val shade = 40 * kotlin.math.cos(2 * Math.PI * (i % size) / size)
            val v = ((grain[i] and 0xFF) + shade).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val out = FlatGround.evenOut(banded, size)
        fun columnsMean(pixels: IntArray, range: IntRange) =
            (0 until size).flatMap { y -> range.map { x -> (pixels[y * size + x] and 0xFF).toDouble() } }.average()
        val before = columnsMean(banded, 0..8) - columnsMean(banded, 124..132)
        val after = columnsMean(out, 0..8) - columnsMean(out, 124..132)
        assertTrue("$before to $after", abs(after) < abs(before) * 0.4)
        // The grain itself stays.
        assertTrue(spread(out, size, 20..40) > 10)
    }

    @Test
    fun `the field is laid from the tile without repeating it, and tiles itself`() {
        val tileSize = 64
        val tile = noise(tileSize, 11)
        val fieldSize = 256
        val field = FlatGround.field(tile, tileSize, fieldSize = fieldSize, cells = 4)
        fun grey(x: Int, y: Int) = field[y.mod(fieldSize) * fieldSize + x.mod(fieldSize)] and 0xFF
        // Across its own edges it runs on as within it (the patches wrap round).
        var edge = 0.0
        var inside = 0.0
        for (i in 0 until fieldSize) {
            edge += abs(grey(fieldSize - 1, i) - grey(0, i))
            inside += abs(grey(fieldSize / 2 - 1, i) - grey(fieldSize / 2, i))
        }
        assertTrue("$edge against $inside", edge < inside * 1.3)
        // A tile's width along, it is not the same again: the patches come from different places.
        var same = 0
        for (y in 0 until fieldSize step 4) for (x in 0 until fieldSize - tileSize step 4) {
            if (grey(x, y) == grey(x + tileSize, y)) same++
        }
        val looked = (fieldSize / 4) * ((fieldSize - tileSize) / 4)
        assertTrue("$same of $looked alike", same < looked / 4)
    }

    @Test
    fun `the broad light and dark tiles, stays gentle, and does not repeat with the earth`() {
        val size = FlatGround.VARIATION_SIZE
        val v = FlatGround.variation(size)
        fun grey(x: Int, y: Int) = v[y.mod(size) * size + x.mod(size)] and 0xFF
        for (i in 0 until size) {
            assertTrue(abs(grey(size - 1, i) - grey(0, i)) < 12)
            assertTrue(abs(grey(i, size - 1) - grey(i, 0)) < 12)
        }
        val all = (0 until size * size).map { v[it] and 0xFF }
        assertEquals(128.0, all.average(), 20.0)
        assertTrue(all.all { it in 60..196 })
        // Its repeat is no whole number of the field's, which is itself well over a cell or two.
        assertTrue(FLAT_GROUND_CELLS > 3f)
        val ratio = GROUND_VARIATION_CELLS / FLAT_GROUND_CELLS
        assertTrue(abs(ratio - Math.round(ratio)) > 0.1f)
        assertTrue(source("ui/render/GroundSlabRenderer.kt").contains("PorterDuff.Mode.OVERLAY"))
    }

    // --- 3.2 Tilled soil ---

    @Test
    fun `a bed is turned earth, darker than the wasteland round it`() {
        fun faceLuminance(name: String, slab: GroundSlab): Double {
            val image = TestPng.read(File(main, "res/drawable/$name.png"))
            val cx = (slab.anchorX * image.width).toInt()
            val cy = (slab.anchorY * image.height).toInt()
            var sum = 0.0
            var n = 0
            for (dy in -60..60 step 5) for (dx in -120..120 step 5) {
                if (image.alpha(cx + dx, cy + dy) < 200) continue
                sum += image.luminance(cx + dx, cy + dy)
                n++
            }
            return sum / n
        }
        // #57: the beds are pictures of their own, laid on every bed; since #61 soil_revived is
        // the ground the hoe has broken up on its way to a bed, darker than the wasteland too.
        val wasteland = faceLuminance("soil_wasteland", GroundSlab.WASTELAND)
        val tilled = faceLuminance("soil_tilled", GroundSlab.TILLED)
        val broken = faceLuminance("soil_revived", GroundSlab.REVIVED)
        assertTrue("tilled $tilled vs ground $wasteland", tilled < wasteland - 20)
        assertTrue("broken $broken vs ground $wasteland", broken < wasteland - 20)
        assertTrue(source("ui/components/GardenCanvas.kt").contains("face = terrain.tilled"))
        // Drawn without them, a bed keeps its own colours.
        val body = SoilPalette.mix(SoilPalette.DRY, SoilPalette.DARK, SoilPalette.bodyMix(0f))
        val (r, _, b) = SoilPalette.rgb(body)
        assertTrue(r > b)
        // Furrows read: ridges lighter, hollows darker than the body.
        assertTrue(SoilPalette.luma(SoilPalette.RIDGE) > SoilPalette.luma(body))
        assertTrue(SoilPalette.luma(SoilPalette.DEEP) < SoilPalette.luma(body))
        val canvas = source("ui/components/GardenCanvas.kt")
        assertTrue(canvas.contains("private val DIRT_DARK = Color(SoilPalette.DARK)"))
        assertTrue(canvas.contains("if (i < 6 || onBareEarth)"))
    }

    // --- 3.3 Ponds joined ---

    private fun cells(vararg xy: Pair<Int, Int>) = xy.map { GridCell(it.first, it.second) }.toSet()

    @Test
    fun `two ponds side by side join once, with both ends of the join on the bank`() {
        val ponds = cells(2 to 2, 3 to 2)
        val bridges = PondJoin.bridges(ponds)
        assertEquals(listOf(PondJoin.Bridge(2, 2, alongX = true, openBefore = true, openAfter = true)), bridges)
        assertEquals(PondJoin.EAST, PondJoin.mask(2, 2) { x, y -> GridCell(x, y) in ponds })
        assertEquals(PondJoin.WEST, PondJoin.mask(3, 2) { x, y -> GridCell(x, y) in ponds })
        assertEquals(1, PondJoin.pools(ponds).size)
    }

    @Test
    fun `four ponds in a square make one pool, open in the middle`() {
        val ponds = cells(1 to 1, 2 to 1, 1 to 2, 2 to 2)
        val isPond: (Int, Int) -> Boolean = { x, y -> GridCell(x, y) in ponds }
        val bridges = PondJoin.bridges(ponds)
        assertEquals(4, bridges.size)
        assertTrue(PondJoin.cornerInside(1, 1, isPond))
        assertFalse(PondJoin.cornerInside(2, 2, isPond))
        // The joins' ends on the inside of the square are water, not bank.
        val top = bridges.single { it.alongX && it.y == 1 }
        assertTrue(top.openBefore)
        assertFalse(top.openAfter)
        val left = bridges.single { !it.alongX && it.x == 1 }
        assertTrue(left.openBefore)
        assertFalse(left.openAfter)
    }

    @Test
    fun `ponds only touching at a corner are two pools, and a lone pond has no join`() {
        val ponds = cells(0 to 0, 1 to 1)
        assertTrue(PondJoin.bridges(ponds).isEmpty())
        assertEquals(2, PondJoin.pools(ponds).size)
        assertEquals(0, PondJoin.mask(5, 5) { _, _ -> false })
    }

    @Test
    fun `the garden paints the joins after both ponds, over their logs`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        val branch = canvas.substringAfter("if (obj.isPond) {").substringBefore("        if (LawnGrowth.isLawn(obj)) {")
        assertTrue(branch.contains("PondJoin.bridgesFrom(obj.gridX, obj.gridY, isPond)"))
        assertTrue(branch.contains("later + 0.5f"))
        assertTrue(branch.contains("drawPondCorner("))
        // The join covers both ponds' logs: from inside the first's open water past the second's.
        assertTrue(PondJoin.JOIN_FROM < 0.7f && PondJoin.JOIN_TO > 1.2f)
    }

    // --- 3.4 The lawn, ready to grow ---

    private fun lawn(progress: Float) = GridObject("g", 1, 1, objectId = LawnGrowth.OBJECT_ID, growthProgress = progress, lastUpdatedTimestamp = 0L)

    @Test
    fun `a lawn's stage follows how far it has grown, and every stage covers more`() {
        assertEquals(LawnStage.SOWN, LawnGrowth.stageFor(0f))
        assertEquals(LawnStage.SPROUTING, LawnGrowth.stageFor(0.3f))
        assertEquals(LawnStage.PATCHY, LawnGrowth.stageFor(0.8f))
        assertEquals(LawnStage.FULL, LawnGrowth.stageFor(1f))
        assertEquals(LawnStage.FULL, LawnGrowth.stageFor(5f))
        LawnStage.entries.zipWithNext { a, b ->
            assertTrue(a.from < b.from)
            assertTrue(a.cover < b.cover)
        }
        assertEquals(1f, LawnStage.FULL.cover)
    }

    @Test
    fun `a lawn is drawn by its stage while lawns grow in stages, fully grown otherwise`() {
        // Turned on in #55 (DeviceFeedbackTest).
        assertTrue(LawnGrowth.GROWS_IN_STAGES)
        assertEquals(LawnStage.FULL, LawnGrowth.stageOf(lawn(0f), growsInStages = false))
        assertEquals(LawnStage.SOWN, LawnGrowth.stageOf(lawn(0f), growsInStages = true))
        assertTrue(LawnGrowth.isLawn(lawn(0f)))
    }

    @Test
    fun `a lawn comes on from seed to full in its time, and no further`() {
        val hour = 3_600_000L
        assertEquals(0.5f, LawnGrowth.grownBy(0f, (LawnGrowth.HOURS_TO_FULL / 2 * hour).toLong()), 1e-4f)
        assertEquals(1f, LawnGrowth.grownBy(0.9f, 100 * hour), 0f)
        assertEquals(0.4f, LawnGrowth.grownBy(0.4f, -5L), 0f)
    }

    @Test
    fun `pebbles and wild flowers cover their cell, not a few specks (#60, #74)`() {
        // #74: pictures seen from straight above, laid over the whole cell; the drawn scatter is gone.
        val canvas = source("ui/components/GardenCanvas.kt")
        assertTrue(canvas.contains("submit(obj, RenderLayer.BOTTOM_OBJECTS, depth) { drawGroundTexture(cover, transform, obj.gridX, obj.gridY) }"))
        assertFalse(canvas.contains("drawPebbles("))
        assertFalse(canvas.contains("drawWildFlowers("))
    }

    @Test
    fun `the garden draws a lawn over its whole cell by how far it has grown (#61)`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        val branch = canvas.substringAfter("if (LawnGrowth.isLawn(obj)) {").substringBefore("continue")
        assertTrue(branch.contains("LawnGrowth.growthOf(obj)"))
        assertTrue(branch.contains("drawLawnPatch("))
        // Not the whole picture at a stage's strength (a thin wash, before #60), nor clumps on
        // part of the cell (#60): a seedbed over the whole cell and blades cut from the picture.
        assertFalse(canvas.contains("alpha = stage.cover"))
        assertFalse(canvas.contains("lawnClumpsPath("))
        assertTrue(canvas.contains("drawPath(seedbed, LAWN_SEEDBED)"))
        assertTrue(canvas.contains("clipPath(blades) { picture(1f) }"))
        // The drawn, yellow-green tufts over the lawn are gone.
        assertFalse(canvas.contains("drawGrassBlade("))
        assertFalse(canvas.contains("GRASS_TUFT_DARK"))
        assertEquals(0.4f, LawnGrowth.growthOf(lawn(0.4f)), 0f)
        assertEquals(1f, LawnGrowth.growthOf(lawn(0.4f), growsInStages = false), 0f)
    }
}
