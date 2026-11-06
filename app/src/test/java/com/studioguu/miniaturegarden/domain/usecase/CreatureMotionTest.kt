package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.CreatureAction
import com.studioguu.miniaturegarden.domain.model.CreaturePerches
import com.studioguu.miniaturegarden.domain.model.PerchKind
import com.studioguu.miniaturegarden.domain.model.PlantTop
import com.studioguu.miniaturegarden.domain.model.CreaturePose
import com.studioguu.miniaturegarden.domain.model.CreatureRoute
import com.studioguu.miniaturegarden.domain.model.CreatureRoutes
import com.studioguu.miniaturegarden.domain.model.CreatureVisit
import com.studioguu.miniaturegarden.domain.model.CreatureVisits
import com.studioguu.miniaturegarden.domain.model.Facing
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.render.PlantTops
import com.studioguu.miniaturegarden.ui.render.VisitorLook
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the visitors go about the garden: [CreatureRoutes]. */
class CreatureMotionTest {

    private var nextId = 0

    private fun at(x: Int, y: Int, objectId: String, growth: Float = 0f) = GridObject(
        instanceId = "o${nextId++}", gridX = x, gridY = y, objectId = objectId, growthProgress = growth, lastUpdatedTimestamp = 0L,
    )

    /**
     * A garden full of things a creature cannot walk onto, round a rock, a well and two ponds:
     * a river across the top (from the map and laid), benches, fences, a cottage, a shiitake log,
     * with crops and lawn left to walk through.
     */
    private val map = GardenMap.parse(
        "~~~~~~~~~~~",
        "...........",
        "...........",
        "...........",
        "...........",
        "...........",
        "...........",
        "...........",
        "...........",
        "...........",
        "...........",
    )
    private val rock = at(5, 5, "deco_rock")
    private val well = at(5, 7, StructureData.WELL.objectId)
    private val objects = listOf(
        rock, well,
        at(1, 1, GridObject.RIVER_OBJECT_ID), at(2, 1, GridObject.RIVER_OBJECT_ID), at(3, 1, GridObject.RIVER_OBJECT_ID),
        at(4, 4, "deco_bench"), at(6, 4, "struct_fence"), at(7, 5, "struct_fence"),
        at(4, 6, "plant_shiitake", 1f), at(6, 6, "plant_sunflower", 1f), at(3, 5, "ground_grass"),
        at(6, 8, GridObject.POND_OBJECT_ID), at(4, 8, GridObject.POND_OBJECT_ID),
        at(8, 1, StructureData.COTTAGE.objectId),
        at(8, 8, "plant_tulip"), at(9, 8, "plant_tulip"), at(10, 8, "plant_tulip"),
    )
    private val grid = GardenGrid(map, objects)

    /** Cells no one may walk onto (a frog may go into the ponds). */
    private val blocked: Set<GridCell> = buildSet {
        for (x in 0 until 11) add(GridCell(x, 0))
        for (obj in objects) if (obj.objectId != "plant_sunflower" && obj.objectId != "ground_grass" && obj.objectId != "plant_tulip") addAll(obj.occupiedCells())
    }
    private val stream = setOf(GridCell(1, 1), GridCell(2, 1), GridCell(3, 1))
    private val ponds = setOf(GridCell(6, 8), GridCell(4, 8))

    private fun visit(rule: VisitRule, cell: GridCell, host: GridObject? = null, arrivesAt: Long = 1_000_000L, stay: Long = 200_000L) =
        CreatureVisit(rule, cell, host, arrivesAt, arrivesAt + stay)

    /** The poses of [route] over its whole visit, every [step] millis. */
    private fun posesOf(route: CreatureRoute, step: Long = 50L): List<Pair<Long, CreaturePose>> =
        (route.visit.arrivesAt until route.visit.leavesAt step step).map { it to route.poseAt(it)!! }

    private fun isInside(pose: CreaturePose) = pose.x >= 0f && pose.y >= 0f && pose.x < map.size && pose.y < map.size

    // --- The same moment, the same pose ---

    @Test
    fun `the same moment finds a visitor in the same place, facing the same way`() {
        val visits = listOf(
            visit(VisitRule.SQUIRREL_SUNFLOWER, GridCell(5, 4)),
            visit(VisitRule.FROG_RAIN, GridCell(5, 8)),
            visit(VisitRule.BUTTERFLY_BLOOMS, GridCell(6, 6), host = objects.first { it.objectId == "plant_sunflower" }),
            visit(VisitRule.DRAGONFLY_AUTUMN, GridCell(2, 1), host = objects.first { it.objectId == GridObject.RIVER_OBJECT_ID }),
            visit(VisitRule.LADYBUG_CROPS, GridCell(8, 8), host = objects.first { it.objectId == "plant_tulip" }),
            visit(VisitRule.BIRD_COTTAGE, GridCell(10, 3), host = objects.first { it.objectId == StructureData.COTTAGE.objectId }),
        )
        for (v in visits) {
            // Worked out twice, as after the screen is turned or the app comes back.
            val first = CreatureRoutes.plan(v, grid)
            val again = CreatureRoutes.plan(v.copy(), GardenGrid(map, objects.toList()))
            for (now in v.arrivesAt until v.leavesAt step 997L) assertEquals("${v.rule} at $now", first.poseAt(now), again.poseAt(now))
        }
    }

