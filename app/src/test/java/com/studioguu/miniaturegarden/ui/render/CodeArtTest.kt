package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.data.asset.AtlasManifest
import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.CreatureVisit
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What is still drawn in code (the visitors and some crops) is set up so that its artwork can be
 * packed into the atlas later without touching any drawing code: everything has the sprite id it
 * will have, and the garden looks for that sprite first.
 */
class CodeArtTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val manifest: AtlasManifest by lazy { AtlasManifest.parse(File(main, "assets/atlas/assets_manifest.json").readText()) }
    private val sprites: Set<String> by lazy { manifest.assets.map { it.assetId }.toSet() }

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
            assertEquals("${id}_body", TileSetDefinition.creatureBodyId(creature))
            assertEquals(listOf("${id}_wing_near", "${id}_wing_far"), TileSetDefinition.creaturePartIds(creature))
        }
        assertEquals(listOf("creature_squirrel_tail"), TileSetDefinition.creaturePartIds(Creature.SQUIRREL))
        // Once bundled, it holds nothing the garden would not look up.
        val file = File(main, "assets/atlas/creatures_manifest.json")
        if (file.exists()) {
            val packed = AtlasManifest.parse(file.readText()).assets.map { it.assetId }.toSet()
            assertEquals(emptySet<String>(), packed - ids.toSet())
        }
    }

    @Test
    fun `the visitors' movements are made by moving their pictures, not by drawing them anew`() {
        // A stand-in is drawn from the creature and its tile size alone, plus how far its wings are
        // open or its tail swung: the parts the artwork will have. No clock reaches the drawing.
        val source = File(main, "java/com/studioguu/miniaturegarden/ui/render/CreatureSprites.kt").readText()
        val standIns = source.substringAfter("private fun DrawScope.drawCreature(")
        val found = Regex("""private fun DrawScope\.draw(Butterfly|Ladybug|Bird|Squirrel|Frog|Dragonfly)\(([^)]*)\)""").findAll(standIns).toList()
        assertEquals(6, found.size)
        for (match in found) {
            assertFalse(match.value, match.groupValues[2].contains("clock") || match.groupValues[2].contains("Long"))
        }
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
        val creatureIds = Creature.entries.map(TileSetDefinition::assetIdFor).toSet()
        val sprayFrames = (0 until TileSetDefinition.SPRINKLER_SPRAY_FRAMES).map(TileSetDefinition::sprinklerSprayId).toSet()
        for (id in missing) {
            val species = PlantSpecies.entries.firstOrNull { id in TileSetDefinition.spriteIdsFor(it) }
            when {
                species != null -> throw AssertionError("$id: every crop has its pictures since #69")
                // Every visitor has its drawing in drawCreature, an exhaustive `when`.
                id in creatureIds -> Unit
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
