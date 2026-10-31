package com.studioguu.miniaturegarden.data.asset

import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.WeatherOutlook
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hakusai, the nanohana, the weathervane, the app's icon and the sizes put right in #87. */
class NewArtTest {
    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val manifest = AtlasManifest.parse(File(main, "assets/atlas/assets_manifest.json").readText())
    private val tile = manifest.tileWidth.toFloat()
    private val sized = CropScale.normalized(manifest)

    private fun sprite(id: String) = sized.assets.first { it.assetId == id }
    private fun width(id: String) = sprite(id).let { it.width * it.drawScale } / tile
    private fun height(id: String) = sprite(id).let { it.height * it.drawScale } / tile

    private val icons = File(main, "res/drawable-nodpi")

    @Test
    fun `the hakusai, the nanohana and the weathervane are on sale, with pictures and pages`() {
        for (species in listOf(PlantSpecies.HAKUSAI, PlantSpecies.NANOHANA)) {
            for (id in TileSetDefinition.spriteIdsFor(species)) assertNotNull(id, manifest.assets.firstOrNull { it.assetId == id })
            assertNotNull(species.name, CollectionEntry.fromObjectId(species.objectId))
        }
        assertNotNull(manifest.assets.firstOrNull { it.assetId == WeatherOutlook.WEATHERVANE_OBJECT_ID })
        assertNotNull(CollectionEntry.fromObjectId(WeatherOutlook.WEATHERVANE_OBJECT_ID))
        for (item in listOf(GardenItem.SEED_HAKUSAI, GardenItem.SEED_NANOHANA, GardenItem.DECO_WEATHERVANE)) assertTrue(item.name, item.inShop)
    }

    @Test
    fun `no later stage of the new crops is narrower or lower than the one before, and each fits its cell`() {
        for (crop in listOf("plant_hakusai", "plant_nanohana")) {
            val stages = (1..3).map { "${crop}_stage$it" }
            for ((small, big) in stages.zipWithNext()) {
                assertTrue("$small is wider than $big", width(small) <= width(big))
                assertTrue("$small is taller than $big", height(small) <= height(big))
            }
            for (id in stages + "${crop}_withered") {
                assertTrue(id, width(id) <= 1f)
                assertTrue(id, height(id) <= CropScale.MAX_HEIGHT + 0.01f)
            }
        }
    }

    @Test
    fun `the lavender stands at 85 percent of what it did, in the same order`() {
        val heights = (1..3).map { height("plant_lavender_stage$it") }
        assertEquals(0.56f, heights[2], 0.02f)
        assertEquals(0.42f, heights[1], 0.02f)
        assertTrue(heights.toString(), heights[0] < heights[1] && heights[1] < heights[2])
        assertEquals(heights[2], height("plant_lavender_withered"), 0.02f)
    }

    @Test
    fun `the cosmos' first stage is small next to its second, and no longer pale`() {
        assertTrue(height("plant_cosmos_stage1") <= 0.3f)
        assertTrue(height("plant_cosmos_stage1") < height("plant_cosmos_stage2"))
    }

    @Test
    fun `every item has a picture for its icon, none is left to an emoji`() {
        val labels = File(main, "java/com/studioguu/miniaturegarden/ui/UiLabels.kt").readText()
        val pictures = labels.substringAfter("fun itemIconRes").substringBefore("else -> null")
        for (item in GardenItem.entries) {
            val line = Regex("GardenItem\\.${item.name} -> R\\.drawable\\.(\\w+)").find(pictures)
            assertNotNull("${item.name} has no picture", line)
            assertTrue(line!!.groupValues[1], File(icons, "${line.groupValues[1]}.png").isFile)
        }
    }

    @Test
    fun `the launcher icon is the box garden on the sky, in every density and both old shapes`() {
        val res = File(main, "res")
        for (density in listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")) {
            for (name in listOf("foreground", "background", "monochrome")) assertTrue(File(res, "mipmap-$density/ic_launcher_$name.png").isFile)
            for (name in listOf("ic_launcher", "ic_launcher_round")) assertTrue(File(res, "mipmap-$density/$name.png").isFile)
        }
        for (name in listOf("ic_launcher", "ic_launcher_round")) {
            val adaptive = File(res, "mipmap-anydpi-v26/$name.xml").readText()
            assertTrue(adaptive.contains("@mipmap/ic_launcher_foreground"))
            assertTrue(adaptive.contains("<monochrome"))
        }
        assertFalse(File(res, "drawable/ic_launcher_foreground.xml").exists())
        assertTrue(File(main, "AndroidManifest.xml").readText().contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
    }

    @Test
    fun `the store's icon is 512 square, out of the build`() {
        val icon = TestPng.read(File(main, "../../../docs/store/icon_512.png"))
        assertEquals(512, icon.width)
        assertEquals(512, icon.height)
        // Corners square: Play rounds them itself.
        assertEquals(255, icon.alpha(0, 0))
    }
}
