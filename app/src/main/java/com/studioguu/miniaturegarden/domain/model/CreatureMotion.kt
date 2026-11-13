package com.studioguu.miniaturegarden.domain.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Which way a visitor faces on the screen. Its pictures are drawn facing left. */
enum class Facing {
    LEFT,
    RIGHT;

    fun turned(): Facing = if (this == LEFT) RIGHT else LEFT
}

/** What a visitor is doing at a moment of its visit. */
enum class CreatureAction {
    /** Popping up where it is, as one that walks arrives. */
    APPEAR,

    /** Popping away again, as one that walks leaves. */
    VANISH,

    /** Standing still: looking about, pecking, its tail swaying. */
    STAND,

    /** Getting about on the ground, or on what it sits on: a hop, a jump or a few steps. */
    STEP,

    /** In flight from one point to another; coming in from outside the garden and going away again too. */
    FLY,

    /** Holding still in the air. */
    HOVER,

    /** Resting on a flower, its wings opening and closing slowly. */
    PERCH,
}

/**
 * Where a visitor is and what it is doing at one moment ([CreatureRoute.poseAt]).
 *
 * @property x where it is over the ground, in grid units: (x, y) lies in cell (floor x, floor y),
 *   and a cell's middle is its corner plus 0.5. For one on a building, a point of its footprint.
 * @property altitude how high it flies, in tile widths; 0 for one on its feet.
 * @property elapsed how long it has been at [action], in millis, out of [duration].
 * @property cell the cell it is drawn in, in depth order: the one it is over, or the front cell of
 *   what it sits on.
 * @property inPond a frog in a pond or a stream: half under the water.
 * @property heading which way a flier's nose points on the screen, degrees clockwise from straight up.
 * @property setsOff a [CreatureAction.STEP] that starts a move of a few steps (the squirrel breaks
 *   into a run), and [stops] one that ends it (it sits up again).
 * @property surface how high what it stands on is (a roof, a rim, a seat), in tile widths; 0 on
 *   the ground. A flier's height is all in [altitude].
 * @property grounded on its feet on the ground: drawn in depth order by where it is in its cell
 *   ([CreatureRoutes.drawDepth]), so that it goes behind what grows there.
 * @property turn how wide it is drawn as it turns from facing one way to the other: 1 as usual,
 *   down to 0 at the moment it turns ([CreatureRoutes.FACE_TURN_MILLIS]), so that it is seen to
 *   turn round rather than flip from one picture to the other.
 * @property turnPassed whether the moment of that turn ([turn] below 1) is behind it, so that it
 *   already faces the new way; before it, it still faces the old one. Drawing crosses from the old
 *   picture to the new one about that moment (#fb-1107).
 */
data class CreaturePose(
    val x: Float,
    val y: Float,
    val altitude: Float,
    val facing: Facing,
    val action: CreatureAction,
    val elapsed: Long,
    val duration: Long,
    val cell: GridCell,
    val inPond: Boolean = false,
    val heading: Float = 0f,
    val setsOff: Boolean = false,
    val stops: Boolean = false,
    val surface: Float = 0f,
    val grounded: Boolean = false,
    val turn: Float = 1f,
    val turnPassed: Boolean = false,
    /** Settled on a plant (the ladybird on its leaves or flowers): drawn over the plants in front, never hidden by them. */
    val onPlant: Boolean = false,
) {
    /** How far through [action] it is, 0 to 1. */
    val progress: Float get() = if (duration <= 0L) 1f else (elapsed.toFloat() / duration).coerceIn(0f, 1f)

    /**
     * Settled on something rather than the ground: on a flower or a plant, on a seat, a rim or a
     * roof. It casts no shadow on the ground then (#fb-1113 6): the oval read as a hole under it.
     */
    val restsOnSomething: Boolean get() = onPlant || surface > 0f || action == CreatureAction.PERCH
}

/**
 * The way one visitor goes about the garden over its whole visit, worked out once when it comes
 * ([CreatureRoutes.plan]) and then only read: [poseAt] looks up the moment asked for.
 *
 * Like the visit, it is made from the time the visitor came, what it came for and the garden,
 * and nothing else, so the same moment always finds it in the same place, facing the same way:
 * turning the screen or coming back to the app does not move it.
 */
