package com.studioguu.miniaturegarden.data.asset

import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.FencePart
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
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
    fun `the ground pictures are bundled where the code looks for them, one cell square (#74)`() {
        for (name in listOf("floor_cobblestone", "floor_wood", "ground_pebble", "ground_flowers")) {
            val picture = Picture.read(File(main, "res/drawable-nodpi/$name.png"))
            assertEquals(name, 512, picture.width)
            assertEquals(name, 512, picture.height)
        }
        // The illustrated paving it replaced is no longer bundled.
        assertFalse(File(main, "res/drawable-nodpi/tile_path_cobblestone.png").exists())
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
    fun `each atlas page is the size its manifest says, with every sprite inside its page`() {
        val images = manifest.pages.map { page -> Picture.read(File(main, "assets/atlas/${page.file}")).also {
            assertEquals(page.file, page.width, it.width)
            assertEquals(page.file, page.height, it.height)
        } }
        for (sprite in manifest.assets) {
            val image = images[sprite.page]
            assertTrue(sprite.assetId, sprite.atlasX >= 0 && sprite.atlasX + sprite.width <= image.width)
            assertTrue(sprite.assetId, sprite.atlasY >= 0 && sprite.atlasY + sprite.height <= image.height)
        }
    }

    @Test
    fun `the crops are on pages of their own, and only the pages in a manifest are bundled (#73)`() {
        for (sprite in manifest.assets) {
            val sheet = manifest.pages[sprite.page].file.substringBeforeLast('_')
            assertEquals(sprite.assetId, if (sprite.assetId.startsWith("plant_")) "crops" else "world", sheet)
        }
        val creatures = AtlasManifest.parse(File(main, "assets/atlas/creatures_manifest.json").readText())
        val listed = (manifest.pages + creatures.pages).map { it.file }.toSet()
        val bundled = File(main, "assets/atlas").listFiles()!!.filter { it.extension == "png" }.map { it.name }.toSet()
        assertEquals(listed, bundled)
    }

    // --- Encyclopedia ---

    private fun onSale(species: PlantSpecies) = GardenItem.entries.single { it.objectId == species.objectId }.inShop

    @Test
    fun `every crop page can show its plant in full bloom from the atlas`() {
        // Every crop on sale has its pictures since #69 (the poinsettia was the last drawn in code).
        for (species in PlantSpecies.entries.filter(::onSale)) {
            val bloom = "${species.objectId}_stage${species.growthStages}"
            assertTrue(bloom, manifest.assets.any { it.assetId == bloom })
        }
    }

    @Test
    fun `poinsettia has its pictures in three stages and its icon (#69)`() {
        assertEquals(3, PlantSpecies.POINSETTIA.growthStages)
        val ids = TileSetDefinition.spriteIdsFor(PlantSpecies.POINSETTIA)
        assertEquals(listOf("plant_poinsettia_stage1", "plant_poinsettia_stage2", "plant_poinsettia_stage3", "plant_poinsettia_withered"), ids)
        for (id in ids) assertTrue(id, manifest.assets.any { it.assetId == id })
        assertFalse(manifest.assets.any { it.assetId == "plant_poinsettia_stage4" })
        val stages = ids.dropLast(1).map { id -> manifest.assets.single { it.assetId == id } }
        stages.zipWithNext { a, b -> assertTrue("${a.assetId} -> ${b.assetId}", a.width < b.width && a.height < b.height) }
        assertTrue(File(main, "res/drawable-nodpi/icon_poinsettia.png").isFile)
    }

    @Test
    fun `every crop has an icon picture, cut out of its background (#72)`() {
        // Every crop on sale, that is: the hakusai and the nanohana wait for theirs.
        for (species in PlantSpecies.entries.filter(::onSale)) {
            val file = File(main, "res/drawable-nodpi/icon_${species.objectId.removePrefix("plant_")}.png")
            assertTrue("${file.name} is missing", file.isFile)
            val icon = TestPng.read(file)
            assertEquals(file.name, 256, icon.width)
            assertEquals(file.name, 256, icon.height)
            for ((x, y) in listOf(0 to 0, 255 to 0, 0 to 255, 255 to 255)) assertEquals("${file.name} corner", 0, icon.alpha(x, y))
        }
        // The daikon's white root survives the cut (#72: the usual cut left about 470 of these).
        val daikon = TestPng.read(File(main, "res/drawable-nodpi/icon_daikon.png"))
        val white = (0 until 256).sumOf { y -> (0 until 256).count { x -> daikon.alpha(x, y) > 200 && daikon.luminance(x, y) > 200 } }
        assertTrue("$white", white > 1200)
    }

    @Test
    fun `the buildings, decorations and ground have icon pictures, cut out of their background (#74)`() {
        val labels = File(main, "java/com/studioguu/miniaturegarden/ui/UiLabels.kt").readText()
        for (id in listOf(
            "struct_hut", "struct_well", "deco_rock", "deco_bench", "deco_lantern", "deco_sprinkler",
            "ground_pebble", "ground_flowers", "floor_wood", "floor_cobblestone",
        )) {
            val file = File(main, "res/drawable-nodpi/icon_$id.png")
            assertTrue("${file.name} is missing", file.isFile)
            val icon = TestPng.read(file)
            assertEquals(file.name, 256, icon.width)
            for ((x, y) in listOf(0 to 0, 255 to 0, 0 to 255, 255 to 255)) assertEquals("${file.name} corner", 0, icon.alpha(x, y))
            assertTrue(id, labels.contains("-> R.drawable.icon_$id\n"))
        }
    }

    @Test
    fun `shiitake is a log as long at every stage, within its cell, standing on the foot of the log (#68)`() {
        val ids = TileSetDefinition.spriteIdsFor(PlantSpecies.SHIITAKE)
        assertEquals(listOf("plant_shiitake_stage1", "plant_shiitake_stage2", "plant_shiitake_stage3", "plant_shiitake_withered"), ids)
        val normalized = CropScale.normalized(manifest)
        val widths = ids.map { id -> normalized.assets.single { it.assetId == id }.let { it.width * it.drawScale / manifest.tileWidth } }
        // The log is half a cell (#68: at 0.85 the one in front hid half of the one behind it; 0.6
        // until the device feedback after #75).
        for (w in widths) assertTrue("$w", w in 0.49f..0.55f)
        assertTrue("$widths", widths.max() - widths.min() < 0.02f)
        for (id in ids) assertTrue(id, manifest.assets.single { it.assetId == id }.anchorY >= 0.99f)
        assertTrue(File(main, "res/drawable-nodpi/icon_shiitake.png").isFile)
    }

    @Test
    fun `lavender has its pictures in three stages and its icon (#67)`() {
        assertEquals(3, PlantSpecies.LAVENDER.growthStages)
        val ids = TileSetDefinition.spriteIdsFor(PlantSpecies.LAVENDER)
        assertEquals(listOf("plant_lavender_stage1", "plant_lavender_stage2", "plant_lavender_stage3", "plant_lavender_withered"), ids)
        for (id in ids) assertTrue(id, manifest.assets.any { it.assetId == id })
        assertFalse(manifest.assets.any { it.assetId == "plant_lavender_stage4" })
        val stages = ids.dropLast(1).map { id -> manifest.assets.single { it.assetId == id } }
        stages.zipWithNext { a, b -> assertTrue("${a.assetId} -> ${b.assetId}", a.width < b.width && a.height < b.height) }
        assertTrue(File(main, "res/drawable-nodpi/icon_lavender.png").isFile)
    }

    @Test
    fun `watermelon has its pictures in three stages, a mound no wider than its cell (#65)`() {
        assertEquals(3, PlantSpecies.WATERMELON.growthStages)
        val ids = TileSetDefinition.spriteIdsFor(PlantSpecies.WATERMELON)
        assertEquals(listOf("plant_watermelon_stage1", "plant_watermelon_stage2", "plant_watermelon_stage3", "plant_watermelon_withered"), ids)
        for (id in ids) assertTrue(id, manifest.assets.any { it.assetId == id })
        assertFalse(manifest.assets.any { it.assetId == "plant_watermelon_stage4" })
        val sprites = ids.map { id -> manifest.assets.single { it.assetId == id } }
        sprites.dropLast(1).zipWithNext { a, b -> assertTrue("${a.assetId} -> ${b.assetId}", a.width < b.width && a.height < b.height) }
        // The full plant and the withered one lie within their own cell: no wider than 0.86 of a tile.
        for (sprite in sprites.takeLast(2)) {
            assertTrue(sprite.assetId, sprite.width * sprite.drawScale <= manifest.tileWidth * 0.86f)
        }
        assertTrue(File(main, "res/drawable-nodpi/icon_watermelon.png").isFile)
    }

    @Test
    fun `snowdrop has its pictures in three stages and its icon, standing on the foot of its leaves (#64, #66)`() {
        assertEquals(3, PlantSpecies.SNOWDROP.growthStages)
        val ids = TileSetDefinition.spriteIdsFor(PlantSpecies.SNOWDROP)
        assertEquals(listOf("plant_snowdrop_stage1", "plant_snowdrop_stage2", "plant_snowdrop_stage3", "plant_snowdrop_withered"), ids)
        for (id in ids) assertTrue(id, manifest.assets.any { it.assetId == id })
        assertFalse(manifest.assets.any { it.assetId == "plant_snowdrop_stage4" })
        val stages = ids.dropLast(1).map { id -> manifest.assets.single { it.assetId == id } }
        stages.zipWithNext { a, b -> assertTrue("${a.assetId} -> ${b.assetId}", a.width < b.width && a.height < b.height) }
        // #66: its icon, which first came as a copy of the withered picture, is the right one now.
        assertTrue(File(main, "res/drawable-nodpi/icon_snowdrop.png").isFile)
    }

    @Test
    fun `cosmos has its pictures in three stages, and one saved at its old fourth stage shows the third (#63)`() {
        assertEquals(3, PlantSpecies.COSMOS.growthStages)
        val ids = TileSetDefinition.spriteIdsFor(PlantSpecies.COSMOS)
        assertEquals(listOf("plant_cosmos_stage1", "plant_cosmos_stage2", "plant_cosmos_stage3", "plant_cosmos_withered"), ids)
        for (id in ids) assertTrue(id, manifest.assets.any { it.assetId == id })
        assertFalse(manifest.assets.any { it.assetId == "plant_cosmos_stage4" })
        fun cosmos(progress: Float) =
            GridObject("c", 1, 1, objectId = PlantSpecies.COSMOS.objectId, growthProgress = progress, lastUpdatedTimestamp = 0L)
        assertEquals("plant_cosmos_stage2", TileSetDefinition.assetIdFor(cosmos(0.9f)))
        assertEquals("plant_cosmos_stage3", TileSetDefinition.assetIdFor(cosmos(1f)))
        assertTrue(File(main, "res/drawable-nodpi/icon_cosmos.png").isFile)
        // Each stage stands taller and wider than the one before (#62: mint's second was wider than its third).
        val stages = ids.dropLast(1).map { id -> manifest.assets.single { it.assetId == id } }
        stages.zipWithNext { a, b ->
            assertTrue("${a.assetId} -> ${b.assetId}", a.width < b.width && a.height < b.height)
        }
    }

    @Test
    fun `mint has its pictures in three stages, and a mint saved at its old fourth stage shows the third (#62)`() {
        assertEquals(3, PlantSpecies.MINT.growthStages)
        val ids = TileSetDefinition.spriteIdsFor(PlantSpecies.MINT)
        assertEquals(listOf("plant_mint_stage1", "plant_mint_stage2", "plant_mint_stage3", "plant_mint_withered"), ids)
        for (id in ids) assertTrue(id, manifest.assets.any { it.assetId == id })
        assertFalse(manifest.assets.any { it.assetId == "plant_mint_stage4" })
        // Only the growth is saved, never a stage: a mint that was at stage 4 of 4 (three quarters
        // grown and on) is drawn at the last of three now, and nothing asks for a fourth.
        fun mint(progress: Float, status: PlantStatus = PlantStatus.HEALTHY) =
            GridObject("m", 1, 1, objectId = PlantSpecies.MINT.objectId, growthProgress = progress, status = status, lastUpdatedTimestamp = 0L)
        assertEquals("plant_mint_stage1", TileSetDefinition.assetIdFor(mint(0f)))
        assertEquals("plant_mint_stage2", TileSetDefinition.assetIdFor(mint(0.5f)))
        for (old in listOf(0.75f, 0.9f, 0.999f)) assertEquals("plant_mint_stage2", TileSetDefinition.assetIdFor(mint(old)))
        assertEquals("plant_mint_stage3", TileSetDefinition.assetIdFor(mint(1f)))
        assertEquals("plant_mint_stage3", TileSetDefinition.assetIdFor(mint(1.3f)))
        assertEquals("plant_mint_withered", TileSetDefinition.assetIdFor(mint(0.9f, PlantStatus.WITHERED)))
        assertTrue(File(main, "res/drawable-nodpi/icon_mint.png").isFile)
    }

    @Test
    fun `the four crops with artwork have a sprite for every stage and for withered, standing on their stem`() {
        for (species in listOf(PlantSpecies.TOMATO, PlantSpecies.STRAWBERRY, PlantSpecies.PAPRIKA, PlantSpecies.DAIKON)) {
            assertEquals(species.name, 3, species.growthStages)
            val ids = (1..species.growthStages).map { "${species.objectId}_stage$it" } + "${species.objectId}_withered"
            for (id in ids) {
                val sprite = manifest.assets.firstOrNull { it.assetId == id }
                assertNotNull("no sprite for $id", sprite)
                // The clump of earth under the plant is taken out by the packer (#47): it stands on
                // the base of its stem, near its middle, at the sprite's foot (or a little above it,
                // where a withered leaf droops lower than the stem).
                assertTrue(id, sprite!!.anchorY in 0.85f..1f)
                assertTrue(id, sprite.anchorX in 0.3f..0.7f)
            }
        }
        // Their pictures went into the atlas; the files the art came in are not left in the app.
        val drawable = File(main, "res/drawable")
        assertTrue(drawable.listFiles()!!.none { it.name.startsWith("crop_") || it.name.startsWith("rop_") })
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
            "collection_reveal_button",
        )) {
            assertTrue("missing string $name", strings.contains("name=\"$name\""))
        }
        // Short enough for four bookmarks side by side; "たてもの・さく" used to be cut off.
        assertTrue(strings.contains("<string name=\"collection_tab_crops\">さくもつ</string>"))
        assertTrue(strings.contains("<string name=\"collection_tab_terrain\">ちけい</string>"))
        assertTrue(strings.contains("<string name=\"collection_tab_structures\">たてもの</string>"))
        // No sparkle in front of it any more (#55).
        assertTrue(strings.contains("<string name=\"collection_reveal_button\">登録する</string>"))
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
