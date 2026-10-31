package com.studioguu.miniaturegarden.ui

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.data.asset.AssetManifest
import com.studioguu.miniaturegarden.data.asset.AssetRepository
import com.studioguu.miniaturegarden.data.asset.AtlasManifest
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.ui.components.PreviewExtent
import com.studioguu.miniaturegarden.ui.hud.removeLabel
import com.studioguu.miniaturegarden.ui.render.GardenClip
import com.studioguu.miniaturegarden.ui.render.LayerPipeline
import com.studioguu.miniaturegarden.ui.render.RenderCommand
import com.studioguu.miniaturegarden.ui.render.RenderLayer
import com.studioguu.miniaturegarden.ui.render.SunLight
import com.studioguu.miniaturegarden.ui.render.TapTargets
import com.studioguu.miniaturegarden.ui.render.WindmillSails
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The fixes to how the garden looks and what it says, from the device feedback after #75. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FeedbackVisualTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    private fun source(path: String) = File(main, "java/com/studioguu/miniaturegarden/$path").readText().replace("\r\n", "\n")

    private val canvas by lazy { source("ui/components/GardenCanvas.kt") }

    private val manifest: AtlasManifest by lazy { AtlasManifest.parse(File(main, "assets/atlas/assets_manifest.json").readText()) }
    private val sprites: (String) -> AssetManifest? = { id -> manifest.assets.firstOrNull { it.assetId == id } }

    // --- 1, 2. The lights of the night ---

    @Test
    fun `a light is cut out where something nearer than its owner stands, as the garden drew it`() {
        val lamps = canvas.substringAfter("private fun DrawScope.drawLamps(").substringBefore("\n}\n")
        assertTrue(lamps.contains("it.layer == RenderLayer.GRID_OBJECTS && it.depth > depth"))
        assertTrue(lamps.contains("blendMode = BlendMode.DstOut"))
        assertTrue(lamps.contains("LayerPipeline.draw(this, command, clip)"))
        // What was drawn is what it is cut by.
        assertTrue(canvas.contains("val drawn = pipeline.render(this, clip)"))
        assertTrue(canvas.contains("drawLamps(gridObjects, transform, atlas, spriteScale, lamps, drawn, clip)"))
    }

    @Test
    fun `the windows are lit where their glass is, slanted with their walls`() {
        assertTrue(canvas.contains("StructureData.HUT.objectId to listOf(Lamp(0.3147f, 0.4703f, 0.1066f, 0.111f, skew = 0.0406f))"))
        // The cottage's gable window is in the wall that runs the other way.
        assertTrue(canvas.contains("Lamp(0.718f, 0.4909f, 0.046f, 0.0914f, skew = -0.0094f)"))
    }

    @Test
    fun `the pipeline hands back what it drew, in order`() {
        val pipeline = LayerPipeline()
        val order = mutableListOf<String>()
        pipeline.submit(RenderLayer.GRID_OBJECTS, 2f) { order += "front" }
        pipeline.submit(RenderLayer.GRID_OBJECTS, 1f) { order += "back" }
        pipeline.submit(RenderLayer.BASE_TERRAIN) { order += "ground" }
        assertTrue(order.isEmpty())
        val drawn = pipeline.sortedCommands()
        assertEquals(listOf(RenderLayer.BASE_TERRAIN, RenderLayer.GRID_OBJECTS, RenderLayer.GRID_OBJECTS), drawn.map { it.layer })
        assertEquals(listOf(0f, 1f, 2f), drawn.map { it.depth })
    }

    // --- 3. The seams between beds ---

    @Test
    fun `a bed's picture is taken from inside the white behind its far furrows`() {
        // The white reaches 0.068 in from the far edge of soil_tilled.
        assertTrue(AssetRepository.BED_INSET > 0.068f)
    }

    // --- 4. The frame ---

    @Test
    fun `things of the garden are kept inside its frame, the ground to its diamond, what stands to above it`() {
        val ground = Path()
        val standing = Path()
        val clip = GardenClip(ground, standing)
        fun command(layer: RenderLayer, inGarden: Boolean = true) = RenderCommand(layer, 0f, {}, inGarden)
        assertSame(ground, clip.pathFor(command(RenderLayer.BASE_TERRAIN)))
        assertSame(ground, clip.pathFor(command(RenderLayer.SHADOWS)))
        assertSame(ground, clip.pathFor(command(RenderLayer.BOTTOM_OBJECTS)))
        assertSame(standing, clip.pathFor(command(RenderLayer.GRID_OBJECTS)))
        // Bubbles and sparkles over it, and what is not of the garden (the frame, the visitors), are not.
        assertNull(clip.pathFor(command(RenderLayer.TOP_EFFECTS)))
        assertNull(clip.pathFor(command(RenderLayer.GRID_OBJECTS, inGarden = false)))
        assertNull(clip.pathFor(command(RenderLayer.BASE_TERRAIN, inGarden = false)))
        // Everything an object puts in goes through the one place that marks it as the garden's.
        val objects = canvas.substringAfter("fun submit(obj: GridObject, layer: RenderLayer, depth: Float, plainDraw").substringBefore("\n    for (obj in gridObjects)")
        assertEquals(6, Regex("pipeline\\.submit\\(layer, depth, inGarden = true").findAll(objects).count())
        assertFalse(Regex("pipeline\\.submit\\(layer, depth\\)").containsMatchIn(objects))
    }

    // --- 5. Fences ---

    @Test
    fun `a fence joins only fences that are finished`() {
        assertTrue(canvas.contains("FenceAutotile.maskAt(obj.gridX, obj.gridY) { x, y -> grid.occupantAt(x, y)?.let { it.runKind == run && !it.isWorking } == true }"))
    }

    // --- 6. The water and food bubbles ---

    @Test
    fun `a plant's bubble sits just over the top of its picture, and taps find it there`() {
        val base = Offset(100f, 500f)
        val plant = GridObject(instanceId = "p", gridX = 0, gridY = 0, objectId = PlantSpecies.MINT.objectId, growthProgress = 0.5f, lastUpdatedTimestamp = 0L, status = PlantStatus.THIRSTY)
        val centre = TapTargets.statusBubbleCentre(base, plant, unit = 128f, plantTop = 60f)
        assertEquals(500f - 60f - 128f * TapTargets.STATUS_BUBBLE_GAP, centre.y, 1e-3f)
        assertEquals(100f, centre.x, 0f)
        // Without the picture, the guess from before.
        assertEquals(base.y - 128f * (0.55f * 0.675f + 0.3f), TapTargets.statusBubbleCentre(base, plant, 128f).y, 1e-3f)
        assertTrue(canvas.contains("plantTop = { obj -> sprites[TileSetDefinition.assetIdFor(obj)]?.let { it.anchorY * it.drawnHeight(scale) } }"))
        assertTrue(canvas.contains("val plantTop = sprite?.let { base.y - it.drawTop(base.y, spriteScale) }"))
    }

    // --- 7. The book's pictures ---

    @Test
    fun `a building's preview is fitted to its sails as they stand and to its shadow`() {
        val step = 16f / 128f
        val tile = manifest.tileWidth.toFloat()
        fun extent(objectId: String, light: SunLight?) = PreviewExtent.of(
            2, 0.45f, listOf(GridObject(instanceId = "x", gridX = 0, gridY = 0, objectId = objectId, lastUpdatedTimestamp = 0L)),
            sprites, tile, step, light,
        )
        val calm = SunLight(dx = 0.5f, dy = 0.2f, alpha = 0.5f)
        val hut = StructureData.HUT.objectId
        assertTrue(extent(hut, calm).right > extent(hut, null).right)
        assertEquals(extent(hut, calm).left, extent(hut, null).left, 1e-4f)
        // The sails do not turn in a preview: no room for the circle they would sweep.
        val mill = extent(WindmillSails.MILL_OBJECT_ID, null)
        val sails = sprites(WindmillSails.SAILS_ASSET_ID)!!
        val scale = 1f / tile
        val hub = sprites(WindmillSails.MILL_OBJECT_ID)!!.attachmentAt(0f, 0.5f, scale, flipped = false)!!
        assertTrue(mill.right < hub.first + kotlin.math.hypot(sails.drawnWidth(scale), sails.drawnHeight(scale)) / 2f)
    }

    // --- 8. The shop's icons ---

    @Test
    fun `everything on sale has a picture for its icon, and the picture is there`() {
        val labels = source("ui/UiLabels.kt")
        for (item in GardenItem.entries.filter { it.inShop }) {
            val line = Regex("GardenItem\\.${item.name} -> R\\.drawable\\.(icon_[a-z_]+)").find(labels)
            assertTrue("${item.name} has no picture", line != null)
            assertTrue(line!!.groupValues[1], File(main, "res/drawable-nodpi/${line.groupValues[1]}.png").isFile)
        }
    }

    // --- 11, 12. Words ---

    @Test
    fun `a crop is pulled up, a bed turned back, anything else taken away`() {
        fun at(objectId: String) = GridObject(instanceId = "x", gridX = 0, gridY = 0, objectId = objectId, lastUpdatedTimestamp = 0L)
        assertEquals(R.string.action_pull, removeLabel(at(PlantSpecies.TULIP.objectId)))
        assertEquals(R.string.action_restore_ground, removeLabel(at(GridObject.TILLED_SOIL_OBJECT_ID)))
        assertEquals(R.string.action_remove, removeLabel(at("deco_bench")))
        assertEquals(R.string.action_remove, removeLabel(at(StructureData.HUT.objectId)))
    }

    // --- 13. Compost ---

    @Test
    fun `a composted bed has no flecks, and a pulled plant crumbles into it in under a second`() {
        assertFalse(canvas.contains("COMPOST_LEAF"))
        assertFalse(canvas.contains("COMPOST_STRAW"))
        val millis = Regex("private const val CRUMBLE_MILLIS = (\\d+)").find(canvas)!!.groupValues[1].toInt()
        assertTrue(millis in 300..999)
        // Only a plant the player pulled up crumbles; a harvest still lifts off.
        assertTrue(canvas.contains("Ghost(gridObject, crumbles = gridObject.isPlant && gridObject.instanceId in currentPulled)"))
        assertTrue(source("ui/GardenViewModel.kt").contains("if (obj.isPlant) markPulled(obj.instanceId)"))
    }
}
