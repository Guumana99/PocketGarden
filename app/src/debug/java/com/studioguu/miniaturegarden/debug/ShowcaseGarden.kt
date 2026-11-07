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

    /**
     * The daikons' row, in the middle with the path in front: left bare for the tilling clip, or
     * as empty beds of each grade of soil.
     */
    val OPEN_ROW: List<GridCell> = cells(7..9, 5..5)

    /** The tulips' ordinary colours, laid along their bed (never the rare ones: docs/promo). */
    private val TULIP_COLOURS = listOf("red", "pink", "yellow")

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

    /**
     * Everything in the garden, as last looked after at [now]. [bare] cells have nothing on them,
     * not even lawn (open ground for the hoe); [emptyBeds] are ridged soil with nothing sown, of
     * the grade given ([GridObject.soilQuality]). [roof] and [wall] paint the cottage (BuildingPaint ids).
     * [thirsty] leaves every crop half watered, so that a rain helps them all and they all glint after it.
     * [tulipBed] turns the mint's row into tulips and opens them all, for the close picture of them.
     */
    fun objects(
        now: Long,
        bare: Collection<GridCell> = emptyList(),
        emptyBeds: Map<GridCell, Int> = emptyMap(),
        roof: String? = null,
        wall: String? = null,
        tulipBed: Boolean = false,
        thirsty: Boolean = false,
    ): List<GridObject> {
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

        val plants = BEDS.mapIndexedNotNull { index, (bedCell, bedSpecies) ->
            val cell = bedCell
            if (cell in bare || cell in emptyBeds) return@mapIndexedNotNull null
            // The tulip bed: the mint's row turned to tulips too, and every tulip open.
            val species = if (tulipBed && bedSpecies == PlantSpecies.MINT) PlantSpecies.TULIP else bedSpecies
            val growth = if (tulipBed && species == PlantSpecies.TULIP) 1f else growthOf(index, cell)
            at(cell, species.objectId, growth = growth, flipped = (cell.x * 3 + cell.y) % 2 == 0).let {
                if (species == PlantSpecies.TULIP && growth >= 1f) it.copy(bloomColour = TULIP_COLOURS[(cell.x + cell.y) % TULIP_COLOURS.size]) else it
            }.let { if (thirsty) it.copy(moistureLevel = 0.5f) else it }
        }
        val beds = BEDS.filter { (cell, _) -> cell !in bare }
            .map { (cell, _) -> at(cell, GridObject.TILLED_SOIL_OBJECT_ID).copy(soilQuality = emptyBeds[cell] ?: 0) }
        val things = listOf(
            at(0, 0, StructureData.COTTAGE.objectId).copy(roofColour = roof, wallColour = wall),
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
        val taken = placed.filter { it.objectId != GridObject.FENCE_OBJECT_ID }.flatMap { it.occupiedCells() }.toSet() + bare
        val lawn = cells(0 until SIZE, 0 until SIZE).filter { it !in taken }.map { at(it, "ground_grass", growth = 1f) }
        return placed + lawn
    }

    /**
     * Strawberries in a chequer on a lawn (`--es garden birdcrops`): the bird called to
     * the ground among them ([BIRD_GROUND]) walks on the lawn between the crops, for the pictures
     * of it going behind their stems.
     */
    fun birdCrops(now: Long): List<GridObject> {
        val beds = cells(2..8, 2..8).filter { (it.x + it.y) % 2 == 0 }
        fun at(cell: GridCell, objectId: String, growth: Float = 0f) = GridObject(
            instanceId = "birdcrops_${objectId}_${cell.x}_${cell.y}",
            gridX = cell.x,
            gridY = cell.y,
            objectId = objectId,
            growthProgress = growth,
            isFlipped = (cell.x + cell.y * 3) % 2 == 0,
            lastUpdatedTimestamp = now,
        )
        val placed = beds.map { at(it, GridObject.TILLED_SOIL_OBJECT_ID) } +
            beds.map { at(it, PlantSpecies.STRAWBERRY.objectId, growth = 1f) }
        val taken = placed.flatMap { it.occupiedCells() }.toSet()
        return placed + cells(0 until SIZE, 0 until SIZE).filter { it !in taken }.map { at(it, "ground_grass", growth = 1f) }
    }

    /** Where the bird is called to in [birdCrops]: a cell of lawn in the middle of the crops. */
    val BIRD_GROUND = GridCell(5, 4)
}
