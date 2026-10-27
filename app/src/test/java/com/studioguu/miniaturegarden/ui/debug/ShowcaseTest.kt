package com.studioguu.miniaturegarden.ui.debug

import com.studioguu.miniaturegarden.debug.ShowcaseGarden
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.CreatureVisit
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.LawnGrowth
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.VisitRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The look fixed for promotional pictures (docs/promo), and the garden they are taken of. */
class ShowcaseTest {
    private val now = 1_700_000_000_000L
    private val garden = ShowcaseGarden.objects(now)

    @Test
    fun `the showcase garden has only what a level 5 player has, and the cottage`() {
        val crops = setOf(
            PlantSpecies.MINT, PlantSpecies.TULIP, PlantSpecies.STRAWBERRY, PlantSpecies.TOMATO, PlantSpecies.COSMOS,
            PlantSpecies.SNOWDROP, PlantSpecies.CARROT, PlantSpecies.SUNFLOWER, PlantSpecies.DAIKON, PlantSpecies.LAVENDER,
        ).map { it.objectId }
        val things = setOf(
            StructureData.COTTAGE.objectId, StructureData.WELL.objectId, GridObject.POND_OBJECT_ID, GridObject.FENCE_OBJECT_ID,
            GridObject.COBBLESTONE_OBJECT_ID, GridObject.TILLED_SOIL_OBJECT_ID,
            "deco_bench", "deco_rock", "ground_flowers", "ground_pebble", LawnGrowth.OBJECT_ID,
        )
        val ids = garden.map { it.objectId }.toSet()
        assertEquals(emptySet<String>(), ids - crops.toSet() - things)
        // Every one of the ten crops is there.
        assertEquals(crops.toSet(), ids.filter { it.startsWith(GridObject.PLANT_ID_PREFIX) }.toSet())
    }

    @Test
    fun `everything fits in the garden, one thing to a cell, crops on beds`() {
        val size = ShowcaseGarden.SIZE
        assertTrue(garden.flatMap { it.occupiedCells() }.all { it.x in 0 until size && it.y in 0 until size })
        val standing = garden.filter { !it.isFloor && !LawnGrowth.isLawn(it) }.flatMap { it.occupiedCells() }
        assertEquals(standing.size, standing.toSet().size)
        val beds = garden.filter { it.isTilledSoil }.map { GridCell(it.gridX, it.gridY) }.toSet()
        assertTrue(garden.filter { it.isPlant }.all { GridCell(it.gridX, it.gridY) in beds })
        // No cell is left as bare earth.
        val covered = garden.flatMap { it.occupiedCells() }.toSet()
        assertEquals(size * size, covered.size)
    }

    @Test
    fun `the crops stand at every stage, none withered, the butterfly's flower in bloom`() {
        val plants = garden.filter { it.isPlant }
        assertTrue(plants.none { it.status == PlantStatus.WITHERED })
        assertTrue(plants.any { it.growthProgress >= 1f } && plants.any { it.growthProgress in 0.3f..0.9f } && plants.any { it.growthProgress < 0.3f })
        val flower = plants.single { GridCell(it.gridX, it.gridY) == ShowcaseGarden.BUTTERFLY_FLOWER }
        assertEquals(PlantSpecies.COSMOS.objectId, flower.objectId)
        assertEquals(1f, flower.growthProgress)
    }

    @Test
    fun `only the kinds allowed visit, and the butterfly asked for comes`() {
        val bird = CreatureVisit(VisitRule.BIRD_BENCH, GridCell(7, 8), null, now, now + 60_000L)
        val butterfly = CreatureVisit(VisitRule.BUTTERFLY_LAVENDER, GridCell(8, 1), null, now, now + 60_000L)
        val visits = listOf(bird, butterfly)

        assertEquals(visits, Showcase().visitors(visits, garden))
        assertEquals(emptyList<CreatureVisit>(), Showcase(creatures = emptySet()).visitors(visits, garden))
        assertEquals(listOf(butterfly), Showcase(creatures = setOf(Creature.BUTTERFLY)).visitors(visits, garden))

        val summoned = Showcase(creatures = emptySet(), butterfly = ShowcaseGarden.BUTTERFLY_FLOWER, butterflyAt = now)
            .visitors(visits, garden).single()
        assertEquals(Creature.BUTTERFLY, summoned.creature)
        assertEquals(ShowcaseGarden.BUTTERFLY_FLOWER, GridCell(summoned.host!!.gridX, summoned.host!!.gridY))
        assertEquals(now + Showcase.BUTTERFLY_STAY_MILLIS, summoned.leavesAt)

        // Nothing grows on the paving: no butterfly comes for it.
        assertEquals(emptyList<CreatureVisit>(), Showcase(creatures = emptySet(), butterfly = GridCell(3, 3)).visitors(visits, garden))
    }

    @Test
    fun `a summoned butterfly sets off when the showcase is set`() {
        ShowcaseControls.set(Showcase(butterfly = ShowcaseGarden.BUTTERFLY_FLOWER), now = now)
        assertEquals(now, ShowcaseControls.showcase.value?.butterflyAt)
        ShowcaseControls.set(null)
        assertEquals(null, ShowcaseControls.showcase.value)
    }
}
