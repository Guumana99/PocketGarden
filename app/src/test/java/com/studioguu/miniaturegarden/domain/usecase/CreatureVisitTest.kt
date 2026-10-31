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
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.SeasonPhase
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.TimeOfDay
import com.studioguu.miniaturegarden.domain.model.VisitCondition
import com.studioguu.miniaturegarden.domain.model.VisitPlacement
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.ui.render.TapTargets
import java.io.File
import com.studioguu.miniaturegarden.domain.model.NatureNote
import com.studioguu.miniaturegarden.domain.model.NatureNotes
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

    private fun water(vararg xs: Int, objectId: String = GridObject.RIVER_OBJECT_ID) = xs.map { at(it, 4, objectId) }

    /** A garden made for each combination, and nothing else. */
    private fun gardenFor(rule: VisitRule): List<GridObject> = when (rule) {
        VisitRule.BUTTERFLY_BLOOMS -> listOf(at(1, 1, "plant_tulip", 1f), at(2, 1, "plant_rose", 1f))
        VisitRule.BUTTERFLY_LAVENDER -> listOf(at(1, 1, "plant_tulip", 1f), at(2, 1, "plant_rose", 1f), at(3, 1, "plant_lavender"))
        VisitRule.LADYBUG_CROPS, VisitRule.LADYBUG_SPRING ->
            listOf(at(1, 1, "plant_tomato"), at(2, 1, "plant_tulip"), at(3, 1, "plant_carrot"), at(5, 5, "ground_grass"))
        VisitRule.BIRD_BENCH -> listOf(at(4, 4, "deco_bench"))
        VisitRule.BIRD_FENCE -> listOf(at(0, 0, "struct_fence"), at(1, 0, "struct_fence"), at(2, 0, "struct_fence"))
        VisitRule.BIRD_COTTAGE -> listOf(at(3, 3, StructureData.COTTAGE.objectId))
        VisitRule.BIRD_HUT -> listOf(at(3, 3, StructureData.HUT.objectId))
        VisitRule.BIRD_WELL -> listOf(at(4, 4, StructureData.WELL.objectId))
        VisitRule.SQUIRREL_SHIITAKE -> listOf(at(4, 4, "plant_shiitake", 1f))
        VisitRule.SQUIRREL_SUNFLOWER -> listOf(at(1, 1, "plant_sunflower", 1f), at(5, 5, "deco_rock"))
        VisitRule.FROG_RAIN, VisitRule.FROG_SUMMER -> water(0, 1, 2)
        VisitRule.DRAGONFLY_AUTUMN -> water(0, 1)
    }

    private fun weatherFor(rule: VisitRule) = if (rule == VisitRule.FROG_RAIN) WeatherType.RAINY else sunny

    /** The season each combination is the best one in; spring for those that come in any. */
    private fun seasonFor(rule: VisitRule) = when (rule) {
        VisitRule.FROG_SUMMER, VisitRule.LADYBUG_CROPS -> Season.SUMMER
        VisitRule.DRAGONFLY_AUTUMN -> Season.AUTUMN
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
            val periods = 4_000
            val came = visitsOver(periods, gardenFor(rule), weatherFor(rule), seasonFor(rule)).count { it.rule == rule }
            val rate = came.toFloat() / periods
            assertTrue("${rule.name}: $rate against ${rule.chance}", kotlin.math.abs(rate - rule.chance) < 0.03f)
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
            val visits = visitsOver(400, objects, weatherFor(rule), seasonFor(rule)).filter { it.rule == rule }
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
        val visit = visitsOver(300, listOf(cottage)).first()
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

    // --- The chances ---

    private fun chanceOf(creature: Creature, garden: GardenSnapshot) =
        VisitRule.forCreature(creature).filter { it.isMetBy(garden) }.maxOf { it.chanceIn(garden) }

    @Test
    fun `butterflies come at a fifth of the time, a quarter with lavender about, flowering or not`() {
        val blooms = gardenFor(VisitRule.BUTTERFLY_BLOOMS)
        assertEquals(0.2f, chanceOf(Creature.BUTTERFLY, GardenSnapshot(blooms, sunny)))
        val lavender = gardenFor(VisitRule.BUTTERFLY_LAVENDER)
        assertEquals(0.25f, chanceOf(Creature.BUTTERFLY, GardenSnapshot(lavender, sunny)))
        // Lavender alone is not a garden in bloom.
        val onlyLavender = listOf(at(1, 1, "plant_lavender"), at(2, 1, "plant_lavender"))
        assertFalse(VisitRule.BUTTERFLY_LAVENDER.isMetBy(GardenSnapshot(onlyLavender, sunny)))
        // It settles on the lavender, and rolls once: no more often than the better chance.
        val visits = visitsOver(4_000, lavender).filter { it.creature == Creature.BUTTERFLY }
        assertTrue(visits.all { it.rule == VisitRule.BUTTERFLY_LAVENDER && it.host?.objectId == "plant_lavender" })
        assertTrue(kotlin.math.abs(visits.size / 4_000f - 0.25f) < 0.03f)
    }

    @Test
    fun `only flowers in bloom count as flowers`() {
        val vegetables = listOf(at(1, 1, "plant_tomato", 1f), at(2, 1, "plant_carrot", 1f))
        assertFalse(VisitRule.BUTTERFLY_BLOOMS.isMetBy(GardenSnapshot(vegetables, sunny)))
        for (species in listOf(PlantSpecies.TULIP, PlantSpecies.ROSE, PlantSpecies.LAVENDER, PlantSpecies.SUNFLOWER)) assertTrue(species.isFlower)
        assertFalse(PlantSpecies.SHIITAKE.isFlower)
    }

    @Test
    fun `ladybugs like any three crops and grass, and spring best`() {
        val crops = gardenFor(VisitRule.LADYBUG_CROPS)
        for (season in Season.entries) {
            assertEquals(season.name, if (season == Season.SPRING) 0.2f else 0.15f, chanceOf(Creature.LADYBUG, GardenSnapshot(crops, sunny, season)))
        }
    }

    @Test
    fun `any of a bird's places will do, and a flower in bloom doubles the chance`() {
        val birds = listOf(VisitRule.BIRD_BENCH, VisitRule.BIRD_FENCE, VisitRule.BIRD_COTTAGE, VisitRule.BIRD_HUT, VisitRule.BIRD_WELL)
        assertEquals(birds.toSet(), VisitRule.forCreature(Creature.BIRD).toSet())
        val bloom = at(9, 9, "plant_tulip", 1f)
        for (rule in birds) {
            for (season in Season.entries) assertTrue(rule.name, rule.isMetBy(GardenSnapshot(gardenFor(rule), sunny, season)))
            assertEquals(rule.name, 0.1f, chanceOf(Creature.BIRD, GardenSnapshot(gardenFor(rule), sunny)))
            assertEquals(rule.name, 0.2f, chanceOf(Creature.BIRD, GardenSnapshot(gardenFor(rule) + bloom, sunny)))
            // A flower still growing is not in bloom.
            assertEquals(rule.name, 0.1f, chanceOf(Creature.BIRD, GardenSnapshot(gardenFor(rule) + bloom.copy(growthProgress = 0.9f), sunny)))
        }
        assertFalse(VisitRule.BIRD_FENCE.isMetBy(GardenSnapshot(gardenFor(VisitRule.BIRD_FENCE).take(2), sunny)))
    }

    @Test
    fun `a bird with two places as good comes to both`() {
        val visits = visitsOver(800, gardenFor(VisitRule.BIRD_BENCH) + gardenFor(VisitRule.BIRD_FENCE))
        assertTrue(visits.any { it.rule == VisitRule.BIRD_BENCH })
        assertTrue(visits.any { it.rule == VisitRule.BIRD_FENCE })
    }

    @Test
    fun `a squirrel comes to a ripe log or a sunflower by a rock, and more often in autumn`() {
        for (rule in listOf(VisitRule.SQUIRREL_SHIITAKE, VisitRule.SQUIRREL_SUNFLOWER)) {
            for (season in Season.entries) {
                assertEquals(rule.name, if (season == Season.AUTUMN) 0.25f else 0.2f, chanceOf(Creature.SQUIRREL, GardenSnapshot(gardenFor(rule), sunny, season)))
            }
        }
        assertFalse(VisitRule.SQUIRREL_SHIITAKE.isMetBy(GardenSnapshot(listOf(at(4, 4, "plant_shiitake", 0.9f)), sunny)))
        val withered = gardenFor(VisitRule.SQUIRREL_SHIITAKE).map { it.copy(status = PlantStatus.WITHERED) }
        assertFalse(VisitRule.SQUIRREL_SHIITAKE.isMetBy(GardenSnapshot(withered, sunny)))
        // A rock by itself draws nobody, even in autumn.
        val rock = listOf(at(5, 5, "deco_rock"))
        for (rule in VisitRule.entries) assertFalse(rule.name, rule.isMetBy(GardenSnapshot(rock, sunny, Season.AUTUMN)))
    }

    @Test
    fun `whatever counts as a rock is counted and sat beside, however many kinds there are`() {
        val rocks = VisitRule.SQUIRREL_SUNFLOWER.anchor.objectIds
        assertTrue("deco_rock" in rocks)
        val needs = VisitRule.SQUIRREL_SUNFLOWER.conditions.filterIsInstance<VisitCondition.Things>().single()
        assertEquals(rocks, needs.objectIds)
    }

    @Test
    fun `a pond counts as water as much as a river`() {
        val mixed = water(0, 1) + water(2, objectId = GridObject.POND_OBJECT_ID)
        assertTrue(VisitRule.FROG_RAIN.isMetBy(GardenSnapshot(mixed, WeatherType.RAINY)))
        assertTrue(VisitRule.FROG_SUMMER.isMetBy(GardenSnapshot(mixed, sunny, Season.SUMMER)))
        val pond = water(0, 1, objectId = GridObject.POND_OBJECT_ID)
        assertTrue(VisitRule.DRAGONFLY_AUTUMN.isMetBy(GardenSnapshot(pond, sunny, Season.AUTUMN)))
        assertFalse(VisitRule.FROG_RAIN.isMetBy(GardenSnapshot(pond, WeatherType.RAINY)))
    }

    @Test
    fun `a frog takes the better of rain and summer`() {
        val river = gardenFor(VisitRule.FROG_RAIN)
        assertEquals(0.25f, chanceOf(Creature.FROG, GardenSnapshot(river, WeatherType.RAINY, Season.SPRING)))
        assertEquals(0.1f, chanceOf(Creature.FROG, GardenSnapshot(river, sunny, Season.SUMMER)))
        assertEquals(0.25f, chanceOf(Creature.FROG, GardenSnapshot(river, WeatherType.RAINY, Season.SUMMER)))
    }

    @Test
    fun `the dragonfly comes from the end of summer through autumn`() {
        val river = gardenFor(VisitRule.DRAGONFLY_AUTUMN)
        fun comes(season: Season, phase: SeasonPhase) =
            VisitRule.DRAGONFLY_AUTUMN.isMetBy(GardenSnapshot(river, sunny, season, phase = phase))
        for (phase in SeasonPhase.entries) {
            assertEquals(phase.name, phase == SeasonPhase.LATE, comes(Season.SUMMER, phase))
            assertTrue(phase.name, comes(Season.AUTUMN, phase))
            assertFalse(phase.name, comes(Season.SPRING, phase))
            assertFalse(phase.name, comes(Season.WINTER, phase))
        }
        // The time of year reaches the rules from the visits themselves.
        val late = (0 until 300).flatMap {
            CreatureVisits.visitorsAt(it * CreatureVisits.PERIOD_MILLIS + 1, river, sunny, map, Season.SUMMER, phase = SeasonPhase.LATE)
        }
        assertTrue(late.isNotEmpty())
    }

    // --- Staying away ---

    /** Goes through [periods] periods as the game does: what has come is remembered from then on. */
    private fun played(periods: Int, objects: List<GridObject>): List<CreatureVisit> {
        val leftAt = HashMap<Creature, Long>()
        return (0 until periods).flatMap { period ->
            CreatureVisits.visitorsAt(period * CreatureVisits.PERIOD_MILLIS + 1, objects, sunny, map, leftAt = leftAt)
                .onEach { leftAt[it.creature] = it.leavesAt }
        }
    }

    @Test
    fun `a creature that has left stays away for a quarter of an hour`() {
        assertEquals(15L * 60 * 1000, CreatureVisits.AWAY_MILLIS)
        val visits = played(4_000, gardenFor(VisitRule.BUTTERFLY_LAVENDER))
        assertTrue(visits.size > 100)
        for ((before, after) in visits.zipWithNext()) {
            assertTrue(after.arrivesAt - before.leavesAt >= CreatureVisits.AWAY_MILLIS)
        }
        // One creature staying away keeps nobody else out.
        val both = gardenFor(VisitRule.BUTTERFLY_BLOOMS) + gardenFor(VisitRule.BIRD_COTTAGE).map { it.copy(gridX = 5, gridY = 5) }
        val seen = played(2_000, both)
        assertTrue(seen.any { it.creature == Creature.BUTTERFLY } && seen.any { it.creature == Creature.BIRD })
    }

    @Test
    fun `once the time is up it may come again`() {
        val objects = gardenFor(VisitRule.BUTTERFLY_LAVENDER)
        val visit = visitsOver(100, objects).first()
        val again = visitsOver(4_000, objects).first {
            CreatureVisits.periodOf(it.arrivesAt) > CreatureVisits.periodOf(visit.arrivesAt) && it.arrivesAt >= visit.leavesAt + CreatureVisits.AWAY_MILLIS
        }
        val now = again.arrivesAt
        val leftAt = mapOf(Creature.BUTTERFLY to visit.leavesAt)
        assertEquals(listOf(again), CreatureVisits.visitorsAt(now, objects, sunny, map, leftAt = leftAt))
    }

    @Test
    fun `its own stay this period does not count as having left`() {
        val objects = gardenFor(VisitRule.BUTTERFLY_LAVENDER)
        val visit = visitsOver(100, objects).first()
        val now = visit.arrivesAt + 1_000
        // Written when it arrived, and looked at again when the garden changes under it.
        val leftAt = mapOf(Creature.BUTTERFLY to visit.leavesAt)
        assertEquals(listOf(visit), CreatureVisits.visitorsAt(now, objects, sunny, map, leftAt = leftAt))
    }

    @Test
    fun `a time from the future, the clock having been put back, keeps nobody away`() {
        val objects = gardenFor(VisitRule.BUTTERFLY_LAVENDER)
        val visit = visitsOver(100, objects).first()
        val leftAt = mapOf(Creature.BUTTERFLY to visit.leavesAt + 24 * 60 * 60 * 1000L)
        assertEquals(listOf(visit), CreatureVisits.visitorsAt(visit.arrivesAt, objects, sunny, map, leftAt = leftAt))
    }

    @Test
    fun `butterflies, dragonflies and frogs stay away on a snowy day`() {
        for (rule in listOf(VisitRule.BUTTERFLY_BLOOMS, VisitRule.BUTTERFLY_LAVENDER, VisitRule.DRAGONFLY_AUTUMN, VisitRule.FROG_RAIN)) {
            assertFalse(rule.name, rule.isMetBy(GardenSnapshot(gardenFor(rule), WeatherType.SNOWY, seasonFor(rule))))
        }
        // The rest do not mind the snow.
        assertTrue(VisitRule.BIRD_WELL.isMetBy(GardenSnapshot(gardenFor(VisitRule.BIRD_WELL), WeatherType.SNOWY, Season.WINTER)))
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
        // Birds, ladybirds and squirrels have gone to rest at night.
        for (rule in VisitRule.entries.filter { it.creature in setOf(Creature.BIRD, Creature.LADYBUG, Creature.SQUIRREL) }) {
            assertEquals(rule.name, listOf(true, true, true, false), TimeOfDay.entries.map { comesAt(rule, it) })
        }
        // Frogs come at any hour.
        for (rule in VisitRule.forCreature(Creature.FROG)) assertTrue(rule.name, TimeOfDay.entries.all { comesAt(rule, it) })
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

    // --- Who is out at night, and a tap near a visitor ---

    @Test
    fun `at night the frogs stay out, and the ladybirds and the squirrels have gone to rest`() {
        val outAtNight = VisitRule.entries.filter { rule ->
            rule.conditions.filterIsInstance<VisitCondition.TimeIs>().all { TimeOfDay.NIGHT in it.times }
        }
        assertEquals(setOf(Creature.FROG), outAtNight.map { it.creature }.toSet())
        // By day they come as before.
        for (rule in VisitRule.forCreature(Creature.LADYBUG) + VisitRule.forCreature(Creature.SQUIRREL)) {
            assertTrue(rule.name, rule.conditions.filterIsInstance<VisitCondition.TimeIs>().single().times.containsAll(listOf(TimeOfDay.MORNING, TimeOfDay.DAY, TimeOfDay.EVENING)))
        }
    }

    @Test
    fun `a visitor takes a tap only with something new for the notes, and the tap is otherwise the garden's`() {
        val rule = VisitRule.BIRD_BENCH
        assertTrue(NatureNotes(emptyList()).isNews(rule))
        val creature = NatureNote(NatureNotes.creatureKey(rule.creature), 0L, 1)
        val combination = NatureNote(NatureNotes.ruleKey(rule), 0L, 1)
        // A creature met before, here for something new.
        assertTrue(NatureNotes(listOf(creature)).isNews(rule))
        assertFalse(NatureNotes(listOf(creature, combination)).isNews(rule))
        val canvas = sourceOf("ui/components/GardenCanvas.kt")
        assertTrue(canvas.contains("if (visitor != null && currentOnVisitorTapped(visitor)) return@detectTapGestures"))
        // And with a cell being looked for, no visitor comes into it.
        assertTrue(canvas.contains("val visitor = if (currentEditing) null else visitorAt("))
    }

    @Test
    fun `a visitor can be tapped however far out the garden is zoomed`() {
        val finger = 63f
        // Up close, the circle goes with the tile; far out, it stays a finger across.
        assertEquals(330f * TapTargets.VISITOR_REACH, TapTargets.visitorReach(330f, finger), 1e-3f)
        assertEquals(finger, TapTargets.visitorReach(60f, finger), 0f)
    }

    private fun sourceOf(path: String): String =
        listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
            .resolve("java/com/studioguu/miniaturegarden/$path").readText()
}
