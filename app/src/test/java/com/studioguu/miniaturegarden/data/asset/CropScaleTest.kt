package com.studioguu.miniaturegarden.data.asset

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The crops' stages, as the bundled art is drawn after [CropScale]: natural next to each other. */
class CropScaleTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val raw = AtlasManifest.parse(File(main, "assets/atlas/assets_manifest.json").readText())
    private val scaled = CropScale.normalized(raw)
    private val tile = raw.tileWidth.toFloat()

    private fun drawn(id: String): AssetManifest = scaled.assets.first { it.assetId == id }
    private fun height(id: String) = drawn(id).drawnHeight(1f) / tile
    private fun width(id: String) = drawn(id).drawnWidth(1f) / tile

    private val crops: Map<String, List<String>> = raw.assets.map { it.assetId }
        .mapNotNull { Regex("""^(plant_[a-z_]+?)_stage(\d+)$""").find(it)?.let { m -> m.groupValues[1] to it } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, ids) -> ids.sortedBy { it.substringAfterLast("stage").toInt() } }

    @Test
    fun `a tomato seedling is no taller than the tulip's first shoots`() {
        // It came painted at 316 px, against the tulip's 222.
        assertTrue(raw.assets.first { it.assetId == "plant_tomato_stage1" }.height > 300)
        assertTrue(height("plant_tomato_stage1") <= height("plant_tulip_stage1") * 1.1f)
        assertTrue(height("plant_tomato_stage1") <= CropScale.SEEDLING_HEIGHT + 1e-4f)
        // The tulip itself, the measure, is left almost as it was.
        assertEquals(1f, drawn("plant_tulip_stage1").drawScale, 0.02f)
    }

    @Test
    fun `every seedling is small, and every crop only grows from stage to stage`() {
        assertTrue(crops.keys.containsAll(listOf("plant_tomato", "plant_tulip", "plant_rose", "plant_strawberry", "plant_daikon")))
        for ((crop, stages) in crops) {
            assertTrue(crop, height(stages.first()) <= CropScale.SEEDLING_HEIGHT + 1e-4f)
            stages.zipWithNext { a, b -> assertTrue("$a is taller than $b", height(a) <= height(b) + 1e-4f) }
        }
    }

    @Test
    fun `no stage stands taller or spreads wider than its place`() {
        for (asset in scaled.assets.filter { it.assetId.startsWith("plant_") }) {
            assertTrue(asset.assetId, height(asset.assetId) <= CropScale.MAX_HEIGHT + 1e-4f)
            assertTrue(asset.assetId, width(asset.assetId) <= CropScale.MAX_WIDTH + 1e-4f)
        }
        // The grown daikon's leaves came two tiles wide.
        assertTrue(raw.assets.first { it.assetId == "plant_daikon_stage3" }.width > 2 * tile * 0.9f)
    }

    @Test
    fun `pictures are only ever made smaller, and nothing but the crops is touched`() {
        for ((before, after) in raw.assets.zip(scaled.assets)) {
            assertEquals(before.assetId, after.assetId)
            assertTrue(after.assetId, after.drawScale <= before.drawScale)
            if (!before.assetId.startsWith("plant_")) assertEquals(before, after)
        }
    }

    @Test
    fun `the garden draws the atlas through the crop scale`() {
        val repository = File(main, "java/com/studioguu/miniaturegarden/data/asset/AssetRepository.kt").readText()
        assertTrue(repository.contains("CropScale.normalized(AtlasManifest.parse(manifestText))"))
    }
}
