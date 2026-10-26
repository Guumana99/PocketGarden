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
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.TimeOfDay
import com.studioguu.miniaturegarden.domain.model.VisitCondition
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
        VisitRule.FROG_RAIN, VisitRule.FROG_SUMMER ->
            listOf(at(0, 4, GridObject.RIVER_OBJECT_ID), at(1, 4, GridObject.RIVER_OBJECT_ID), at(2, 4, GridObject.RIVER_OBJECT_ID))
        VisitRule.SQUIRREL_AUTUMN -> listOf(at(5, 5, "deco_rock"))
        VisitRule.DRAGONFLY_AUTUMN -> listOf(at(0, 4, GridObject.RIVER_OBJECT_ID), at(1, 4, GridObject.RIVER_OBJECT_ID))
        VisitRule.BUTTERFLY_LAVENDER -> listOf(at(1, 1, "plant_lavender"), at(2, 1, "plant_lavender"))
        VisitRule.LADYBUG_STRAWBERRY -> listOf(at(1, 1, "plant_strawberry"), at(2, 1, "plant_strawberry"))
        VisitRule.BIRD_HUT -> listOf(at(3, 3, StructureData.HUT.objectId))
        VisitRule.BIRD_WINTER_WELL, VisitRule.FROG_WELL -> listOf(at(4, 4, StructureData.WELL.objectId))
        VisitRule.SQUIRREL_SHIITAKE -> listOf(at(4, 4, "plant_shiitake", 1f))
    }

    private fun weatherFor(rule: VisitRule) = if (rule == VisitRule.FROG_RAIN || rule == VisitRule.FROG_WELL) WeatherType.RAINY else sunny

    /** The season a combination needs; spring for those that come in any. */
    private fun seasonFor(rule: VisitRule) = when (rule) {
        VisitRule.FROG_SUMMER -> Season.SUMMER
        VisitRule.SQUIRREL_AUTUMN, VisitRule.DRAGONFLY_AUTUMN -> Season.AUTUMN
        VisitRule.BIRD_WINTER_WELL -> Season.WINTER
        else -> Season.SPRING
    }

    /** Visits over many periods: [periods] consecutive ones from the start of time. */
    private fun visitsOver(
        periods: Int,
        objects: List<GridObject>,
        weather: WeatherType = sunny,
        season: Season = Season.SPRING,
    ): List<CreatureVisit> =
        (0 until periods).flatMap { CreatureVisits.visitorsAt(it * CreatureVisits.PERIOD_MILLIS + 1, objects, weather, map, season) }

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
            val season = seasonFor(rule)
            assertTrue(rule.name, rule.isMetBy(GardenSnapshot(objects, weatherFor(rule), season)))
            // Take any one thing away and it is no longer met.
            for (missing in objects) {
                assertFalse("${rule.name} without ${missing.objectId}", rule.isMetBy(GardenSnapshot(objects - missing, weatherFor(rule), season)))
            }
            assertFalse(rule.name, rule.isMetBy(GardenSnapshot(emptyList(), weatherFor(rule), season)))
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
            val came = visitsOver(periods, gardenFor(rule), weatherFor(rule), seasonFor(rule)).count { it.rule == rule }
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
            val visits = visitsOver(100, objects, weatherFor(rule), seasonFor(rule)).filter { it.rule == rule }
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

    // --- Coming and going ---

    @Test
    fun `a visitor is in the garden from its arrival up to its leaving`() {
        // How it comes and goes (a pop, a flight) is its route's: see CreatureMotionTest.
        val visit = CreatureVisit(VisitRule.BIRD_COTTAGE, GridCell(0, 0), null, arrivesAt = 100_000, leavesAt = 200_000)
        assertFalse(visit.isPresentAt(99_999))
        assertTrue(visit.isPresentAt(100_000))
        assertTrue(visit.isPresentAt(199_999))
        assertFalse(visit.isPresentAt(200_000))
    }

    // --- The combinations added with the lavender, strawberry, pumpkin (now the shiitake), hut and well ---

    private val added = listOf(
        VisitRule.BUTTERFLY_LAVENDER, VisitRule.LADYBUG_STRAWBERRY, VisitRule.BIRD_HUT,
        VisitRule.BIRD_WINTER_WELL, VisitRule.SQUIRREL_SHIITAKE, VisitRule.FROG_WELL,
    )

    @Test
    fun `the added combinations come after the older ones, which keep their places`() {
        assertEquals(added, VisitRule.entries.takeLast(added.size))
        assertEquals(VisitRule.DRAGONFLY_AUTUMN, VisitRule.entries[8])
        assertEquals(15, VisitRule.entries.size)
    }

    @Test
    fun `butterflies come for the scent of lavender, flowering or not, but not in the rain`() {
        val young = gardenFor(VisitRule.BUTTERFLY_LAVENDER)
        for (season in Season.entries) assertTrue(VisitRule.BUTTERFLY_LAVENDER.isMetBy(GardenSnapshot(young, sunny, season)))
        assertFalse(VisitRule.BUTTERFLY_LAVENDER.isMetBy(GardenSnapshot(young, WeatherType.RAINY)))
        // Young lavender is not a garden in bloom, so this is the butterfly's only reason to come.
        assertFalse(VisitRule.BUTTERFLY_BLOOMS.isMetBy(GardenSnapshot(young, sunny)))
        assertTrue(visitsOver(100, young).filter { it.creature == Creature.BUTTERFLY }.all { it.rule == VisitRule.BUTTERFLY_LAVENDER })
        // In full bloom it comes too, for either reason, and is still one butterfly.
        val blooming = young.map { it.copy(growthProgress = 1f) }
        assertTrue(VisitRule.BUTTERFLY_LAVENDER.isMetBy(GardenSnapshot(blooming, sunny)))
        assertTrue(VisitRule.BUTTERFLY_BLOOMS.isMetBy(GardenSnapshot(blooming, sunny)))
        // One lavender is not enough.
        assertFalse(VisitRule.BUTTERFLY_LAVENDER.isMetBy(GardenSnapshot(young.take(1), sunny)))
    }

    @Test
    fun `ladybugs visit strawberries only in spring`() {
        val strawberries = gardenFor(VisitRule.LADYBUG_STRAWBERRY)
        for (season in Season.entries) {
            assertEquals(season.name, season == Season.SPRING, VisitRule.LADYBUG_STRAWBERRY.isMetBy(GardenSnapshot(strawberries, sunny, season)))
        }
    }

    @Test
    fun `a bird sits on the hut's roof, at its front cell`() {
        val hut = at(3, 3, StructureData.HUT.objectId)
        val visit = visitsOver(100, listOf(hut)).first { it.rule == VisitRule.BIRD_HUT }
        assertEquals(hut, visit.host)
        assertEquals(GridCell(4, 4), visit.cell)
        // Not while it is still going up.
        val site = hut.copy(workKind = WorkKind.BUILDING)
        assertFalse(VisitRule.BIRD_HUT.isMetBy(GardenSnapshot(listOf(site), sunny)))
    }

    @Test
    fun `the well draws a bird in winter and a frog in the rain, and a frog needs no river`() {
        val well = gardenFor(VisitRule.FROG_WELL)
        for (season in Season.entries) {
            assertEquals(season.name, season == Season.WINTER, VisitRule.BIRD_WINTER_WELL.isMetBy(GardenSnapshot(well, sunny, season)))
        }
        assertTrue(VisitRule.FROG_WELL.isMetBy(GardenSnapshot(well, WeatherType.RAINY)))
        assertFalse(VisitRule.FROG_WELL.isMetBy(GardenSnapshot(well, sunny)))
        assertFalse(VisitRule.FROG_WELL.isMetBy(GardenSnapshot(well, WeatherType.CLOUDY)))
        // A garden with a well and no river: in the rain, a frog does come, beside the well.
        val frogs = visitsOver(200, well, WeatherType.RAINY).filter { it.creature == Creature.FROG }
        assertTrue(frogs.isNotEmpty())
        assertTrue(frogs.all { it.rule == VisitRule.FROG_WELL && it.host == null })
    }

    @Test
    fun `a squirrel waits beside a shiitake log once it is ready to pick, in any season`() {
        val young = listOf(at(4, 4, "plant_shiitake", 0.9f))
        assertFalse(VisitRule.SQUIRREL_SHIITAKE.isMetBy(GardenSnapshot(young, sunny)))
        val ripe = gardenFor(VisitRule.SQUIRREL_SHIITAKE)
        for (season in Season.entries) assertTrue(VisitRule.SQUIRREL_SHIITAKE.isMetBy(GardenSnapshot(ripe, sunny, season)))
        // A withered one draws nobody.
        val withered = listOf(ripe.single().copy(status = PlantStatus.WITHERED))
        assertFalse(VisitRule.SQUIRREL_SHIITAKE.isMetBy(GardenSnapshot(withered, sunny)))
    }

    @Test
    fun `winter now has a visitor of its own, besides the ones that come in any season`() {
        val winterOnly = VisitRule.entries.filter { rule ->
            rule.conditions.any { it is VisitCondition.SeasonIs && it.seasons == setOf(Season.WINTER) }
        }
        assertEquals(listOf(VisitRule.BIRD_WINTER_WELL), winterOnly)
        val wintry = visitsOver(200, gardenFor(VisitRule.BIRD_WINTER_WELL), season = Season.WINTER)
        assertTrue(wintry.any { it.rule == VisitRule.BIRD_WINTER_WELL })
    }

    @Test
    fun `butterflies, dragonflies and frogs stay away on a snowy day`() {
        for (rule in listOf(VisitRule.BUTTERFLY_BLOOMS, VisitRule.BUTTERFLY_LAVENDER, VisitRule.DRAGONFLY_AUTUMN, VisitRule.FROG_RAIN, VisitRule.FROG_WELL)) {
            assertFalse(rule.name, rule.isMetBy(GardenSnapshot(gardenFor(rule), WeatherType.SNOWY, seasonFor(rule))))
        }
        // The rest do not mind the snow.
        assertTrue(VisitRule.BIRD_WINTER_WELL.isMetBy(GardenSnapshot(gardenFor(VisitRule.BIRD_WINTER_WELL), WeatherType.SNOWY, Season.WINTER)))
        assertTrue(VisitRule.SQUIRREL_SUNFLOWER.isMetBy(GardenSnapshot(gardenFor(VisitRule.SQUIRREL_SUNFLOWER), WeatherType.SNOWY)))
        // Clouds are fair weather.
        assertTrue(VisitRule.BUTTERFLY_BLOOMS.isMetBy(GardenSnapshot(gardenFor(VisitRule.BUTTERFLY_BLOOMS), WeatherType.CLOUDY)))
    }

    @Test
    fun `some visitors keep to their times of day`() {
        fun comesAt(rule: VisitRule, time: TimeOfDay) =
            rule.isMetBy(GardenSnapshot(gardenFor(rule), weatherFor(rule), seasonFor(rule), time))
        // Butterflies in the morning and by day; dragonflies by day and in the evening.
        for (rule in listOf(VisitRule.BUTTERFLY_BLOOMS, VisitRule.BUTTERFLY_LAVENDER)) {
            assertEquals(rule.name, listOf(true, true, false, false), TimeOfDay.entries.map { comesAt(rule, it) })
        }
        assertEquals(listOf(false, true, true, false), TimeOfDay.entries.map { comesAt(VisitRule.DRAGONFLY_AUTUMN, it) })
        // Birds are gone to roost at night.
        for (rule in VisitRule.entries.filter { it.creature == Creature.BIRD }) {
            assertEquals(rule.name, listOf(true, true, true, false), TimeOfDay.entries.map { comesAt(rule, it) })
        }
        // The others come at any hour.
        for (rule in VisitRule.entries.filter { it.creature in setOf(Creature.LADYBUG, Creature.SQUIRREL, Creature.FROG) }) {
            assertTrue(rule.name, TimeOfDay.entries.all { comesAt(rule, it) })
        }
    }

    /** PHILOSOPHY 12: a rumour hints at what a creature likes; it never spells out counts. */
    @Test
    fun `every combination has a name and a rumour in the nature notes, with no numbers in them`() {
        val main = listOf(java.io.File("src/main"), java.io.File("app/src/main")).first { it.isDirectory }
        val strings = java.io.File(main, "res/values/strings.xml").readText()
        for (rule in VisitRule.entries) {
            for (prefix in listOf("combo_", "hint_")) {
                val name = prefix + rule.name.lowercase()
                val text = Regex("<string name=\"$name\">([^<]*)</string>").find(strings)?.groupValues?.get(1)
                assertTrue("$name is missing", !text.isNullOrBlank())
                assertFalse("$name gives a number: $text", text!!.any { it.isDigit() || it in "０１２３４５６７８９" })
            }
        }
    }
}
