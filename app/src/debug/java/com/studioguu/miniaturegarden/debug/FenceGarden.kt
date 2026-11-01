package com.studioguu.miniaturegarden.debug

import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.RunKind
import com.studioguu.miniaturegarden.domain.model.StructureData

/**
 * Gardens to look at the runs and at the bridge's ends (debug builds only), loaded with
 * `--ez load true --es garden fences` and `--es garden bridges`.
 *
 *     y\x 0 1 2 3 4 5 6 7 8 9 10          fences
 *      0  W . H . . . . . W H B       W H B a wood, white, bamboo section alone; the last three
 *      1  . . . . . . . . . . .         side by side, which must not join
 *      2  . W W W . H H H B B B       a run with a corner of each (the white and the bamboo touch,
 *      3  . . . W . . . H . . B         and must not join)
 *      4  . . . W . . . H . . B
 */
object FenceGarden {
    const val SIZE = 11

    fun fences(now: Long): List<GridObject> {
        fun at(x: Int, y: Int, objectId: String) = GridObject(
            instanceId = "fg_${objectId}_${x}_$y", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = now,
        )
        val wood = RunKind.WOOD_FENCE.objectId
        val white = RunKind.WHITE_FENCE.objectId
        val bamboo = RunKind.BAMBOO_FENCE.objectId
        val alone = listOf(at(0, 0, wood), at(2, 0, white), at(8, 0, wood), at(9, 0, white), at(10, 0, bamboo))
        val corner = { kind: String, x: Int -> listOf(GridCell(x, 2), GridCell(x + 1, 2), GridCell(x + 2, 2), GridCell(x + 2, 3), GridCell(x + 2, 4)).map { at(it.x, it.y, kind) } }
        return alone + corner(wood, 1) + corner(white, 5) + corner(bamboo, 8) +
            // Under the white fence, a dark ground and a light one, to see its outline against.
            (5..7).map { at(it, 2, GridObject.TILLED_SOIL_OBJECT_ID) } + (3..4).map { at(7, it, GridObject.COBBLESTONE_OBJECT_ID) }
    }

    /**
     * Bridges with an end on each edge of the garden: the long one and the short one along gridY
     * on the far and the near edge, and flipped, along gridX, the same.
     */
    /**
     * Rivers to put a bridge over, one for each way it can go (`--es garden bridgeplace`, with
     * bridges in the bag): one along gridX at y = 3, one along gridY at x = 7, the two crossing at
     * (7, 3), one three wide, one along the far edge; and buildings painted, to see them in the game.
     */
    fun bridgePlaces(now: Long): List<GridObject> {
        fun at(x: Int, y: Int, objectId: String) = GridObject(
            instanceId = "bp_${x}_$y", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = now,
        )
        val river = GridObject.RIVER_OBJECT_ID
        val water = (0..6).map { it to 3 } + (0..10).map { 7 to it } + (8..10).map { it to 3 } +
            (1..4).flatMap { x -> (6..8).map { y -> x to y } } + (3..6).map { it to 0 }
        return water.map { (x, y) -> at(x, y, river) } + listOf(
            at(0, 0, StructureData.COTTAGE.objectId).copy(roofColour = "blue"),
            at(9, 5, StructureData.HUT.objectId).copy(roofColour = "green", wallColour = "white"),
            at(9, 8, StructureData.WELL.objectId).copy(roofColour = "blue"),
            at(9, 9, StructureData.WELL.objectId).copy(roofColour = "green"),
            at(9, 10, StructureData.WELL.objectId).copy(roofColour = "dark_brown"),
        )
    }

    fun bridges(now: Long): List<GridObject> {
        fun at(x: Int, y: Int, objectId: String) = GridObject(
            instanceId = "fb_${objectId}_${x}_$y", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = now,
        )
        val river = (0..10).flatMap { listOf(GridCell(it, 1), GridCell(it, 2), GridCell(it, 9), GridCell(1, it), GridCell(9, it), GridCell(8, it)) }.toSet()
        val bridge = StructureData.BRIDGE.objectId
        val things = listOf(
            at(4, 0, bridge).copy(turned = true), // 0..3, end on the far edge
            at(5, 8, bridge), // 8..10, end on the near edge
            at(0, 4, bridge).copy(isFlipped = true), // 0..2, end on the far edge
            at(7, 5, bridge).copy(isFlipped = true, turned = true), // 7..10, end on the near edge
        )
        return river.map { at(it.x, it.y, GridObject.RIVER_OBJECT_ID) } + things
    }
}
