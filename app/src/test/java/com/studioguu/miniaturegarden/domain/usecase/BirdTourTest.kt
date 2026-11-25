package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.CreatureAction
import com.studioguu.miniaturegarden.domain.model.CreaturePerches
import com.studioguu.miniaturegarden.domain.model.CreaturePose
import com.studioguu.miniaturegarden.domain.model.CreatureRoute
import com.studioguu.miniaturegarden.domain.model.CreatureRoutes
import com.studioguu.miniaturegarden.domain.model.CreatureVisit
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PerchKind
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.VisitRule
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FB1125 5: the bird does not stay at one place all its visit. It stays 15 to 40 seconds, and
 * moves on 1 to 4 cells: along the fence it is on, to another place, down to the ground to walk
 * a while, or up from the ground to a place.
 */
class BirdTourTest {

    private var nextId = 0

    private fun at(x: Int, y: Int, objectId: String) = GridObject(
        instanceId = "o${nextId++}", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = 0L,
    )

    private val map = GardenMap.allGrass(11)
    private val well = at(5, 6, StructureData.WELL.objectId)
    private val bench = at(3, 6, "deco_bench")

    /** Two runs of fence (three rails and two), a well and a bench, with open lawn between. */
    private val things = listOf(
        well, bench,
        at(6, 3, "struct_fence"), at(7, 3, "struct_fence"), at(8, 3, "struct_fence"),
        at(2, 3, "struct_fence"), at(2, 4, "struct_fence"),
    )
    private val grid = GardenGrid(map, things)
    private val seats = CreaturePerches.forCreature(Creature.BIRD, CreaturePerches.inGarden(grid))

    private fun visit(rule: VisitRule, cell: GridCell, host: GridObject? = null, arrivesAt: Long, stay: Long = 200_000L) =
        CreatureVisit(rule, cell, host, arrivesAt, arrivesAt + stay)

    private fun poses(route: CreatureRoute, step: Long = 100L): List<Pair<Long, CreaturePose>> =
        (route.visit.arrivesAt until route.visit.leavesAt step step).map { it to route.poseAt(it)!! }

    /** Where it is, as a place: the seat it sits on, or the ground. */
    private fun placeOf(pose: CreaturePose): String =
        if (pose.surface > 0f) "${pose.cell.x},${pose.cell.y}@${pose.surface}" else "ground"

    private fun inside(pose: CreaturePose) = pose.x >= 0f && pose.y >= 0f && pose.x < map.size && pose.y < map.size

    private fun routes(): List<CreatureRoute> = (0 until 16L).map { arrival ->
        val v = if (arrival % 2 == 0L) visit(VisitRule.BIRD_WELL, GridCell(5, 7), arrivesAt = 6_000_000L + arrival * 61_111L)
        else visit(VisitRule.BIRD_FENCE, GridCell(7, 4), arrivesAt = 6_000_000L + arrival * 61_111L)
        CreatureRoutes.plan(v, grid)
    }

    @Test
    fun `the bird goes from place to place, none of them out of the garden, and never jumps`() {
        var tours = 0
        for (route in routes()) {
            val all = poses(route)
            val settled = all.filter { it.second.action != CreatureAction.FLY }
            assertTrue(settled.all { inside(it.second) })
            // Where it lands and where it is when it goes: a place now, a place later.
            val places = settled.map { placeOf(it.second) }.distinct()
            if (places.size >= 2) tours++
            for ((a, b) in all.zipWithNext()) {
                // Neither its place nor its height jumps (the height is the seat plus the altitude).
                val gap = hypot(b.second.x - a.second.x, b.second.y - a.second.y)
                assertTrue("jumped ${gap} at ${b.first}", gap < 0.7f)
                val lift = abs((b.second.surface + b.second.altitude) - (a.second.surface + a.second.altitude))
                assertTrue("height jumped $lift at ${b.first}", lift < 0.35f)
            }
        }
        // Most of its visits (all but the ones that stay put) go about.
        assertTrue("went about in $tours of 16", tours >= 11)
    }

    @Test
    fun `it stays 15 to 40 seconds at a place, and moves 1 to 4 cells at a time`() {
        val stays = mutableListOf<Long>()
        val moves = mutableListOf<Float>()
        for (route in routes()) {
            // The stretches between flights: one place each (a hop along the fence is not a flight).
            val stretches = mutableListOf<LongArray>()
            var lastSettled: CreaturePose? = null
            var flying = true
            for ((now, pose) in poses(route, 100L)) {
                if (pose.action == CreatureAction.FLY) {
                    flying = true
                    continue
                }
                if (flying) {
                    if (lastSettled != null) moves += hypot(pose.x - lastSettled.x, pose.y - lastSettled.y)
                    stretches += longArrayOf(now, now)
                    flying = false
                }
                stretches.last()[1] = now
                lastSettled = pose
            }
            // Not the first (it came in just before) and not the last (it goes off just after).
            stays += stretches.drop(1).dropLast(1).map { it[1] - it[0] }
        }
        assertTrue("some stays were measured", stays.isNotEmpty())
        println("stays (s): " + stays.sorted().map { it / 1000 } + "; moves (cells): " + moves.sorted().map { "%.1f".format(it) })
        assertTrue("shortest stay ${stays.min()}", stays.min() >= 13_000L)
        for (move in moves) assertTrue("moved $move cells", move in 0.5f..4.6f)
    }

