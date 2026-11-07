package com.studioguu.miniaturegarden.domain.model

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How many of a kind come together ([CreatureCrowds]). */
class CreatureCrowdTest {

    private val map = GardenMap.allGrass(11)
    private var nextId = 0

    private fun at(x: Int, y: Int, objectId: String, growth: Float = 0f) =
        GridObject("c${nextId++}", x, y, objectId = objectId, growthProgress = growth, lastUpdatedTimestamp = 0L)

    /** [n] tulips in bloom on beds, row by row from (1, 1). */
    private fun tulips(n: Int): List<GridObject> = (0 until n).flatMap { i ->
        val x = 1 + i % 8
        val y = 1 + i / 8 * 2
        listOf(at(x, y, GridObject.TILLED_SOIL_OBJECT_ID), at(x, y, PlantSpecies.TULIP.objectId, growth = 1f))
    }

    @Test
    fun `every tier adds up, and one alone is always at least an even chance`() {
        for (tier in CreatureCrowds.FLOWER_TIERS + CreatureCrowds.WATER_TIERS) {
            assertEquals(1f, tier.chances.sum(), 1e-5f)
            assertTrue(tier.chances.first() >= 0.5f)
        }
        // The table, as decided.
        assertEquals(listOf(1f), CreatureCrowds.chances(Creature.BUTTERFLY, 5, 7))
        assertEquals(listOf(0.7f, 0.3f), CreatureCrowds.chances(Creature.BUTTERFLY, 6, 1))
        assertEquals(listOf(0.7f, 0.3f), CreatureCrowds.chances(Creature.LADYBUG, 0, 8))
        assertEquals(listOf(0.7f, 0.3f), CreatureCrowds.chances(Creature.BUTTERFLY, 12, 11))
        assertEquals(listOf(0.5f, 0.35f, 0.15f), CreatureCrowds.chances(Creature.LADYBUG, 12, 12))
        assertEquals(listOf(1f), CreatureCrowds.chances(Creature.DRAGONFLY, 3, 9))
        assertEquals(listOf(0.7f, 0.3f), CreatureCrowds.chances(Creature.FROG, 4, 1))
        assertEquals(listOf(0.7f, 0.3f), CreatureCrowds.chances(Creature.DRAGONFLY, 0, 10))
        assertEquals(listOf(0.5f, 0.5f), CreatureCrowds.chances(Creature.FROG, 8, 14))
        // The bird and the squirrel always come alone.
        for (creature in listOf(Creature.BIRD, Creature.SQUIRREL)) assertEquals(1, CreatureCrowds.mostFor(creature, 99, 99))
    }

    @Test
    fun `the roll picks how many`() {
        assertEquals(1, CreatureCrowds.countFor(Creature.BUTTERFLY, 12, 12, 0.49f))
        assertEquals(2, CreatureCrowds.countFor(Creature.BUTTERFLY, 12, 12, 0.84f))
        assertEquals(3, CreatureCrowds.countFor(Creature.BUTTERFLY, 12, 12, 0.86f))
        assertEquals(1, CreatureCrowds.countFor(Creature.FROG, 4, 1, 0.69f))
        assertEquals(2, CreatureCrowds.countFor(Creature.FROG, 4, 1, 0.71f))
        assertEquals(1, CreatureCrowds.countFor(Creature.FROG, 0, 1, 0.99f))
    }

    @Test
    fun `flowers in bloom and wild flowers count for the butterfly, cells of ponds and streams for the frog`() {
        val garden = tulips(3) + at(9, 9, PlantSpecies.TULIP.objectId, growth = 0.5f) + at(0, 9, CreaturePerches.WILD_FLOWERS_OBJECT_ID) +
            at(0, 0, GridObject.POND_OBJECT_ID) + at(10, 0, GridObject.RIVER_OBJECT_ID) + at(10, 1, GridObject.RIVER_OBJECT_ID)
        assertEquals(4, CreatureCrowds.habitat(Creature.BUTTERFLY, garden))
        assertEquals(4, CreatureCrowds.habitat(Creature.LADYBUG, garden))
        assertEquals(3, CreatureCrowds.habitat(Creature.FROG, garden))
        assertEquals(0, CreatureCrowds.habitat(Creature.BIRD, garden))
    }

