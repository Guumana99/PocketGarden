package com.studioguu.miniaturegarden.domain.model

/**
 * A creature staying in the garden for a while.
 *
 * @property cell where it comes to. For one sitting on a building, the building's front cell, which is
 *   where it is drawn in depth order.
 * @property host what it sits on, when it sits on something ([VisitPlacement.ON_ANCHOR]); null
 *   for one on the lawn.
 * @property arrivesAt when it comes into the garden (epoch millis): one that walks pops up then,
 *   one that flies sets off from outside the garden.
 * @property leavesAt when it has gone again. How it gets about in between is its route
 *   ([CreatureRoutes.plan]).
 */
data class CreatureVisit(
    val rule: VisitRule,
    val cell: GridCell,
    val host: GridObject?,
    val arrivesAt: Long,
    val leavesAt: Long,
) {
    val creature: Creature get() = rule.creature

    fun isPresentAt(now: Long): Boolean = now in arrivesAt until leavesAt
}

/**
 * Who comes to the garden, and when. Time is cut into periods of [PERIOD_MILLIS]; at the start of
 * each, every combination the garden meets ([VisitRule]) rolls its [VisitRule.chance]. A creature
 * that comes arrives a little after the period begins and leaves a little before it ends, so one
 * staying on is seen to go and come back rather than to blink.
 *
 * Everything is decided from the time and the garden alone, so the same moment always gives the
 * same visitors: they do not change when the screen is turned or the app comes back, and no
 * visit has to be stored. At most [MAX_VISITORS] come at once, one of each kind.
 */
object CreatureVisits {
    const val PERIOD_MILLIS = 5L * 60 * 1000
    const val MAX_VISITORS = 2

    /** The least and most a visitor waits after the period begins, and leaves before it ends. */
    const val MIN_MARGIN_MILLIS = 20_000L
    const val MAX_MARGIN_MILLIS = 70_000L

    fun periodOf(now: Long): Long = Math.floorDiv(now, PERIOD_MILLIS)

    fun periodStart(period: Long): Long = period * PERIOD_MILLIS

    /**
     * The visitors of the period [now] falls in, for the garden as it is: [objects] on [map],
     * under [weather], in [season], at [timeOfDay]. Visits whose time has already passed are
     * included; see [CreatureVisit.isPresentAt].
     */
    fun visitorsAt(
        now: Long,
        objects: List<GridObject>,
        weather: WeatherType,
        map: GardenMap,
        season: Season = Season.SPRING,
        timeOfDay: TimeOfDay = TimeOfDay.DAY,
    ): List<CreatureVisit> {
        val period = periodOf(now)
        val garden = GardenSnapshot(objects, weather, season, timeOfDay)
        val grid = GardenGrid(map, objects)
        val taken = HashSet<GridCell>()
        val visits = ArrayList<CreatureVisit>()
        for (rule in VisitRule.entries) {
            if (visits.size >= MAX_VISITORS) break
            if (visits.any { it.creature == rule.creature }) continue
            if (!rule.isMetBy(garden)) continue
            if (roll(period, rule, salt = 0) >= rule.chance) continue
            val spot = spotFor(rule, garden, grid, map, period, taken) ?: continue
            taken += spot.first
            val start = periodStart(period)
            visits += CreatureVisit(
                rule = rule,
                cell = spot.first,
                host = spot.second,
                arrivesAt = start + margin(period, rule, salt = 1),
                leavesAt = start + PERIOD_MILLIS - margin(period, rule, salt = 2),
            )
        }
        return visits
    }

