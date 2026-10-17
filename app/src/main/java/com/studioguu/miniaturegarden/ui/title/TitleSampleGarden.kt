package com.studioguu.miniaturegarden.ui.title

import com.studioguu.miniaturegarden.domain.model.CreatureVisit
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.VisitRule

/**
 * The garden shown on the title screen: a made-up garden in full swing, drawn by the real garden
 * renderer, to show at a glance what a garden can become. It is never saved. It is as large as
 * a garden grows once its cottage stands, and has a little of everything: a cottage, a hut and a well, fenced beds of
 * every flower, paths, a deck by the river, and birds and butterflies keeping it company. Its
 * flowers grow from seed to full bloom and start over, each on its own beat, so most are in
 * bloom and a few are always on their way; the wind and the river move it as they move the real one.
 *
 *     y\x 0 1 2 3 4 5 6 7 8 9 10
 *      0  C C C # F F F F F F F      C cottage (3x3), H hut (2x2), W well (1x2)
 *      1  C C C # R R R R R R F      R roses, T tulips, S sunflowers (each on tilled soil)
 *      2  C C C # T T T T T T F      # paving, F fence, ~ river, = wooden deck
 *      3  . l . # # # # # # # F      b bench, l lantern, o rock, * wild flowers,
 *      4  F T T # b W . l . . F      , pebbles, " grass tufts
 *      5  F T T # . W " = H H F
 *      6  F T T # * " . = H H F
 *      7  ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~
 *      8  * S S S . = = = . * .
 *      9  . S S S , = b = o . *
 *     10  * " . * . . * . , * .
 */
object TitleSampleGarden {

    /** How many cells the sample garden is across: a garden grown once (see GardenExpansion). */
    const val SIZE = 11

    val MAP: GardenMap = GardenMap.allGrass(SIZE)

    /** How many beats a flower takes from seed to full bloom; it then stays in bloom for [BLOOM_BEATS]. */
    const val GROW_BEATS = 8
    const val BLOOM_BEATS = 12

    /** Beats a full cycle of one flower lasts. */
    const val CYCLE_BEATS = GROW_BEATS + BLOOM_BEATS

    private fun cells(xs: IntRange, ys: IntRange): List<GridCell> = ys.flatMap { y -> xs.map { x -> GridCell(x, y) } }

    /** The flower beds, each sown with one kind so that they read as planted on purpose. */
    private val BEDS: List<Pair<GridCell, PlantSpecies>> =
        cells(4..9, 1..1).map { it to PlantSpecies.ROSE } +
            cells(4..9, 2..2).map { it to PlantSpecies.TULIP } +
            cells(1..2, 4..6).map { it to PlantSpecies.TULIP } +
            cells(1..3, 8..9).map { it to PlantSpecies.SUNFLOWER }

    /** How far a flower that started its cycle [phase] beats ago has grown at [beat], 0 to 1. */
    fun growthAt(beat: Int, phase: Int): Float {
        val t = Math.floorMod(beat + phase, CYCLE_BEATS)
        return if (t >= GROW_BEATS) 1f else t / GROW_BEATS.toFloat()
    }

    /** The garden as it is at [beat]: everything but the flowers' growth stays the same. */
    fun objects(beat: Int): List<GridObject> = fixed + BEDS.mapIndexed { index, (cell, species) ->
        GridObject(
            instanceId = "sample_plant_${cell.x}_${cell.y}",
            gridX = cell.x,
            gridY = cell.y,
            objectId = species.objectId,
            growthProgress = growthAt(beat, phase = index * 7),
            isFlipped = (cell.x * 3 + cell.y) % 2 == 0,
            lastUpdatedTimestamp = 0L,
        )
    }

    private fun at(x: Int, y: Int, objectId: String) =
        GridObject(instanceId = "sample_${objectId}_${x}_$y", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = 0L)

    private val cottage = at(0, 0, StructureData.COTTAGE.objectId)
    private val bench = at(4, 4, "deco_bench")
    private val deckBench = at(6, 9, "deco_bench")

    private val fixed: List<GridObject> =
        listOf(cottage, at(8, 5, StructureData.HUT.objectId), at(5, 4, StructureData.WELL.objectId)) +
            // The windmill by the water, its sails turning.
            listOf(at(8, 8, StructureData.WINDMILL.objectId)) +
            // Fences round the back and the sides of the garden above the river.
            (4..10).map { at(it, 0, GridObject.FENCE_OBJECT_ID) } +
            (1..6).map { at(10, it, GridObject.FENCE_OBJECT_ID) } +
            (4..6).map { at(0, it, GridObject.FENCE_OBJECT_ID) } +
            // Paths from the cottage's door along the beds.
            (0..6).map { at(3, it, GridObject.COBBLESTONE_OBJECT_ID) } +
            (4..9).map { at(it, 3, GridObject.COBBLESTONE_OBJECT_ID) } +
            BEDS.map { (cell, _) -> at(cell.x, cell.y, GridObject.TILLED_SOIL_OBJECT_ID) } +
            (0 until SIZE).map { at(it, 7, GridObject.RIVER_OBJECT_ID) } +
            // Wooden decks: one by the hut, one by the water.
            listOf(7 to 5, 7 to 6).map { (x, y) -> at(x, y, GridObject.WOOD_DECK_OBJECT_ID) } +
            listOf(5 to 8, 6 to 8, 7 to 8, 5 to 9, 6 to 9, 7 to 9).map { (x, y) -> at(x, y, GridObject.WOOD_DECK_OBJECT_ID) } +
            listOf(bench, deckBench, at(1, 3, "deco_lantern"), at(7, 4, "deco_lantern"), at(10, 8, "deco_rock")) +
            listOf(4 to 6, 0 to 8, 4 to 8, 10 to 9, 0 to 10, 3 to 10, 6 to 10, 9 to 10).map { (x, y) -> at(x, y, "ground_flowers") } +
            listOf(6 to 5, 5 to 6, 1 to 10).map { (x, y) -> at(x, y, "ground_grass") } +
            listOf(4 to 9, 8 to 10).map { (x, y) -> at(x, y, "ground_pebble") }

    /**
     * Who is visiting: birds on the cottage roof and the bench, butterflies over the beds. They
     * stay for good (from the beginning of time to its end), so they are always fully there.
     */
    val visitors: List<CreatureVisit> = listOf(
        CreatureVisit(VisitRule.BIRD_COTTAGE, GridCell(cottage.gridX, cottage.gridY), cottage, 0L, Long.MAX_VALUE),
        CreatureVisit(VisitRule.BIRD_BENCH, GridCell(bench.gridX, bench.gridY), bench, 0L, Long.MAX_VALUE),
        CreatureVisit(VisitRule.BUTTERFLY_BLOOMS, GridCell(6, 1), null, 0L, Long.MAX_VALUE),
        CreatureVisit(VisitRule.BUTTERFLY_BLOOMS, GridCell(2, 8), null, 0L, Long.MAX_VALUE),
    )
}