    @Test
    fun `in a garden of many flowers butterflies come in twos and threes, one after another, each to a flower of its own`() {
        val garden = tulips(14)
        val crowds = (0 until 400L).map { period ->
            CreatureVisits.visitorsAt(CreatureVisits.periodStart(period) + 1_000L, garden, WeatherType.SUNNY, map, level = 12)
                .filter { it.creature == Creature.BUTTERFLY }
        }.filter { it.isNotEmpty() }
        val sizes = crowds.groupingBy { it.size }.eachCount()
        assertTrue(sizes.toString(), sizes.keys == setOf(1, 2, 3))
        assertTrue(sizes.getValue(1) >= sizes.getValue(2) && sizes.getValue(2) >= sizes.getValue(3))
        for (crowd in crowds) {
            assertEquals(crowd.indices.toList(), crowd.map { it.companion })
            assertTrue(crowd.all { it.crowd == crowd.size })
            for ((a, b) in crowd.zipWithNext()) assertEquals(CreatureCrowds.STAGGER_MILLIS, b.arrivesAt - a.arrivesAt)
            assertEquals(crowd.size, crowd.map { it.host!!.instanceId }.toSet().size)
        }
    }

    @Test
    fun `at level one with few flowers, and for the bird, one comes alone`() {
        val garden = tulips(3) + at(9, 9, "deco_bench")
        for (period in 0 until 300L) {
            val visits = CreatureVisits.visitorsAt(CreatureVisits.periodStart(period) + 1_000L, garden, WeatherType.SUNNY, map, level = 1)
            assertTrue(visits.groupBy { it.creature }.values.all { it.size == 1 })
            assertTrue(visits.map { it.creature }.distinct().size <= CreatureVisits.MAX_VISITORS)
        }
    }

    @Test
    fun `two of a crowd never settle on the same point at the same time`() {
        val garden = tulips(14)
        val grid = GardenGrid(map, garden)
        val perches = CreaturePerches.inGarden(grid)
        val host = garden.first { it.isPlant }
        val crowd = CreatureCrowds.crowdOf(
            CreatureVisit(VisitRule.BUTTERFLY_BLOOMS, CreatureVisits.frontCell(host), host, 0L, 200_000L), 3,
            GardenSnapshot(garden, WeatherType.SUNNY), grid, map, HashSet(),
        ) { it.toLong() }
        assertEquals(3, crowd.size)
        // Each has its own share of the points.
        val shares = crowd.map { CreatureRoutes.shareOf(it, perches).toSet() }
        for (i in shares.indices) for (j in i + 1 until shares.size) assertTrue(shares[i].intersect(shares[j]).isEmpty())
        val routes = crowd.map { CreatureRoutes.plan(it, grid, perches) }
        for (t in 0L until 200_000L step 250L) {
            val perched = routes.mapNotNull { r -> r.poseAt(t)?.takeIf { it.action == CreatureAction.PERCH } }
            for (i in perched.indices) for (j in i + 1 until perched.size) {
                assertTrue(abs(perched[i].x - perched[j].x) + abs(perched[i].y - perched[j].y) > 0.05f)
            }
        }
    }

    @Test
    fun `with no place for another, fewer come`() {
        // One crop only: a second ladybird would have nowhere of its own.
        val garden = tulips(1)
        val host = garden.first { it.isPlant }
        val crowd = CreatureCrowds.crowdOf(
            CreatureVisit(VisitRule.LADYBUG_CROPS, CreatureVisits.frontCell(host), host, 0L, 100_000L), 3,
            GardenSnapshot(garden, WeatherType.SUNNY), GardenGrid(map, garden), map, HashSet(),
        ) { it.toLong() }
        assertEquals(1, crowd.size)
        assertEquals(1, crowd.single().crowd)
    }

    @Test
    fun `called butterflies come as many as the garden allows`() {
        val garden = tulips(14)
        val visits = CreatureCalls.visitsAt(0L, listOf(Creature.BUTTERFLY), garden, map, level = 12)
        assertEquals(3, visits.size)
        assertEquals(2, CreatureCalls.visitsAt(0L, listOf(Creature.BUTTERFLY), garden, map, level = 1).size)
    }
}
