package com.studioguu.miniaturegarden.ui.components

import com.studioguu.miniaturegarden.data.asset.AssetManifest
import com.studioguu.miniaturegarden.data.asset.AtlasManifest
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.ui.render.WindmillSails
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How much room the encyclopedia's picture of a building is given. */
class PreviewExtentTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val manifest: AtlasManifest by lazy { AtlasManifest.parse(File(main, "assets/atlas/assets_manifest.json").readText()) }
    private val sprites: (String) -> AssetManifest? = { id -> manifest.assets.firstOrNull { it.assetId == id } }

    /** A height step in tile widths, as the garden draws it. */
    private val step = 16f / 128f

    private fun at(objectId: String) = GridObject(
        instanceId = "preview",
        gridX = 0,
        gridY = 0,
        objectId = objectId,
        growthProgress = 1f,
        lastUpdatedTimestamp = 0L,
    )

    private fun extentOf(objectId: String, gridSize: Int, headroom: Float = 0.45f, sprites: (String) -> AssetManifest? = this.sprites) =
        PreviewExtent.of(gridSize, headroom, listOf(at(objectId)), sprites, manifest.tileWidth.toFloat(), step)

    @Test
    fun `without a building the lawn and the headroom are all there is, as before`() {
        val extent = PreviewExtent.of(1, 1.05f, listOf(at("plant_tulip")), sprites, manifest.tileWidth.toFloat(), step)
        assertEquals(-0.5f, extent.left, 1e-4f)
        assertEquals(0.5f, extent.right, 1e-4f)
        assertEquals(-1.05f, extent.top, 1e-4f)
        assertEquals((1 + 0.6f) / 2f, extent.bottom, 1e-4f)
    }

    @Test
    fun `the windmill's roof and turning sails are inside the picture`() {
        val mill = sprites(WindmillSails.MILL_OBJECT_ID)!!
        val sails = sprites(WindmillSails.SAILS_ASSET_ID)!!
        val extent = extentOf(WindmillSails.MILL_OBJECT_ID, gridSize = 2)
        val scale = 1f / manifest.tileWidth
        // The mill stands on the middle of its 2x2 patch, half a tile width below the far corner.
        val baseY = 0.5f
        assertTrue("roof cut off: ${extent.top}", extent.top <= mill.drawTop(baseY, scale))
        // The hub is right of the middle; the sails sweep round it, past the patch's right corner.
        val hub = mill.attachmentAt(0f, baseY, scale, flipped = false)!!
        val reach = maxOf(sails.drawnWidth(scale), sails.drawnHeight(scale)) / 2f
        assertTrue("sails cut off: ${extent.right}", extent.right >= hub.first + reach)
        assertTrue("sails cut off: ${extent.top}", extent.top <= hub.second - reach)
        assertTrue(extent.right > 1f)
        // Far taller than the fixed headroom every building used to be given.
        assertTrue(extent.top < -0.45f - 1f)
    }

    @Test
    fun `every building keeps its whole patch of lawn in the picture`() {
        for (structure in StructureData.entries) {
            val extent = extentOf(structure.objectId, maxOf(2, structure.footprint.width, structure.footprint.height))
            assertTrue(structure.name, extent.width > 0f && extent.height > 0f)
            // The patch itself is always inside.
            val n = maxOf(2, structure.footprint.width, structure.footprint.height)
            assertTrue(structure.name, extent.left <= -n / 2f && extent.right >= n / 2f)
        }
    }

    @Test
    fun `a building without a picture is given room for how tall it stands`() {
        val hut = StructureData.HUT
        val extent = extentOf(hut.objectId, gridSize = 2, headroom = 0f, sprites = { null })
        // Its middle is half a tile width below the far corner.
        assertTrue(extent.top <= 0.5f - hut.heightSteps * step)
    }
}