class CreatureRoute internal constructor(
    val visit: CreatureVisit,
    private val opening: Leg,
    private val legs: List<Leg>,
    /** How long [legs] last; read round and round when [cyclic]. */
    private val span: Long,
    private val cyclic: Boolean,
    private val closingMillis: Long,
    private val closing: (from: CreaturePose) -> Leg,
    private val depthCell: GridCell?,
    private val ponds: Set<GridCell>,
    /** How high what it stands on is ([CreaturePose.surface]). */
    private val surface: Float = 0f,
    /** Whether it is on its feet on the ground ([CreaturePose.grounded]). */
    private val grounded: Boolean = false,
    /** Whether it settles on a plant ([CreaturePose.onPlant]). */
    private val onPlant: Boolean = false,
    /**
     * Whether it found nowhere to settle and only passes over: in and straight out again, and
     * then gone ([passingOver]). A route with no [opening] at all is never seen.
     */
    private val passing: Boolean = false,
) {
    private val starts = LongArray(legs.size) { legs[it].start }

    /** Every moment of [legs] it turns to face the other way, in order: across legs, however short. */
    private val turns: LongArray = legs.flatMap { leg ->
        buildList {
            if (leg.turnIn) add(leg.start)
            if (leg.lookFrom >= 0L) {
                add(leg.start + leg.lookFrom)
                add(leg.start + leg.lookUntil)
            }
        }
    }.sorted().toLongArray()

    /** Where the visitor is at [now] and what it is doing; null while it is not in the garden. */
    fun poseAt(now: Long): CreaturePose? {
        if (!visit.isPresentAt(now)) return null
        val t = now - visit.arrivesAt
        val length = visit.leavesAt - visit.arrivesAt
        if (passing) {
            if (opening.duration <= 0L || t >= opening.duration + closingMillis) return null
            if (t < opening.duration) return pose(opening, t)
            return pose(closing(pose(opening, opening.duration)), t - opening.duration)
        }
        if (t < opening.duration) return pose(opening, t)
        val closeAt = length - closingMillis
        if (t >= closeAt && closeAt >= opening.duration) {
            val leg = closing(middle(closeAt - opening.duration))
            return pose(leg, t - closeAt)
        }
        val pose = middle(t - opening.duration)
        // Going away the other way: it turns round just before it goes.
        val toClose = closeAt - t
        if (closeAt >= opening.duration && toClose < CreatureRoutes.FACE_TURN_MILLIS / 2 && closing(middle(closeAt - opening.duration)).turnIn) {
            val closing = toClose.toFloat() / (CreatureRoutes.FACE_TURN_MILLIS / 2)
            if (closing < pose.turn) return pose.copy(turn = closing, turnPassed = false)
        }
        return pose
    }

    private fun middle(t: Long): CreaturePose {
        if (legs.isEmpty()) return pose(opening, opening.duration)
        val u = if (cyclic) Math.floorMod(t, span) else t.coerceIn(0L, span - 1)
        var index = starts.binarySearch(u)
        if (index < 0) index = -index - 2
        val leg = legs[index.coerceIn(0, legs.lastIndex)]
        val pose = pose(leg, u - leg.start)
        if (turns.isEmpty()) return pose
        val near = turnNear(u)
        return if (abs(near) < pose.turn) pose.copy(turn = abs(near), turnPassed = near >= 0f) else pose
    }

    /**
     * How wide it is drawn at [u] for the nearest turn of [turns] ([CreaturePose.turn]), signed:
     * less than 0 before the moment of turning, 0 or more from it on.
     */
    private fun turnNear(u: Long): Float {
        var i = turns.binarySearch(u)
        if (i < 0) i = -i - 1
        var near = Long.MAX_VALUE
        var passed = false
        if (i < turns.size) near = turns[i] - u
        if (i > 0 && u - turns[i - 1] < near) {
            near = u - turns[i - 1]
            passed = true
        }
        if (near == 0L) passed = true
        val width = (near.toFloat() / (CreatureRoutes.FACE_TURN_MILLIS / 2)).coerceIn(0f, 1f)
        return if (passed) width else -width
    }

    private fun pose(leg: Leg, into: Long): CreaturePose {
        val elapsed = into.coerceIn(0L, leg.duration)
        val f = if (leg.duration <= 0L) 1f else elapsed.toFloat() / leg.duration
        val k = when {
            leg.darts -> ((elapsed - CreatureRoutes.TURN_MILLIS).toFloat() / (leg.duration - CreatureRoutes.TURN_MILLIS).coerceAtLeast(1L)).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) }
            leg.action == CreatureAction.FLY -> f * f * (3f - 2f * f)
            else -> f
        }
        val x = leg.fromX + (leg.toX - leg.fromX) * k
        val y = leg.fromY + (leg.toY - leg.fromY) * k
        val facing = if (elapsed in leg.lookFrom until leg.lookUntil) leg.facing.turned() else leg.facing
        val cell = leg.cell ?: depthCell ?: GridCell(floor(x).toInt(), floor(y).toInt())
        return CreaturePose(
            x = x,
            y = y,
            altitude = leg.fromAlt + (leg.toAlt - leg.fromAlt) * k,
            facing = facing,
            action = leg.action,
            elapsed = elapsed,
            duration = leg.duration,
            cell = cell,
            inPond = cell in ponds,
            heading = leg.headingAt(elapsed),
            setsOff = leg.setsOff,
            stops = leg.stops,
            surface = surface,
            grounded = grounded && leg.action != CreatureAction.FLY,
            onPlant = onPlant && leg.action != CreatureAction.FLY && leg.action != CreatureAction.APPEAR && leg.action != CreatureAction.VANISH,
            turn = abs(turnOf(leg, elapsed)),
            turnPassed = turnOf(leg, elapsed) >= 0f,
        )
    }

    /**
     * How wide it is drawn [elapsed] millis into [leg]: narrowing over the half of
     * [CreatureRoutes.FACE_TURN_MILLIS] before the moment it faces the other way, and widening again
     * over the half after. Signed like [turnNear]: less than 0 before the moment.
     */
    private fun turnOf(leg: Leg, elapsed: Long): Float {
        var near = Long.MAX_VALUE
        var passed = false
        fun consider(distance: Long, after: Boolean) {
            if (distance < near) {
                near = distance
                passed = after || distance == 0L
            }
        }
        if (leg.turnIn) consider(elapsed, true)
        if (leg.turnOut) consider(leg.duration - elapsed, false)
        if (leg.lookFrom >= 0L) {
            consider(abs(elapsed - leg.lookFrom), elapsed >= leg.lookFrom)
            consider(abs(elapsed - leg.lookUntil), elapsed >= leg.lookUntil)
        }
        val width = (near.toFloat() / (CreatureRoutes.FACE_TURN_MILLIS / 2)).coerceIn(0f, 1f)
        return if (passed) width else -width
    }
}

/**
 * One stretch of a route: from one point to another (or staying put) over [duration] millis,
 * starting [start] millis into the part of the route it belongs to.
 *
 * @property lookFrom while standing, the stretch (millis into the leg) it looks the other way.
 * @property heading where a flier's nose points on this leg ([CreaturePose.heading]), turned to from
 *   [turnedFrom] over the first [CreatureRoutes.TURN_MILLIS].
 * @property darts a dragonfly's dart: it turns on the spot, shoots off, and brakes to a stop.
 */
internal class Leg(
    val start: Long,
    val duration: Long,
    val action: CreatureAction,
    val fromX: Float,
    val fromY: Float,
    val fromAlt: Float,
    val toX: Float = fromX,
    val toY: Float = fromY,
    val toAlt: Float = fromAlt,
    val facing: Facing,
    val lookFrom: Long = -1L,
    val lookUntil: Long = -1L,
    /** The first step of a move on foot, and the last: see [CreaturePose.setsOff]. */
    val setsOff: Boolean = false,
    val stops: Boolean = false,
    val heading: Float = 0f,
    val turnedFrom: Float = heading,
    val darts: Boolean = false,
    /** The cell it is drawn in while on this leg: that of the flower it rests on. */
    val cell: GridCell? = null,
) {
    /** It starts facing the other way from the leg before ([CreaturePose.turn]). */
    var turnIn: Boolean = false

    /** The leg after it starts facing the other way. */
    var turnOut: Boolean = false

    val end: Long get() = start + duration

    fun headingAt(elapsed: Long): Float {
        val f = (elapsed.toFloat() / CreatureRoutes.TURN_MILLIS).coerceIn(0f, 1f)
        return turnedFrom + CreatureRoutes.shortestTurn(turnedFrom, heading) * f * f * (3f - 2f * f)
    }
}

/**
 * How visitors get about the garden ([CreatureRoute]).
 *
 * Those that walk pop up next to what they came for (or on it), wander a few cells round it,
 * stopping now and then to look about, and pop away again where they are; the bird flies in from
 * outside the garden instead, lands, and flies off out of it at the end (#71). Those that fly come in
 * from outside the garden, flit round what they came for, settling on its flowers now and then,
 * and fly away out of the garden at the end.
 */
object CreatureRoutes {
    /** How long a visitor that walks takes to pop up, and to pop away. */
    const val POP_MILLIS = 400L

    /** Height of a flier coming in or going away, in tile widths: up in the sky. */
    const val SKY_ALTITUDE = 1.6f

    /** How high a flier keeps while it flits about, in tile widths. */
    fun cruiseAltitude(creature: Creature): Float = when (creature) {
        Creature.BUTTERFLY -> 0.42f
        Creature.DRAGONFLY -> 0.3f
        else -> 0f
    }

    /** How long a flier takes to turn to a new heading. */
    const val TURN_MILLIS = 170L

    /**
     * How long turning from facing one way to the other takes: it narrows (to six tenths of its
     * width, never to nothing) and widens again facing the other way, the bird and the squirrel
     * facing the viewer for a moment in between, like turning round on the spot (0.2 s before
     * 11/7, with a flat sliver in the middle).
     */
    const val FACE_TURN_MILLIS = 300L

    /** The least time between two turns from facing one way to the other: no flapping to and fro. */
    const val MIN_FACING_MILLIS = 900L

    /** How high a butterfly sits on a flower, in tile widths. */
    const val PERCH_ALTITUDE = 0.2f

    /**
     * The longest stretch of a route worked out in full. A visit longer than that (the title's
     * sample garden, whose visitors never leave) goes round the same stretch again and again.
     */
    const val CYCLE_MILLIS = 6L * 60 * 1000

    /** Whether [creature] keeps to the air. */
    fun flies(creature: Creature): Boolean = cruiseAltitude(creature) > 0f