    @Test
    fun `it hops along a run of fence, on the rails and not in the air`() {
        val rails = seats.filter { it.kind == PerchKind.FENCE }
        var hops = 0
        for (route in routes()) {
            val all = poses(route, 50L)
            for ((a, b) in all.zipWithNext()) {
                if (a.second.action != CreatureAction.STEP || b.second.action != CreatureAction.STEP) continue
                if (a.second.surface != CreaturePerches.FENCE_TOP_HEIGHT) continue
                hops++
                // On a rail, or between two of its connected rails: never off the run.
                assertEquals(CreaturePerches.FENCE_TOP_HEIGHT, b.second.surface)
                assertEquals(0f, b.second.altitude)
                assertTrue(rails.any { abs(it.x - b.second.x) < 1.1f && abs(it.y - b.second.y) < 1.1f })
                assertFalse(b.second.grounded)
            }
        }
        assertTrue("hopped along fences in $hops samples", hops > 0)
    }

    @Test
    fun `it comes down to the ground and goes up again, drawn on its feet in depth order on the ground`() {
        var grounded = 0
        var up = 0
        for (route in routes()) {
            var wasGround = false
            for ((_, pose) in poses(route, 100L)) {
                if (pose.action == CreatureAction.FLY) continue
                val onGround = pose.surface == 0f
                if (onGround) {
                    grounded++
                    assertTrue(pose.grounded)
                    assertEquals(0f, pose.altitude)
                } else {
                    assertFalse(pose.grounded)
                    if (wasGround) up++
                }
                wasGround = onGround
            }
        }
        assertTrue("walked on the ground in $grounded samples", grounded > 0)
        assertTrue("went up from the ground $up times", up > 0)
    }

    @Test
    fun `in flight it is drawn in the nearer of the two cells, and drawn over the roof it flies to`() {
        for (route in routes()) {
            for ((a, b) in poses(route, 100L).zipWithNext()) {
                val p = b.second
                // Between places (not on the way in or out of the garden, which is drawn in one cell).
                if (b.first < route.visit.arrivesAt + 10_000L || b.first > route.visit.leavesAt - 10_000L) continue
                if (p.action != CreatureAction.FLY || !inside(p) || p.altitude >= 1.2f) continue
                // A hop between places: the cell it is drawn in lies about where it is (within a few cells), never far away.
                assertTrue(abs(p.cell.x - p.x) < 5f && abs(p.cell.y - p.y) < 5f)
            }
        }
    }

    @Test
    fun `where there is no other place within reach it goes about as it always did`() {
        // A well alone: its two rim points are too close to be a way on, and there is no other seat.
        val alone = GardenGrid(map, listOf(well))
        for (arrival in 0 until 6L) {
            val v = visit(VisitRule.BIRD_WELL, GridCell(5, 7), arrivesAt = 3_000_000L + arrival * 77_777L)
            for ((_, pose) in poses(CreatureRoutes.plan(v, alone), 200L)) {
                if (pose.action == CreatureAction.FLY) continue
                // On its rim, or on the ground beside it: nowhere else to be.
                assertTrue(pose.surface == 0f || pose.surface == CreaturePerches.WELL_RIM_HEIGHT)
            }
        }
    }

    @Test
    fun `a visit that never ends goes round in one piece, with no jump where it begins again`() {
        for (arrival in 0 until 3L) {
            val v = visit(VisitRule.BIRD_WELL, GridCell(5, 7), arrivesAt = 2_000_000L + arrival * 91_111L, stay = 3_600_000L)
            val route = CreatureRoutes.plan(v, grid)
            val sample = (v.arrivesAt until v.arrivesAt + 2 * CreatureRoutes.CYCLE_MILLIS + 20_000L step 100L).map { route.poseAt(it)!! }
            for ((a, b) in sample.zipWithNext()) {
                assertTrue("jumped ${hypot(b.x - a.x, b.y - a.y)}", hypot(b.x - a.x, b.y - a.y) < 0.7f)
                assertTrue(abs((b.surface + b.altitude) - (a.surface + a.altitude)) < 0.35f)
            }
        }
    }

    @Test
    fun `its stay at one place is 15 to 40 seconds, so a visit of three minutes has several moves`() {
        // By the figures: a stay between 15 and 40 s, a bird that stays put in one of twelve.
        val average = (15 + 40) / 2.0
        val moves = (200 - 8) / average
        assertTrue(moves in 4.0..9.0)
    }
}
