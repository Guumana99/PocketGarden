package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.CreatureVisit
import com.studioguu.miniaturegarden.domain.model.CreatureVisits
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenSnapshot
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.VisitPlacement
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.model.WorkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatureVisitTest {

    private val map = GardenMap.DEFAULT
    private val sunny = WeatherType.SUNNY
    private var nextId = 0

    private fun at(x: Int, y: Int, objectId: String, growth: Float = 0f) = GridObject(
        instanceId = "o${nextId++}", gridX = x, gridY = y, objectId = objectId, growthProgress = growth, lastUpdatedTimestamp = 0L,
    )

    /** A garden made for each combination, and nothing else. */
    private fun gardenFor(rule: VisitRule): List<GridObject> = when (rule) {
        VisitRule.BUTTERFLY_BLOOMS -> listOf(at(1, 1, "plant_tulip", 1f), at(2, 1, "plant_rose", 1f))
        VisitRule.LADYBUG_TULIPS -> listOf(at(1, 1, "plant_tulip"), at(2, 1, "plant_tulip"), at(3, 1, "plant_tulip"), at(5, 5, "ground_grass"))
        VisitRule.BIRD_BENCH -> listOf(at(4, 4, "deco_bench"), at(0, 0, "struct_fence"), at(1, 0, "struct_fence"), at(2, 0, "struct_fence"))
        VisitRule.BIRD_COTTAGE -> listOf(at(3, 3, StructureData.COTTAGE.objectId))
        VisitRule.SQUIRREL_SUNFLOWER -> listOf(at(1, 1, "plant_sunflower", 1f), at(5, 5, "deco_rock"))
        VisitRule.FROG_RAIN -> listOf(at(0, 4, GridObject.RIVER_OBJECT_ID), at(1, 4, GridObject.RIVER_OBJECT_ID), at(2, 4, GridObject.RIVER_OBJECT_ID))
    }

    private fun weatherFor(rule: VisitRule) = if (rule == VisitRule.FROG_RAIN) WeatherType.RAINY else sunny

    /** Visits over many periods: [periods] consecutive ones from the start of time. */
    private fun visitsOver(periods: Int, objects: List<GridObject>, weather: WeatherType = sunny): List<CreatureVisit> =
        (0 until periods).flatMap { CreatureVisits.visitorsAt(it * CreatureVisits.PERIOD_MILLIS + 1, objects, weather, map) }

    // --- The table ---

    @Test
    fun `every creature can be met, and every combination is complete`() {
        for (creature in Creature.entries) assertTrue(creature.name, VisitRule.forCreature(creature).isNotEmpty())
        assertEquals(Creature.entries.size, Creature.entries.map { it.id }.toSet().size)
        for (rule in VisitRule.entries) {
            assertTrue(rule.name, rule.conditions.isNotEmpty())
            assertTrue(rule.name, rule.chance > 0f && rule.chance <= 1f)
            // It settles by things the garden can really hold.
            assertTrue(rule.name, rule.anchor.objectIds.isNotEmpty() && rule.anchor.objectIds.all { it in TileSetDefinition.objectIds })
        }
    }

    @Test
    fun `each combination is met by its own garden and by nothing less`() {
        for (rule in VisitRule.entries) {
            val objects = gardenFor(rule)
            assertTrue(rule.name, rule.isMetBy(GardenSnapshot(objects, weatherFor(rule))))
            // Take any one thing away and it is no longer met.
            for (missing in objects) {
                assertFalse("${rule.name} without ${missing.objectId}", rule.isMetBy(GardenSnapshot(objects - missing, weatherFor(rule))))
            }
            assertFalse(rule.name, rule.isMetBy(GardenSnapshot(emptyList(), weatherFor(rule))))
        }
    }

    @Test
    fun `the weather matters to butterflies and frogs`() {
        assertFalse(VisitRule.BUTTERFLY_BLOOMS.isMetBy(GardenSnapshot(gardenFor(VisitRule.BUTTERFLY_BLOOMS), WeatherType.RAINY)))
        assertTrue(VisitRule.BUTTERFLY_BLOOMS.isMetBy(GardenSnapshot(gardenFor(VisitRule.BUTTERFLY_BLOOMS), WeatherType.CLOUDY)))
        assertFalse(VisitRule.FROG_RAIN.isMetBy(GardenSnapshot(gardenFor(VisitRule.FROG_RAIN), sunny)))
    }

    @Test
    fun `work sites and withered crops draw nobody`() {
        val building = listOf(at(3, 3, StructureData.COTTAGE.objectId).copy(workKind = WorkKind.BUILDING))
        assertFalse(VisitRule.BIRD_COTTAGE.isMetBy(GardenSnapshot(building, sunny)))
        val wilted = gardenFor(VisitRule.BUTTERFLY_BLOOMS).map { it.copy(status = PlantStatus.WITHERED) }
        assertFalse(VisitRule.BUTTERFLY_BLOOMS.isMetBy(GardenSnapshot(wilted, sunny)))
        // Not grown enough is not in bloom.
        val buds = gardenFor(VisitRule.BUTTERFLY_BLOOMS).map { it.copy(growthProgress = 0.9f) }
        assertFalse(VisitRule.BUTTERFLY_BLOOMS.isMetBy(GardenSnapshot(buds, sunny)))
    }

    // --- Who comes, and when ---

    @Test
    fun `an empty garden has no visitors`() {
        assertTrue(visitsOver(200, emptyList()).isEmpty())
    }

    @Test
    fun `the same moment always brings the same visitors`() {
        val objects = gardenFor(VisitRule.BUTTERFLY_BLOOMS) + gardenFor(VisitRule.SQUIRREL_SUNFLOWER)
        for (period in 0 until 50) {
            val now = period * CreatureVisits.PERIOD_MILLIS + 12_345
            assertEquals(CreatureVisits.visitorsAt(now, objects, sunny, map), CreatureVisits.visitorsAt(now, objects, sunny, map))
            // Anywhere in the period gives the same answer.
            assertEquals(
                CreatureVisits.visitorsAt(now, objects, sunny, map),
                CreatureVisits.visitorsAt(period * CreatureVisits.PERIOD_MILLIS + CreatureVisits.PERIOD_MILLIS - 1, objects, sunny, map),
            )
        }
    }

    @Test
    fun `each combination comes about as often as its chance says`() {
        for (rule in VisitRule.entries) {
            val periods = 2_000
            val came = visitsOver(periods, gardenFor(rule), weatherFor(rule)).count { it.rule == rule }
            val rate = came.toFloat() / periods
            assertTrue("${rule.name}: $rate against ${rule.chance}", kotlin.math.abs(rate - rule.chance) < 0.05f)
        }
    }

    @Test
    fun `a visitor arrives after the period begins and leaves before it ends`() {
        val visits = visitsOver(300, gardenFor(VisitRule.BUTTERFLY_BLOOMS))
        assertTrue(visits.isNotEmpty())
        for (visit in visits) {
            val start = CreatureVisits.periodStart(CreatureVisits.periodOf(visit.arrivesAt))
            assertTrue(visit.arrivesAt - start >= CreatureVisits.MIN_MARGIN_MILLIS)
            assertTrue(start + CreatureVisits.PERIOD_MILLIS - visit.leavesAt >= CreatureVisits.MIN_MARGIN_MILLIS)
            assertTrue(visit.leavesAt > visit.arrivesAt)
        }
    }

    @Test
    fun `no more than two at once, and never two of a kind`() {
        val everything = VisitRule.entries.filter { it != VisitRule.FROG_RAIN }.flatMap { rule ->
            gardenFor(rule).map { it.copy(gridX = it.gridX + 0, gridY = it.gridY) }
        }.distinctBy { it.gridX to it.gridY }
        for (period in 0 until 500) {
            val visits = CreatureVisits.visitorsAt(period * CreatureVisits.PERIOD_MILLIS, everything, sunny, map)
            assertTrue(visits.size <= CreatureVisits.MAX_VISITORS)
            assertEquals(visits.size, visits.map { it.creature }.toSet().size)
            assertEquals(visits.size, visits.map { it.cell }.toSet().size)
        }
    }

    @Test
    fun `a bird coming for either reason is still one bird`() {
        val both = gardenFor(VisitRule.BIRD_BENCH) + gardenFor(VisitRule.BIRD_COTTAGE)
        val visits = visitsOver(500, both)
        assertTrue(visits.any { it.rule == VisitRule.BIRD_BENCH })
        assertTrue(visits.any { it.rule == VisitRule.BIRD_COTTAGE })
        visits.groupBy { CreatureVisits.periodOf(it.arrivesAt) }.values.forEach { inPeriod ->
            assertTrue(inPeriod.count { it.creature == Creature.BIRD } <= 1)
        }
    }

    // --- Where ---

    @Test
    fun `a visitor on something sits on it, one beside something stays on free lawn next to it`() {
        for (rule in VisitRule.entries) {
            val objects = gardenFor(rule)
            val grid = GardenGrid(map, objects)
            val visits = visitsOver(100, objects, weatherFor(rule)).filter { it.rule == rule }
            assertTrue(rule.name, visits.isNotEmpty())
            for (visit in visits) {
                when (rule.placement) {
                    VisitPlacement.ON_ANCHOR -> {
                        val host = visit.host!!
                        assertTrue(rule.anchor.matches(host))
                        assertEquals(CreatureVisits.frontCell(host), visit.cell)
                    }
                    VisitPlacement.BESIDE_ANCHOR -> {
                        assertEquals(null, visit.host)
                        assertEquals(null, grid.occupantAt(visit.cell.x, visit.cell.y))
                        assertTrue(map.isInside(visit.cell.x, visit.cell.y))
                        val nextToAnchor = objects.filter { rule.anchor.matches(it) }.flatMap { it.occupiedCells() }.any { cell ->
                            kotlin.math.abs(cell.x - visit.cell.x) + kotlin.math.abs(cell.y - visit.cell.y) == 1
                        }
                        assertTrue(rule.name, nextToAnchor)
                    }
                }
            }
        }
    }

    @Test
    fun `a bird on the cottage sits on it, drawn at its front cell`() {
        val cottage = at(3, 3, StructureData.COTTAGE.objectId)
        val visit = visitsOver(50, listOf(cottage)).first()
        assertEquals(cottage, visit.host)
        assertEquals(GridCell(5, 5), visit.cell)
    }

    @Test
    fun `with no free lawn beside it, the squirrel does not come`() {
        val rock = at(0, 0, "deco_rock")
        val boxedIn = listOf(rock, at(1, 0, "deco_bench"), at(0, 1, "deco_bench"), at(4, 4, "plant_sunflower", 1f))
        assertTrue(visitsOver(200, boxedIn).none { it.creature == Creature.SQUIRREL })
    }

    // --- Coming and going softly ---

    @Test
    fun `a visitor fades in on arrival and out before leaving`() {
        val visit = CreatureVisit(VisitRule.BIRD_COTTAGE, GridCell(0, 0), null, arrivesAt = 100_000, leavesAt = 200_000)
        assertEquals(0f, visit.opacityAt(99_999))
        assertEquals(0f, visit.opacityAt(100_000))
        assertEquals(0.5f, visit.opacityAt(100_000 + CreatureVisit.FADE_MILLIS / 2), 0.001f)
        assertEquals(1f, visit.opacityAt(150_000))
        assertEquals(0.5f, visit.opacityAt(200_000 - CreatureVisit.FADE_MILLIS / 2), 0.001f)
        assertEquals(0f, visit.opacityAt(200_000))
        assertFalse(visit.isPresentAt(200_000))
    }
}