    @Test
    fun `the visits of a period get the same routes however often they are worked out`() {
        val garden = listOf(at(5, 5, "deco_rock"))
        for (period in 0 until 40L) {
            val now = CreatureVisits.periodStart(period) + 100_000L
            val a = CreatureVisits.visitorsAt(now, garden, WeatherType.SUNNY, GardenMap.DEFAULT, Season.AUTUMN)
            val b = CreatureVisits.visitorsAt(now + 30_000L, garden, WeatherType.SUNNY, GardenMap.DEFAULT, Season.AUTUMN)
            assertEquals(a, b)
            val g = GardenGrid(GardenMap.DEFAULT, garden)
            for ((x, y) in a.zip(b)) assertEquals(CreatureRoutes.plan(x, g).poseAt(now + 60_000L), CreatureRoutes.plan(y, g).poseAt(now + 60_000L))
        }
    }

    @Test
    fun `different visits go different ways`() {
        val a = CreatureRoutes.plan(visit(VisitRule.SQUIRREL_SUNFLOWER, GridCell(5, 4), arrivesAt = 1_000_000L), grid)
        val b = CreatureRoutes.plan(visit(VisitRule.SQUIRREL_SUNFLOWER, GridCell(5, 4), arrivesAt = 2_000_000L), grid)
        val differ = (0 until 200_000L step 1_000L).count { a.poseAt(1_000_000L + it)?.let { p -> p.x to p.y } != b.poseAt(2_000_000L + it)?.let { p -> p.x to p.y } }
        assertTrue(differ > 50)
    }

    // --- On foot ---

    @Test
    fun `those that walk keep off water, buildings, fences, rocks, benches and logs, and to the garden`() {
        val starts = listOf(GridCell(5, 4), GridCell(5, 6), GridCell(4, 5), GridCell(6, 5))
        for (rule in listOf(VisitRule.SQUIRREL_SUNFLOWER, VisitRule.BIRD_WELL, VisitRule.FROG_RAIN)) {
            for (start in starts) for (arrival in 0 until 12L) {
                val route = CreatureRoutes.plan(visit(rule, start, arrivesAt = 7_000_000L + arrival * 333_331L), grid)
                for ((now, pose) in posesOf(route)) {
                    // The bird flies in and away (#71): over anything, from outside the garden.
                    if (pose.action == CreatureAction.FLY) continue
                    assertTrue("$rule at $now in ${pose.cell}", isInside(pose))
                    // The bird come for the well may sit on its rim instead : not walking.
                    if (pose.surface > 0f) {
                        assertEquals(VisitRule.BIRD_WELL, rule)
                        assertEquals(CreaturePerches.WELL_RIM_HEIGHT, pose.surface)
                        continue
                    }
                    val cell = GridCell(floor(pose.x).toInt(), floor(pose.y).toInt())
                    assertEquals(cell, pose.cell)
                    assertTrue(pose.grounded)
                    val allowed = cell !in blocked || (rule.creature == Creature.FROG && cell in ponds)
                    assertTrue("$rule at $now walked onto $cell", allowed)
                    assertTrue(CreatureRoutes.canWalk(rule.creature, grid, cell.x, cell.y))
                }
            }
        }
    }

    @Test
    fun `only the frog goes into a pond`() {
        val start = GridCell(5, 8)
        var frogInPond = false
        for (arrival in 0 until 30L) {
            val frog = CreatureRoutes.plan(visit(VisitRule.FROG_RAIN, start, arrivesAt = 3_000_000L + arrival * 299_993L), grid)
            val squirrel = CreatureRoutes.plan(visit(VisitRule.SQUIRREL_SUNFLOWER, start, arrivesAt = 3_000_000L + arrival * 299_993L), grid)
            for ((_, pose) in posesOf(frog, 250L)) {
                assertEquals(pose.cell in ponds, pose.inPond)
                if (pose.inPond) frogInPond = true
            }
            for ((_, pose) in posesOf(squirrel, 250L)) {
                assertFalse(pose.cell in ponds)
                assertFalse(pose.inPond)
            }
        }
        assertTrue("the frog never went into a pond", frogInPond)
        assertTrue(CreatureRoutes.canWalk(Creature.FROG, grid, 6, 8))
        for (creature in Creature.entries - Creature.FROG) assertFalse(CreatureRoutes.canWalk(creature, grid, 6, 8))
    }

