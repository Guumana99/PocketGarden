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
    fun `the garden's variants - a bare row, empty beds of each grade, a tulip bed, thirsty crops`() {
        fun at(objects: List<GridObject>, cell: GridCell) = objects.filter { cell in it.occupiedCells() }
        val bare = ShowcaseGarden.objects(now, bare = ShowcaseGarden.OPEN_ROW)
        assertTrue(ShowcaseGarden.OPEN_ROW.all { at(bare, it).isEmpty() })

        val soil = ShowcaseGarden.objects(now, emptyBeds = ShowcaseGarden.OPEN_ROW.mapIndexed { i, cell -> cell to i }.toMap())
        assertEquals(listOf(0, 1, 2), ShowcaseGarden.OPEN_ROW.map { cell -> at(soil, cell).single { it.isTilledSoil }.soilQuality })
        assertTrue(ShowcaseGarden.OPEN_ROW.all { cell -> at(soil, cell).none { it.isPlant } })

        val tulips = ShowcaseGarden.objects(now, tulipBed = true).filter { it.objectId == PlantSpecies.TULIP.objectId }
        assertEquals(6, tulips.size)
        assertTrue(tulips.all { it.growthProgress == 1f })
        // Only the ordinary colours, here and in the garden as it is (docs/promo: no rare ones shown).
        val ordinary = setOf("red", "pink", "yellow")
        assertTrue(tulips.all { it.bloomColour in ordinary })
        assertTrue(garden.filter { it.objectId == PlantSpecies.TULIP.objectId && it.growthProgress >= 1f }.all { it.bloomColour in ordinary })

        assertTrue(ShowcaseGarden.objects(now, thirsty = true).filter { it.isPlant }.all { it.moistureLevel < 1f })

        val painted = ShowcaseGarden.objects(now, roof = "blue", wall = "cream").single { it.objectId == StructureData.COTTAGE.objectId }
        assertEquals("blue" to "cream", painted.roofColour to painted.wallColour)
    }

    @Test
    fun `the bird's garden is strawberries in a chequer, with lawn where it is called`() {
        val crops = ShowcaseGarden.birdCrops(now)
        assertEquals(setOf(PlantSpecies.STRAWBERRY.objectId), crops.filter { it.isPlant }.map { it.objectId }.toSet())
        val ground = crops.filter { ShowcaseGarden.BIRD_GROUND in it.occupiedCells() }
        assertTrue(ground.isNotEmpty() && ground.all { LawnGrowth.isLawn(it) })
        assertEquals(ShowcaseGarden.SIZE * ShowcaseGarden.SIZE, crops.flatMap { it.occupiedCells() }.toSet().size)
    }

    @Test
    fun `the soil's row is three empty beds, plain to fluffy, side by side across the screen, on a lawn`() {
        val row = ShowcaseGarden.soilRow(now)
        val beds = ShowcaseGarden.SOIL_ROW.map { cell -> row.single { it.isTilledSoil && GridCell(it.gridX, it.gridY) == cell } }
        assertEquals(listOf(0, 1, 2), beds.map { it.soilQuality })
        assertTrue(row.none { it.isPlant })
        // Side by side on the screen: each a step right (x + 1) and a step up (y - 1), so x - y grows and x + y stays.
        assertEquals(1, ShowcaseGarden.SOIL_ROW.map { it.x + it.y }.toSet().size)
        assertEquals(ShowcaseGarden.SIZE * ShowcaseGarden.SIZE, row.flatMap { it.occupiedCells() }.toSet().size)
    }

    @Test
    fun `a bird is called to a building or onto the ground, in place of any other bird`() {
        val other = CreatureVisit(VisitRule.BIRD_BENCH, GridCell(7, 8), null, now, now + 60_000L)
        val roof = Showcase(bird = StructureData.COTTAGE.objectId, birdAt = now).visitors(listOf(other), garden).single()
        assertEquals(VisitRule.BIRD_COTTAGE, roof.rule)
        assertEquals(StructureData.COTTAGE.objectId, roof.host?.objectId)
        assertEquals(now + Showcase.BIRD_STAY_MILLIS, roof.leavesAt)

        val well = Showcase(bird = StructureData.WELL.objectId).visitors(emptyList(), garden).single()
        assertEquals(VisitRule.BIRD_WELL, well.rule)

        val walking = Showcase(bird = "5,4").visitors(listOf(other), garden).single()
        assertEquals(GridCell(5, 4), walking.cell)
        assertEquals(null, walking.host)
        assertEquals(Creature.BIRD, walking.creature)

        // Nothing of that id in the garden: no bird is called, and the others stay.
        assertEquals(listOf(other), Showcase(bird = StructureData.HUT.objectId).visitors(listOf(other), garden))
    }

    @Test
    fun `a called bird comes when the showcase is set`() {
        ShowcaseControls.set(Showcase(bird = "5,4"), now = now)
        assertEquals(now, ShowcaseControls.showcase.value?.birdAt)
        ShowcaseControls.set(null)
    }

    @Test
    fun `a summoned butterfly sets off when the showcase is set`() {
        ShowcaseControls.set(Showcase(butterfly = ShowcaseGarden.BUTTERFLY_FLOWER), now = now)
        assertEquals(now, ShowcaseControls.showcase.value?.butterflyAt)
        ShowcaseControls.set(null)
        assertEquals(null, ShowcaseControls.showcase.value)
    }
}