    /**
     * Whether [creature] may go onto (x, y) on foot, as [CreaturePerches.ALLOWED] has it: the
     * ground ([CreaturePerches.isGround]) for the bird, the squirrel and the frog, and a pond for
     * the frog. Only the squirrel goes in among what grows (on the ground between the stems,
     * behind the plants in front of it); no one walks onto a building, a fence, a rock, a bench, a
     * shiitake log, a stream, a work site or a greenhouse.
     */
    fun canWalk(creature: Creature, grid: GardenGrid, x: Int, y: Int): Boolean {
        if (CreaturePerches.isGround(grid, x, y)) return CreaturePerches.allows(creature, PerchKind.GROUND)
        if (!grid.map.isInside(x, y) || !grid.map.isBuildable(x, y)) return false
        if (grid.floorAt(x, y)?.isGreenhouse == true) return false
        val thing = grid.occupantAt(x, y) ?: return false
        return when {
            thing.isWorking -> false
            thing.isPond -> CreaturePerches.allows(creature, PerchKind.POND)
            thing.isPlant -> creature == Creature.SQUIRREL && thing.objectId != PlantSpecies.SHIITAKE.objectId
            else -> false
        }
    }

    /**
     * Where [pose] goes in the garden's depth order ([com.studioguu.miniaturegarden.ui.render.LayerPipeline.depthOf]:
     * a cell's x + y, a thing at its front cell). One on its feet on the ground goes by where it
     * is in its cell, so that it is behind a plant's stem when it is behind the cell's middle and
     * in front of it when in front; one up on something, or in the air, is drawn over its cell.
     */
    fun drawDepth(pose: CreaturePose): Float {
        val cell = (pose.cell.x + pose.cell.y).toFloat()
        if (pose.onPlant) return cell + ABOVE_CELL_DEPTH + PLANT_PERCH_LIFT
        if (!pose.grounded) return cell + ABOVE_CELL_DEPTH
        return (pose.x + pose.y - 1f - GROUND_DEPTH_BIAS).coerceIn(cell - CELL_DEPTH_SPREAD, cell + CELL_DEPTH_SPREAD)
    }

    /**
     * Works out [visit]'s route through the garden as [grid] has it, settling only where
     * [perches] has a place for it ([CreaturePerches.ALLOWED]).
     *
     * [direction], when given, says where it goes and which way it comes in from (the promo
     * build's scripts); without it (the game) the route is exactly as it always was.
     */
    fun plan(
        visit: CreatureVisit,
        grid: GardenGrid,
        perches: List<Perch> = CreaturePerches.inGarden(grid),
        direction: CreatureDirection? = null,
    ): CreatureRoute {
        val creature = visit.creature
        val random = Random(seedOf(visit))
        val length = visit.leavesAt - visit.arrivesAt
        val spots = CreaturePerches.forCreature(creature, perches)
        return when {
            flies(creature) -> planFlight(visit, grid, spots, random, length, direction)
            visit.host != null -> planOnHost(visit, grid, spots, random, length, direction?.fromAngle)
            else -> planOnGround(visit, visit.cell, grid, spots, random, length, direction?.fromAngle)
        }
    }

    // --- Those that walk on the ground ---

    private fun planOnGround(
        visit: CreatureVisit,
        home: GridCell,
        grid: GardenGrid,
        spots: List<Perch>,
        random: Random,
        length: Long,
        fromAngle: Double? = null,
    ): CreatureRoute {
        // The bird come for a well or a fence sits on its rim or its rail more often than not.
        if (visit.creature == Creature.BIRD) {
            val onAnchor = CreaturePerches.near(home.x + 0.5f, home.y + 0.5f, ANCHOR_PERCH_REACH, spots)
                .filter { it.kind != PerchKind.GROUND && it.on != null && visit.rule.anchor.matches(it.on) }
            if (onAnchor.isNotEmpty() && random.nextFloat() < BIRD_PERCH_CHANCE) {
                return planOnPerch(visit, onAnchor[random.nextInt(onAnchor.size)], grid.map.size, random, length, fromAngle)
            }
        }
        val manner = Manner.of(visit.creature)
        val region = regionAround(visit.creature, grid, home, manner.roam)
        val footings = footingsOf(grid)
        val ponds = region.filterTo(HashSet()) { cell -> grid.occupantAt(cell.x, cell.y)?.let { it.isPond || it.isTerrain } == true }
        val landing = landingOf(visit.creature, home.x + 0.5f, home.y + 0.5f, grid.map.size, random, fromAngle)
        val (span, cyclic) = spanOf(length - 2 * (landing?.duration ?: POP_MILLIS))
        val plan = planner(random, manner, span, cyclic, home.x + 0.5f, home.y + 0.5f, landing)
        val reserve = if (cyclic) (manner.stepMillis * MAX_WALK_PACE * manner.roam * 2).toLong() + manner.rest.first else 0L
        plan.standFor(plan.between(manner.firstRest))
        var last: Mood? = null
        while (true) {
            // What it does next (#fb-1110): a usual move, a little shuffle, a longer way, or just
            // staying put a while; never the same twice running, so the next is hard to guess.
            val mood = Mood.next(random, last)
            last = mood
            if (mood == Mood.STILL) {
                val stay = (plan.between(manner.rest) * mood.restScale(random)).toLong()
                // Room left after it for one more stop: it never turns round just as it goes.
                if (plan.t + stay + reserve + manner.rest.first > span) break
                plan.standFor(stay)
                continue
            }
            val to = pointNearby(plan, region, footings, manner, random, mood.strideScale(random))
                ?: pointNearby(plan, region, footings, manner, random) ?: break
            val millis = plan.pace(hypot(to.first - plan.x, to.second - plan.y) * manner.stepMillis, wide = true)
            val rest = (plan.between(manner.rest) * mood.restScale(random)).toLong()
            if (plan.t + millis + rest + reserve > span) break
            plan.stepTo(to.first, to.second, millis)
            plan.standFor(rest)
        }
        if (cyclic) goHome(plan, region, manner, home)
        plan.standUntil(span)
        return arriving(visit, plan, landing, home.x + 0.5f, home.y + 0.5f, grid.map.size, depthCell = null, ponds = ponds, grounded = true)
    }

    /**
     * A point a short way from where [plan] has got to, anywhere inside a cell of [region] rather
     * than in its middle, with the way there inside [region] too; null if a few tries find none.
     */
    private fun pointNearby(
        plan: Planner,
        region: Set<GridCell>,
        footings: Set<GridCell>,
        manner: Manner,
        random: Random,
        scale: Float = 1f,
    ): Pair<Float, Float>? {
        repeat(NEARBY_TRIES) {
            val angle = random.nextDouble() * 2 * PI
            val reach = scale * (manner.stride.start + random.nextFloat() * (manner.stride.endInclusive - manner.stride.start))
            val x = plan.x + cos(angle).toFloat() * reach
            val y = plan.y + sin(angle).toFloat() * reach
            if (isClearWay(plan.x, plan.y, x, y, region) && isClearOf(x, y, footings)) return x to y
        }
        return null
    }

    /**
     * Whether a walker stopping at (x, y) keeps [EDGE_MARGIN] of a cell clear of the [footings] of
     * the buildings (#fb-1113 1): right at a cell's edge, its body reached into the stones of the
     * building next door (the frog wedged into the corner of the hut's footing). Crops and the like
     * it may still brush past, or it would hardly move between them.
     */
    internal fun isClearOf(x: Float, y: Float, footings: Set<GridCell>): Boolean {
        if (footings.isEmpty()) return true
        for (dx in EDGE_OFFSETS) for (dy in EDGE_OFFSETS) {
            if (cellOf(x + dx, y + dy) in footings) return false
        }
        return true
    }