    @Test
    fun `each new picture shows only in its own moment, and is never cut by the water`() {
        fun pose(action: CreatureAction, elapsed: Long, duration: Long, inPond: Boolean = false) =
            CreaturePose(5f, 5f, 0f, Facing.LEFT, action, elapsed, duration, GridCell(5, 5), inPond = inPond, setsOff = true, stops = true)
        // The frog: in its leaping picture in the air, sitting on the ground, in the water and out of it.
        for (inPond in listOf(false, true)) {
            assertFalse(VisitorLook.of(Creature.FROG, pose(CreatureAction.STEP, 0L, 600L, inPond), 0L).pose)
            assertFalse(VisitorLook.of(Creature.FROG, pose(CreatureAction.STEP, 600L, 600L, inPond), 0L).pose)
            assertFalse(VisitorLook.of(Creature.FROG, pose(CreatureAction.STAND, 100L, 600L, inPond), 0L).pose)
            for (elapsed in 0L..600L step 10L) {
                val look = VisitorLook.of(Creature.FROG, pose(CreatureAction.STEP, elapsed, 600L, inPond), 0L)
                assertFalse(look.pose && look.submerged)
            }
            assertTrue(VisitorLook.of(Creature.FROG, pose(CreatureAction.STEP, 300L, 600L, inPond), 0L).pose)
        }
        // The squirrel: stretched out at the top of a bound, gathered up at its foot.
        val run = (0L until 760L step 10L).map { VisitorLook.of(Creature.SQUIRREL, pose(CreatureAction.STEP, it, 760L), 0L) }
        assertTrue(run.any { it.pose } && run.any { !it.pose })
        assertTrue(run.zipWithNext().count { (a, b) -> a.pose != b.pose } >= 4)
        for (look in run) assertEquals(look.lift < 0.06f * 0.45f, look.pose)
        // The bird: its pecking picture in a peck, and not between.
        val stop = (0L until 4000L step 10L).map { VisitorLook.of(Creature.BIRD, pose(CreatureAction.STAND, it, 4000L), 0L) }
        assertTrue(stop.any { it.pose } && stop.any { !it.pose })
        for (creature in listOf(Creature.BIRD, Creature.SQUIRREL, Creature.FROG)) assertFalse(VisitorLook.still(creature).pose)
    }

    @Test
    fun `a frog keeps to the bank of a stream, and only a pond is its to go into`() {
        for (arrival in 0 until 30L) {
            val frog = CreatureRoutes.plan(visit(VisitRule.FROG_RAIN, GridCell(2, 2), arrivesAt = 3_000_000L + arrival * 299_993L), grid)
            for ((_, pose) in posesOf(frog, 250L)) {
                assertFalse(pose.cell in stream)
                assertFalse(pose.inPond)
            }
        }
        for (creature in Creature.entries) assertFalse(CreatureRoutes.canWalk(creature, grid, 2, 1))
    }

    @Test
    fun `a dragonfly turns on the spot, shoots off, and holds its heading while it hovers`() {
        val river = objects.first { it.objectId == GridObject.RIVER_OBJECT_ID }
        val route = CreatureRoutes.plan(visit(VisitRule.DRAGONFLY_AUTUMN, GridCell(1, 1), river), grid)
        val poses = posesOf(route, 10L).map { it.second }
        val darts = poses.filter { it.action == CreatureAction.FLY && it.altitude < 0.5f }
        assertTrue(darts.size > 20)
        for ((a, b) in poses.zipWithNext()) {
            if (a.action == CreatureAction.HOVER && b.action == CreatureAction.HOVER && a.elapsed < b.elapsed) assertEquals(a.heading, b.heading, 1e-3f)
            // In the air its turns are short and quick: never a sudden flip from one frame to the next.
            if (a.action == b.action && a.elapsed < b.elapsed) assertTrue(abs(CreatureRoutes.shortestTurn(a.heading, b.heading)) < 40f)
            // It does not set off until it has turned.
            if (b.action == CreatureAction.FLY && b.duration < 2_000L && b.elapsed < CreatureRoutes.TURN_MILLIS && a.action == CreatureAction.FLY && a.elapsed < b.elapsed) {
                assertEquals(a.x, b.x, 1e-4f)
            }
        }
    }

    @Test
    fun `a lawn and paving can be walked through, and among the crops only by the squirrel`() {
        assertTrue(CreatureRoutes.canWalk(Creature.SQUIRREL, grid, 6, 6))
        for (creature in Creature.entries - Creature.SQUIRREL) assertFalse(CreatureRoutes.canWalk(creature, grid, 6, 6))
        // Neither the ladybird nor the fliers walk on the ground at all.
        for (creature in listOf(Creature.LADYBUG, Creature.BUTTERFLY, Creature.DRAGONFLY)) assertFalse(CreatureRoutes.canWalk(creature, grid, 2, 3))
        for (creature in listOf(Creature.SQUIRREL, Creature.BIRD, Creature.FROG)) {
            assertTrue(CreatureRoutes.canWalk(creature, grid, 3, 5))
            assertTrue(CreatureRoutes.canWalk(creature, grid, 2, 3))
            assertFalse(CreatureRoutes.canWalk(creature, grid, 4, 6))
            assertFalse(CreatureRoutes.canWalk(creature, grid, 2, 0))
            assertFalse(CreatureRoutes.canWalk(creature, grid, -1, 5))
        }
    }

