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
        if (gridObject.status == PlantStatus.WITHERED) return witheredId(gridObject.objectId)
        val stages = PlantSpecies.forObjectId(gridObject.objectId).growthStages
        return stageId(gridObject.objectId, growthStage(gridObject.growthProgress, stages))
    }

    private fun stageId(objectId: String, stage: Int) = "${objectId}_stage$stage"

    private fun witheredId(objectId: String) = "${objectId}_withered"

    /** Every sprite a crop is drawn with: `<objectId>_stage1` to `_stage<N>`, then `<objectId>_withered`. */
    fun spriteIdsFor(species: PlantSpecies): List<String> =
        (1..species.growthStages).map { stageId(species.objectId, it) } + witheredId(species.objectId)

    /** The sprite of a visitor: `creature_<id>`, standing with its anchor at its feet. */
    fun assetIdFor(creature: Creature): String = "creature_${creature.id}"

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
            (objectIds - PlantSpecies.entries.map { it.objectId }.toSet()) +
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
