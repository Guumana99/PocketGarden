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
        // The #39 one came painted at 316 px, against the tulip's 222; the #57 one is drawn small
        // already, and is held under the same cap.
        // (#58: the tulip's own first shoots are drawn small too, and no longer the measure.)
        assertTrue(height("plant_tomato_stage1") <= CropScale.SEEDLING_HEIGHT + 1e-4f)
        // #60: the tulip's own shoots are brought down a little too, under the smaller caps.
        assertTrue(drawn("plant_tulip_stage1").drawScale in 0.85f..1f)
    }

    @Test
    fun `every seedling is small, and every crop only grows from stage to stage`() {
        assertTrue(crops.keys.containsAll(listOf("plant_tomato", "plant_tulip", "plant_rose", "plant_strawberry", "plant_daikon")))
        for ((crop, stages) in crops) {
            // A field (the wheat) is the cell's size at every stage, not a plant that grows taller.
            if (crop in CropScale.FIELDS) continue
            assertTrue(crop, height(stages.first()) <= CropScale.SEEDLING_HEIGHT + 1e-4f)
            stages.zipWithNext { a, b -> assertTrue("$a is taller than $b", height(a) <= height(b) + 1e-4f) }
        }
    }

    @Test
    fun `a bushy seedling like the rose's is held to a slim shoot's breadth`() {
        for ((crop, stages) in crops) {
            // The corn's three seedlings and the wheat's field of shoots are several plants (#57).
            val cap = if (crop in CropScale.CLUMPS) CropScale.MAX_WIDTH else CropScale.SEEDLING_WIDTH
            assertTrue(stages.first(), width(stages.first()) <= cap + 1e-4f)
        }
        // The rose's first stage is drawn at under half its painted size (it stood as full as a
        // young plant; about 0.6 until the seedlings were made smaller in #60).
        val rose = drawn("plant_rose_stage1").drawScale
        assertTrue("rose seedling at $rose", rose in 0.4f..0.5f)
        assertTrue(height("plant_rose_stage1") < height("plant_rose_stage2") * 0.6f)
    }

    @Test
    fun `no stage stands taller or spreads wider than its place`() {
        for (asset in scaled.assets.filter { it.assetId.startsWith("plant_") }) {
            val field = CropScale.FIELDS.any { asset.assetId.startsWith(it) }
            if (!field) assertTrue(asset.assetId, height(asset.assetId) <= CropScale.MAX_HEIGHT + 1e-4f)
            assertTrue(asset.assetId, width(asset.assetId) <= CropScale.MAX_WIDTH + 1e-4f)
        }
        // The grown daikon's leaves came two tiles wide.
        assertTrue(raw.assets.first { it.assetId == "plant_daikon_stage3" }.width > 2 * tile * 0.9f)
    }

    @Test
    fun `seedlings are small next to their cell (#60)`() {
        assertTrue(CropScale.SEEDLING_HEIGHT <= 0.3f)
        assertTrue(CropScale.SEEDLING_WIDTH <= 0.25f)
        for ((crop, stages) in crops) {
            if (crop in CropScale.FIELDS) continue
            assertTrue(stages.first(), height(stages.first()) <= 0.3f)
        }
    }

    @Test
    fun `the daikon stays on its own bed (#60)`() {
        val cap = CropScale.WIDTH_CAPS.getValue("plant_daikon")
        assertTrue(cap < CropScale.MAX_WIDTH)
        for (id in crops.getValue("plant_daikon") + "plant_daikon_withered") {
            assertTrue(id, width(id) <= cap + 1e-4f)
        }
    }

    @Test
    fun `every stage of the wheat field is laid one cell wide, rows and all (#60)`() {
        val wheat = crops.getValue("plant_wheat") + "plant_wheat_withered"
        for (id in wheat) {
            // As it was made: one cell (0.92) wide, not shrunk under the next stage's height.
            assertEquals(id, raw.assets.first { it.assetId == id }.drawScale, drawn(id).drawScale, 1e-6f)
            assertEquals(id, 0.92f, width(id), 0.02f)
        }
    }

    @Test
    fun `the eggplant stands on its bed, not on the tips of its fruit (#60)`() {
        for ((id, ground) in listOf("plant_eggplant_stage2" to 0.85f, "plant_eggplant_stage3" to 0.78f, "plant_eggplant_withered" to 0.83f)) {
            assertEquals(id, ground, drawn(id).anchorY, 0.005f)
        }
        // The carrot shows only the shoulder of its root: its grown picture is barely taller than
        // its leaves alone (stage 2), where it was twice as tall with the whole root out.
        val carrot = raw.assets.first { it.assetId == "plant_carrot_stage3" }
        assertTrue(carrot.height < 1.35f * raw.assets.first { it.assetId == "plant_carrot_stage2" }.height)
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
