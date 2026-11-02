package com.studioguu.miniaturegarden.debug

import com.studioguu.miniaturegarden.domain.model.GreenhouseSide
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
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
     * Beds against the water (`--es garden banks`): the stream and the pond must not lie over them.
     *
     *     y\x 0 1 2 3 4 5 6 7 8 9 10
     *      2  . . R R R T T T . . .      R ridges, T tilled earth, ~ stream, O pond, G lawn
     *      3  ~ ~ ~ ~ ~ ~ ~ ~ ~ . .
     *      4  . . T T T R R R ~ . .
     *      5  . . . . . . G G ~ . .
     *      6  . . R R R . . . ~ . .
     *      7  . . R O R . . . . . .
     *      8  . . R R R . . . . . .
     */
    fun banks(now: Long): List<GridObject> =
        place(now, GridObject.RIVER_OBJECT_ID, cells(0..8, 3..3) + cells(8..8, 4..6)) +
            place(now, GridObject.TILLED_SOIL_OBJECT_ID, cells(2..4, 2..2) + cells(5..7, 4..4) + cells(2..4, 6..8).filter { it != GridCell(3, 7) }) +
            place(now, GridObject.LOOSE_SOIL_OBJECT_ID, cells(5..7, 2..2) + cells(2..4, 4..4)) +
            place(now, LAWN, cells(6..7, 5..5)) +
            place(now, GridObject.POND_OBJECT_ID, listOf(GridCell(3, 7)))

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
     * The joined-up greenhouse (`--es garden greenhouse`): each shape with a mix of tall crops, grown.
     *
     *     y\\x 0 1 2 3 4 5 6 7 8 9 10
     *      0  . . . . . . . . . . .
     *      1  . A . B B B . C C . .      A one cell, B one by three, C two by two
     *      2  . . . . . . . C C . .
     *      3  . . . . . . . . . . .
     *      4  . D D D . . . . . . .      D three by three
     *      5  . D D D . L . . . . .      L an L
     *      6  . D D D . L . . . . .
     *      7  . . . . . L L L . . .
     *      8  . . . . . . . . . . .
     *      9  . E E . . . . . . . .      E two by two, empty
     *     10  . E E . . . . . . . .
     *
     * and F, two by two at (9, 4), its east side against the garden's frame.
     */
    fun greenhouse(now: Long): List<GridObject> {
        val planted = listOf(GridCell(1, 1)) + cells(3..5, 1..1) + cells(7..8, 1..2) + cells(1..3, 4..6) +
            cells(5..5, 5..7) + cells(6..7, 7..7)
        val crops = listOf(PlantSpecies.SUNFLOWER, PlantSpecies.CORN, PlantSpecies.TOMATO, PlantSpecies.WHEAT)
        return place(now, GridObject.GREENHOUSE_OBJECT_ID, planted + cells(1..2, 9..10) + cells(9..10, 4..5)) +
            planted.flatMapIndexed { i, cell -> place(now, crops[i % crops.size].objectId, listOf(cell)).map { it.copy(growthProgress = 1f) } }
    }

    /**
     * A field of grown [species], a row gone over, and one under glass (`--es garden hakusai`, or
     * nanohana). [young]: how far grown the back row and the one under glass are.
     */
    fun field(now: Long, species: PlantSpecies, young: Float = 1f): List<GridObject> {
        val grown = cells(2..6, 3..4)
        val withered = cells(2..6, 5..5)
        val beds = grown + withered
        fun crop(cell: GridCell) = place(now, species.objectId, listOf(cell)).map { it.copy(growthProgress = 1f) }
        fun youngCrop(cell: GridCell) = crop(cell).map { it.copy(growthProgress = young) }
        return place(now, GridObject.TILLED_SOIL_OBJECT_ID, beds) + grown.flatMap { if (it.y == 3) youngCrop(it) else crop(it) } +
            withered.flatMap(::crop).map { it.copy(status = PlantStatus.WITHERED) } +
            place(now, GridObject.GREENHOUSE_OBJECT_ID, listOf(GridCell(8, 4))) + youngCrop(GridCell(8, 4))
    }

    /**
     * The garden of `--es garden expand` starts this large, at this level and this rich, enough to
     * grow twice at once (`--ei expand_size`, `--ei expand_level`, `--ei expand_coins` to change them).
     */
    const val EXPAND_SIZE = 13
    const val EXPAND_LEVEL = 20
    const val EXPAND_COINS = 40_000

    /** What must stay joined up as the garden grows: an L of greenhouse with its door turned, and a run of fence. */
    fun joinedUp(now: Long): List<GridObject> {
        val glass = cells(11..11, 9..12) + cells(12..12, 12..12)
        return place(now, GridObject.GREENHOUSE_OBJECT_ID, glass).map { it.copy(doorSide = GreenhouseSide.WEST) } +
            place(now, RunKind.WHITE_FENCE.objectId, cells(0..7, 12..12))
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
