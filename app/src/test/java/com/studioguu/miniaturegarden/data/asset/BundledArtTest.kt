package com.studioguu.miniaturegarden.data.asset

import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.FencePart
import com.studioguu.miniaturegarden.domain.model.GridObject
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
    fun `the waves of neighbouring tiles join when each tile runs a frame behind the one upstream`() {
        val atlas = Picture.read(File(main, "res/drawable-nodpi/tile_river_autotile.png"))
        val cell = atlas.width / RiverAutotile.COLUMNS
        val frames = RiverAutotile.frameCount(atlas.width, atlas.height)

        /** How unlike two strips of pixels are: the mean difference per colour channel. */
        fun unlike(a: IntArray, b: IntArray): Double = a.indices.sumOf { i ->
            (0..16 step 8).sumOf { shift -> kotlin.math.abs((a[i] shr shift and 0xFF) - (b[i] shr shift and 0xFF)) }
        } / (a.size * 3.0)

        // A stretch running east-west (mask 10): the east edge of one tile against the west edge
        // of its neighbour downstream, over the rows that are water.
        val water = cell * 5 / 16 until cell * 11 / 16
        fun eastEdge(frame: Int) = atlas.pixels(RiverAutotile.column(10) * cell + cell - 1, RiverAutotile.cellTop(10, frame, atlas.width, atlas.height) + water.first, 1, water.count())
        fun westEdge(frame: Int) = atlas.pixels(RiverAutotile.column(10) * cell, RiverAutotile.cellTop(10, frame, atlas.width, atlas.height) + water.first, 1, water.count())
        // And one running north-south (mask 5): south edge against the north edge of the next.
        fun southEdge(frame: Int) = atlas.pixels(RiverAutotile.column(5) * cell + water.first, RiverAutotile.cellTop(5, frame, atlas.width, atlas.height) + cell - 1, water.count(), 1)
        fun northEdge(frame: Int) = atlas.pixels(RiverAutotile.column(5) * cell + water.first, RiverAutotile.cellTop(5, frame, atlas.width, atlas.height), water.count(), 1)

        for (tick in 0 until frames) {
            // As the game plays it: the tile at (x, y) and the tiles one step east and south of it.
            val here = Math.floorMod(RiverAutotile.frameFor(tick, 3, 4), frames)
            val east = Math.floorMod(RiverAutotile.frameFor(tick, 4, 4), frames)
            val south = Math.floorMod(RiverAutotile.frameFor(tick, 3, 5), frames)

            val joinedEast = unlike(eastEdge(here), westEdge(east))
            val joinedSouth = unlike(southEdge(here), northEdge(south))
            assertTrue("tick $tick: seam to the east $joinedEast", joinedEast < 6.0)
            assertTrue("tick $tick: seam to the south $joinedSouth", joinedSouth < 6.0)
            // Played all on the same frame, the same edges would not meet.
            assertTrue(unlike(eastEdge(here), westEdge(here)) > joinedEast * 1.5)
            assertTrue(unlike(southEdge(here), northEdge(here)) > joinedSouth * 1.5)
        }
    }

    @Test
    fun `the water has depth, light on it and a bank round it`() {
        val atlas = Picture.read(File(main, "res/drawable-nodpi/tile_river_autotile.png"))
        val cell = atlas.width / RiverAutotile.COLUMNS
        // A straight stretch running east-west (mask 10), first frame.
        val left = RiverAutotile.column(10) * cell
        val top = RiverAutotile.cellTop(10, 0, atlas.width, atlas.height)
        val pixels = atlas.pixels(left, top, cell, cell)
        fun at(x: Int, y: Int) = pixels[y * cell + x]
        fun red(argb: Int) = argb shr 16 and 0xFF
        fun green(argb: Int) = argb shr 8 and 0xFF
        fun blue(argb: Int) = argb and 0xFF
        fun opaque(argb: Int) = argb ushr 24 == 0xFF

        // Down the middle column, from the turf above the stream to its centre: nothing, then
        // bank (earth: more red than blue), then water (more blue than red).
        val column = (0 until cell / 2).map { at(cell / 2, it) }
        val firstOpaque = column.indexOfFirst(::opaque)
        val firstWater = column.indexOfFirst { opaque(it) && blue(it) > red(it) + 20 }
        assertTrue("turf shows above the bank", firstOpaque > 0)
        assertTrue("a bank lies between turf and water", firstWater - firstOpaque >= 4)
        assertTrue(column.subList(firstOpaque, firstWater).all { red(it) >= blue(it) })

        // Depth: the water is paler at the bank than mid-stream, on average over the tile's width.
        fun brightness(row: Int) = (0 until cell).map { at(it, row) }.sumOf { red(it) + green(it) + blue(it) }
        assertTrue("shallows are paler than mid-stream", brightness(firstWater + 3) > brightness(cell / 2))

        // Light: some pixels of the water are almost white, most are not.
        // (White has as much red as blue, so the water is told from the bank by its rows here.)
        val water = (firstWater until cell - firstWater).flatMap { row -> (0 until cell).map { at(it, row) } }
        val bright = water.count { red(it) > 225 && green(it) > 235 }
        assertTrue("there are highlights on the water", bright > 20)
        assertTrue("but it is not washed out", bright < water.size / 4)
        // And it is painted in many tones, not three flat ones.
        assertTrue(water.map { it and 0xF0F0F0 }.toSet().size > 25)
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
        // The crops that came with the seasons are drawn in code for now (ui/render/SeasonalArt.kt).
        val drawnInCode = setOf(
            PlantSpecies.PUMPKIN, PlantSpecies.POINSETTIA,
        )
        for (species in PlantSpecies.entries - drawnInCode) {
            val bloom = "${species.objectId}_stage${species.growthStages}"
            assertTrue(bloom, manifest.assets.any { it.assetId == bloom })
        }
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