    @Test
    fun `one that walks moves, stops and looks about, without jumping from place to place`() {
        val route = CreatureRoutes.plan(visit(VisitRule.SQUIRREL_SUNFLOWER, GridCell(5, 4)), grid)
        val poses = posesOf(route)
        val actions = poses.map { it.second.action }.toSet()
        assertTrue(actions.containsAll(listOf(CreatureAction.APPEAR, CreatureAction.STEP, CreatureAction.STAND, CreatureAction.VANISH)))
        assertTrue(poses.map { it.second.facing }.toSet() == Facing.entries.toSet())
        for ((a, b) in poses.zipWithNext()) {
            assertTrue("jumped at ${b.first}", hypot(b.second.x - a.second.x, b.second.y - a.second.y) < 0.2f)
        }
        // It faces the way it goes on screen.
        val steps = poses.zipWithNext().filter { (a, b) ->
            a.second.action == CreatureAction.STEP && b.second.action == CreatureAction.STEP && b.second.elapsed > a.second.elapsed
        }
        for ((a, b) in steps) {
            val screen = (b.second.x - a.second.x) - (b.second.y - a.second.y)
            if (screen > 0.001f) assertEquals(Facing.RIGHT, b.second.facing)
            if (screen < -0.001f) assertEquals(Facing.LEFT, b.second.facing)
        }
        // It stays near what it came for.
        for ((_, pose) in poses) assertTrue(abs(pose.cell.x - 5) + abs(pose.cell.y - 4) <= 3)
    }

    @Test
    fun `one on what it came for moves about a little on one of its points, drawn by its front cell`() {
        val cottage = objects.first { it.objectId == StructureData.COTTAGE.objectId }
        val tulip = objects.first { it.objectId == "plant_tulip" }
        for ((rule, host) in listOf(VisitRule.BIRD_COTTAGE to cottage, VisitRule.LADYBUG_CROPS to tulip)) {
            val v = visit(rule, CreatureVisits.frontCell(host), host)
            val route = CreatureRoutes.plan(v, grid)
            val points = CreaturePerches.pointsOf(host)
            val poses = posesOf(route)
            assertTrue(poses.any { it.second.action == CreatureAction.STEP })
            for ((_, pose) in poses) {
                assertEquals(v.cell, pose.cell)
                assertFalse(pose.grounded)
                if (pose.action == CreatureAction.FLY) continue
                assertTrue(points.any { abs(pose.x - it.x) <= 0.25f && abs(pose.y - it.y) <= 0.25f && pose.surface == it.height })
                assertEquals(0f, pose.altitude)
            }
        }
    }

    // --- Popping up and away ---

    @Test
    fun `one that walks pops up where it comes and pops away where it is, quickly`() {
        assertTrue(CreatureRoutes.POP_MILLIS in 300L..500L)
        for (rule in listOf(VisitRule.SQUIRREL_SUNFLOWER, VisitRule.FROG_RAIN, VisitRule.LADYBUG_CROPS)) {
            val tulip = objects.first { it.objectId == "plant_tulip" }
            val v = if (rule == VisitRule.LADYBUG_CROPS) visit(rule, GridCell(8, 8), tulip) else visit(rule, GridCell(5, 6))
            val route = CreatureRoutes.plan(v, grid)
            val first = route.poseAt(v.arrivesAt)!!
            assertEquals(CreatureAction.APPEAR, first.action)
            assertEquals(0f, first.progress)
            assertEquals(v.cell, first.cell)
            if (rule != VisitRule.LADYBUG_CROPS) {
                assertEquals(5.5f, first.x)
                assertEquals(6.5f, first.y)
            }
            assertEquals(CreatureAction.APPEAR, route.poseAt(v.arrivesAt + CreatureRoutes.POP_MILLIS - 1)!!.action)
            assertTrue(route.poseAt(v.arrivesAt + CreatureRoutes.POP_MILLIS)!!.action != CreatureAction.APPEAR)
            val going = route.poseAt(v.leavesAt - CreatureRoutes.POP_MILLIS)!!
            val last = route.poseAt(v.leavesAt - 1)!!
            assertEquals(CreatureAction.VANISH, going.action)
            assertEquals(CreatureAction.VANISH, last.action)
            // Where it stood, not moving as it goes.
            assertEquals(going.x, last.x)
            assertEquals(going.y, last.y)
            val before = route.poseAt(v.leavesAt - CreatureRoutes.POP_MILLIS - 1)!!
            assertEquals(before.x, going.x, 0.001f)
            assertEquals(before.y, going.y, 0.001f)
            assertNull(route.poseAt(v.leavesAt))
            assertNull(route.poseAt(v.arrivesAt - 1))
        }
    }

