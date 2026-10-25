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
    fun `a crop has all its sprites or none, and one with none is drawn in its own shape`() {
        for (species in PlantSpecies.entries) {
            val ids = TileSetDefinition.spriteIdsFor(species)
            val packed = ids.filter { it in sprites }
            when {
                packed.size == ids.size -> Unit
                packed.isEmpty() -> assertTrue("${species.name} has no sprites and no drawing", species in CROPS_DRAWN_IN_CODE)
                else -> throw AssertionError("${species.name} has only some of its sprites: ${ids - packed.toSet()} are missing")
            }
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
                species != null -> assertTrue(id, species in CROPS_DRAWN_IN_CODE)
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
    fun `the crop drawings live in CropArt, and nothing outside it draws one by name`() {
        val render = File(main, "java/com/studioguu/miniaturegarden/ui/render")
        val cropArt = File(render, "CropArt.kt").readText()
        assertTrue(cropArt.contains("private fun DrawScope.drawPoinsettia("))
        val canvas = File(main, "java/com/studioguu/miniaturegarden/ui/components/GardenCanvas.kt").readText()
        assertTrue(canvas.contains("drawCropInCode("))
        assertFalse(Regex("\\bdraw(Cosmos|Poinsettia|Tomato|Lavender)\\(").containsMatchIn(canvas))
    }
}
