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
 * @property inPond a frog in a pond: half under the water.
 * @property setsOff a [CreatureAction.STEP] that starts a move of a few steps (the squirrel breaks
 *   into a run), and [stops] one that ends it (it sits up again).
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
    val setsOff: Boolean = false,
    val stops: Boolean = false,
) {
    /** How far through [action] it is, 0 to 1. */
    val progress: Float get() = if (duration <= 0L) 1f else (elapsed.toFloat() / duration).coerceIn(0f, 1f)
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
) {
    private val starts = LongArray(legs.size) { legs[it].start }

    /** Where the visitor is at [now] and what it is doing; null while it is not in the garden. */
    fun poseAt(now: Long): CreaturePose? {
        if (!visit.isPresentAt(now)) return null
        val t = now - visit.arrivesAt
        val length = visit.leavesAt - visit.arrivesAt
        if (t < opening.duration) return pose(opening, t)
        val closeAt = length - closingMillis
        if (t >= closeAt && closeAt >= opening.duration) {
            val leg = closing(middle(closeAt - opening.duration))
            return pose(leg, t - closeAt)
        }
        return middle(t - opening.duration)
    }

    private fun middle(t: Long): CreaturePose {
        if (legs.isEmpty()) return pose(opening, opening.duration)
        val u = if (cyclic) Math.floorMod(t, span) else t.coerceIn(0L, span - 1)
        var index = starts.binarySearch(u)
        if (index < 0) index = -index - 2
        val leg = legs[index.coerceIn(0, legs.lastIndex)]
        return pose(leg, u - leg.start)
    }

    private fun pose(leg: Leg, into: Long): CreaturePose {
        val elapsed = into.coerceIn(0L, leg.duration)
        val f = if (leg.duration <= 0L) 1f else elapsed.toFloat() / leg.duration
        val k = if (leg.action == CreatureAction.FLY) f * f * (3f - 2f * f) else f
        val x = leg.fromX + (leg.toX - leg.fromX) * k
        val y = leg.fromY + (leg.toY - leg.fromY) * k
        val facing = if (elapsed in leg.lookFrom until leg.lookUntil) leg.facing.turned() else leg.facing
        val cell = depthCell ?: GridCell(floor(x).toInt(), floor(y).toInt())
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
            setsOff = leg.setsOff,
            stops = leg.stops,
        )
    }
}

