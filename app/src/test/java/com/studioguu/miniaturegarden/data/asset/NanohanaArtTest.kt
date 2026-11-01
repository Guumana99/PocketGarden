package com.studioguu.miniaturegarden.data.asset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The nanohana's spires of flowers (#93), and the icons that come out of the same run of the tools. */
class NanohanaArtTest {
    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val tools: File = listOf(File("../tools"), File("tools")).first { it.isDirectory }
    private val manifest = AtlasManifest.parse(File(main, "assets/atlas/assets_manifest.json").readText())

    private fun sprite(name: String) = manifest.assets.first { it.assetId == "plant_nanohana_$name" }
    private val tile = manifest.tileWidth.toFloat()

    @Test
    fun `grown, it fits its cell, and is the largest of its stages`() {
        val grown = sprite("stage3")
        assertTrue(grown.width / tile <= 0.85f)
        assertTrue(grown.height / tile <= 0.85f)
        val before = sprite("stage2")
        assertTrue(grown.width > before.width && grown.height > before.height)
    }

    @Test
    fun `in bud, it stands well between the seedling and the grown plant`() {
        val seedling = sprite("stage1")
        val budding = sprite("stage2")
        val grown = sprite("stage3")
        assertTrue(budding.width > seedling.width * 1.3f && budding.height > seedling.height * 1.3f)
        assertTrue(budding.width < grown.width * 0.8f && budding.height < grown.height * 0.8f)
    }

    @Test
    fun `withered, it is about the size it was grown`() {
        val grown = sprite("stage3")
        val dead = sprite("withered")
        assertEquals(grown.width.toFloat(), dead.width.toFloat(), grown.width * 0.05f)
        assertEquals(grown.height.toFloat(), dead.height.toFloat(), grown.height * 0.05f)
    }

    @Test
    fun `the greenhouse's icon is left to its own tool, and the other does not draw over it`() {
        assertFalse(File(tools, "prepare_drawable_art.py").readText().contains("\"floor_greenhouse\": RAW"))
        assertTrue(File(tools, "prepare_greenhouse_art.py").readText().contains("icon_floor_greenhouse.png"))
    }
}