    /** The cells the buildings stand on: the hut, the cottage, the windmill, the well and the like. */
    internal fun footingsOf(grid: GardenGrid): Set<GridCell> =
        grid.objects.filter { StructureData.fromObjectId(it.objectId).let { s -> s != null && s != StructureData.BRIDGE } }.flatMapTo(HashSet()) { it.occupiedCells() }

    /** Whether a straight line stays in [region], also where it cuts past the corner of a cell. */
    private fun isClearWay(fromX: Float, fromY: Float, toX: Float, toY: Float, region: Set<GridCell>): Boolean {
        var last = cellOf(fromX, fromY)
        for (i in 1..WAY_SAMPLES) {
            val f = i / WAY_SAMPLES.toFloat()
            val cell = cellOf(fromX + (toX - fromX) * f, fromY + (toY - fromY) * f)
            if (cell !in region || GridCell(last.x, cell.y) !in region || GridCell(cell.x, last.y) !in region) return false
            last = cell
        }
        return true
    }

    /** Takes [plan] back to [home] by the cells of [region], for a route that goes round. */
    private fun goHome(plan: Planner, region: Set<GridCell>, manner: Manner, home: GridCell) {
        var from = cellOf(plan.x, plan.y)
        if (from == home && hypot(plan.x - (home.x + 0.5f), plan.y - (home.y + 0.5f)) < 0.01f) return
        for (cell in pathBetween(from, home, region) + (if (from == home) listOf(home) else emptyList())) {
            plan.stepTo(cell.x + 0.5f, cell.y + 0.5f, manner.stepMillis)
            from = cell
        }
    }

    private fun cellOf(x: Float, y: Float) = GridCell(floor(x).toInt(), floor(y).toInt())

