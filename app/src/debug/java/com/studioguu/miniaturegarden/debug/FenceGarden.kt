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
 *      0  W . H . B . E . W H B       W H B E a wood, white, bamboo, brick section alone; the last
 *      1  . . . . . . . . . . .         three side by side, which must not join
 *      2  . W W W . H H H B B B       (the white and the bamboo run touch, and must not join)
 *      3  . . . W . . . H . . B       a run with a corner of each
 *      4  . . . W . . . H . . B
 *      5  . . . . . . . . . . .
 *      6  . . E E E E E . . . .       a ring of brick round a bed of three by three, with crops in
 *      7  . . E t t t E . . . .
 *      8  . . E t t t E . . . .
 *      9  . . E t t t E . . . .
 *     10  . . E E E E E . . . .
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
        val brick = RunKind.BRICK_EDGING.objectId
        val alone = listOf(at(0, 0, wood), at(2, 0, white), at(4, 0, bamboo), at(6, 0, brick), at(8, 0, wood), at(9, 0, white), at(10, 0, bamboo))
        val corner = { kind: String, x: Int -> listOf(GridCell(x, 2), GridCell(x + 1, 2), GridCell(x + 2, 2), GridCell(x + 2, 3), GridCell(x + 2, 4)).map { at(it.x, it.y, kind) } }
        val ring = (2..6).flatMap { x -> (6..10).map { y -> GridCell(x, y) } }.filter { it.x == 2 || it.x == 6 || it.y == 6 || it.y == 10 }
        val bed = (3..5).flatMap { x -> (7..9).map { y -> GridCell(x, y) } }
        val crops = listOf("plant_cosmos", "plant_tulip", "plant_cosmos", "plant_rose", "plant_tulip", "plant_rose", "plant_cosmos", "plant_tulip", "plant_cosmos")
        return alone + corner(wood, 1) + corner(white, 5) + corner(bamboo, 8) +
            // Under the white fence, a dark ground and a light one, to see its outline against.
            (5..7).map { at(it, 2, GridObject.TILLED_SOIL_OBJECT_ID) } + (3..4).map { at(7, it, GridObject.COBBLESTONE_OBJECT_ID) } +
            ring.map { at(it.x, it.y, brick) } + bed.map { at(it.x, it.y, GridObject.TILLED_SOIL_OBJECT_ID) } +
            bed.zip(crops) { cell, crop -> at(cell.x, cell.y, crop).copy(growthProgress = 1f) }
    }

    /**
     * Bridges with an end on each edge of the garden: the long one and the short one along gridY
     * on the far and the near edge, and flipped, along gridX, the same.
     */
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
