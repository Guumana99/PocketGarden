package com.studioguu.miniaturegarden.debug

import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.RunKind
import com.studioguu.miniaturegarden.domain.model.Sprinkler
import com.studioguu.miniaturegarden.domain.model.WeatherOutlook

/**
 * Gardens to look at how the grounds meet (debug builds only), loaded with `--ez load true --es
 * garden ground` and `--es garden fencelook`. Cells left out are the bare wasteland.
 *
 *     y\x 0 1 2 3 4 5 6 7 8 9 10          ground
 *      0  G G G R R R G G G G G       G lawn, R ridges, T tilled earth, C cobblestones, D decking,
 *      1  G G G R R R G G G . .         P pebbles, F wild flowers; the top row and the right
 *      2  T T T G G G C C C G G         column reach the garden's frame
 *      3  T T T G G G C C C G G
 *      4  G G G D D D R R R G T
 *      5  G G G D D D R R R G T
 *      6  . . . . . . . . . . T
 *      7  . T T T . R R R . P P       3 x 3 of tilled earth and of ridges
 *      8  . T T T . R R R . P P
 *      9  . T T T . R R R . F F
 *     10  a b c d e . G a G F F       a..e the five rocks on a lawn each, and one more in reach
 *
 *     y\x 0 1 2 3 4 5 6 7 8 9 10          fencelook: a run of each fence on each ground
 *      0  . . . . . . . . . . .
 *      1  W W W . # # # . G G G       W H B wood, white, bamboo, along x, one row of each kind
 *      2  . . . . # # # . G G G       . bare ground, # tilled earth, G lawn
 *      3  . . . . . . . . . . .
 *      4  H H H . # # # . G G G
 *      5  . . . . # # # . G G G
 *      ...
 */
object GroundGarden {
    private const val LAWN = "ground_grass"
    private val ROCKS = listOf("deco_rock", "deco_rock_flat", "deco_rock_mossy", "deco_rock_standing", "deco_rock_pebbles")

    private fun cells(xs: IntRange, ys: IntRange): List<GridCell> = ys.flatMap { y -> xs.map { x -> GridCell(x, y) } }

    private fun place(now: Long, objectId: String, cells: List<GridCell>): List<GridObject> = cells.map {
        GridObject(
            instanceId = "gg_${objectId}_${it.x}_${it.y}", gridX = it.x, gridY = it.y, objectId = objectId,
            growthProgress = if (objectId == LAWN) 1f else 0f, lastUpdatedTimestamp = now,
        )
    }

    fun ground(now: Long): List<GridObject> {
        val lawn = cells(0..2, 0..1) + cells(6..10, 0..0) + cells(6..8, 1..1) + cells(3..5, 2..3) + cells(9..10, 2..3) +
            cells(0..2, 4..5) + cells(9..9, 4..5) + cells(6..8, 10..10) + cells(0..4, 10..10)
        val ridges = cells(3..5, 0..1) + cells(6..8, 4..5) + cells(5..7, 7..9)
        val tilled = cells(0..2, 2..3) + cells(10..10, 4..6) + cells(1..3, 7..9)
        return place(now, LAWN, lawn) +
            place(now, GridObject.TILLED_SOIL_OBJECT_ID, ridges) +
            place(now, GridObject.LOOSE_SOIL_OBJECT_ID, tilled) +
            place(now, GridObject.COBBLESTONE_OBJECT_ID, cells(6..8, 2..3)) +
            place(now, GridObject.WOOD_DECK_OBJECT_ID, cells(3..5, 4..5)) +
            place(now, "ground_pebble", cells(9..10, 7..8)) +
            place(now, "ground_flowers", cells(9..10, 9..10)) +
            ROCKS.flatMapIndexed { i, rock -> place(now, rock, listOf(GridCell(i, 10))) } +
            place(now, ROCKS.first(), listOf(GridCell(7, 10)))
    }

    /**
     * The small ornaments on a lawn (`--es garden lawnthings`), and a sprinkler among beds.
     *
     *     y\x 0 1 2 3 4 5 6 7 8 9 10
     *      2  . G G G G G G G G G .      G lawn, under everything on these rows
     *      3  . G B G L G W G V G .      B bench, L lantern, W well, V weathervane
     *      4  . G G G G G G G G G .
     *      5  . G G G G S G G G G .      S sprinkler, running
     *      6  . . . . m m m . . . .      m mint in its reach, on tilled earth
     */
    fun lawnThings(now: Long): List<GridObject> {
        val beds = cells(4..6, 6..6)
        return place(now, LAWN, cells(1..9, 2..5)) +
            place(now, "deco_bench", listOf(GridCell(2, 3))) +
            place(now, "deco_lantern", listOf(GridCell(4, 3))) +
            place(now, "struct_well", listOf(GridCell(6, 3))) +
            place(now, WeatherOutlook.WEATHERVANE_OBJECT_ID, listOf(GridCell(8, 3))) +
            place(now, Sprinkler.OBJECT_ID, listOf(GridCell(5, 5))) +
            place(now, GridObject.TILLED_SOIL_OBJECT_ID, beds) +
            place(now, PlantSpecies.MINT.objectId, beds).map { it.copy(growthProgress = 0.5f) }
    }

    /**
     * The greenhouse as it stands (`--es garden greenhouse`): one cell each, full grown crops in
     * them, the tallest first, and an empty one; and four side by side, where a row of them shows
     * what joining them up would have to mend.
     *
     *     y\x 0 1 2 3 4 5 6 7 8 9 10
     *      2  . . S . C . T . W . e      S sunflower, C corn, T tomato, W wheat, e empty
     *      5  . . . . S C . . . . .
     *      6  . . . . T W . . . . .
     *      8  . . - - - - - - - . .      - the ground of a row of bare grid cells beside them
     */
    fun greenhouse(now: Long): List<GridObject> {
        val crops = listOf(
            GridCell(2, 2) to PlantSpecies.SUNFLOWER, GridCell(4, 2) to PlantSpecies.CORN,
            GridCell(6, 2) to PlantSpecies.TOMATO, GridCell(8, 2) to PlantSpecies.WHEAT,
            GridCell(4, 5) to PlantSpecies.SUNFLOWER, GridCell(5, 5) to PlantSpecies.CORN,
            GridCell(4, 6) to PlantSpecies.TOMATO, GridCell(5, 6) to PlantSpecies.WHEAT,
        )
        return place(now, GridObject.GREENHOUSE_OBJECT_ID, crops.map { it.first } + GridCell(10, 2)) +
            crops.flatMap { (cell, species) -> place(now, species.objectId, listOf(cell)).map { it.copy(growthProgress = 1f) } }
    }

    fun fencesOnGrounds(now: Long): List<GridObject> {
        val rows = listOf(RunKind.WOOD_FENCE to 1, RunKind.WHITE_FENCE to 4, RunKind.BAMBOO_FENCE to 7)
        return rows.flatMap { (kind, y) ->
            place(now, GridObject.LOOSE_SOIL_OBJECT_ID, cells(4..6, y..y + 1)) +
                place(now, LAWN, cells(8..10, y..y + 1)) +
                place(now, kind.objectId, cells(0..2, y..y) + cells(4..6, y..y) + cells(8..10, y..y))
        }
    }
}