    /** Cells [creature] can reach on foot from [home] without going more than [roam] cells from it. */
    private fun regionAround(creature: Creature, grid: GardenGrid, home: GridCell, roam: Int): Set<GridCell> {
        val region = LinkedHashSet<GridCell>().apply { add(home) }
        val queue = ArrayDeque(listOf(home))
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            for (next in neighbours(cell)) {
                if (next in region || distance(next, home) > roam) continue
                if (!canWalk(creature, grid, next.x, next.y)) continue
                region += next
                queue += next
            }
        }
        return region
    }

    /** The cells walked through from [from] to [to] (not [from] itself), one step at a time, inside [region]. */
    private fun pathBetween(from: GridCell, to: GridCell, region: Set<GridCell>): List<GridCell> {
        val cameFrom = HashMap<GridCell, GridCell>()
        val queue = ArrayDeque(listOf(from))
        cameFrom[from] = from
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (cell == to) break
            for (next in neighbours(cell)) {
                if (next !in region || next in cameFrom) continue
                cameFrom[next] = cell
                queue += next
            }
        }
        if (to !in cameFrom) return emptyList()
        val path = ArrayList<GridCell>()
        var cell = to
        while (cell != from) {
            path += cell
            cell = cameFrom.getValue(cell)
        }
        return path.asReversed()
    }

    // --- Those that walk on what they came for: a bird on a roof, a ladybird on a crop ---

    /**
     * On one of the points of what it came for. Where that has none for it (the bird and the
     * hut's roof), the bird comes down on the ground beside it instead; failing that, it only
     * passes over, and the others do not come at all.
     */
    private fun planOnHost(visit: CreatureVisit, grid: GardenGrid, spots: List<Perch>, random: Random, length: Long, fromAngle: Double? = null): CreatureRoute {
        val host = visit.host!!
        val on = CreaturePerches.on(host, spots)
        if (on.isNotEmpty()) return planOnPerch(visit, on[random.nextInt(on.size)], grid.map.size, random, length, fromAngle)
        val homeX = host.gridX + host.footprint.width / 2f
        val homeY = host.gridY + host.footprint.height / 2f
        if (CreaturePerches.allows(visit.creature, PerchKind.GROUND)) {
            val beside = host.occupiedCells().flatMap(::neighbours).distinct()
                .filter { it !in host.occupiedCells() && canWalk(visit.creature, grid, it.x, it.y) }
                .sortedWith(compareBy({ it.y }, { it.x }))
            if (beside.isNotEmpty()) return planOnGround(visit, beside[random.nextInt(beside.size)], grid, emptyList(), random, length, fromAngle)
        }
        return passingOver(visit, homeX, homeY, grid.map.size, random)
    }

    /**
     * A visitor with nowhere to settle: the bird flies over what it came for and away again;
     * the others are not seen at all.
     */
    private fun passingOver(visit: CreatureVisit, homeX: Float, homeY: Float, size: Int, random: Random): CreatureRoute {
        val leave = { from: CreaturePose -> Leg(0L, 0L, CreatureAction.FLY, from.x, from.y, from.altitude, facing = from.facing) }
        if (visit.creature != Creature.BIRD) {
            val nothing = Leg(0L, 0L, CreatureAction.FLY, homeX, homeY, 0f, facing = Facing.LEFT)
            return CreatureRoute(visit, nothing, emptyList(), 1L, false, 0L, leave, null, emptySet(), passing = true)
        }
        val manner = Manner.of(visit.creature)
        val opening = flightIn(homeX, homeY, PASSING_ALTITUDE, size, manner.flySpeed, random)
        val cameFrom = atan2((opening.fromY - opening.toY).toDouble(), (opening.fromX - opening.toX).toDouble())
        // On over it, the way it was going.
        val closing = flightOut(size, opening.duration, cameFrom + PI, 0.0)
        return CreatureRoute(visit, opening, emptyList(), 1L, false, opening.duration, closing, null, emptySet(), passing = true)
    }

    /** Settled on [spot], moving about a little on it. */
    private fun planOnPerch(visit: CreatureVisit, spot: Perch, size: Int, random: Random, length: Long, fromAngle: Double? = null): CreatureRoute {
        val manner = Manner.of(visit.creature)
        val homeX = spot.x
        val homeY = spot.y
        val landing = landingOf(visit.creature, homeX, homeY, size, random, fromAngle)
        val (span, cyclic) = spanOf(length - 2 * (landing?.duration ?: POP_MILLIS))
        val plan = planner(random, manner, span, cyclic, homeX, homeY, landing)
        val reserve = if (cyclic) (manner.hostStepMillis * MAX_PACE).toLong() + manner.rest.first else 0L
        plan.standFor(plan.between(manner.firstRest))
        while (true) {
            val rest = plan.between(manner.rest)
            val millis = plan.pace(manner.hostStepMillis.toFloat())
            if (plan.t + millis + rest + reserve > span) break
            // Along a roof's ridge it may wander; on a rim, a rail, a seat or a leaf, only shuffle.
            val r = if (spot.kind == PerchKind.ROOF) manner.hostReach else minOf(manner.hostReach, PERCH_REACH)
            plan.stepTo(homeX + random.nextFloat() * 2f * r - r, homeY + random.nextFloat() * 2f * r - r, millis)
            plan.standFor(rest)
        }
        if (cyclic) plan.stepTo(homeX, homeY, manner.hostStepMillis)
        plan.standUntil(span)
        // A ladybird on a plant stays in sight: over the plants in front of it too (2026-11-08).
        val onPlant = visit.creature == Creature.LADYBUG && (spot.kind == PerchKind.FLOWER || spot.kind == PerchKind.LEAF)
        return arriving(visit, plan, landing, homeX, homeY, size, depthCell = spot.cell, ponds = emptySet(), surface = spot.height, onPlant = onPlant)
    }

    /**
     * How one that walks comes down: the bird flies in from outside the garden ([flightIn]) and
     * lands on (homeX, homeY); the others pop up there (null).
     */
    private fun landingOf(creature: Creature, homeX: Float, homeY: Float, size: Int, random: Random, fromAngle: Double? = null): Leg? =
        if (creature == Creature.BIRD) flightIn(homeX, homeY, 0f, size, Manner.of(creature).flySpeed, random, fromAngle) else null

    /** The planner of a route starting at (homeX, homeY): facing the way it landed, if it flew in. */
    private fun planner(random: Random, manner: Manner, span: Long, cyclic: Boolean, homeX: Float, homeY: Float, landing: Leg?): Planner =
        if (landing == null) Planner(random, manner, span, cyclic, homeX, homeY) else Planner(random, manner, span, cyclic, homeX, homeY, facing = landing.facing)

    /**
     * A route that comes as [landing] says (or pops up at (homeX, homeY)), follows [plan], and goes
     * from where it has got to: flying off out of the garden after a landing, or popping away.
     */
    private fun arriving(
        visit: CreatureVisit,
        plan: Planner,
        landing: Leg?,
        homeX: Float,
        homeY: Float,
        size: Int,
        depthCell: GridCell?,
        ponds: Set<GridCell>,
        surface: Float = 0f,
        grounded: Boolean = false,
        onPlant: Boolean = false,
    ): CreatureRoute {
        val span = maxOf(plan.span, plan.t)
        landing?.turnOut = plan.legs.firstOrNull()?.turnIn == true
        if (landing != null) {
            val cameFrom = atan2((landing.fromY - landing.toY).toDouble(), (landing.fromX - landing.toX).toDouble())
            val closing = flightOut(size, landing.duration, cameFrom, plan.random.nextDouble() * 1.2 - 0.6)
            return CreatureRoute(visit, landing, plan.legs, span, plan.cyclic, landing.duration, closing, depthCell, ponds, surface, grounded, onPlant = onPlant)
        }
        val opening = Leg(0L, POP_MILLIS, CreatureAction.APPEAR, homeX, homeY, 0f, facing = plan.firstFacing)
        opening.turnOut = plan.legs.firstOrNull()?.turnIn == true
        return CreatureRoute(
            visit, opening, plan.legs, span, plan.cyclic, POP_MILLIS,
            closing = { from -> Leg(0L, POP_MILLIS, CreatureAction.VANISH, from.x, from.y, 0f, facing = from.facing) },
            depthCell = depthCell, ponds = ponds, surface = surface, grounded = grounded, onPlant = onPlant,
        )
    }

    // --- Those that fly ---

    private fun planFlight(visit: CreatureVisit, grid: GardenGrid, spots: List<Perch>, random: Random, length: Long, direction: CreatureDirection? = null): CreatureRoute {
        val creature = visit.creature
        val manner = Manner.of(creature)
        val host = visit.host
        // Told where to settle: those points, in the order given, and straight to the first.
        val told = direction?.perches.orEmpty().mapNotNull { cell ->
            spots.firstOrNull { it.cell == cell || it.on?.occupiedCells()?.contains(cell) == true }
        }
        val homeX = told.firstOrNull()?.x ?: host?.let { it.gridX + it.footprint.width / 2f } ?: (visit.cell.x + 0.5f)
        val homeY = told.firstOrNull()?.y ?: host?.let { it.gridY + it.footprint.height / 2f } ?: (visit.cell.y + 0.5f)
        val cruise = cruiseAltitude(creature)
        val size = grid.map.size
        val opening = flightIn(homeX, homeY, cruise, size, manner.flySpeed, random, direction?.fromAngle, direction?.fromCells)
        val inMillis = opening.duration
        // Coming in from nearer than usual, it still goes the usual way out, at its own speed.
        val outMillis = if (direction?.fromCells != null) flightMillis(creature, size) else inMillis
        val (span, cyclic) = spanOf(length - inMillis - outMillis)
        val plan = Planner(random, manner, span, cyclic, homeX, homeY, cruise, opening.facing, heading = opening.heading)
        val perches = if (told.isNotEmpty()) told else perchesAround(visit, homeX, homeY, spots)
        val inside = { v: Float -> v.coerceIn(EDGE, size - EDGE) }
        val reserve = if (cyclic) 2 * (manner.reach * 2 / manner.flySpeed * 1000).toLong() + manner.rest.first else 0L
        var flightsToPerch = if (told.isNotEmpty()) 0 else manner.flightsBetweenPerches.first + random.nextInt(manner.flightsBetweenPerches.last - manner.flightsBetweenPerches.first + 1)
        // A butterfly goes about it in more than one way (#fb-1113 7), never the same twice running.
        var flutter: Flutter? = null
        val flutters = creature == Creature.BUTTERFLY && told.isEmpty()
        fun nextFlutter() {
            flutter = Flutter.next(random, flutter).also { flightsToPerch = it.flights(random) }
        }
        if (flutters) nextFlutter()
        var nextTold = 0
        // Where it is drifting round: a flower, for as long as it stays with that one.
        var aroundX = homeX
        var aroundY = homeY
        var perchedOn: Perch? = null
        while (true) {
            val perching = perches.isNotEmpty() && flightsToPerch == 0
            val tx: Float
            val ty: Float
            val alt: Float
            if (perching) {
                // Off to another point, if there is one: it does not stay with the one it came for.
                val spot = if (told.isNotEmpty()) {
                    told[nextTold++ % told.size]
                } else {
                    val others = perches.filter { abs(it.x - aroundX) + abs(it.y - aroundY) > 0.01f }.ifEmpty { perches }
                    others[random.nextInt(others.size)]
                }
                tx = spot.x + if (told.isNotEmpty()) 0f else (random.nextFloat() - 0.5f) * PERCH_SCATTER
                ty = spot.y + if (told.isNotEmpty()) 0f else (random.nextFloat() - 0.5f) * PERCH_SCATTER
                alt = spot.height
                aroundX = spot.x
                aroundY = spot.y
                perchedOn = spot
            } else if (flutter == Flutter.PASS && perches.isNotEmpty()) {
                // Down over a flower and on, without settling.
                val others = perches.filter { abs(it.x - plan.x) + abs(it.y - plan.y) > 0.3f }.ifEmpty { perches }
                val spot = others[random.nextInt(others.size)]
                tx = spot.x + (random.nextFloat() - 0.5f) * PASS_SCATTER
                ty = spot.y + (random.nextFloat() - 0.5f) * PASS_SCATTER
                alt = spot.height + PASS_CLEARANCE
                aroundX = spot.x
                aroundY = spot.y
            } else {
                val reach = manner.reach * (flutter?.reach ?: 1f)
                tx = inside(aroundX + (random.nextFloat() * 2f - 1f) * reach)
                ty = inside(aroundY + (random.nextFloat() * 2f - 1f) * reach)
                alt = cruise + (random.nextFloat() - 0.5f) * if (flutter == Flutter.DETOUR) 0.3f else 0.1f
            }
            val flight = maxOf(manner.stepMillis, plan.pace(hypot(tx - plan.x, ty - plan.y) / manner.flySpeed * 1000)) + if (manner.darts) TURN_MILLIS else 0L
            var pause = when {
                perching -> plan.between(flutter?.perch ?: manner.perch)
                flutter?.hovers == false -> 0L
                else -> plan.between(manner.rest)
            }
            // Told where to settle, it stays there for as long as asked, or as the visit has left,
            // rather than not settling at all.
            if (told.isNotEmpty() && perching) pause = minOf(direction?.stayMillis ?: pause, span - plan.t - flight - reserve)
            if (plan.t + flight + pause + reserve > span || (told.isNotEmpty() && perching && pause <= 0L)) break
            plan.flyTo(tx, ty, alt, flight)
            if (perching) {
                plan.hold(CreatureAction.PERCH, pause, cell = perchedOn?.cell)
                flightsToPerch = manner.flightsBetweenPerches.first + random.nextInt(manner.flightsBetweenPerches.last - manner.flightsBetweenPerches.first + 1)
                if (flutters) nextFlutter()
            } else {
                if (pause > 0L) plan.hold(CreatureAction.HOVER, pause)
                flightsToPerch--
            }
        }
        if (cyclic) plan.flyTo(homeX, homeY, cruise, maxOf(manner.stepMillis, (hypot(homeX - plan.x, homeY - plan.y) / manner.flySpeed * 1000).toLong()) + if (manner.darts) TURN_MILLIS else 0L)
        plan.holdUntil(span)

        val cameFrom = atan2((opening.fromY - opening.toY).toDouble(), (opening.fromX - opening.toX).toDouble())
        val closing = flightOut(size, outMillis, cameFrom, random.nextDouble() * 1.2 - 0.6)
        return CreatureRoute(visit, opening, plan.legs, maxOf(plan.span, plan.t), plan.cyclic, outMillis, closing, depthCell = null, ponds = emptySet())
    }

    /** How long [creature] takes to fly in from outside a garden [size] cells across, or out of it. */
    fun flightMillis(creature: Creature, size: Int, fromCells: Float? = null): Long =
        ((fromCells ?: awayFrom(size)) / Manner.of(creature).flySpeed * 1000).toLong()

    /** How far outside a garden [size] cells across a flier comes from and goes to, in cells. */
    private fun awayFrom(size: Int): Float = size * 0.8f + 3f

    /**
     * Coming in: from a point well outside the garden on the side nearest (homeX, homeY), high up
     * in the sky, down to there at [toAlt], at [speed] cells a second.
     */
    private fun flightIn(
        homeX: Float,
        homeY: Float,
        toAlt: Float,
        size: Int,
        speed: Float,
        random: Random,
        fromAngle: Double? = null,
        fromCells: Float? = null,
    ): Leg {
        val away = fromCells ?: awayFrom(size)
        val angle = fromAngle ?: (outwards(homeX, homeY, size, otherwise = random.nextDouble() * 2 * PI) + random.nextDouble() * 1.2 - 0.6)
        val startX = homeX + cos(angle).toFloat() * away
        val startY = homeY + sin(angle).toFloat() * away
        return Leg(
            0L, (away / speed * 1000).toLong(), CreatureAction.FLY, startX, startY, SKY_ALTITUDE, homeX, homeY, toAlt,
            facing = facingOf(homeX - startX, homeY - startY, Facing.LEFT),
            heading = headingOf(homeX - startX, homeY - startY, 0f),
        )
    }

    /**
     * Going away, over [millis]: from wherever it is, outwards from the middle of the garden (the
     * way it came, [cameFrom], from the very middle) turned by [turn], up and out of it.
     */
    private fun flightOut(size: Int, millis: Long, cameFrom: Double, turn: Double): (CreaturePose) -> Leg = { from ->
        val away = awayFrom(size)
        val out = outwards(from.x, from.y, size, otherwise = cameFrom) + turn
        val endX = from.x + cos(out).toFloat() * away
        val endY = from.y + sin(out).toFloat() * away
        Leg(
            0L, millis, CreatureAction.FLY, from.x, from.y, from.altitude, endX, endY, SKY_ALTITUDE, facing = facingOf(endX - from.x, endY - from.y, from.facing),
            heading = headingOf(endX - from.x, endY - from.y, from.heading), turnedFrom = from.heading,
        ).also { it.turnIn = it.facing != from.facing }
    }

    /**
     * The direction (radians, on the ground) from the middle of a garden [size] cells across out
     * through (x, y); [otherwise] from the very middle. A flier going that way, give or take 0.6
     * radians, for [size] * 0.8 + 3 cells is well clear of the garden.
     */
    private fun outwards(x: Float, y: Float, size: Int, otherwise: Double): Double {
        val dx = x - size / 2f
        val dy = y - size / 2f
        return if (abs(dx) + abs(dy) < 0.01f) otherwise else atan2(dy.toDouble(), dx.toDouble())
    }

    /**
     * Where a flier settles now and then: the points of [spots] (already only those it may
     * settle on) within [PERCH_REACH_IN_FLIGHT] cells of where it came. A butterfly goes to the
     * flowers in bloom, the weathervane, the well's rim and the bench; a dragonfly to the flowers,
     * the rim, the weathervane and the banks of ponds and streams.
     */
    private fun perchesAround(visit: CreatureVisit, homeX: Float, homeY: Float, spots: List<Perch>): List<Perch> {
        if (Manner.of(visit.creature).perch.last <= 0L) return emptyList()
        return CreaturePerches.near(homeX, homeY, PERCH_REACH_IN_FLIGHT, shareOf(visit, spots))
    }

    /**
     * The points of [spots] that [visit] has to itself among a crowd of its kind: every n-th of
     * them in a fixed order, so that no two of the crowd ever settle on the same point.
     */
    internal fun shareOf(visit: CreatureVisit, spots: List<Perch>): List<Perch> {
        if (visit.crowd <= 1) return spots
        return spots.sortedWith(compareBy({ it.y }, { it.x }, { it.height }))
            .filterIndexed { i, _ -> i % visit.crowd == visit.companion }
    }

    // --- Shared ---

    /**
     * How long the middle of a route (between coming and going) is worked out for, given how long
     * it lasts, and whether it goes round: see [CYCLE_MILLIS].
     */
    private fun spanOf(span: Long): Pair<Long, Boolean> {
        return if (span > CYCLE_MILLIS) CYCLE_MILLIS to true else span.coerceAtLeast(1L) to false
    }

    private fun seedOf(visit: CreatureVisit): Long =
        visit.arrivesAt * 1_000_003L + visit.rule.ordinal * 7_919L + visit.cell.x * 131L + visit.cell.y

    /** Which way something moving by (dx, dy) on the ground faces on screen; [otherwise] when it moves straight up or down it. */
    internal fun facingOf(dx: Float, dy: Float, otherwise: Facing): Facing {
        val screen = dx - dy
        return when {
            screen > 0.001f -> Facing.RIGHT
            screen < -0.001f -> Facing.LEFT
            else -> otherwise
        }
    }

    /** Which way something moving by (dx, dy) on the ground points on screen ([CreaturePose.heading]); [otherwise] when it stays put. */
    internal fun headingOf(dx: Float, dy: Float, otherwise: Float): Float {
        val across = dx - dy
        val down = (dx + dy) / 2f
        if (abs(across) + abs(down) < 0.001f) return otherwise
        return Math.toDegrees(atan2(across.toDouble(), -down.toDouble())).toFloat()
    }

    /** The way round from [from] to [to], degrees: never the long way. */
    internal fun shortestTurn(from: Float, to: Float): Float = (to - from + 540f).mod(360f) - 180f

    private fun distance(a: GridCell, b: GridCell) = abs(a.x - b.x) + abs(a.y - b.y)

    private fun neighbours(cell: GridCell) = listOf(
        GridCell(cell.x, cell.y - 1), GridCell(cell.x + 1, cell.y), GridCell(cell.x, cell.y + 1), GridCell(cell.x - 1, cell.y),
    )

    /** How near a flier keeps to the garden's edge while it flits about, in cells. */
    private const val EDGE = 0.3f

    /** How far from where it came a flier goes to settle, in cells. */
    internal const val PERCH_REACH_IN_FLIGHT = 3f

    /** How far from a point a flier may settle, in cells. */
    private const val PERCH_SCATTER = 0.06f

    /** How far above a flower a butterfly passing over it goes, in tile widths, and how far to its side. */
    private const val PASS_CLEARANCE = 0.16f
    private const val PASS_SCATTER = 0.3f

    /** How far one settled on a rim, a rail, a seat or a leaf shuffles about, in cells. */
    private const val PERCH_REACH = 0.06f

    /** How far from the cell it came to the bird looks for the rim or the rail of what it came for. */
    private const val ANCHOR_PERCH_REACH = 1.6f

    /** How likely the bird come for a well or a fence is to sit on it rather than on the ground. */
    private const val BIRD_PERCH_CHANCE = 0.6f

    /** How high the bird with nowhere to settle passes over, in tile widths. */
    private const val PASSING_ALTITUDE = 0.9f

    /** Depth over its cell for one up on something or in the air: after what stands there. */
    private const val ABOVE_CELL_DEPTH = 0.6f

    /** How much later still one settled on a plant is drawn: after the next two rows of cells in front of it. */
    private const val PLANT_PERCH_LIFT = 2f

    /** How far a visitor on the ground stays within its own cell's depth. */
    private const val CELL_DEPTH_SPREAD = 0.95f

    /** At the middle of a cell, a visitor on the ground is just behind what stands there. */
    private const val GROUND_DEPTH_BIAS = 0.05f

    /** How many points are tried for the next short move before staying put. */
    private const val NEARBY_TRIES = 6

    /** How far a walker keeps from the edge of a cell it may not walk on, in cells. */
    internal const val EDGE_MARGIN = 0.22f
    private val EDGE_OFFSETS = floatArrayOf(-EDGE_MARGIN, 0f, EDGE_MARGIN)

    /** How many points along a short move are looked at, to see that its way is clear. */
    private const val WAY_SAMPLES = 16
}