/**
 * One stretch of a route: from one point to another (or staying put) over [duration] millis,
 * starting [start] millis into the part of the route it belongs to.
 *
 * @property lookFrom while standing, the stretch (millis into the leg) it looks the other way.
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
) {
    val end: Long get() = start + duration
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
     * Whether [creature] may go onto (x, y) on foot: inside the garden, and not water, a building,
     * a fence, a rock, a bench, a shiitake log, a work site or a greenhouse. Crops and ground
     * cover (lawn, pebbles, wild flowers) can be walked through. A frog may go into a pond.
     */
    fun canWalk(creature: Creature, grid: GardenGrid, x: Int, y: Int): Boolean {
        if (!grid.map.isInside(x, y) || !grid.map.isBuildable(x, y)) return false
        if (grid.floorAt(x, y)?.isGreenhouse == true) return false
        val thing = grid.occupantAt(x, y) ?: return true
        return when {
            thing.isPond -> creature == Creature.FROG
            thing.isTerrain || thing.isWorking -> false
            thing.isPlant -> thing.objectId != PlantSpecies.SHIITAKE.objectId
            thing.isGroundCover -> true
            else -> false
        }
    }

    /** Works out [visit]'s route through the garden as [grid] has it. */
    fun plan(visit: CreatureVisit, grid: GardenGrid): CreatureRoute {
        val creature = visit.creature
        val random = Random(seedOf(visit))
        val length = visit.leavesAt - visit.arrivesAt
        return when {
            flies(creature) -> planFlight(visit, grid, random, length)
            visit.host != null -> planOnHost(visit, grid.map.size, random, length)
            else -> planOnGround(visit, grid, random, length)
        }
    }

    // --- Those that walk on the ground ---

    private fun planOnGround(visit: CreatureVisit, grid: GardenGrid, random: Random, length: Long): CreatureRoute {
        val manner = Manner.of(visit.creature)
        val home = visit.cell
        val region = regionAround(visit.creature, grid, home, manner.roam)
        val ponds = region.filterTo(HashSet()) { grid.occupantAt(it.x, it.y)?.isPond == true }
        val landing = landingOf(visit.creature, home.x + 0.5f, home.y + 0.5f, grid.map.size, random)
        val (span, cyclic) = spanOf(length - 2 * (landing?.duration ?: POP_MILLIS))
        val plan = planner(random, manner, span, cyclic, home.x + 0.5f, home.y + 0.5f, landing)
        val reserve = if (cyclic) (manner.stepMillis * MAX_PACE * manner.roam * 2).toLong() + manner.rest.first else 0L
        plan.standFor(plan.between(manner.firstRest))
        while (true) {
            val to = pointNearby(plan, region, manner, random) ?: break
            val millis = plan.pace(hypot(to.first - plan.x, to.second - plan.y) * manner.stepMillis)
            val rest = plan.between(manner.rest)
            if (plan.t + millis + rest + reserve > span) break
            plan.stepTo(to.first, to.second, millis)
            plan.standFor(rest)
        }
        if (cyclic) goHome(plan, region, manner, home)
        plan.standUntil(span)
        return arriving(visit, plan, landing, home.x + 0.5f, home.y + 0.5f, grid.map.size, depthCell = null, ponds = ponds)
    }

    /**
     * A point a short way from where [plan] has got to, anywhere inside a cell of [region] rather
     * than in its middle, with the way there inside [region] too; null if a few tries find none.
     */
    private fun pointNearby(plan: Planner, region: Set<GridCell>, manner: Manner, random: Random): Pair<Float, Float>? {
        repeat(NEARBY_TRIES) {
            val angle = random.nextDouble() * 2 * PI
            val reach = manner.stride.start + random.nextFloat() * (manner.stride.endInclusive - manner.stride.start)
            val x = plan.x + cos(angle).toFloat() * reach
            val y = plan.y + sin(angle).toFloat() * reach
            if (isClearWay(plan.x, plan.y, x, y, region)) return x to y
        }
        return null
    }

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

    private fun planOnHost(visit: CreatureVisit, size: Int, random: Random, length: Long): CreatureRoute {
        val manner = Manner.of(visit.creature)
        val host = visit.host!!
        val homeX = host.gridX + host.footprint.width / 2f
        val homeY = host.gridY + host.footprint.height / 2f
        val landing = landingOf(visit.creature, homeX, homeY, size, random)
        val (span, cyclic) = spanOf(length - 2 * (landing?.duration ?: POP_MILLIS))
        val plan = planner(random, manner, span, cyclic, homeX, homeY, landing)
        val reserve = if (cyclic) (manner.hostStepMillis * MAX_PACE).toLong() + manner.rest.first else 0L
        plan.standFor(plan.between(manner.firstRest))
        while (true) {
            val rest = plan.between(manner.rest)
            val millis = plan.pace(manner.hostStepMillis.toFloat())
            if (plan.t + millis + rest + reserve > span) break
            val r = manner.hostReach
            plan.stepTo(homeX + random.nextFloat() * 2f * r - r, homeY + random.nextFloat() * 2f * r - r, millis)
            plan.standFor(rest)
        }
        if (cyclic) plan.stepTo(homeX, homeY, manner.hostStepMillis)
        plan.standUntil(span)
        return arriving(visit, plan, landing, homeX, homeY, size, depthCell = visit.cell, ponds = emptySet())
    }

    /**
     * How one that walks comes down: the bird flies in from outside the garden ([flightIn]) and
     * lands on (homeX, homeY); the others pop up there (null).
     */
    private fun landingOf(creature: Creature, homeX: Float, homeY: Float, size: Int, random: Random): Leg? =
        if (creature == Creature.BIRD) flightIn(homeX, homeY, 0f, size, Manner.of(creature).flySpeed, random) else null

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
    ): CreatureRoute {
        val span = maxOf(plan.span, plan.t)
        if (landing != null) {
            val cameFrom = atan2((landing.fromY - landing.toY).toDouble(), (landing.fromX - landing.toX).toDouble())
            val closing = flightOut(size, landing.duration, cameFrom, plan.random.nextDouble() * 1.2 - 0.6)
            return CreatureRoute(visit, landing, plan.legs, span, plan.cyclic, landing.duration, closing, depthCell, ponds)
        }
        val opening = Leg(0L, POP_MILLIS, CreatureAction.APPEAR, homeX, homeY, 0f, facing = plan.firstFacing)
        return CreatureRoute(
            visit, opening, plan.legs, span, plan.cyclic, POP_MILLIS,
            closing = { from -> Leg(0L, POP_MILLIS, CreatureAction.VANISH, from.x, from.y, 0f, facing = from.facing) },
            depthCell = depthCell, ponds = ponds,
        )
    }

    // --- Those that fly ---

    private fun planFlight(visit: CreatureVisit, grid: GardenGrid, random: Random, length: Long): CreatureRoute {
        val creature = visit.creature
        val manner = Manner.of(creature)
        val host = visit.host
        val homeX = host?.let { it.gridX + it.footprint.width / 2f } ?: (visit.cell.x + 0.5f)
        val homeY = host?.let { it.gridY + it.footprint.height / 2f } ?: (visit.cell.y + 0.5f)
        val cruise = cruiseAltitude(creature)
        val size = grid.map.size
        val opening = flightIn(homeX, homeY, cruise, size, manner.flySpeed, random)
        val inMillis = opening.duration
        val outMillis = inMillis
        val (span, cyclic) = spanOf(length - inMillis - outMillis)
        val plan = Planner(random, manner, span, cyclic, homeX, homeY, cruise, opening.facing)
        val flowers = flowersAround(visit, grid)
        val inside = { v: Float -> v.coerceIn(EDGE, size - EDGE) }
        val reserve = if (cyclic) 2 * (manner.reach * 2 / manner.flySpeed * 1000).toLong() + manner.rest.first else 0L
        var flightsToPerch = 1 + random.nextInt(3)
        // Where it is drifting round: a flower, for as long as it stays with that one.
        var aroundX = homeX
        var aroundY = homeY
        while (true) {
            val perching = flowers.isNotEmpty() && flightsToPerch == 0
            val tx: Float
            val ty: Float
            val alt: Float
            if (perching) {
                // Off to another flower, if there is one: it does not stay with the one it came for.
                val others = flowers.filter { abs(it.x + 0.5f - aroundX) + abs(it.y + 0.5f - aroundY) > 0.01f }.ifEmpty { flowers }
                val flower = others[random.nextInt(others.size)]
                tx = flower.x + 0.5f + (random.nextFloat() - 0.5f) * PERCH_SCATTER
                ty = flower.y + 0.5f + (random.nextFloat() - 0.5f) * PERCH_SCATTER
                alt = PERCH_ALTITUDE
                aroundX = flower.x + 0.5f
                aroundY = flower.y + 0.5f
            } else {
                tx = inside(aroundX + (random.nextFloat() * 2f - 1f) * manner.reach)
                ty = inside(aroundY + (random.nextFloat() * 2f - 1f) * manner.reach)
                alt = cruise + (random.nextFloat() - 0.5f) * 0.1f
            }
            val flight = maxOf(manner.stepMillis, plan.pace(hypot(tx - plan.x, ty - plan.y) / manner.flySpeed * 1000))
            val pause = if (perching) plan.between(manner.perch) else plan.between(manner.rest)
            if (plan.t + flight + pause + reserve > span) break
            plan.flyTo(tx, ty, alt, flight)
            if (perching) {
                plan.hold(CreatureAction.PERCH, pause)
                flightsToPerch = 1 + random.nextInt(3)
            } else {
                if (pause > 0L) plan.hold(CreatureAction.HOVER, pause)
                flightsToPerch--
            }
        }
        if (cyclic) plan.flyTo(homeX, homeY, cruise, maxOf(manner.stepMillis, (hypot(homeX - plan.x, homeY - plan.y) / manner.flySpeed * 1000).toLong()))
        plan.holdUntil(span)

        val cameFrom = atan2((opening.fromY - opening.toY).toDouble(), (opening.fromX - opening.toX).toDouble())
        val closing = flightOut(size, outMillis, cameFrom, random.nextDouble() * 1.2 - 0.6)
        return CreatureRoute(visit, opening, plan.legs, maxOf(plan.span, plan.t), plan.cyclic, outMillis, closing, depthCell = null, ponds = emptySet())
    }

    /** How far outside a garden [size] cells across a flier comes from and goes to, in cells. */
    private fun awayFrom(size: Int): Float = size * 0.8f + 3f

    /**
     * Coming in: from a point well outside the garden on the side nearest (homeX, homeY), high up
     * in the sky, down to there at [toAlt], at [speed] cells a second.
     */
    private fun flightIn(homeX: Float, homeY: Float, toAlt: Float, size: Int, speed: Float, random: Random): Leg {
        val away = awayFrom(size)
        val angle = outwards(homeX, homeY, size, otherwise = random.nextDouble() * 2 * PI) + random.nextDouble() * 1.2 - 0.6
        val startX = homeX + cos(angle).toFloat() * away
        val startY = homeY + sin(angle).toFloat() * away
        return Leg(
            0L, (away / speed * 1000).toLong(), CreatureAction.FLY, startX, startY, SKY_ALTITUDE, homeX, homeY, toAlt,
            facing = facingOf(homeX - startX, homeY - startY, Facing.LEFT),
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
        Leg(0L, millis, CreatureAction.FLY, from.x, from.y, from.altitude, endX, endY, SKY_ALTITUDE, facing = facingOf(endX - from.x, endY - from.y, from.facing))
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
     * Where a butterfly settles now and then: the flower it came for, and what else is in full
     * bloom within [FLOWER_REACH] cells of it. Only what is fully grown, so that the route stays
     * the same while the others grow.
     */
    private fun flowersAround(visit: CreatureVisit, grid: GardenGrid): List<GridCell> {
        if (visit.creature != Creature.BUTTERFLY) return emptyList()
        val home = visit.host?.let { GridCell(it.gridX, it.gridY) } ?: visit.cell
        val nearby = (-FLOWER_REACH..FLOWER_REACH).flatMap { dy -> (-FLOWER_REACH..FLOWER_REACH).map { dx -> GridCell(home.x + dx, home.y + dy) } }
            .filter { cell ->
                grid.occupantAt(cell.x, cell.y)?.let { it.isPlant && it.growthProgress >= 1f && it.status != PlantStatus.WITHERED } == true
            }
        return (listOf(home) + nearby).distinct()
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

    private fun distance(a: GridCell, b: GridCell) = abs(a.x - b.x) + abs(a.y - b.y)

    private fun neighbours(cell: GridCell) = listOf(
        GridCell(cell.x, cell.y - 1), GridCell(cell.x + 1, cell.y), GridCell(cell.x, cell.y + 1), GridCell(cell.x - 1, cell.y),
    )

    /** How near a flier keeps to the garden's edge while it flits about, in cells. */
    private const val EDGE = 0.3f

    /** How far from the flower it came for a butterfly goes for others, in cells. */
    private const val FLOWER_REACH = 3

    /** How far from a flower's middle a butterfly may settle, in cells. */
    private const val PERCH_SCATTER = 0.2f

    /** How many points are tried for the next short move before staying put. */
    private const val NEARBY_TRIES = 6

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
 * @property perch how long a butterfly rests on a flower.
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
) {
    companion object {
        const val LINGER_CHANCE = 0.2f
        const val LINGER = 2

        private val SQUIRREL = Manner(stepMillis = 420L, rest = 1500L..4800L, roam = 3, stride = 0.5f..1.5f, lookChance = 0.6f, turnChance = 0.3f)
        private val BIRD = Manner(
            stepMillis = 700L, rest = 1200L..4000L, stride = 0.3f..0.8f, lookChance = 0.5f, turnChance = 0.4f,
            hostStepMillis = 520L, hostReach = 0.22f, flySpeed = 3f,
        )
        private val FROG = Manner(stepMillis = 800L, rest = 2200L..7000L, stride = 0.4f..0.9f, lookChance = 0.2f, turnChance = 0.35f)
        private val LADYBUG = Manner(
            stepMillis = 2600L, rest = 1000L..3600L, stride = 0.2f..0.6f, turnChance = 0.5f,
            hostStepMillis = 1600L, hostReach = 0.13f,
        )
        private val BUTTERFLY = Manner(stepMillis = 900L, rest = 0L..500L, flySpeed = 1.6f, reach = 0.8f, perch = 2500L..7000L)
        private val DRAGONFLY = Manner(stepMillis = 220L, rest = 900L..2600L, flySpeed = 4f, reach = 1.4f)

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

/** How much a move's time varies from one to the next. */
private const val MIN_PACE = 0.8f
private const val MAX_PACE = 1.25f

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
) {
    val firstFacing: Facing = facing
    val legs = ArrayList<Leg>()
    var t = 0L

    fun between(range: LongRange): Long {
        val stop = if (range.last <= range.first) range.first else range.first + random.nextLong(range.last - range.first)
        return if (range.first > 0L && random.nextFloat() < Manner.LINGER_CHANCE) stop * Manner.LINGER else stop
    }

    /** [millis], a little quicker or slower than the last time: no two moves are alike. */
    fun pace(millis: Float): Long = (millis * (MIN_PACE + random.nextFloat() * (MAX_PACE - MIN_PACE))).toLong().coerceAtLeast(1L)

    fun stepTo(toX: Float, toY: Float, millis: Long) {
        facing = CreatureRoutes.facingOf(toX - x, toY - y, facing)
        legs += Leg(t, millis, CreatureAction.STEP, x, y, alt, toX, toY, alt, facing = facing, setsOff = true, stops = true)
        x = toX
        y = toY
        t += millis
    }

    fun flyTo(toX: Float, toY: Float, toAlt: Float, millis: Long) {
        facing = CreatureRoutes.facingOf(toX - x, toY - y, facing)
        legs += Leg(t, millis, CreatureAction.FLY, x, y, alt, toX, toY, toAlt, facing = facing)
        x = toX
        y = toY
        alt = toAlt
        t += millis
    }

    /** Stands still for [millis]: now and then looking the other way a while, or turning round. */
    fun standFor(millis: Long) {
        if (millis <= 0L) return
        val looks = millis >= 1200L && random.nextFloat() < manner.lookChance
        val lookFrom = if (looks) millis * (25 + random.nextInt(20)) / 100 else -1L
        val lookUntil = if (looks) lookFrom + millis * (25 + random.nextInt(20)) / 100 else -1L
        legs += Leg(t, millis, CreatureAction.STAND, x, y, alt, facing = facing, lookFrom = lookFrom, lookUntil = lookUntil)
        t += millis
        if (random.nextFloat() < manner.turnChance) facing = facing.turned()
    }

    /** Stands until [end], in stops of the creature's usual length. */
    fun standUntil(end: Long) {
        while (t < end) standFor(minOf(between(manner.rest).coerceAtLeast(500L), end - t))
    }

    fun hold(action: CreatureAction, millis: Long) {
        if (millis <= 0L) return
        legs += Leg(t, millis, action, x, y, alt, facing = facing)
        t += millis
    }

    fun holdUntil(end: Long) {
        if (t < end) hold(CreatureAction.HOVER, end - t)
    }
}