    @Test
    fun `a pop grows a little past its size and settles, and shrinks away to nothing`() {
        val v = visit(VisitRule.SQUIRREL_SUNFLOWER, GridCell(5, 6))
        val route = CreatureRoutes.plan(v, grid)
        val sizes = (0..CreatureRoutes.POP_MILLIS step 20L).map { VisitorLook.of(Creature.SQUIRREL, route.poseAt(v.arrivesAt + it.coerceAtMost(CreatureRoutes.POP_MILLIS - 1))!!, 0L).scaleX }
        assertEquals(0f, sizes.first(), 0.001f)
        assertTrue(sizes.max() > 1.05f)
        assertEquals(1f, sizes.last(), 0.05f)
        val away = ((0 until CreatureRoutes.POP_MILLIS step 20L) + (CreatureRoutes.POP_MILLIS - 1)).map { VisitorLook.of(Creature.SQUIRREL, route.poseAt(v.leavesAt - CreatureRoutes.POP_MILLIS + it)!!, 0L).scaleX }
        assertEquals(1f, away.first(), 0.001f)
        assertTrue(away.max() > 1.05f)
        assertTrue(away.last() < 0.05f)
    }

    @Test
    fun `the bird flies in from outside the garden, lands where it comes, and flies away out of it (#71)`() {
        val cottage = objects.first { it.objectId == StructureData.COTTAGE.objectId }
        for (arrival in 0 until 10L) {
            for (v in listOf(
                visit(VisitRule.BIRD_WELL, GridCell(5, 6), arrivesAt = 9_000_000L + arrival * 77_777L),
                visit(VisitRule.BIRD_COTTAGE, CreatureVisits.frontCell(cottage), cottage, arrivesAt = 9_000_000L + arrival * 77_777L),
            )) {
                val route = CreatureRoutes.plan(v, grid)
                val poses = posesOf(route)
                assertTrue(poses.none { it.second.action == CreatureAction.APPEAR || it.second.action == CreatureAction.VANISH })
                val first = poses.first().second
                val last = route.poseAt(v.leavesAt - 1)!!
                assertEquals(CreatureAction.FLY, first.action)
                assertFalse("${v.rule} came from inside the garden", isInside(first))
                assertEquals(CreatureRoutes.SKY_ALTITUDE, first.altitude, 0.01f)
                assertEquals(CreatureAction.FLY, last.action)
                assertFalse("${v.rule} left inside the garden", isInside(last))
                // Down on its spot once it has landed (the ground it came to, the well's rim or a
                // point of the roof), and on its feet from then until it takes off.
                val landed = poses.first { it.second.action != CreatureAction.FLY }.second
                val spots = (if (v.host == null) CreaturePerches.pointsOf(well) else CreaturePerches.pointsOf(cottage)).map { it.x to it.y } +
                    (if (v.host == null) listOf(5.5f to 6.5f) else emptyList())
                assertTrue("${v.rule} landed at ${landed.x}, ${landed.y}", spots.any { abs(it.first - landed.x) < 0.001f && abs(it.second - landed.y) < 0.001f })
                for ((_, pose) in poses) if (pose.action != CreatureAction.FLY) assertEquals(0f, pose.altitude)
                for ((a, b) in poses.zipWithNext()) assertTrue("jumped at ${b.first}", hypot(b.second.x - a.second.x, b.second.y - a.second.y) < 0.5f)
            }
        }
    }

    @Test
    fun `a move on foot is marked where it sets off and where it stops`() {
        val route = CreatureRoutes.plan(visit(VisitRule.SQUIRREL_SUNFLOWER, GridCell(5, 4)), grid)
        val poses = posesOf(route, 10L).map { it.second }
        assertTrue(poses.any { it.setsOff } && poses.any { it.stops })
        // Every move on foot starts with a step that sets off, and ends with one that stops.
        for ((a, b) in poses.zipWithNext()) {
            if (a.action != CreatureAction.STEP && b.action == CreatureAction.STEP) assertTrue(b.setsOff)
            if (a.action == CreatureAction.STEP && b.action != CreatureAction.STEP) assertTrue(a.stops)
            if (b.action != CreatureAction.STEP) assertFalse(b.setsOff || b.stops)
        }
    }

    // --- In flight ---

    @Test
    fun `those that fly come in from outside the garden and fly away out of it`() {
        val sunflower = objects.first { it.objectId == "plant_sunflower" }
        val river = objects.first { it.objectId == GridObject.RIVER_OBJECT_ID }
        for (arrival in 0 until 20L) {
            for ((rule, host) in listOf(VisitRule.BUTTERFLY_BLOOMS to sunflower, VisitRule.DRAGONFLY_AUTUMN to river)) {
                val v = visit(rule, CreatureVisits.frontCell(host), host, arrivesAt = 5_000_000L + arrival * 123_457L)
                val route = CreatureRoutes.plan(v, grid)
                val first = route.poseAt(v.arrivesAt)!!
                val last = route.poseAt(v.leavesAt - 1)!!
                assertFalse("$rule came from inside the garden", isInside(first))
                assertFalse("$rule left inside the garden", isInside(last))
                assertEquals(CreatureRoutes.SKY_ALTITUDE, first.altitude, 0.01f)
                assertTrue(last.altitude > 1.4f)
                assertEquals(CreatureAction.FLY, first.action)
                assertEquals(CreatureAction.FLY, last.action)
                // In between, it keeps over the garden, near what it came for, and never pops.
                val poses = posesOf(route)
                assertTrue(poses.none { it.second.action == CreatureAction.APPEAR || it.second.action == CreatureAction.VANISH })
                val middle = route.poseAt(v.arrivesAt + 60_000L)!!
                assertTrue(isInside(middle))
                // A dragonfly settles on banks and flowers up to three cells off.
                assertTrue(hypot(middle.x - (host.gridX + 0.5f), middle.y - (host.gridY + 0.5f)) < 4.5f)
                for ((a, b) in poses.zipWithNext()) assertTrue("jumped at ${b.first}", hypot(b.second.x - a.second.x, b.second.y - a.second.y) < 0.5f)
            }
        }
    }