/**
 * How a kind of visitor goes about.
 *
 * @property stepMillis on the ground, how long it takes to go one cell; for a flier, the shortest
 *   flight.
 * @property rest how long it stops for between moves (a flier: hovers). Now and then, with
 *   [LINGER_CHANCE], it stays [LINGER] times as long.
 * @property firstRest how long it stays put after arriving.
 * @property roam how many cells it goes from where it came, on the ground.
 * @property stride how far it goes in one go, in cells: a short way, to a point nearby.
 * @property lookChance how likely it is to look the other way for a while when it stops.
 * @property turnChance how likely it is to turn round for good when it stops.
 * @property hostStepMillis on what it came for, how long a move takes there.
 * @property hostReach on what it came for, how far it moves from the middle of it, in cells.
 * @property flySpeed a flier's speed, in cells a second.
 * @property reach how far a flier flits from what it came for, in cells.
 * @property perch how long a flier rests on a flower (or what else it settles on); none, never.
 * @property flightsBetweenPerches how many flights it makes before it settles again.
 * @property darts a flier that darts from one stop to the next ([Leg.darts]), turning first.
 */
internal class Manner(
    val stepMillis: Long,
    val rest: LongRange,
    val firstRest: LongRange = 900L..1600L,
    val roam: Int = 2,
    val stride: ClosedFloatingPointRange<Float> = 0.4f..1f,
    val lookChance: Float = 0f,
    val turnChance: Float = 0f,
    val hostStepMillis: Long = 600L,
    val hostReach: Float = 0.15f,
    val flySpeed: Float = 1f,
    val reach: Float = 1f,
    val perch: LongRange = 0L..0L,
    val flightsBetweenPerches: IntRange = 1..3,
    val darts: Boolean = false,
) {
    companion object {
        const val LINGER_CHANCE = 0.2f
        const val LINGER = 2

        private val SQUIRREL = Manner(stepMillis = 420L, rest = 1500L..4800L, roam = 3, stride = 0.5f..1.5f, lookChance = 0.6f, turnChance = 0.3f)
        private val BIRD = Manner(
            stepMillis = 700L, rest = 1200L..4000L, stride = 0.3f..0.8f, lookChance = 0.5f, turnChance = 0.4f,
            hostStepMillis = 520L, hostReach = 0.22f, flySpeed = 3f,
        )
        private val FROG = Manner(stepMillis = 800L, rest = 1400L..4200L, stride = 0.4f..0.9f, lookChance = 0.2f, turnChance = 0.35f)
        private val LADYBUG = Manner(
            stepMillis = 2600L, rest = 1000L..3600L, stride = 0.2f..0.6f, turnChance = 0.5f,
            hostStepMillis = 1600L, hostReach = 0.13f,
        )
        private val BUTTERFLY = Manner(stepMillis = 900L, rest = 0L..500L, flySpeed = 1.6f, reach = 0.8f, perch = 2500L..7000L)
        private val DRAGONFLY = Manner(
            stepMillis = 220L, rest = 900L..2600L, flySpeed = 4f, reach = 1.4f, perch = 2500L..6500L, flightsBetweenPerches = 3..6, darts = true,
        )

        fun of(creature: Creature): Manner = when (creature) {
            Creature.SQUIRREL -> SQUIRREL
            Creature.BIRD -> BIRD
            Creature.FROG -> FROG
            Creature.LADYBUG -> LADYBUG
            Creature.BUTTERFLY -> BUTTERFLY
            Creature.DRAGONFLY -> DRAGONFLY
        }
    }
}

