package com.studioguu.miniaturegarden.data.asset

import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantStatus

/** Ground tiles. Their sprites are anchored at the center of the tile's top face. */
enum class GroundTile(val assetId: String) {
    GRASS("tile_grass"),
    GRASS_DARK("tile_grass_dark"),
    SOIL("tile_soil"),
}

/** Everything that can be stored in a grid cell; [objectId] is what the database holds. */
sealed interface PlaceableDefinition {
    val objectId: String
}

/**
 * @property growthStages number of growth sprites, named `<objectId>_stage1` .. `_stage<N>`.
 */
enum class PlantSpecies(override val objectId: String, val growthStages: Int) : PlaceableDefinition {
    TULIP("plant_tulip", growthStages = 4),
}

enum class Decoration(override val objectId: String) : PlaceableDefinition {
    ROCK("deco_rock"),
}

/** Flat ground cover, drawn below plants and decorations. */
enum class GroundCover(override val objectId: String) : PlaceableDefinition {
    PEBBLE("ground_pebble"),
    GRASS_TUFT("ground_grass"),
}

object TileSetDefinition {

    /** Stage count assumed for plant ids that have no [PlantSpecies] entry yet. */
    const val DEFAULT_GROWTH_STAGES = 4

    val placeables: List<PlaceableDefinition> = PlantSpecies.entries + Decoration.entries + GroundCover.entries

    fun find(objectId: String): PlaceableDefinition? = placeables.firstOrNull { it.objectId == objectId }

    /** The two-tone checkerboard used for the lawn. */
    fun groundTileAt(gridX: Int, gridY: Int): GroundTile =
        if ((gridX + gridY) % 2 == 0) GroundTile.GRASS else GroundTile.GRASS_DARK

    /**
     * Sprite id for an object in its current state. Plants resolve to a growth-stage sprite
     * (or `<objectId>_withered`); everything else uses its object id unchanged.
     */
    fun assetIdFor(gridObject: GridObject): String {
        if (!gridObject.isPlant) return gridObject.objectId
        if (gridObject.status == PlantStatus.WITHERED) return "${gridObject.objectId}_withered"
        val stages = (find(gridObject.objectId) as? PlantSpecies)?.growthStages ?: DEFAULT_GROWTH_STAGES
        return "${gridObject.objectId}_stage${growthStage(gridObject.growthProgress, stages)}"
    }

    /** 1-based stage; the last stage is reserved for a fully grown plant (progress 1.0). */
    fun growthStage(growthProgress: Float, stages: Int): Int {
        if (stages <= 1) return 1
        val progress = growthProgress.coerceIn(0f, 1f)
        return 1 + (progress * (stages - 1)).toInt()
    }
}