    @Test
    fun `a butterfly and a dragonfly settle now and then, only where they may, at the height of the point`() {
        val sunflower = objects.first { it.objectId == "plant_sunflower" }
        val river = objects.first { it.objectId == GridObject.RIVER_OBJECT_ID }
        val all = CreaturePerches.inGarden(grid)
        for ((v, creature) in listOf(
            visit(VisitRule.BUTTERFLY_BLOOMS, GridCell(6, 6), sunflower) to Creature.BUTTERFLY,
            visit(VisitRule.DRAGONFLY_AUTUMN, GridCell(1, 1), river) to Creature.DRAGONFLY,
        )) {
            val route = CreatureRoutes.plan(v, grid)
            val poses = posesOf(route, 50L).map { it.second }
            val perched = poses.filter { it.action == CreatureAction.PERCH }
            assertTrue("$creature never settled", perched.isNotEmpty())
            for (pose in perched) {
                val spot = all.filter { abs(it.x - pose.x) <= 0.05f && abs(it.y - pose.y) <= 0.05f && it.height == pose.altitude }
                assertTrue("$creature settled at ${pose.x}, ${pose.y}, ${pose.altitude}", spot.any { CreaturePerches.allows(creature, it.kind) })
            }
        }
        // The dragonfly holds still in the air between darts too.
        val dragonfly = CreatureRoutes.plan(visit(VisitRule.DRAGONFLY_AUTUMN, GridCell(1, 1), river), grid)
        assertTrue(CreatureAction.HOVER in posesOf(dragonfly, 50L).map { it.second.action })
    }

    @Test
    fun `a frog goes a short way at a time, to no cell's middle, and no two moves alike`() {
        val frog = CreatureRoutes.plan(visit(VisitRule.FROG_RAIN, GridCell(5, 6)), grid)
        val poses = posesOf(frog, 20L).map { it.second }
        val moves = poses.filter { it.action == CreatureAction.STEP }.groupBy { it.duration }
        assertTrue(moves.size >= 3)
        val stops = poses.filter { it.action == CreatureAction.STAND }.map { it.x to it.y }.distinct()
        assertTrue(stops.size >= 3)
        assertTrue(stops.drop(1).any { (x, y) -> x % 1f != 0.5f || y % 1f != 0.5f })
        for ((a, b) in stops.zipWithNext()) assertTrue(hypot(b.first - a.first, b.second - a.second) < 1.1f)
    }

    @Test
    fun `a butterfly drifts over the flowers near the one it came for, and rests on them`() {
        val flowers = listOf(at(4, 4, "plant_tulip", 1f), at(6, 4, "plant_rose", 1f), at(5, 6, "plant_tulip", 1f))
        val garden = GardenGrid(GardenMap.allGrass(11), flowers)
        val butterfly = CreatureRoutes.plan(visit(VisitRule.BUTTERFLY_BLOOMS, GridCell(4, 4), flowers[0]), garden)
        val perched = posesOf(butterfly, 100L).map { it.second }.filter { it.action == CreatureAction.PERCH }
        val visited = perched.map { floor(it.x).toInt() to floor(it.y).toInt() }.toSet()
        assertTrue(visited.toString(), visited.size >= 2)
    }

    // --- Where they may be ---

    @Test
    fun `each creature may be only where the table says`() {
        val expected = mapOf(
            Creature.BIRD to setOf(PerchKind.GROUND, PerchKind.ROOF, PerchKind.WELL_RIM, PerchKind.FENCE, PerchKind.BENCH),
            Creature.BUTTERFLY to setOf(PerchKind.FLOWER, PerchKind.WEATHERVANE, PerchKind.WELL_RIM, PerchKind.BENCH),
            Creature.SQUIRREL to setOf(PerchKind.GROUND),
            Creature.DRAGONFLY to setOf(PerchKind.FLOWER, PerchKind.WELL_RIM, PerchKind.WEATHERVANE, PerchKind.POND_EDGE, PerchKind.RIVER_EDGE),
            Creature.LADYBUG to setOf(PerchKind.FLOWER, PerchKind.LEAF, PerchKind.WELL_RIM, PerchKind.FENCE, PerchKind.BENCH),
            Creature.FROG to setOf(PerchKind.POND, PerchKind.POND_EDGE, PerchKind.WELL_RIM, PerchKind.GROUND, PerchKind.BENCH),
        )
        assertEquals(expected, CreaturePerches.ALLOWED)
        // The roofs are the bird's alone, and the weathervane is not.
        for (creature in Creature.entries - Creature.BIRD) assertFalse(CreaturePerches.allows(creature, PerchKind.ROOF))
        assertFalse(CreaturePerches.allows(Creature.BIRD, PerchKind.WEATHERVANE))
        assertFalse(CreaturePerches.allows(Creature.FROG, PerchKind.FENCE))
    }