/**
 * How a butterfly goes from one flower it settles on to the next (#fb-1113 7: flying and settling
 * by turns read as one beat over and over). Drawn afresh after each stop, never the same twice.
 *
 * - [DRIFT]: a few short drifts about the flower, hovering a moment between (as before).
 * - [HOP]: straight across to another flower, and a shorter stay there.
 * - [DETOUR]: a wider, unbroken loop up and down, off its way and back.
 * - [PASS]: down over a flower or two and on without settling, then the next one it does.
 *
 * @property flightRange how many flights before it settles.
 * @property reach how far a drift goes, times the butterfly's usual reach.
 * @property hovers whether it holds still in the air between flights.
 * @property perch how long it stays when it settles; null for its usual stay.
 */
internal enum class Flutter(
    private val weight: Float,
    private val flightRange: IntRange,
    val reach: Float = 1f,
    val hovers: Boolean = true,
    val perch: LongRange? = null,
) {
    DRIFT(0.35f, 1..3),
    HOP(0.25f, 0..0, perch = 1200L..3500L),
    DETOUR(0.2f, 2..4, reach = 2.2f, hovers = false),
    PASS(0.2f, 1..2, hovers = false);

    fun flights(random: Random): Int = flightRange.first + random.nextInt(flightRange.last - flightRange.first + 1)

    companion object {
        /** One by the weights, never [last] again. */
        fun next(random: Random, last: Flutter?): Flutter {
            val choices = entries.filter { it != last }
            var roll = random.nextFloat() * choices.sumOf { it.weight.toDouble() }.toFloat()
            for (flutter in choices) {
                roll -= flutter.weight
                if (roll <= 0f) return flutter
            }
            return choices.last()
        }
    }
}

/** How much a move's time varies from one to the next. */
private const val MIN_PACE = 0.8f
private const val MAX_PACE = 1.25f

