package com.studioguu.miniaturegarden.data.asset

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lawn and tilled soil made from flat textures (#53), and nothing loose in the APK's assets. */
class GroundArtTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val tools: File = listOf(File("../tools"), File("tools")).first { it.isDirectory }
    private val manifest = AtlasManifest.parse(File(main, "assets/atlas/assets_manifest.json").readText())

    @Test
    fun `the apk's assets hold the atlas only - received pictures stay in tools`() {
        val loose = File(main, "assets").listFiles()!!.filter { it.name != "atlas" }.map { it.name }
        assertEquals(emptyList<String>(), loose)
        // #57: the lawn's slab, and the crops and icons as they came (cut out into raw_assets and res).
        for (name in listOf("tile_grass_iso.png", "tile_soil_flat.png", "pond.png", "crop_corn_stage1.png", "icon_corn.png")) {
            assertTrue(name, File(tools, "source_art/$name").isFile)
        }
        // The pond has no object yet, so it is not packed: CodeArtTest would refuse it.
        assertFalse(File(tools, "raw_assets/pond.png").exists())
        assertTrue(manifest.assets.none { it.assetId.contains("pond") })
    }

    @Test
    fun `the ground tiles stand on the middle of their top face`() {
        for (id in listOf("tile_grass", "tile_grass_dark", "floor_tilled")) {
            val tile = manifest.assets.single { it.assetId == id }
            assertEquals(id, 0.5f, tile.anchorX, 0.002f)
            // The face is a 2:1 diamond one tile wide: its middle is a quarter of a tile down.
            val faceMiddle = manifest.tileWidth / 4f / tile.height
            assertEquals(id, faceMiddle, tile.anchorY, 0.01f)
        }
    }
}
