package com.studioguu.miniaturegarden.data.asset

import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus

/** Ground tiles. Their sprites are anchored at the center of the tile's top face. */
enum class GroundTile(val assetId: String) {
    GRASS("tile_grass"),
    GRASS_DARK("tile_grass_dark"),
    SOIL("tile_soil"),
}

enum class Decoration(val objectId: String) {
    ROCK("deco_rock"),
}

/** Flat ground cover, drawn below plants and decorations. */
enum class GroundCover(val objectId: String) {
    PEBBLE("ground_pebble"),
    GRASS_TUFT("ground_grass"),
}

/** Maps what is on the grid to the sprites that draw it. Plants themselves are [PlantSpecies]. */
object TileSetDefinition {

    /** Every object id that can be stored in a grid cell. */
    val objectIds: List<String> =
        PlantSpecies.entries.map { it.objectId } + Decoration.entries.map { it.objectId } +
            GroundCover.entries.map { it.objectId }

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
        val stages = PlantSpecies.forObjectId(gridObject.objectId).growthStages
        return "${gridObject.objectId}_stage${growthStage(gridObject.growthProgress, stages)}"
    }

    /** 1-based stage; the last stage is reserved for a fully grown plant (progress 1.0). */
    fun growthStage(growthProgress: Float, stages: Int): Int {
        if (stages <= 1) return 1
        val progress = growthProgress.coerceIn(0f, 1f)
        return 1 + (progress * (stages - 1)).toInt()
    }
}