    /** Where [rule]'s creature settles: a cell, and what it sits on there (if anything). */
    private fun spotFor(
        rule: VisitRule,
        garden: GardenSnapshot,
        grid: GardenGrid,
        map: GardenMap,
        period: Long,
        taken: Set<GridCell>,
    ): Pair<GridCell, GridObject?>? {
        val anchors = garden.settled.filter { rule.anchor.matches(it) }.sortedWith(compareBy({ it.gridY }, { it.gridX }))
        if (anchors.isEmpty()) return null
        return when (rule.placement) {
            VisitPlacement.ON_ANCHOR -> {
                val free = anchors.filter { frontCell(it) !in taken }
                if (free.isEmpty()) return null
                val host = free[pick(period, rule, free.size)]
                frontCell(host) to host
            }
            VisitPlacement.BESIDE_ANCHOR -> {
                val cells = anchors.flatMap { anchor -> anchor.occupiedCells().flatMap(::neighbours) }
                    .distinct()
                    .filter { map.isBuildable(it.x, it.y) && grid.occupantAt(it.x, it.y) == null && it !in taken }
                    .sortedWith(compareBy({ it.y }, { it.x }))
                if (cells.isEmpty()) return null
                cells[pick(period, rule, cells.size)] to null
            }
        }
    }

    /** The cell of [obj]'s footprint nearest the viewer. */
    fun frontCell(obj: GridObject): GridCell =
        GridCell(obj.gridX + obj.footprint.width - 1, obj.gridY + obj.footprint.height - 1)

    private fun neighbours(cell: GridCell) = listOf(
        GridCell(cell.x, cell.y - 1), GridCell(cell.x + 1, cell.y), GridCell(cell.x, cell.y + 1), GridCell(cell.x - 1, cell.y),
    )

    /** A number in [0, 1) fixed by the period, the rule and [salt]. */
    fun roll(period: Long, rule: VisitRule, salt: Int): Float = (hash(period, rule, salt) % 10_000L) / 10_000f

    private fun pick(period: Long, rule: VisitRule, size: Int): Int = (hash(period, rule, salt = 3) % size).toInt()

    private fun margin(period: Long, rule: VisitRule, salt: Int): Long =
        MIN_MARGIN_MILLIS + (hash(period, rule, salt) % (MAX_MARGIN_MILLIS - MIN_MARGIN_MILLIS))

    private fun hash(period: Long, rule: VisitRule, salt: Int): Long {
        var h = period * 0x9E3779B97F4A7C15uL.toLong() + (rule.ordinal + 1) * 0xBF58476D1CE4E5B9uL.toLong() + salt * 0x94D049BB133111EBuL.toLong()
        h = (h xor (h ushr 31)) * 0x94D049BB133111EBuL.toLong()
        h = h xor (h ushr 29)
        return h and Long.MAX_VALUE
    }
}

/**
 * One line of the nature notes: a creature met, or a combination that brought one.
 *
 * @property noteId [NatureNotes.creatureKey] or [NatureNotes.ruleKey].
 * @property count how many visits it has been seen on. Kept, not shown.
 * @property opened whether the player has looked at its page since it was written. A creature
 *   met for the first time is not, until its page is turned to: the book marks it as new.
 */
data class NatureNote(val noteId: String, val firstSeenAt: Long, val count: Int, val opened: Boolean = true)

/** The nature notes as a whole: what has been met and found so far. */
class NatureNotes(notes: List<NatureNote>) {
    private val byId = notes.associateBy { it.noteId }

    fun hasMet(creature: Creature): Boolean = creatureKey(creature) in byId

    fun hasFound(rule: VisitRule): Boolean = ruleKey(rule) in byId

    fun noteOf(creature: Creature): NatureNote? = byId[creatureKey(creature)]

    /** Creatures met so far. */
    val met: Int get() = Creature.entries.count(::hasMet)

    /** Met, and its page not looked at since: shown as new in the book. */
    fun isNew(creature: Creature): Boolean = noteOf(creature)?.opened == false

    /** Creatures met whose page has not been looked at yet. */
    val unopened: Int get() = Creature.entries.count(::isNew)

    companion object {
        fun creatureKey(creature: Creature) = "creature:${creature.id}"

        fun ruleKey(rule: VisitRule) = "combination:${rule.name.lowercase()}"
    }
}
