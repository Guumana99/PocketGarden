package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.data.asset.AtlasManifest
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Beds side by side are one field, the lawn one meadow, and crops stand on their stems (#47). */
class ConnectedGroundTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    // --- Fields of tilled soil ---

    @Test
    fun `a bed knows which of its sides carry on into another bed`() {
        val beds = setOf(3 to 3, 4 to 3, 3 to 4)
        val joins = SoilJoins.at(3, 3) { x, y -> (x to y) in beds }
        assertEquals(SoilJoins(north = false, east = true, south = true, west = false), joins)
        assertEquals(SoilJoins.NONE, SoilJoins.at(7, 7) { x, y -> (x to y) in beds })
    }

    @Test
    fun `beds side by side meet edge to edge, with no strip of grass between them`() {
        val left = SoilTexture.outline(3, 4, SoilJoins(east = true))
        val right = SoilTexture.outline(4, 4, SoilJoins(west = true))
        // The east side of the left bed and the west side of the right bed are both the line x = 4.
        val leftEast = left.subList(5, 10).map { it.first }
        val rightWest = right.subList(15, 20).map { it.first } + right[0].first
        assertTrue(leftEast.all { abs(it - 4f) < 1e-5f })
        assertTrue(rightWest.all { abs(it - 4f) < 1e-5f })
        // Alone, the same beds keep a strip of grass between them.
        assertEquals(2 * SoilTexture.INSET, SoilTexture.outline(4, 4)[0].first - SoilTexture.outline(3, 4)[5].first, 1e-5f)
    }

    @Test
    fun `a joined side spills no earth and has its grains right up to it`() {
        val joins = SoilJoins(north = true, east = true, south = true, west = true)
        assertTrue(SoilTexture.drift(5, 5, joins).isEmpty())
        val inner = SoilTexture.grains(5, 5, joins)
        assertTrue(inner.any { it.x < 5f + SoilTexture.INSET } || inner.any { it.y < 5f + SoilTexture.INSET })
        // A lone bed still spills earth all round.
        assertEquals(SoilTexture.DRIFT_COUNT, SoilTexture.drift(5, 5).size)
        // Half joined: only the open sides spill.
        val half = SoilTexture.drift(5, 5, SoilJoins(north = true, east = true))
        assertTrue(half.isNotEmpty() && half.size < SoilTexture.DRIFT_COUNT)
        assertTrue(half.none { it.y < 5f })      // nothing spilled north, into the next bed
        assertTrue(half.none { it.x > 6f })      // nor east
    }

    @Test
    fun `the furrows of every bed lie on the same rows, so that they run on across a field`() {
        val rows = SoilTexture.furrowRows(4)
        assertEquals(listOf(0.125f, 0.375f, 0.625f, 0.875f), rows)
    }

    @Test
    fun `the earth's dampness shades smoothly across a field rather than jumping from bed to bed`() {
        var biggestStep = 0f
        for (x in 0 until 8) for (y in 0 until 8) {
            val here = SoilTexture.fieldDampness(x + 0.5f, y + 0.5f)
            val east = SoilTexture.fieldDampness(x + 1.5f, y + 0.5f)
            biggestStep = maxOf(biggestStep, abs(here - east))
            assertTrue(here in 0f..1f)
        }
        assertTrue("step $biggestStep", biggestStep < 0.6f)
        // Continuous: the same point seen from either side of a cell edge.
        assertEquals(SoilTexture.fieldDampness(4f, 2.3f), SoilTexture.fieldDampness(4f, 2.3f), 0f)
        val a = SoilTexture.fieldDampness(3.999f, 2.3f)
        val b = SoilTexture.fieldDampness(4.001f, 2.3f)
        assertTrue(abs(a - b) < 0.01f)
    }

    @Test
    fun `damp patches reach over the edges of their cells`() {
        val patches = (0 until 9).flatMap { x -> (0 until 9).flatMap { y -> SoilTexture.patches(x, y) } }
        assertTrue(patches.any { p -> p.radius > 0.3f && (p.x % 1f < 0.3f || p.x % 1f > 0.7f) })
        assertEquals(SoilTexture.patches(2, 3), SoilTexture.patches(2, 3))
    }

    @Test
    fun `the garden draws its beds joined, from what is next to them`() {
        val canvas = File(main, "java/com/studioguu/miniaturegarden/ui/components/GardenCanvas.kt").readText()
        assertTrue(canvas.contains("SoilJoins.at(obj.gridX, obj.gridY) { bx, by -> grid.floorAt(bx, by)?.isTilledSoil == true }"))
        // The bed is not the old one-cell picture any more (that was a slab with edges all round).
        val tilled = canvas.substringAfter("if (obj.isTilledSoil) {").substringBefore("} else if (obj.isGreenhouse")
        assertFalse(tilled.contains("drawSprite("))
    }

    // --- The lawn ---

    @Test
    fun `the lawn's light ignores the cells - broad faint patches all over the garden`() {
        for (size in listOf(9, 11, 13)) {
            val patches = LawnShade.patches(size)
            assertEquals((size * size * LawnShade.PER_CELL).toInt(), patches.size)
            assertTrue(patches.all { it.x in 0f..size.toFloat() && it.y in 0f..size.toFloat() })
            assertTrue(patches.all { it.radius in LawnShade.MIN_RADIUS..LawnShade.MAX_RADIUS })
            assertTrue(patches.all { it.strength in LawnShade.MIN_STRENGTH..LawnShade.MAX_STRENGTH })
            // Wider than a cell, and both lighter and darker ones.
            assertTrue(patches.any { it.radius > 1.5f })
            assertTrue(patches.any { it.light } && patches.any { !it.light })
            assertEquals(patches, LawnShade.patches(size))
        }
        assertTrue(LawnShade.CELL_OVERLAP > 1f && LawnShade.CELL_OVERLAP < 1.1f)
    }

    // --- Crops ---

    private val EGGPLANT_BUSHES = setOf("plant_eggplant_stage2", "plant_eggplant_stage3", "plant_eggplant_withered")

    @Test
    fun `every crop picture stands on the base of its stem, its earth taken out`() {
        val manifest = AtlasManifest.parse(File(main, "assets/atlas/assets_manifest.json").readText())
        val crops = manifest.assets.filter { it.assetId.startsWith("plant_") }
        assertTrue(crops.size >= 30)
        // The wheat is a field over the whole cell (#57): it stands on the field's middle, not on a stem.
        val (fields, rest) = crops.partition { it.assetId.startsWith("plant_wheat_") }
        // The eggplant (#60) is a bush with no stem to be seen and its fruit hanging below: it stands
        // where the bush meets the bed, well up its picture.
        val (bushes, plants) = rest.partition { it.assetId in EGGPLANT_BUSHES }
        assertEquals(EGGPLANT_BUSHES, bushes.map { it.assetId }.toSet())
        for (bush in bushes) assertTrue(bush.assetId, bush.anchorY in 0.75f..0.9f)
        assertEquals(4, fields.size)
        for (field in fields) {
            assertEquals(field.assetId, 0.5f, field.anchorX, 1e-4f)
            assertTrue(field.assetId, field.anchorY in 0.55f..0.8f)
        }
        for (crop in plants) {
            // At the foot of the picture, or a little above where a withered leaf droops lower.
            assertTrue(crop.assetId, crop.anchorY in 0.85f..1f)
            assertTrue(crop.assetId, crop.anchorX in 0.3f..0.7f)
        }
        // Healthy plants stand right at the foot of their pictures. The watermelon's stage 3 (#65) is
        // a low mound of leaves with its fruit lying in front, below where the mound meets the bed.
        val healthy = plants.filter { !it.assetId.endsWith("_withered") && it.assetId != "plant_watermelon_stage3" }
        assertTrue(healthy.filter { it.anchorY < 0.97f }.joinToString { it.assetId }, healthy.all { it.anchorY >= 0.97f })
        // So the wind, which leans a picture about its anchor, moves the plant and not the ground.
        val tools = listOf(File("../tools"), File("tools")).first { it.isDirectory }
        val overrides = File(tools, "atlas_overrides.json").readText()
        // Only the wheat's fields, the tomato whose foot is a hanging leaf (#57), the sunflower's
        // three seedlings (#58), the eggplant's bushes (#60) and the shiitake's logs, stood on the
        // foot of the log (#68), are stood by hand.
        val byHand = Regex("\"(plant_[a-z_0-9]+)\"").findAll(overrides).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf("plant_wheat_stage1", "plant_wheat_stage2", "plant_wheat_stage3", "plant_wheat_withered", "plant_tomato_stage3",
                "plant_sunflower_stage1", "plant_shiitake_stage1", "plant_shiitake_stage2", "plant_shiitake_stage3",
                "plant_shiitake_withered") + EGGPLANT_BUSHES,
            byHand,
        )
    }
}
