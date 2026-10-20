package com.studioguu.miniaturegarden.ui

import com.studioguu.miniaturegarden.data.asset.FlatGround
import com.studioguu.miniaturegarden.data.asset.GroundSlab
import com.studioguu.miniaturegarden.data.asset.TestPng
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.ui.effects.GardenEffect
import com.studioguu.miniaturegarden.ui.render.FLAT_GROUND_CELLS
import com.studioguu.miniaturegarden.ui.render.PondWater
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feedback from the device: a building's plaque without its experience, the wasteland as one
 * seamless field with no tufts or rim, no specks on the ground, a hoe that takes its time and
 * shows its rank, a way back to the book's contents, no "登録しました +N" over the book, and a
 * pond whose water moves.
 */
class FeedbackBatchTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private fun source(path: String) = File(main, "java/com/studioguu/miniaturegarden/$path").readText().replace("\r\n", "\n")
    private val strings = File(main, "res/values/strings.xml").readText()
    private fun string(name: String) = Regex("<string name=\"$name\">(.*?)</string>").find(strings)?.groupValues?.get(1)

    // --- 1. The building's plaque and its experience ---

    @Test
    fun `an unveiled building's plaque says it is finished, without its experience`() {
        val viewModel = source("ui/GardenViewModel.kt")
        val unveil = viewModel.substringAfter("// A finished building under wraps: the tap unveils it.").substringBefore("} else if (_hoeActive.value)")
        assertTrue(unveil.contains("GardenEffect.WorkDone(it, site.objectId, WorkKind.BUILDING)"))
        assertFalse(unveil.contains("GardenEffect.Xp("))
        assertFalse(unveil.contains("xp = "))
        // "完成", the new page and "+XP" at once were too much: the plaque no longer carries the XP.
        val plaque = source("ui/effects/FloatingEffects.kt").substringAfter("is GardenEffect.WorkDone -> stringResource(")
        assertFalse(plaque.substringBefore("    Text(").contains("effect_xp"))
    }

    // --- 2 and 3. The wasteland as one field, without its rim and tufts ---

    @Test
    fun `the flat texture is taken from inside the face, where there are no tufts or rim`() {
        val image = TestPng.read(File(main, "res/drawable/soil_wasteland.png"))
        val m = FlatGround.textureToImage(image.width, image.height, GroundSlab.WASTELAND, FlatGround.SIZE)
        var bright = 0
        var seen = 0
        val steps = 64
        for (j in 0 until steps) for (i in 0 until steps) {
            val ti = (i + 0.5f) * FlatGround.SIZE / steps
            val tj = (j + 0.5f) * FlatGround.SIZE / steps
            val x = (m[0] * ti + m[1] * tj + m[2]).toInt()
            val y = (m[3] * ti + m[4] * tj + m[5]).toInt()
            assertTrue("($x, $y) is on the picture", x in 0 until image.width && y in 0 until image.height)
            seen++
            if (image.luminance(x, y) > 200) bright++
        }
        // The white tufts stand along the rim; inside it the earth is dark, with a pale pebble here and there.
        assertTrue("$bright of $seen bright", bright * 100 < seen)
        // The corners of the texture are well inside the face's own corners.
        val corner = floatArrayOf(m[2], m[5])
        val faceTop = GroundSlab.WASTELAND.anchorY * image.height - GroundSlab.WASTELAND.topWidth * image.width / 4f
        assertTrue(corner[1] > faceTop + 20f)
    }

    @Test
    fun `the flat texture tiles - its far edge runs on into its near one`() {
        // A ramp from dark on the left to light on the right: repeated as it is, a hard step.
        val size = 64
        val ramp = IntArray(size * size) { i -> val v = (i % size) * 255 / (size - 1); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val out = FlatGround.seamless(ramp, size)
        // A tile a margin smaller than the face (#54: the far edge is blended into the near one).
        val tile = FlatGround.tileSize(size)
        assertEquals(tile * tile, out.size)
        fun grey(p: Int) = p and 0xFF
        for (y in 0 until tile) {
            val wrap = abs(grey(out[y * tile + tile - 1]) - grey(out[y * tile]))
            assertTrue("row $y: a step of $wrap across the edge", wrap < 40)
        }
        assertTrue(abs(grey(ramp[size - 1]) - grey(ramp[0])) > 200)
        // Opaque everywhere, a see-through picture included: filled with the earth's own colour.
        val seeThrough = IntArray(size * size) { if (it % 2 == 0) 0x00000000 else 0xFF402A1C.toInt() }
        assertTrue(FlatGround.seamless(seeThrough, size).all { (it ushr 24) == 0xFF })
    }

    @Test
    fun `the garden lays the wasteland in one pass over the whole field, repeating, never a slab a cell`() {
        val canvas = source("ui/components/GardenCanvas.kt")
        assertTrue(canvas.contains("if (wasteland != null) drawFlatGround(wasteland, transform, gardenOutline(transform, max))"))
        assertFalse(canvas.contains("GroundSlab.WASTELAND, transform.tileCenter(x, y)"))
        val renderer = source("ui/render/GroundSlabRenderer.kt")
        assertTrue(renderer.contains("BitmapShader(texture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)"))
        assertTrue(FLAT_GROUND_CELLS != 1f && FLAT_GROUND_CELLS != 2f)
        val repository = source("data/asset/AssetRepository.kt")
        assertTrue(repository.contains("FlatGround.flatten(slab)"))
    }

    // --- 4. Nothing scattered over the ground ---

    @Test
    fun `no petals, leaves or glints are scattered over the ground`() {
        assertFalse(source("ui/components/GardenCanvas.kt").contains("drawSeasonGround"))
        val seasonal = source("ui/render/SeasonalArt.kt")
        assertFalse(seasonal.contains("object SeasonGround"))
        assertFalse(seasonal.contains("fun DrawScope.drawSeasonGround"))
    }

    // --- 5. The hoe takes its time, and shows a rank ---

    @Test
    fun `a tool shows stars and a rank, never a bare level number`() {
        assertEquals("", toolStars(1))
        assertEquals("★", toolStars(2))
        assertEquals("★★★★", toolStars(5))
        assertEquals(null, string("tool_level"))
        assertEquals("見習い", string("tool_rank_1"))
        assertEquals("名人", string("tool_rank_5"))
        for (name in listOf("effect_hoe_level_up", "effect_can_level_up")) {
            val text = string(name)!!
            assertFalse(name, text.contains("Lv"))
            assertTrue(name, text.contains("%1\$s"))
        }
        val bar = source("ui/hud/QuickActionBar.kt")
        assertTrue(bar.contains("toolStars(level)"))
        assertFalse(bar.contains("R.string.tool_level"))
    }

    // --- 6 and 7. The book ---

    @Test
    fun `every page of the book has a way straight back to its contents`() {
        val footer = source("ui/collection/BookParts.kt").substringAfter("internal fun BookFooter(").substringBefore("\n}\n")
        assertTrue(footer.contains("if (pagerState.currentPage > 0)"))
        assertTrue(footer.contains("text = stringResource(R.string.book_to_contents)"))
        // Straight there, not sliding through every page in between (#54).
        assertTrue(footer.contains("pagerState.turnTo(0)"))
        assertEquals("📖 もくじ", string("book_to_contents"))
    }

    // --- 8. The pond's water ---

    @Test
    fun `the pond's water glints and ripples, on the open water, and keeps moving`() {
        val a = PondWater.frameAt(1_000L, seed = 7)
        assertEquals(a, PondWater.frameAt(1_000L, seed = 7))
        assertEquals(PondWater.GLINTS, a.glints.size)
        assertEquals(PondWater.RIPPLES, a.ripples.size)
        for (t in 0L..20_000L step 250L) {
            val frame = PondWater.frameAt(t, seed = 7)
            for (g in frame.glints) {
                assertTrue(g.u in 0f..1f && g.v in 0f..1f)
                assertTrue(g.alpha in 0f..1f)
            }
            for (r in frame.ripples) {
                assertTrue(r.radius in 0f..PondWater.RIPPLE_REACH)
                assertTrue(r.alpha in 0f..1f)
            }
        }
        // It moves, and two ponds do not move in step.
        assertNotEquals(a, PondWater.frameAt(1_400L, seed = 7))
        assertNotEquals(a, PondWater.frameAt(1_000L, seed = 8))
        // A ring spreads as it fades.
        val ring = { t: Long -> PondWater.frameAt(t, seed = 3).ripples[0] }
        val start = (0L until PondWater.RIPPLE_MILLIS step 20L).minByOrNull { ring(it).radius }!!
        assertTrue(ring(start + 1_000L).radius > ring(start).radius)
        assertTrue(ring(start + 1_000L).alpha < ring(start).alpha)
        // The water is inside the logs.
        assertTrue(PondWater.WATER_FROM > 0.1f && PondWater.WATER_TO < 0.9f)
        val canvas = source("ui/components/GardenCanvas.kt")
        assertTrue(canvas.contains("PondWater.frameAt(ambience.windMillis ?: 0L"))
        assertTrue(canvas.contains("drawPondWater(transform, obj.gridX, obj.gridY, water)"))
    }
}