/** On the ground it varies more (#fb-1110); a flier keeps the narrower spread, to fly smoothly. */
private const val MIN_WALK_PACE = 0.7f
private const val MAX_WALK_PACE = 1.45f

/**
 * The kinds of thing a visitor on the ground does next (#fb-1110), so that its comings and goings
 * do not settle into a beat: a move of its usual length, a little shuffle with a short stop, a
 * longer way with the usual stop, or staying where it is for a good while.
 */
internal enum class Mood(
    private val weight: Float,
    private val stride: ClosedFloatingPointRange<Float>,
    private val rest: ClosedFloatingPointRange<Float>,
) {
    USUAL(0.4f, 0.8f..1.2f, 0.7f..1.3f),
    SHUFFLE(0.25f, 0.25f..0.55f, 0.3f..0.7f),
    WANDER(0.2f, 1.3f..1.9f, 0.6f..1.2f),
    STILL(0.15f, 0f..0f, 1.6f..2.8f);

    fun strideScale(random: Random): Float = stride.start + random.nextFloat() * (stride.endInclusive - stride.start)
    fun restScale(random: Random): Float = rest.start + random.nextFloat() * (rest.endInclusive - rest.start)

    companion object {
        /** A mood by the weights, never [last] again (that one is drawn afresh). */
        fun next(random: Random, last: Mood?): Mood {
            val choices = entries.filter { it != last }
            var roll = random.nextFloat() * choices.sumOf { it.weight.toDouble() }.toFloat()
            for (mood in choices) {
                roll -= mood.weight
                if (roll <= 0f) return mood
            }
            return choices.last()
        }
    }
}

/** Lays the legs of a route one after another, from (x, y). */
private class Planner(
    val random: Random,
    val manner: Manner,
    val span: Long,
    val cyclic: Boolean,
    var x: Float,
    var y: Float,
    var alt: Float = 0f,
    var facing: Facing = if (random.nextBoolean()) Facing.LEFT else Facing.RIGHT,
    var heading: Float = 0f,
) {
    val firstFacing: Facing = facing
    val legs = ArrayList<Leg>()
    var t = 0L

    /** Which way it was last seen facing, at the end of the last leg laid. */
    private var shown: Facing = facing

    /** When it last turned to face the other way. */
    private var lastTurn = Long.MIN_VALUE / 2

    /** Whether it may turn to face the other way at [at]: not too soon after the last time ([CreatureRoutes.MIN_FACING_MILLIS]). */
    private fun mayTurn(at: Long): Boolean = at - lastTurn >= CreatureRoutes.MIN_FACING_MILLIS

    /**
     * The way it faces going by (dx, dy). If that is the other way and it turned too lately to
     * turn again, it first stays put (or hovers) a moment until it may: it never goes backwards.
     */
    private fun facingFor(dx: Float, dy: Float): Facing {
        val wanted = CreatureRoutes.facingOf(dx, dy, facing)
        if (wanted != shown && !mayTurn(t)) {
            val wait = lastTurn + CreatureRoutes.MIN_FACING_MILLIS - t
            add(Leg(t, wait, if (alt > 0f) CreatureAction.HOVER else CreatureAction.STAND, x, y, alt, facing = shown, heading = heading))
            t += wait
        }
        return wanted
    }

    /** Lays [leg], marking where it turns to face the other way. */
    private fun add(leg: Leg) {
        if (leg.facing != shown) {
            leg.turnIn = true
            legs.lastOrNull()?.turnOut = true
            lastTurn = leg.start
        }
        if (leg.lookFrom >= 0L) lastTurn = leg.start + leg.lookUntil
        shown = leg.facing
        legs += leg
    }

    fun between(range: LongRange): Long {
        val stop = if (range.last <= range.first) range.first else range.first + random.nextLong(range.last - range.first)
        return if (range.first > 0L && random.nextFloat() < Manner.LINGER_CHANCE) stop * Manner.LINGER else stop
    }

    /** [millis], a little quicker or slower than the last time: no two moves are alike. */
    fun pace(millis: Float, wide: Boolean = false): Long {
        val (low, high) = if (wide) MIN_WALK_PACE to MAX_WALK_PACE else MIN_PACE to MAX_PACE
        return (millis * (low + random.nextFloat() * (high - low))).toLong().coerceAtLeast(1L)
    }

    fun stepTo(toX: Float, toY: Float, millis: Long) {
        facing = facingFor(toX - x, toY - y)
        add(Leg(t, millis, CreatureAction.STEP, x, y, alt, toX, toY, alt, facing = facing, setsOff = true, stops = true))
        x = toX
        y = toY
        t += millis
    }

    fun flyTo(toX: Float, toY: Float, toAlt: Float, millis: Long) {
        facing = facingFor(toX - x, toY - y)
        val from = heading
        heading = CreatureRoutes.headingOf(toX - x, toY - y, heading)
        add(Leg(t, millis, CreatureAction.FLY, x, y, alt, toX, toY, toAlt, facing = facing, heading = heading, turnedFrom = from, darts = manner.darts))
        x = toX
        y = toY
        alt = toAlt
        t += millis
    }

    /** Stands still for [millis]: now and then looking the other way a while, or turning round. */
    fun standFor(millis: Long) {
        if (millis <= 0L) return
        val looks = millis >= 1200L && random.nextFloat() < manner.lookChance
        var lookFrom = if (looks) millis * (25 + random.nextInt(20)) / 100 else -1L
        var lookUntil = if (looks) lookFrom + millis * (25 + random.nextInt(20)) / 100 else -1L
        // A look the other way only when it is long enough, and not too soon after the last turn.
        val turnedAt = if (facing != shown) t else lastTurn
        val tooSoon = looks && (t + lookFrom - turnedAt < CreatureRoutes.MIN_FACING_MILLIS || lookUntil - lookFrom < CreatureRoutes.MIN_FACING_MILLIS || millis - lookUntil < CreatureRoutes.FACE_TURN_MILLIS)
        if (tooSoon) {
            lookFrom = -1L
            lookUntil = -1L
        }
        add(Leg(t, millis, CreatureAction.STAND, x, y, alt, facing = facing, lookFrom = lookFrom, lookUntil = lookUntil))
        t += millis
        if (random.nextFloat() < manner.turnChance && mayTurn(t)) facing = facing.turned()
    }

    /** Stands until [end], in stops of the creature's usual length. */
    fun standUntil(end: Long) {
        while (t < end) standFor(minOf(between(manner.rest).coerceAtLeast(500L), end - t))
    }

    fun hold(action: CreatureAction, millis: Long, cell: GridCell? = null) {
        if (millis <= 0L) return
        add(Leg(t, millis, action, x, y, alt, facing = facing, heading = heading, cell = cell))
        t += millis
    }

    fun holdUntil(end: Long) {
        if (t < end) hold(CreatureAction.HOVER, end - t)
    }
}

/**
 * Where a visitor is sent and which way it comes in, from outside the game (the promo build's
 * scripts). The game never gives one.
 *
 * @property perches the cells whose points it settles on, in this order (a flier only).
 * @property fromAngle the direction it comes in from, in radians on the ground from where it is
 *   going (as atan2(dy, dx)); null for the way it would come anyway.
 * @property stayMillis how long it stays on each point; null for as long as the creature would.
 * @property fromCells how far away it sets off, in cells (a flier only); null for the usual
 *   [CreatureRoutes.flightMillis] distance, well outside the garden. It still flies at its own speed.
 */
data class CreatureDirection(
    val perches: List<GridCell> = emptyList(),
    val fromAngle: Double? = null,
    val stayMillis: Long? = null,
    val fromCells: Float? = null,
)
