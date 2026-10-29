package com.studioguu.miniaturegarden.debug

import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.WorkKind

/**
 * A garden laid out to look at the drawing faults reported from the device (debug builds only),
 * loaded with `--ez load true --es garden fb` ([ShowcaseReceiver]). Everything that was reported
 * stands in it at once, so before and after can be taken from the same spots:
 *
 *     y\x 0 1 2 3 4 5 6 7 8 9 10
 *      0  C C C . H H . W W . .     C cottage, H hut, W windmill (their lit windows at night)
 *      1  C C C . H H . W W . .
 *      2  C C C . . . . . . . .
 *      3  . . . . . . . . . . K     K carrot (grown) on a bed along the edge
 *      4  . . . . F f F F . . K     F fence, f a fence still going up between two finished ones
 *      5  . L U . . . . . . . t     L stone lantern, U sunflower and Z corn standing in front of it
 *      6  . U Z . . . . . . . e     t thirsty tomato, e hungry eggplant, a lavender, r rose
 *      7  . . . . m m m m . . a     m mint on a block of beds (the seams between beds)
 *      8  . . . . m _ _ m . . r     _ an empty bed
 *      9  . . . . . . . . . . _
 *     10  . . c c _ S K K s . .     c composted bed, S shiitake log, s thirsty strawberry
 *
 * Every other cell is lawn, the edge cells included (lawn over the garden's frame).
 */
object FeedbackGarden {
    const val SIZE = 11

    /** The player has everything: the lantern, the hut and the windmill are later than Lv.5. */
    const val PLAYER_LEVEL = 20

    private fun cells(xs: IntRange, ys: IntRange): List<GridCell> = ys.flatMap { y -> xs.map { x -> GridCell(x, y) } }

    fun objects(now: Long): List<GridObject> {
        fun at(x: Int, y: Int, objectId: String, growth: Float = 0f) = GridObject(
            instanceId = "fb_${objectId}_${x}_$y", gridX = x, gridY = y, objectId = objectId,
            growthProgress = growth, lastUpdatedTimestamp = now,
        )
        fun crop(x: Int, y: Int, species: PlantSpecies, growth: Float = 1f) = at(x, y, species.objectId, growth)

        val beds = (cells(10..10, 3..9) + cells(4..7, 7..8) + cells(2..8, 10..10)).map { cell ->
            // Two beds along the bottom have had a crop pulled up on them: compost.
            val composted = cell.y == 10 && cell.x in 2..3
            at(cell.x, cell.y, GridObject.TILLED_SOIL_OBJECT_ID, growth = if (composted) 0.5f else 0f)
        }
        val crops = listOf(
            crop(10, 3, PlantSpecies.CARROT),
            crop(10, 4, PlantSpecies.CARROT),
            crop(10, 5, PlantSpecies.TOMATO, 0.6f).copy(moistureLevel = 0.1f, status = PlantStatus.THIRSTY),
            crop(10, 6, PlantSpecies.EGGPLANT, 0.6f).copy(nutritionLevel = 0.1f, status = PlantStatus.UNNOURISHED),
            crop(10, 7, PlantSpecies.LAVENDER),
            crop(10, 8, PlantSpecies.ROSE, 0.5f),
            crop(1, 6, PlantSpecies.SUNFLOWER),
            crop(2, 5, PlantSpecies.SUNFLOWER),
            crop(2, 6, PlantSpecies.CORN),
            crop(5, 10, PlantSpecies.SHIITAKE),
            crop(6, 10, PlantSpecies.CARROT),
            crop(7, 10, PlantSpecies.CARROT),
            crop(8, 10, PlantSpecies.STRAWBERRY, 0.3f).copy(moistureLevel = 0.1f, status = PlantStatus.THIRSTY),
        ) + listOf(GridCell(4, 7), GridCell(5, 7), GridCell(6, 7), GridCell(7, 7), GridCell(4, 8), GridCell(7, 8))
            .map { crop(it.x, it.y, PlantSpecies.MINT, 0.6f) }
        val things = listOf(
            at(0, 0, StructureData.COTTAGE.objectId),
            at(4, 0, StructureData.HUT.objectId),
            at(7, 0, StructureData.WINDMILL.objectId),
            at(1, 5, "deco_lantern"),
            at(4, 4, GridObject.FENCE_OBJECT_ID),
            at(5, 4, GridObject.FENCE_OBJECT_ID).copy(workKind = WorkKind.BUILDING, workStartedAt = now, workEndsAt = now + DAY),
            at(6, 4, GridObject.FENCE_OBJECT_ID),
            at(7, 4, GridObject.FENCE_OBJECT_ID),
        )
        val placed = beds + crops + things
        val taken = placed.filter { it.objectId != GridObject.FENCE_OBJECT_ID }.flatMap { it.occupiedCells() }.toSet()
        val lawn = cells(0 until SIZE, 0 until SIZE).filter { it !in taken }.map { at(it.x, it.y, "ground_grass", growth = 1f) }
        return placed + lawn
    }

    private const val DAY = 24L * 60 * 60 * 1000
}
