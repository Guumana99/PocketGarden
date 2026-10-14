package com.studioguu.miniaturegarden.ui.title

import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.StructureData

/**
 * The garden shown on the title screen: a made-up, finished-looking garden drawn by the real
 * garden renderer. It is never saved. Its flowers grow from seed to full bloom and start over,
 * each on its own beat, so the garden is always somewhere in the middle of growing; the wind and
 * the river move it as they move the real one.
 *
 *     y\x 0 1 2 3 4 5 6 7 8
 *      0  F F F F # F F F F      F fence, # paving, T tilled soil with a plant on it
 *      1  . T T T # T T T .      h hut (2x2), b bench, l lantern, * wild flowers, ~ river
 *      2  . T T T # T T T .
 *      3  * . . . # . . . *
 *      4  . . b . # . h h .
 *      5  . . . . # l h h .
 *      6  . * . . # . . . *
 *      7  ~ ~ ~ ~ ~ ~ ~ ~ ~
 *      8  . . * . . . . * .
 */
object TitleSampleGarden {

    /** How many beats a flower takes from seed to full bloom; it then stays in bloom for [BLOOM_BEATS]. */
    const val GROW_BEATS = 10
    const val BLOOM_BEATS = 4

    /** Beats a full cycle of one flower lasts. */
    const val CYCLE_BEATS = GROW_BEATS + BLOOM_BEATS

    private val BEDS: List<Pair<Int, Int>> = (1..2).flatMap { y -> listOf(1, 2, 3, 5, 6, 7).map { x -> x to y } }
    private val SPECIES = listOf(PlantSpecies.TULIP, PlantSpecies.SUNFLOWER, PlantSpecies.ROSE)

    /** How far a flower that started its cycle [phase] beats ago has grown at [beat], 0 to 1. */
    fun growthAt(beat: Int, phase: Int): Float {
        val t = Math.floorMod(beat + phase, CYCLE_BEATS)
        return if (t >= GROW_BEATS) 1f else t / GROW_BEATS.toFloat()
    }

    /** The garden as it is at [beat]: everything but the flowers' growth stays the same. */
    fun objects(beat: Int): List<GridObject> = fixed + BEDS.mapIndexed { index, (x, y) ->
        GridObject(
            instanceId = "sample_plant_${x}_$y",
            gridX = x,
            gridY = y,
            objectId = SPECIES[(x + y) % SPECIES.size].objectId,
            growthProgress = growthAt(beat, phase = index * 5),
            isFlipped = (x * 3 + y) % 2 == 0,
            lastUpdatedTimestamp = 0L,
        )
    }

    private fun at(x: Int, y: Int, objectId: String) =
        GridObject(instanceId = "sample_${objectId}_${x}_$y", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = 0L)

    private val fixed: List<GridObject> =
        (0..8).filter { it != 4 }.map { at(it, 0, GridObject.FENCE_OBJECT_ID) } +
            (0..6).map { at(4, it, GridObject.COBBLESTONE_OBJECT_ID) } +
            BEDS.map { (x, y) -> at(x, y, GridObject.TILLED_SOIL_OBJECT_ID) } +
            (0..8).map { at(it, 7, GridObject.RIVER_OBJECT_ID) } +
            listOf(0 to 3, 8 to 3, 1 to 6, 8 to 6, 2 to 8, 7 to 8).map { (x, y) -> at(x, y, "ground_flowers") } +
            listOf(
                at(2, 4, "deco_bench"),
                at(5, 5, "deco_lantern"),
                at(6, 4, StructureData.HUT.objectId),
            )
}
