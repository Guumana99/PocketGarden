package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.data.asset.AtlasManifest
import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.CreatureVisit
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What was drawn in code until its artwork came (the crops, then the visitors in #71) is set up so that its artwork can be
 * packed into the atlas later without touching any drawing code: everything has the sprite id it
 * will have, and the garden looks for that sprite first.
 */
class CodeArtTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val manifest: AtlasManifest by lazy { AtlasManifest.parse(File(main, "assets/atlas/assets_manifest.json").readText()) }
    private val sprites: Set<String> by lazy { manifest.assets.map { it.assetId }.toSet() }
    private val creatureManifest: AtlasManifest by lazy { AtlasManifest.parse(File(main, "assets/atlas/creatures_manifest.json").readText()) }

    // --- Visitors ---

    @Test
    fun `every visitor has a sprite id of its own, which nothing else uses`() {
        val ids = Creature.entries.map(TileSetDefinition::assetIdFor)
        assertEquals(Creature.entries.map { "creature_${it.id}" }, ids)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it in TileSetDefinition.objectIds })
    }

    @Test
    fun `the visitors' pictures go in an atlas of their own, named by one rule`() {
        // The main atlas is as large as an image may be; the visitors are not packed into it.
        assertTrue(sprites.none { it.startsWith("creature_") })
        assertTrue(TileSetDefinition.expectedSpriteIds.none { it.startsWith("creature_") })
        val ids = TileSetDefinition.expectedCreatureSpriteIds
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.containsAll(Creature.entries.map(TileSetDefinition::assetIdFor)))
        for (creature in listOf(Creature.BUTTERFLY, Creature.DRAGONFLY)) {
            val id = TileSetDefinition.assetIdFor(creature)
            assertEquals("${id}_body", TileSetDefinition.wingedBodyId(creature))
            assertEquals(listOf("${id}_wing_left", "${id}_wing_right"), TileSetDefinition.wingIds(creature))
        }
        assertEquals("creature_bird_fly_body", TileSetDefinition.wingedBodyId(Creature.BIRD))
        assertEquals(listOf("creature_bird_fly_wing_left", "creature_bird_fly_wing_right"), TileSetDefinition.wingIds(Creature.BIRD))
        for (creature in listOf(Creature.SQUIRREL, Creature.FROG, Creature.LADYBUG)) assertTrue(TileSetDefinition.wingIds(creature).isEmpty())
        assertEquals("creature_squirrel_run", TileSetDefinition.SQUIRREL_RUNNING)
    }

    @Test
    fun `every visitor's picture is in its atlas, and nothing the garden would not look up (#71)`() {
        // #71: the Recraft pictures came for all six; the stand-ins drawn in code are gone.
        val packed = creatureManifest.assets.map { it.assetId }.toSet()
        assertEquals(TileSetDefinition.expectedCreatureSpriteIds.toSet(), packed)
    }

    @Test
    fun `the creature atlas is small, and drawn as large as the garden's closest zoom shows it`() {
        val image = File(main, "assets/atlas/${creatureManifest.atlas}")
        assertEquals("creatures.png", image.name)
        assertTrue("${image.length()} bytes", image.length() < 700 * 1024)
        // A tile is 128 dp, 336 px on a 420 dpi screen, and 2.5 times that at the closest zoom.
        assertTrue(creatureManifest.tileWidth >= 128 * 2.625 * GardenCamera.MAX_SCALE)
    }

    @Test
    fun `each wing hangs on its body by its root`() {
        for (creature in Creature.entries) {
            for (id in TileSetDefinition.wingIds(creature)) {
                val wing = creatureManifest.assets.single { it.assetId == id }
                assertNotNull("$id has no attachment point", wing.attachX)
                assertNotNull(wing.attachY)
            }
        }
    }

    @Test
    fun `the visitors are about the size they should be`() {
        fun widthOf(id: String) = creatureManifest.assets.single { it.assetId == id }.width.toFloat() / creatureManifest.tileWidth
        fun heightOf(id: String) = creatureManifest.assets.single { it.assetId == id }.height.toFloat() / creatureManifest.tileWidth
        assertEquals(0.2f, heightOf("creature_squirrel"), 0.01f)
        for (id in listOf("creature_bird", "creature_frog", "creature_butterfly", "creature_dragonfly")) assertEquals(id, 0.2f, widthOf(id), 0.01f)
        assertTrue(widthOf("creature_ladybug") in 0.06f..0.08f)
    }

    @Test
    fun `the butterfly and the dragonfly fly, the rest keep to the ground`() {
        assertEquals(setOf(Creature.BUTTERFLY, Creature.DRAGONFLY), Creature.entries.filter { it.flies() }.toSet())
        for (creature in Creature.entries) assertEquals(creature.name, creature.flies(), creature.hoverHeight > 0f)
    }

    @Test
    fun `the garden and the nature notes draw visitors through the atlas`() {
        for (path in listOf("ui/components/GardenCanvas.kt", "ui/collection/NatureNotesChapter.kt")) {
            val source = File(main, "java/com/studioguu/miniaturegarden/$path").readText()
            assertTrue(path, source.contains("drawVisitor("))
            // Not straight to the code drawing, which would skip artwork once it is there.
            assertFalse(path, Regex("""\bdrawCreature\(""").containsMatchIn(source))
        }
    }

    /** PHILOSOPHY 12: visitors come by themselves; they are not caught, chased off, or paid for. */
    @Test
    fun `a visitor carries no reward and a tap in the garden never reaches one`() {
        val fields = CreatureVisit::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(fields.toString(), fields.none { "coin" in it || "xp" in it || "reward" in it })
        val viewModel = File(main, "java/com/studioguu/miniaturegarden/ui/GardenViewModel.kt").readText()
        val start = viewModel.indexOf("fun onTileTapped(")
        val end = viewModel.indexOf("\n    fun ", start + 1)
        val onTap = viewModel.substring(start, end)
        assertFalse(onTap, Regex("visit|creature", RegexOption.IGNORE_CASE).containsMatchIn(onTap))
    }

    // --- Crops ---

    @Test
    fun `every crop has all its sprites`() {
        // #69: the poinsettia was the last crop drawn in code; there are no drawn stand-ins any more.
        for (species in PlantSpecies.entries) {
            val missing = TileSetDefinition.spriteIdsFor(species).filter { it !in sprites }
            assertTrue("${species.name} is missing $missing", missing.isEmpty())
        }
    }

    // --- Sprite ids ---

    @Test
    fun `every sprite in the atlas is one the game looks up`() {
        // A picture packed under a misspelt id would never be drawn.
        assertEquals(emptySet<String>(), sprites - TileSetDefinition.expectedSpriteIds.toSet())
        assertEquals(TileSetDefinition.expectedSpriteIds.size, TileSetDefinition.expectedSpriteIds.toSet().size)
    }

    @Test
    fun `every sprite still missing is drawn in code meanwhile`() {
        val missing = TileSetDefinition.expectedSpriteIds.filter { it !in sprites }
        val sprayFrames = (0 until TileSetDefinition.SPRINKLER_SPRAY_FRAMES).map(TileSetDefinition::sprinklerSprayId).toSet()
        for (id in missing) {
            val species = PlantSpecies.entries.firstOrNull { id in TileSetDefinition.spriteIdsFor(it) }
            when {
                species != null -> throw AssertionError("$id: every crop has its pictures since #69")
                // A running sprinkler's water is drawn in code from SprinklerSpray, frame by frame.
                id in sprayFrames -> Unit
                // Everything else in a cell has a placeholder shape (drawPlaceholder, drawStructure).
                else -> assertTrue(id, id in TileSetDefinition.objectIds || id.startsWith("tile_"))
            }
        }
        // The crop names follow the one rule the garden draws them by.
        val tomato = TileSetDefinition.spriteIdsFor(PlantSpecies.TOMATO)
        assertEquals(listOf("plant_tomato_stage1", "plant_tomato_stage2", "plant_tomato_stage3", "plant_tomato_withered"), tomato)
    }

    @Test
    fun `no crop is drawn in code any more`() {
        val render = File(main, "java/com/studioguu/miniaturegarden/ui/render")
        assertFalse(File(render, "CropArt.kt").exists())
        val canvas = File(main, "java/com/studioguu/miniaturegarden/ui/components/GardenCanvas.kt").readText()
        assertFalse(canvas.contains("drawCropInCode("))
        assertFalse(Regex("\\bdraw(Cosmos|Poinsettia|Tomato|Strawberry|Lavender)\\(").containsMatchIn(canvas))
    }
}
