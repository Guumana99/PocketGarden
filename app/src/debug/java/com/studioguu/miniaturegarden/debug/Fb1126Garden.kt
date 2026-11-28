package com.studioguu.miniaturegarden.debug

import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.WorkKind

/**
 * The strawberry field of the device feedback of 11/26-27 (`--ez load true --es garden fb1126`):
 * ripe strawberries on a block of beds with weak ones (no food) in front of some, whose bubbles
 * hang over the fruit behind, and one ripe plant at the garden's right corner.
 */
object Fb1126Garden {

    /** Plants with no food left: each just in front of a ripe one. */
    val WEAK = listOf(GridCell(5, 6), GridCell(7, 5))

    /** The ripe plant at the right edge of the garden. */
    val RIGHT_EDGE = GridCell(10, 1)

    fun objects(now: Long): List<GridObject> {
        val field = (4..7).flatMap { y -> (4..7).map { x -> GridCell(x, y) } } + RIGHT_EDGE
        val beds = field.map { cell ->
            GridObject(
                instanceId = "fb1126_bed_${cell.x}_${cell.y}", gridX = cell.x, gridY = cell.y,
                objectId = GridObject.TILLED_SOIL_OBJECT_ID, lastUpdatedTimestamp = now,
                nutritionLevel = if (cell in WEAK) 0f else 1f,
            )
        }
        val crops = field.map { cell ->
            GridObject(
                instanceId = "fb1126_crop_${cell.x}_${cell.y}", gridX = cell.x, gridY = cell.y,
                objectId = PlantSpecies.STRAWBERRY.objectId, growthProgress = 1f, lastUpdatedTimestamp = now,
                nutritionLevel = if (cell in WEAK) 0f else 1f,
                status = if (cell in WEAK) PlantStatus.UNNOURISHED else PlantStatus.HEALTHY,
            )
        }
        val taken = field.toSet()
        val lawn = (0 until FeedbackGarden.SIZE).flatMap { y -> (0 until FeedbackGarden.SIZE).map { x -> GridCell(x, y) } }
            .filter { it !in taken }
            .map { GridObject(instanceId = "fb1126_lawn_${it.x}_${it.y}", gridX = it.x, gridY = it.y, objectId = "ground_grass", growthProgress = 1f, lastUpdatedTimestamp = now) }
        return beds + crops + lawn
    }

    /**
     * The news of a building (`--es garden fb1126work`): a hut finished and waiting to be unveiled,
     * and a well whose taking down ends [DEMOLISH_MILLIS] after loading.
     */
    fun work(now: Long): List<GridObject> {
        val things = listOf(
            GridObject(
                instanceId = "fb1126w_hut", gridX = 3, gridY = 5, objectId = StructureData.HUT.objectId,
                lastUpdatedTimestamp = now, workKind = WorkKind.READY, workStartedAt = now, workEndsAt = now,
            ),
            GridObject(
                instanceId = "fb1126w_well", gridX = 7, gridY = 4, objectId = StructureData.WELL.objectId,
                lastUpdatedTimestamp = now, workKind = WorkKind.DEMOLISHING, workStartedAt = now, workEndsAt = now + DEMOLISH_MILLIS,
            ),
        )
        val taken = things.flatMap { it.occupiedCells() }.toSet()
        return things + (0 until FeedbackGarden.SIZE).flatMap { y -> (0 until FeedbackGarden.SIZE).map { x -> GridCell(x, y) } }
            .filter { it !in taken }
            .map { GridObject(instanceId = "fb1126w_lawn_${it.x}_${it.y}", gridX = it.x, gridY = it.y, objectId = "ground_grass", growthProgress = 1f, lastUpdatedTimestamp = now) }
    }

    private const val DEMOLISH_MILLIS = 50_000L

    /**
     * The lit things for a look at night (`--es garden fb1126night`): each as built along the back,
     * and turned round along the front, with a lantern of each in the open between them. The turned
     * cottage is painted.
     */
    fun night(now: Long): List<GridObject> {
        fun at(x: Int, y: Int, objectId: String, flipped: Boolean = false) = GridObject(
            instanceId = "fb1126n_${x}_$y", gridX = x, gridY = y, objectId = objectId, isFlipped = flipped, lastUpdatedTimestamp = now,
        )
        val things = listOf(
            at(0, 0, StructureData.COTTAGE.objectId), at(4, 0, StructureData.HUT.objectId), at(7, 0, StructureData.WINDMILL.objectId),
            at(0, 6, StructureData.COTTAGE.objectId, flipped = true).copy(roofColour = "blue", wallColour = "cream"),
            at(4, 6, StructureData.HUT.objectId, flipped = true), at(7, 6, StructureData.WINDMILL.objectId, flipped = true),
            at(9, 4, "deco_lantern"), at(7, 4, "deco_lantern", flipped = true),
        )
        val taken = things.flatMap { it.occupiedCells() }.toSet()
        return things + (0 until FeedbackGarden.SIZE).flatMap { y -> (0 until FeedbackGarden.SIZE).map { x -> GridCell(x, y) } }
            .filter { it !in taken }
            .map { GridObject(instanceId = "fb1126n_lawn_${it.x}_${it.y}", gridX = it.x, gridY = it.y, objectId = "ground_grass", growthProgress = 1f, lastUpdatedTimestamp = now) }
    }
}
