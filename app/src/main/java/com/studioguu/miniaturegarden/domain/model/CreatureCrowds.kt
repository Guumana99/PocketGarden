package com.studioguu.miniaturegarden.domain.model

/**
 * How many of a kind come together. The butterfly and the ladybird come in more numbers to a
 * garden of many flowers, the dragonfly and the frog to one of much water, and either way to a
 * player well on in the game; the bird and the squirrel always come alone. Whatever the garden,
 * one alone is at least an even chance, so that a lone visitor is never a thing of the past.
 *
 * All the numbers are here, to be tuned in one place:
 *
 *     creature              when                                        1     2     3
 *     butterfly, ladybird   always                                      100%
 *                           6 flowers in bloom or more, or Lv.8 on       70%   30%
 *                           12 flowers in bloom or more, and Lv.12 on    50%   35%   15%
 *     dragonfly, frog       always                                      100%
 *                           4 cells of water or more, or Lv.10 on        70%   30%
 *                           8 cells of water or more, and Lv.14 on       50%   50%
 *
 * Flowers are those in bloom ([CreaturePerches.isInBloom]) and the cells of wild flowers; water is
 * the cells of ponds and streams. Each after the first comes [STAGGER_MILLIS] after the one before
 * it, to a place of its own; with too few places, fewer come.
 */
object CreatureCrowds {

    /**
     * One step of the table: at [habitat] or more, and (or, when not [both]) at [level] or more,
     * the chances of one, two, three... coming, which add up to 1.
     */
    data class Tier(val habitat: Int, val level: Int, val both: Boolean, val chances: List<Float>) {
        fun holds(habitat: Int, level: Int): Boolean =
            if (both) habitat >= this.habitat && level >= this.level else habitat >= this.habitat || level >= this.level
    }

    /** For the butterfly and the ladybird, by flowers in bloom; the best tier first. */
    val FLOWER_TIERS = listOf(
        Tier(habitat = 12, level = 12, both = true, chances = listOf(0.5f, 0.35f, 0.15f)),
        Tier(habitat = 6, level = 8, both = false, chances = listOf(0.7f, 0.3f)),
    )

    /** For the dragonfly and the frog, by cells of water; the best tier first. */
    val WATER_TIERS = listOf(
        Tier(habitat = 8, level = 14, both = true, chances = listOf(0.5f, 0.5f)),
        Tier(habitat = 4, level = 10, both = false, chances = listOf(0.7f, 0.3f)),
    )

    /** How long after the one before it each of a crowd comes. */
    const val STAGGER_MILLIS = 4_000L

    /** How much sooner than the one before it each leaves, so that they do not all go at once. */
    const val LEAVE_STAGGER_MILLIS = 2_500L

    /** The tiers [creature] comes in numbers by; null for one that always comes alone. */
    fun tiersFor(creature: Creature): List<Tier>? = when (creature) {
        Creature.BUTTERFLY, Creature.LADYBUG -> FLOWER_TIERS
        Creature.DRAGONFLY, Creature.FROG -> WATER_TIERS
        Creature.BIRD, Creature.SQUIRREL -> null
    }

    /** What [creature]'s numbers go by in [garden]: its flowers in bloom, or its cells of water. */
    fun habitat(creature: Creature, garden: List<GridObject>): Int = when (tiersFor(creature)) {
        FLOWER_TIERS -> garden.count { CreaturePerches.isInBloom(it) || it.objectId == CreaturePerches.WILD_FLOWERS_OBJECT_ID }
        WATER_TIERS -> garden.filter { it.isPond || it.objectId == GridObject.RIVER_OBJECT_ID }.sumOf { it.occupiedCells().size }
        else -> 0
    }

    /** The chances of one, two, three... of [creature] coming, at [habitat] and [level]. */
    fun chances(creature: Creature, habitat: Int, level: Int): List<Float> =
        tiersFor(creature)?.firstOrNull { it.holds(habitat, level) }?.chances ?: listOf(1f)

    /** How many come, for a [roll] in [0, 1). */
    fun countFor(creature: Creature, habitat: Int, level: Int, roll: Float): Int {
        var sum = 0f
        val chances = chances(creature, habitat, level)
        chances.forEachIndexed { i, chance ->
            sum += chance
            if (roll < sum) return i + 1
        }
        return chances.size
    }

    /** The most that may come, at [habitat] and [level]. */
    fun mostFor(creature: Creature, habitat: Int, level: Int): Int = chances(creature, habitat, level).size

    /**
     * [first] and up to [count] - 1 more of its kind, each to a place of its own: a host of its
     * own (another flower, crop or stretch of water the rule settles on, the nearest first), or a
     * cell of its own beside the water for the frog. A flier may share its host with the others,
     * as long as there are points enough round it for each to settle on ([CreatureRoutes] gives
     * each its own share of them). With too few places, fewer come.
     *
     * @param pick turns a companion's number into the [CreatureVisits.spotFor] choice of a cell.
     */
    internal fun crowdOf(
        first: CreatureVisit,
        count: Int,
        garden: GardenSnapshot,
        grid: GardenGrid,
        map: GardenMap,
        taken: MutableSet<GridCell>,
        pick: (Int) -> Long,
    ): List<CreatureVisit> {
        if (count <= 1) return listOf(first)
        val creature = first.creature
        val rule = first.rule
        val flies = CreatureRoutes.flies(creature)
        val visits = arrayListOf(first)
        val hosts = HashSet<String>().apply { first.host?.let { add(it.instanceId) } }
        val room = if (flies) {
            val home = first.host?.let { GridCell(it.gridX, it.gridY) } ?: first.cell
            CreaturePerches.near(home.x + 0.5f, home.y + 0.5f, CreatureRoutes.PERCH_REACH_IN_FLIGHT, CreaturePerches.forCreature(creature, CreaturePerches.inGarden(grid))).size
        } else {
            Int.MAX_VALUE
        }
        for (k in 1 until count) {
            if (flies && room < k + 1) break
            val next: Pair<GridCell, GridObject?>? = if (first.host != null) {
                val other = garden.settled.filter { (rule.anchor.matches(it) || it.objectId == first.host.objectId) && it.instanceId !in hosts && CreatureVisits.frontCell(it) !in taken }
                    .minByOrNull { (it.gridX - first.host.gridX) * (it.gridX - first.host.gridX) + (it.gridY - first.host.gridY) * (it.gridY - first.host.gridY) }
                when {
                    other != null -> CreatureVisits.frontCell(other) to other
                    flies -> first.cell to first.host
                    else -> null
                }
            } else {
                CreatureVisits.spotFor(rule, garden, grid, map, pick(k), taken)
            }
            next ?: break
            next.second?.let { hosts += it.instanceId }
            taken += next.first
            visits += first.copy(
                cell = next.first,
                host = next.second,
                arrivesAt = first.arrivesAt + k * STAGGER_MILLIS,
                leavesAt = (first.leavesAt - k * LEAVE_STAGGER_MILLIS).coerceAtLeast(first.arrivesAt + k * STAGGER_MILLIS + 1L),
                companion = k,
            )
        }
        return visits.map { it.copy(crowd = visits.size) }
    }
}
