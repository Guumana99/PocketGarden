package com.studioguu.miniaturegarden.debug

import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.StructureData

/**
 * The garden the promotional pictures are taken of (docs/promo): an 11 x 11 garden someone has
 * looked after for a while. Debug builds only. Everything in it is what a player has by level 5,
 * and the cottage; nothing later is shown, so the pictures give nothing away.
 *
 *     y\x 0 1 2 3 4 5 6 7 8 9 10
 *      0  C C C # F F F F F F F      C cottage (3x3), W well, P pond, b bench, o rock
 *      1  C C C # M M M " L L F      M mint, L lavender, T tulip, R strawberry, K carrot,
 *      2  C C C # T T T " L L F      D daikon, N snowdrop, U sunflower, O tomato, S cosmos
 *      3  * " W # # # # # # # F      (each on tilled soil)
 *      4  F " . # R R " K K K F      # paving, F fence (on lawn), * wild flowers, , pebbles,
 *      5  F P " # R R * D D D F      " and . lawn
 *      6  F o . # # # # # # # F
 *      7  F N N # U U U " S S *
 *      8  F N N # O O O b S S .
 *      9  . * " # O O O , S S o
 *     10  * , . # . * . " * . "
 *
 * The crops stand at every stage, most of them ready for picking; none has withered.
 */
object ShowcaseGarden {
    const val SIZE = 11

    /** The cosmos a summoned butterfly comes to (docs/promo/README.md). */
    val BUTTERFLY_FLOWER = GridCell(8, 8)

    private fun cells(xs: IntRange, ys: IntRange): List<GridCell> = ys.flatMap { y -> xs.map { x -> GridCell(x, y) } }

    private val BEDS: List<Pair<GridCell, PlantSpecies>> =
        cells(4..6, 1..1).map { it to PlantSpecies.MINT } +
            cells(4..6, 2..2).map { it to PlantSpecies.TULIP } +
            cells(8..9, 1..2).map { it to PlantSpecies.LAVENDER } +
            cells(4..5, 4..5).map { it to PlantSpecies.STRAWBERRY } +
            cells(7..9, 4..4).map { it to PlantSpecies.CARROT } +
            cells(7..9, 5..5).map { it to PlantSpecies.DAIKON } +
            cells(1..2, 7..8).map { it to PlantSpecies.SNOWDROP } +
            cells(4..6, 7..7).map { it to PlantSpecies.SUNFLOWER } +
            cells(4..6, 8..9).map { it to PlantSpecies.TOMATO } +
            cells(8..9, 7..9).map { it to PlantSpecies.COSMOS }

    /** How far each crop has grown, by its place in [BEDS]: mostly ready, some on the way, a few just up. */
    private val STAGES = floatArrayOf(1f, 1f, 0.55f, 1f, 0.15f, 1f, 1f, 0.55f)

    private fun growthOf(index: Int, cell: GridCell): Float =
        if (cell == BUTTERFLY_FLOWER) 1f else STAGES[index % STAGES.size]

    private val PATHS: List<GridCell> = cells(3..3, 0..10) + cells(4..9, 3..3) + cells(4..9, 6..6)

    private val FENCES: List<GridCell> = cells(4..10, 0..0) + cells(10..10, 1..6) + cells(0..0, 4..8)

    private val FLOWERS = listOf(0 to 3, 1 to 9, 6 to 5, 10 to 7, 0 to 10, 5 to 10, 8 to 10)
    private val PEBBLES = listOf(7 to 9, 1 to 10)

    /** Everything in the garden, as last looked after at [now]. */
    fun objects(now: Long): List<GridObject> {
        fun at(cell: GridCell, objectId: String, growth: Float = 0f, flipped: Boolean = false) = GridObject(
            instanceId = "showcase_${objectId}_${cell.x}_${cell.y}",
            gridX = cell.x,
            gridY = cell.y,
            objectId = objectId,
            growthProgress = growth,
            isFlipped = flipped,
            lastUpdatedTimestamp = now,
        )
        fun at(x: Int, y: Int, objectId: String) = at(GridCell(x, y), objectId)

        val plants = BEDS.mapIndexed { index, (cell, species) ->
            at(cell, species.objectId, growth = growthOf(index, cell), flipped = (cell.x * 3 + cell.y) % 2 == 0)
        }
        val beds = BEDS.map { (cell, _) -> at(cell, GridObject.TILLED_SOIL_OBJECT_ID) }
        val things = listOf(
            at(0, 0, StructureData.COTTAGE.objectId),
            at(2, 3, StructureData.WELL.objectId),
            at(1, 5, GridObject.POND_OBJECT_ID),
            at(1, 6, "deco_rock"),
            at(10, 9, "deco_rock"),
            at(7, 8, "deco_bench"),
        )
        val covers = FLOWERS.map { (x, y) -> at(x, y, "ground_flowers") } + PEBBLES.map { (x, y) -> at(x, y, "ground_pebble") }
        val placed = PATHS.map { at(it, GridObject.COBBLESTONE_OBJECT_ID) } +
            FENCES.map { at(it, GridObject.FENCE_OBJECT_ID) } + beds + plants + things + covers
        // Lawn on every cell left over, and under the fences (as a player may sow it), so that no bare earth shows.
        val taken = placed.filter { it.objectId != GridObject.FENCE_OBJECT_ID }.flatMap { it.occupiedCells() }.toSet()
        val lawn = cells(0 until SIZE, 0 until SIZE).filter { it !in taken }.map { at(it, "ground_grass", growth = 1f) }
        return placed + lawn
    }
}