    @Test
    fun `points are set on the well, the cottage, fences, benches and the weathervane, and on no rock, lantern, bridge or hut`() {
        val things = listOf(
            at(1, 1, StructureData.WELL.objectId), at(2, 2, StructureData.COTTAGE.objectId), at(6, 1, "struct_fence"),
            at(7, 1, "deco_bench"), at(8, 1, "deco_weathervane"), at(1, 6, "deco_rock"), at(2, 6, "deco_lantern"),
            at(3, 6, StructureData.HUT.objectId), at(6, 6, StructureData.BRIDGE.objectId),
        )
        val kinds = things.associate { it.objectId to CreaturePerches.pointsOf(it).map { p -> p.kind }.toSet() }
        assertEquals(setOf(PerchKind.WELL_RIM), kinds[StructureData.WELL.objectId])
        assertEquals(setOf(PerchKind.ROOF), kinds[StructureData.COTTAGE.objectId])
        assertEquals(setOf(PerchKind.FENCE), kinds["struct_fence"])
        assertEquals(setOf(PerchKind.BENCH), kinds["deco_bench"])
        assertEquals(setOf(PerchKind.WEATHERVANE), kinds["deco_weathervane"])
        for (id in listOf("deco_rock", "deco_lantern", StructureData.HUT.objectId, StructureData.BRIDGE.objectId)) assertEquals(id, emptySet<PerchKind>(), kinds[id])
        // Each is drawn over what it is on: by its front cell.
        for (thing in things) for (p in CreaturePerches.pointsOf(thing)) assertEquals(CreatureVisits.frontCell(thing), p.cell)
    }

    @Test
    fun `a flower is one of the eight in bloom, or wild flowers, and any other crop is leaves`() {
        val bloom = listOf("plant_cosmos", "plant_rose", "plant_tulip", "plant_lavender", "plant_sunflower", "plant_nanohana", "plant_snowdrop", "plant_poinsettia")
        for (id in bloom) {
            assertEquals(id, PerchKind.FLOWER, CreaturePerches.pointsOf(at(1, 1, id, 1f)).single().kind)
            assertEquals(id, PerchKind.LEAF, CreaturePerches.pointsOf(at(1, 1, id, 0.6f)).single().kind)
        }
        // Whatever their pictures show in flower, the strawberry, the watermelon and the eggplant are leaves.
        for (id in listOf("plant_strawberry", "plant_watermelon", "plant_eggplant", "plant_tomato", "plant_mint")) {
            assertEquals(id, PerchKind.LEAF, CreaturePerches.pointsOf(at(1, 1, id, 1f)).single().kind)
        }
        assertEquals(PerchKind.FLOWER, CreaturePerches.pointsOf(at(1, 1, CreaturePerches.WILD_FLOWERS_OBJECT_ID)).single().kind)
        // The top of the picture, where the screen found it, is where the point goes: across the screen, and up.
        val leaning = CreaturePerches.pointsOf(at(3, 3, "plant_sunflower", 1f)) { PlantTop(across = 0.2f, height = 0.7f) }.single()
        assertEquals(3.7f, leaning.x, 1e-4f)
        assertEquals(3.3f, leaning.y, 1e-4f)
        assertEquals(0.7f, leaning.height)
        assertEquals(GridCell(3, 3), leaning.cell)
    }

    @Test
    fun `the banks of ponds and streams are points on the dry side`() {
        val banks = CreaturePerches.inGarden(grid).filter { it.kind == PerchKind.POND_EDGE || it.kind == PerchKind.RIVER_EDGE }
        assertTrue(banks.any { it.kind == PerchKind.POND_EDGE })
        assertTrue(banks.any { it.kind == PerchKind.RIVER_EDGE })
        for (bank in banks) {
            val cell = GridCell(floor(bank.x).toInt(), floor(bank.y).toInt())
            assertEquals(cell, bank.cell)
            assertTrue(CreaturePerches.isGround(grid, cell.x, cell.y))
        }
    }

    @Test
    fun `the bird come for the hut sits on the ground beside it, never on its roof`() {
        val hut = at(3, 3, StructureData.HUT.objectId)
        val garden = GardenGrid(GardenMap.allGrass(11), listOf(hut))
        for (arrival in 0 until 10L) {
            val v = visit(VisitRule.BIRD_HUT, CreatureVisits.frontCell(hut), hut, arrivesAt = 4_000_000L + arrival * 91_111L)
            for ((_, pose) in posesOf(CreatureRoutes.plan(v, garden), 200L)) {
                if (pose.action == CreatureAction.FLY) continue
                assertEquals(0f, pose.surface)
                assertTrue(pose.grounded)
                assertFalse(GridCell(floor(pose.x).toInt(), floor(pose.y).toInt()) in hut.occupiedCells())
            }
        }
    }

