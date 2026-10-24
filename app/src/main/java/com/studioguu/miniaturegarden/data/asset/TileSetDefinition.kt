package com.studioguu.miniaturegarden.data.asset

import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.FencePart
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.TileType

/** Ground tiles. Their sprites are anchored at the center of the tile's top face. */
enum class GroundTile(val assetId: String) {
    GRASS("tile_grass"),
    GRASS_DARK("tile_grass_dark"),
    SOIL("tile_soil"),
    WATER("tile_water"),
}

enum class Decoration(val objectId: String) {
    ROCK("deco_rock"),
    BENCH("deco_bench"),
    LANTERN("deco_lantern"),
    SPRINKLER("deco_sprinkler"),
    POND("deco_pond"),
}

/** Flat ground cover, drawn below plants and decorations. */
enum class GroundCover(val objectId: String) {
    PEBBLE("ground_pebble"),
    GRASS_TUFT("ground_grass"),
    FLOWERS("ground_flowers"),
}

/**
 * Maps what the garden shows to the sprites that draw it: every sprite id the game looks up is
 * named here, so artwork is added by packing a picture under the id given here (ASSETS.md) and
 * nothing else. Whatever has no sprite yet is drawn in code instead. Plants themselves are
 * [PlantSpecies].
 */
object TileSetDefinition {

    /** Every object id that can be stored in a grid cell. */
    val objectIds: List<String> =
        PlantSpecies.entries.map { it.objectId } + Decoration.entries.map { it.objectId } +
            GroundCover.entries.map { it.objectId } + StructureData.entries.map { it.objectId } +
            GridObject.RIVER_OBJECT_ID + GridObject.COBBLESTONE_OBJECT_ID + GridObject.WOOD_DECK_OBJECT_ID + GridObject.TILLED_SOIL_OBJECT_ID + GridObject.GREENHOUSE_OBJECT_ID +
            FencePart.entries.map { it.objectId } + GridObject.STARTER_CRATE_OBJECT_ID

    /**
     * Ground sprite for a cell: water for the river, grass for the lawn. The lawn used to be a
     * two-tone checkerboard; since #47 it is one shade, laid as one meadow (the grid lines show
     * the cells in edit mode), and [GroundTile.GRASS_DARK] is not used.
     */
    @Suppress("UNUSED_PARAMETER")
    fun groundTileFor(type: TileType, gridX: Int, gridY: Int): GroundTile = when (type) {
        TileType.RIVER -> GroundTile.WATER
        TileType.GRASS -> GroundTile.GRASS
    }

    /**
     * Sprite id for an object in its current state. Plants resolve to a growth-stage sprite
     * (or `<objectId>_withered`); everything else uses its object id unchanged.
     */
    fun assetIdFor(gridObject: GridObject): String {
        if (!gridObject.isPlant) return gridObject.objectId
        val species = PlantSpecies.forObjectId(gridObject.objectId)
        if (gridObject.status == PlantStatus.WITHERED && species.canWither) return witheredId(gridObject.objectId)
        val stage = growthStage(gridObject.growthProgress, species.growthStages)
        val plain = stageId(gridObject.objectId, stage)
        if (stage < species.growthStages) return plain
        val colour = bloomColourOf(gridObject.objectId, gridObject.instanceId) ?: return plain
        return "${plain}_$colour"
    }

    private fun stageId(objectId: String, stage: Int) = "${objectId}_stage$stage"

    private fun witheredId(objectId: String) = "${objectId}_withered"

    /**
     * The flowers a crop blooms in besides its plain last stage (#58): each plant takes one, or
     * the plain one, for good, by its instance id ([PlantSpecies.bloomColourOf]). Drawn as
     * `<objectId>_stage<N>_<colour>`.
     */
    val BLOOM_COLOURS: Map<String, List<String>> =
        PlantSpecies.entries.filter { it.bloomColours.size > 1 }.associate { it.objectId to it.bloomColours.drop(1) }

    /** The colour [instanceId] of [objectId] blooms in, or null for its plain last stage (red for the tulip). */
    fun bloomColourOf(objectId: String, instanceId: String): String? {
        val species = PlantSpecies.fromObjectId(objectId) ?: return null
        return species.bloomColourOf(instanceId)?.takeIf { it != species.bloomColours.first() }
    }

    /**
     * An instance id, made from [base], under which [objectId] blooms in [colour]; in its plain
     * colour when [colour] is null or not one of its own. A picture of a crop (the book's) is
     * drawn as a plant with this id, so that it shows the colour the player grew rather than
     * whichever one a made-up id happens to pick (#59).
     */
    fun instanceIdBlooming(objectId: String, colour: String?, base: String): String {
        val species = PlantSpecies.fromObjectId(objectId) ?: return base
        val wanted = colour?.takeIf { it in species.bloomColours } ?: species.bloomColours.firstOrNull() ?: return base
        return (0 until 1_000).asSequence().map { "${base}_$it" }.firstOrNull { species.bloomColourOf(it) == wanted } ?: base
    }

    /**
     * Every sprite a crop is drawn with: `<objectId>_stage1` to `_stage<N>`, the colours of its
     * last stage, then `<objectId>_withered` unless it never withers.
     */
    fun spriteIdsFor(species: PlantSpecies): List<String> =
        (1..species.growthStages).map { stageId(species.objectId, it) } +
            BLOOM_COLOURS[species.objectId].orEmpty().map { "${stageId(species.objectId, species.growthStages)}_$it" } +
            listOfNotNull(witheredId(species.objectId).takeIf { species.canWither })

    /** The sprite of a visitor: `creature_<id>`, standing with its anchor at its feet. */
    fun assetIdFor(creature: Creature): String = "creature_${creature.id}"

    /**
     * Things drawn from a picture of their own in `res/drawable` (through [TerrainTextures]),
     * not from the atlas: they are never looked up there.
     */
    val DRAWABLE_OBJECT_IDS: Set<String> = setOf(GridObject.POND_OBJECT_ID)

    /** The windmill's sails: a sprite of their own, hung on the mill's hub and turned. */
    const val WINDMILL_SAILS = "struct_windmill_blades"

    /** Frames of a running sprinkler's water, drawn over the sprinkler one after another. */
    const val SPRINKLER_SPRAY_FRAMES = 8

    /** The sprite of one frame of the sprinkler's water, 0 to [SPRINKLER_SPRAY_FRAMES] - 1. */
    fun sprinklerSprayId(frame: Int): String = "deco_sprinkler_spray_$frame"

    /**
     * Every sprite id the garden may look up: the ground tiles, each crop's stages, every other
     * thing that stands in a cell, the windmill's sails and the visitors, whether the atlas has
     * it yet or not. Against the atlas it gives the artwork still missing; anything in the atlas
     * that is not here would never be drawn.
     */
    val expectedSpriteIds: List<String> =
        GroundTile.entries.map { it.assetId } +
            PlantSpecies.entries.flatMap(::spriteIdsFor) +
            (objectIds - PlantSpecies.entries.map { it.objectId }.toSet() - DRAWABLE_OBJECT_IDS) +
            WINDMILL_SAILS +
            (0 until SPRINKLER_SPRAY_FRAMES).map(::sprinklerSprayId) +
            Creature.entries.map(::assetIdFor)

    /** 1-based stage; the last stage is reserved for a fully grown plant (progress 1.0). */
    fun growthStage(growthProgress: Float, stages: Int): Int {
        if (stages <= 1) return 1
        val progress = growthProgress.coerceIn(0f, 1f)
        return 1 + (progress * (stages - 1)).toInt()
    }
}
