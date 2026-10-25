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
) {
    val end: Long get() = start + duration
}

/**
 * How visitors get about the garden ([CreatureRoute]).
 *
 * Those that walk pop up next to what they came for (or on it), wander a few cells round it,
 * stopping now and then to look about, and pop away again where they are. Those that fly come in
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
            visit.host != null -> planOnHost(visit, random, length)
            else -> planOnGround(visit, grid, random, length)
        }
    }

    // --- Those that walk on the ground ---

    private fun planOnGround(visit: CreatureVisit, grid: GardenGrid, random: Random, length: Long): CreatureRoute {
        val manner = Manner.of(visit.creature)
        val home = visit.cell
        val region = regionAround(visit.creature, grid, home, manner.roam)
        val ponds = region.filterTo(HashSet()) { grid.occupantAt(it.x, it.y)?.isPond == true }
        val (span, cyclic) = spanOf(length - 2 * POP_MILLIS)
        val plan = Planner(random, manner, span, cyclic, home.x + 0.5f, home.y + 0.5f)
        var at = home
        val reserve = if (cyclic) manner.stepMillis * manner.roam * 2 + manner.rest.first else 0L
        plan.standFor(plan.between(manner.firstRest))
        while (true) {
            val choices = region.filter { it != at && distance(it, at) <= manner.stride }.sortedWith(CELL_ORDER)
            if (choices.isEmpty()) break
            val path = pathBetween(at, choices[random.nextInt(choices.size)], region)
            val rest = plan.between(manner.rest)
            if (plan.t + path.size * manner.stepMillis + rest + reserve > span) break
            for (cell in path) plan.stepTo(cell.x + 0.5f, cell.y + 0.5f, manner.stepMillis)
            at = path.last()
            plan.standFor(rest)
        }
        if (cyclic && at != home) for (cell in pathBetween(at, home, region)) plan.stepTo(cell.x + 0.5f, cell.y + 0.5f, manner.stepMillis)
        plan.standUntil(span)
        return popping(visit, plan, home.x + 0.5f, home.y + 0.5f, depthCell = null, ponds = ponds)
    }

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

    private fun planOnHost(visit: CreatureVisit, random: Random, length: Long): CreatureRoute {
        val manner = Manner.of(visit.creature)
        val host = visit.host!!
        val homeX = host.gridX + host.footprint.width / 2f
        val homeY = host.gridY + host.footprint.height / 2f
        val (span, cyclic) = spanOf(length - 2 * POP_MILLIS)
        val plan = Planner(random, manner, span, cyclic, homeX, homeY)
        val reserve = if (cyclic) manner.hostStepMillis + manner.rest.first else 0L
        plan.standFor(plan.between(manner.firstRest))
        while (true) {
            val rest = plan.between(manner.rest)
            if (plan.t + manner.hostStepMillis + rest + reserve > span) break
            val r = manner.hostReach
            plan.stepTo(homeX + random.nextFloat() * 2f * r - r, homeY + random.nextFloat() * 2f * r - r, manner.hostStepMillis)
            plan.standFor(rest)
        }
        if (cyclic) plan.stepTo(homeX, homeY, manner.hostStepMillis)
        plan.standUntil(span)
        return popping(visit, plan, homeX, homeY, depthCell = visit.cell, ponds = emptySet())
    }

    /** A route that pops up at (homeX, homeY), follows [plan], and pops away where it has got to. */
    private fun popping(visit: CreatureVisit, plan: Planner, homeX: Float, homeY: Float, depthCell: GridCell?, ponds: Set<GridCell>): CreatureRoute {
        val opening = Leg(0L, POP_MILLIS, CreatureAction.APPEAR, homeX, homeY, 0f, facing = plan.firstFacing)
        return CreatureRoute(
            visit, opening, plan.legs, maxOf(plan.span, plan.t), plan.cyclic, POP_MILLIS,
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
        val away = size * 0.8f + 3f

        // Coming in: from a point well outside the garden on the side nearest where it is going,
        // high up, down to there.
        val angle = outwards(homeX, homeY, size, otherwise = random.nextDouble() * 2 * PI) + random.nextDouble() * 1.2 - 0.6
        val startX = homeX + cos(angle).toFloat() * away
        val startY = homeY + sin(angle).toFloat() * away
        val inMillis = (away / manner.flySpeed * 1000).toLong()
        val opening = Leg(
            0L, inMillis, CreatureAction.FLY, startX, startY, SKY_ALTITUDE, homeX, homeY, cruise,
            facing = facingOf(homeX - startX, homeY - startY, Facing.LEFT),
        )
        val outMillis = inMillis
        val (span, cyclic) = spanOf(length - inMillis - outMillis)
        val plan = Planner(random, manner, span, cyclic, homeX, homeY, cruise, opening.facing)
        val flowers = flowersAround(visit)
        val inside = { v: Float -> v.coerceIn(EDGE, size - EDGE) }
        val reserve = if (cyclic) 2 * (manner.reach * 2 / manner.flySpeed * 1000).toLong() + manner.rest.first else 0L
        var flightsToPerch = 2 + random.nextInt(3)
        while (true) {
            val perching = flowers.isNotEmpty() && flightsToPerch == 0
            val tx: Float
            val ty: Float
            val alt: Float
            if (perching) {
                val flower = flowers[random.nextInt(flowers.size)]
                tx = flower.x + 0.5f
                ty = flower.y + 0.5f
                alt = PERCH_ALTITUDE
            } else {
                tx = inside(homeX + (random.nextFloat() * 2f - 1f) * manner.reach)
                ty = inside(homeY + (random.nextFloat() * 2f - 1f) * manner.reach)
                alt = cruise + (random.nextFloat() - 0.5f) * 0.1f
            }
            val flight = maxOf(manner.stepMillis, (hypot(tx - plan.x, ty - plan.y) / manner.flySpeed * 1000).toLong())
            val pause = if (perching) plan.between(manner.perch) else plan.between(manner.rest)
            if (plan.t + flight + pause + reserve > span) break
            plan.flyTo(tx, ty, alt, flight)
            if (perching) {
                plan.hold(CreatureAction.PERCH, pause)
                flightsToPerch = 2 + random.nextInt(3)
            } else {
                if (pause > 0L) plan.hold(CreatureAction.HOVER, pause)
                flightsToPerch--
            }
        }
        if (cyclic) plan.flyTo(homeX, homeY, cruise, maxOf(manner.stepMillis, (hypot(homeX - plan.x, homeY - plan.y) / manner.flySpeed * 1000).toLong()))
        plan.holdUntil(span)

        // Going away: from wherever it is, outwards from the middle of the garden, up and out of it.
        val leaveAngle = random.nextDouble() * 1.2 - 0.6
        val closing = { from: CreaturePose ->
            val out = outwards(from.x, from.y, size, otherwise = angle)
            val endX = from.x + cos(out + leaveAngle).toFloat() * away
            val endY = from.y + sin(out + leaveAngle).toFloat() * away
            Leg(0L, outMillis, CreatureAction.FLY, from.x, from.y, from.altitude, endX, endY, SKY_ALTITUDE, facing = facingOf(endX - from.x, endY - from.y, from.facing))
        }
        return CreatureRoute(visit, opening, plan.legs, maxOf(plan.span, plan.t), plan.cyclic, outMillis, closing, depthCell = null, ponds = emptySet())
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
     * Where a butterfly settles now and then: on the flower it came for. That one only, and not
     * whichever is in bloom nearby, so that the route stays the same while the flowers grow.
     */
    private fun flowersAround(visit: CreatureVisit): List<GridCell> =
        if (visit.creature == Creature.BUTTERFLY) listOf(visit.host?.let { GridCell(it.gridX, it.gridY) } ?: visit.cell) else emptyList()

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

    private val CELL_ORDER = compareBy<GridCell>({ it.y }, { it.x })

    /** How near a flier keeps to the garden's edge while it flits about, in cells. */
    private const val EDGE = 0.3f
}

/**
 * How a kind of visitor goes about.
 *
 * @property stepMillis on the ground, how long it takes from one cell to the next; for a flier,
 *   the shortest flight.
 * @property rest how long it stops for between moves (a flier: hovers).
 * @property firstRest how long it stays put after arriving.
 * @property roam how many cells it goes from where it came, on the ground.
 * @property stride how many cells at most it goes in one go.
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
    val stride: Int = 2,
    val lookChance: Float = 0f,
    val turnChance: Float = 0f,
    val hostStepMillis: Long = 600L,
    val hostReach: Float = 0.15f,
    val flySpeed: Float = 1f,
    val reach: Float = 1f,
    val perch: LongRange = 0L..0L,
) {
    companion object {
        private val SQUIRREL = Manner(stepMillis = 380L, rest = 1800L..4200L, roam = 3, stride = 3, lookChance = 0.6f, turnChance = 0.3f)
        private val BIRD = Manner(
            stepMillis = 560L, rest = 1500L..3500L, stride = 2, lookChance = 0.5f, turnChance = 0.4f,
            hostStepMillis = 520L, hostReach = 0.22f,
        )
        private val FROG = Manner(stepMillis = 620L, rest = 2500L..6000L, stride = 2, lookChance = 0.2f, turnChance = 0.35f)
        private val LADYBUG = Manner(
            stepMillis = 2200L, rest = 1200L..3000L, stride = 1, turnChance = 0.5f,
            hostStepMillis = 1600L, hostReach = 0.13f,
        )
        private val BUTTERFLY = Manner(stepMillis = 900L, rest = 0L..300L, flySpeed = 1.8f, reach = 1.1f, perch = 3000L..6000L)
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

    fun between(range: LongRange): Long = if (range.last <= range.first) range.first else range.first + random.nextLong(range.last - range.first)

    fun stepTo(toX: Float, toY: Float, millis: Long) {
        facing = CreatureRoutes.facingOf(toX - x, toY - y, facing)
        legs += Leg(t, millis, CreatureAction.STEP, x, y, alt, toX, toY, alt, facing = facing)
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
