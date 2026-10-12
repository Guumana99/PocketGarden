package com.studioguu.miniaturegarden.data.asset

import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.FencePart
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.RewardType
import com.studioguu.miniaturegarden.ui.render.RiverAutotile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the artwork and texts that are actually bundled with the app, as files, against what the
 * code expects of them: a picture that is missing, the wrong size or laid out differently would
 * otherwise only show up on a device.
 */
class BundledArtTest {

    /** Unit tests run in the app module's directory; fall back for runs from the repository root. */
    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    private val manifest: AtlasManifest by lazy {
        AtlasManifest.parse(File(main, "assets/atlas/assets_manifest.json").readText())
    }

    // --- River animation ---

    @Test
    fun `the river atlas holds four frames of sixteen square tiles`() {
        val atlas = Picture.read(File(main, "res/drawable-nodpi/tile_river_autotile.png"))
        assertEquals(4, RiverAutotile.frameCount(atlas.width, atlas.height))
        assertEquals(0, atlas.width % RiverAutotile.COLUMNS)
        val cell = atlas.width / RiverAutotile.COLUMNS
        assertEquals("cells are square", cell * RiverAutotile.ROWS * 4, atlas.height)
    }

    @Test
    fun `every river tile moves from frame to frame and keeps its shape`() {
        val atlas = Picture.read(File(main, "res/drawable-nodpi/tile_river_autotile.png"))
        val cell = atlas.width / RiverAutotile.COLUMNS
        val frames = RiverAutotile.frameCount(atlas.width, atlas.height)

        for (mask in 0..15) {
            val left = RiverAutotile.column(mask) * cell
            fun pixels(frame: Int): IntArray =
                atlas.pixels(left, RiverAutotile.cellTop(mask, frame, atlas.width, atlas.height), cell, cell)

            val first = pixels(0)
            for (frame in 1 until frames) {
                val other = pixels(frame)
                // The ripples are somewhere else in each frame ...
                assertFalse("mask $mask frame $frame is identical to frame 0", first.contentEquals(other))
                // ... but the water covers the same ground, or the banks would flicker.
                val sameOutline = first.indices.all { (first[it] ushr 24 == 0) == (other[it] ushr 24 == 0) }
                assertTrue("mask $mask frame $frame changes the outline of the water", sameOutline)
            }
        }
    }

    @Test
    fun `the paving texture is bundled where the code looks for it`() {
        val paving = Picture.read(File(main, "res/drawable-nodpi/tile_path_cobblestone.png"))
        assertTrue(paving.width > 0 && paving.height > 0)
        // The old location is gone: nothing reads from assets/tiles any more.
        assertFalse(File(main, "assets/tiles").exists())
    }

    // --- Fence parts ---

    @Test
    fun `every fence part has an upright sprite standing on the middle of its tile`() {
        for (part in FencePart.entries) {
            val sprite = manifest.assets.firstOrNull { it.assetId == part.objectId }
            assertNotNull("no sprite for ${part.objectId}", sprite)
            sprite!!
            // Anchored at its horizontal middle and near its foot, so it stands straight on the
            // tile's centre whichever part is shown.
            assertEquals(part.name, 0.5f, sprite.anchorX, 1e-3f)
            assertTrue(part.name, sprite.anchorY in 0.85f..1f)
            // Narrower than a tile and a half: a part never reaches into the next tile but one.
            assertTrue(part.name, sprite.width < manifest.tileWidth * 1.5f)
        }
    }

    @Test
    fun `the parts differ in shape, the post being the narrowest`() {
        val widths = FencePart.entries.associateWith { part -> manifest.assets.first { it.assetId == part.objectId }.width }
        assertEquals(3, widths.values.toSet().size)
        assertEquals(FencePart.POST, widths.minBy { it.value }.key)
        assertEquals(FencePart.SECTION, widths.maxBy { it.value }.key)
    }

    @Test
    fun `the atlas image is the size its manifest says, with every sprite inside it`() {
        val image = Picture.read(File(main, "assets/atlas/${manifest.atlas}"))
        assertEquals(manifest.atlasWidth, image.width)
        assertEquals(manifest.atlasHeight, image.height)
        for (sprite in manifest.assets) {
            assertTrue(sprite.assetId, sprite.atlasX >= 0 && sprite.atlasX + sprite.width <= image.width)
            assertTrue(sprite.assetId, sprite.atlasY >= 0 && sprite.atlasY + sprite.height <= image.height)
        }
    }

    // --- Encyclopedia ---

    @Test
    fun `every crop page can show its plant in full bloom from the atlas`() {
        for (species in PlantSpecies.entries) {
            val bloom = "${species.objectId}_stage${species.growthStages}"
            assertTrue(bloom, manifest.assets.any { it.assetId == bloom })
        }
    }

    @Test
    fun `nothing in the app's texts speaks of completing the encyclopedia`() {
        val strings = File(main, "res/values/strings.xml").readText()

        for (gone in listOf("ぜんぶ あつめると", "コンプリート", "collection_complete")) {
            assertFalse("still mentions '$gone'", strings.contains(gone))
        }
    }

    @Test
    fun `the book has a tab label per chapter and the texts for filling a page in`() {
        val strings = File(main, "res/values/strings.xml").readText()

        for (name in listOf(
            "collection_tab_crops", "collection_tab_terrain", "collection_tab_structures",
            "collection_reveal_button", "collection_revealed_coins", "collection_daily_reward",
        )) {
            assertTrue("missing string $name", strings.contains("name=\"$name\""))
        }
        assertTrue(strings.contains("さくもつ") && strings.contains("ちけい・みち") && strings.contains("たてもの・さく"))
        assertTrue(strings.contains("✨ とうろく"))
    }

    @Test
    fun `the daily bonus for opening the book is ten coins, once`() {
        assertEquals(10, RewardType.BOOK_OPEN_AMOUNT)
        assertEquals(1, RewardType.BOOK_OPEN.dailyLimit)
    }

    @Test
    fun `every page still pays something when it is filled in`() {
        assertTrue(CollectionEntry.entries.all { it.discoveryCoins > 0 })
    }
}

/**
 * A decoded image file. The Android compile classpath has no javax.imageio or java.awt, though
 * the JVM that runs unit tests does, so the decoder is reached by name.
 */
private class Picture(private val image: Any) {
    val width: Int = image.javaClass.getMethod("getWidth").invoke(image) as Int
    val height: Int = image.javaClass.getMethod("getHeight").invoke(image) as Int

    /** ARGB pixels of the given rectangle, row by row. */
    fun pixels(left: Int, top: Int, width: Int, height: Int): IntArray {
        val getRgb = image.javaClass.getMethod(
            "getRGB",
            Int::class.java, Int::class.java, Int::class.java, Int::class.java,
            IntArray::class.java, Int::class.java, Int::class.java,
        )
        return getRgb.invoke(image, left, top, width, height, null, 0, width) as IntArray
    }

    companion object {
        fun read(file: File): Picture {
            require(file.isFile) { "$file is missing" }
            val image = Class.forName("javax.imageio.ImageIO").getMethod("read", File::class.java).invoke(null, file)
            return Picture(requireNotNull(image) { "$file could not be decoded" })
        }
    }
}
