package com.studioguu.miniaturegarden.debug

import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.StructureData

/**
 * A garden laid out to look at the river, the bridge, the rocks, the bench's four sides and the
 * painted buildings (debug builds only), loaded with `--ez load true --es garden art`:
 *
 *     y\x 0 1 2 3 4 5 6 7 8 9 10
 *      0  . . . . . . . . . H H     H hut (painted)
 *      1  ~ ~ B ~ ~ ~ . . . H H     ~ river, two cells wide at first; B the bridge over it
 *      2  ~ ~ B ~ ~ ~ . . . . .
 *      3  . . . . . ~ . . . . .
 *      4  . . . . . ~ . . ~ . .     round two bends and on to an end
 *      5  . O O . . ~ . . ~ . .     O a pond of two cells
 *      6  . . . . . ~ ~ ~ ~ . .
 *      7  . ~ . . . . . . . . .     a pool of one cell
 *      8  . . . . . . . . C C C     C cottage (painted)
 *      9  1 2 3 4 5 . . . C C C     1 to 5 the kinds of rock
 *     10  b c d e . . . . C C C     b c d e the bench, turned 0 to 3 times
 */
object ArtGarden {
    const val SIZE = 11

    fun objects(now: Long): List<GridObject> {
        fun at(x: Int, y: Int, objectId: String) = GridObject(
            instanceId = "art_${objectId}_${x}_$y", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = now,
        )
        val river = (0..5).flatMap { x -> listOf(GridCell(x, 1), GridCell(x, 2)) } +
            (3..6).map { GridCell(5, it) } + (6..8).map { GridCell(it, 6) } + listOf(GridCell(8, 5), GridCell(8, 4), GridCell(1, 7))
        val rocks = listOf("deco_rock", "deco_rock_flat", "deco_rock_mossy", "deco_rock_standing", "deco_rock_pebbles")
            .mapIndexed { i, id -> at(i, 9, id) }
        var bench = at(0, 10, "deco_bench")
        val benches = (0 until 4).map { i ->
            bench.copy(instanceId = "art_bench_$i", gridX = i).also { bench = bench.quarterTurned() }
        }
        val things = listOf(
            at(2, 1, StructureData.BRIDGE.objectId),
            at(1, 5, GridObject.POND_OBJECT_ID),
            at(2, 5, GridObject.POND_OBJECT_ID),
            at(9, 0, StructureData.HUT.objectId).copy(roofColour = "green", wallColour = "white"),
            at(8, 8, StructureData.COTTAGE.objectId).copy(roofColour = "blue", wallColour = "pale_blue"),
        )
        return river.map { at(it.x, it.y, GridObject.RIVER_OBJECT_ID) } + rocks + benches + things
    }
}