    @Test
    fun `the bird come for the well sits on its rim now and then`() {
        val rims = (0 until 20L).count { arrival ->
            val v = visit(VisitRule.BIRD_WELL, GridCell(5, 6), arrivesAt = 6_000_000L + arrival * 55_555L)
            posesOf(CreatureRoutes.plan(v, grid), 1_000L).any { it.second.surface == CreaturePerches.WELL_RIM_HEIGHT }
        }
        assertTrue(rims in 3..19)
    }

    @Test
    fun `with nowhere to settle the bird only passes over, and a ladybird is not seen`() {
        val log = at(3, 3, "plant_shiitake", 1f)
        val garden = GardenGrid(GardenMap.allGrass(11), listOf(log))
        val ladybug = CreatureRoutes.plan(visit(VisitRule.LADYBUG_CROPS, GridCell(3, 3), log), garden)
        assertTrue((1_000_000L until 1_200_000L step 500L).all { ladybug.poseAt(it) == null })
        // A hut standing in water all round, with no ground beside it.
        val hut = at(0, 0, StructureData.HUT.objectId)
        val walled = GardenGrid(GardenMap.allGrass(2), listOf(hut))
        val v = visit(VisitRule.BIRD_HUT, CreatureVisits.frontCell(hut), hut)
        val bird = CreatureRoutes.plan(v, walled)
        val seen = (v.arrivesAt until v.leavesAt step 100L).mapNotNull { bird.poseAt(it) }
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it.action == CreatureAction.FLY })
        assertNull(bird.poseAt(v.leavesAt - 1))
    }

    @Test
    fun `on the ground a visitor is behind a plant when behind its cell's middle, and one up on something is over its cell`() {
        fun pose(x: Float, y: Float, grounded: Boolean) =
            CreaturePose(x, y, 0f, Facing.LEFT, CreatureAction.STAND, 0L, 1L, GridCell(floor(x).toInt(), floor(y).toInt()), grounded = grounded)
        // A plant at (4, 4) is drawn at depth 8.
        assertTrue(CreatureRoutes.drawDepth(pose(4.3f, 4.3f, true)) < 8f)
        assertTrue(CreatureRoutes.drawDepth(pose(4.5f, 4.5f, true)) < 8f)
        assertTrue(CreatureRoutes.drawDepth(pose(4.8f, 4.7f, true)) > 8f)
        // Behind everything of the cells in front, in front of everything of the cells behind.
        for (x in listOf(4.01f, 4.5f, 4.99f)) for (y in listOf(4.01f, 4.5f, 4.99f)) {
            val depth = CreatureRoutes.drawDepth(pose(x, y, true))
            assertTrue(depth > 7f && depth < 9f)
        }
        assertEquals(8.6f, CreatureRoutes.drawDepth(pose(4.2f, 4.2f, false)), 1e-4f)
    }

    @Test
    fun `the top of a plant's picture is the middle of its first opaque rows, a little down`() {
        // 10 x 20, empty but for a block whose top row is row 5, columns 6 to 8.
        val w = 10
        val h = 20
        val px = IntArray(w * h)
        for (y in 5 until 20) for (x in 6..8) px[y * w + x] = 0xFF00FF00.toInt()
        val (fx, fy) = PlantTops.topOf(px, w, h)!!
        assertEquals(7.5f / w, fx, 1e-4f)
        assertTrue(fy > 5f / h && fy < 8f / h)
        assertNull(PlantTops.topOf(IntArray(w * h), w, h))
    }

    // --- The title's garden, whose visitors never leave ---

    @Test
    fun `a visitor that never leaves goes round and round without jumping`() {
        val cottage = objects.first { it.objectId == StructureData.COTTAGE.objectId }
        val sunflower = objects.first { it.objectId == "plant_sunflower" }
        for (v in listOf(
            CreatureVisit(VisitRule.BIRD_COTTAGE, CreatureVisits.frontCell(cottage), cottage, 0L, Long.MAX_VALUE),
            CreatureVisit(VisitRule.BUTTERFLY_BLOOMS, GridCell(6, 6), null, 0L, Long.MAX_VALUE),
            CreatureVisit(VisitRule.BUTTERFLY_BLOOMS, GridCell(6, 6), sunflower, 0L, Long.MAX_VALUE),
        )) {
            val route = CreatureRoutes.plan(v, grid)
            val now = 1_761_000_000_000L
            var last = route.poseAt(now)!!
            for (t in now until now + 2 * CreatureRoutes.CYCLE_MILLIS step 50L) {
                val pose = route.poseAt(t)!!
                assertTrue("${v.rule} jumped at $t", hypot(pose.x - last.x, pose.y - last.y) < 0.5f)
                assertTrue(pose.action != CreatureAction.VANISH)
                last = pose
            }
        }
    }
}
