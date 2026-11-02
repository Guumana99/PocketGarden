package com.studioguu.miniaturegarden.domain.model

/**
 * Debug menu: creatures called to the garden, to look at how they move.
 *
 * @property creatures those chosen, at most [MOST] of them, oldest first.
 * @property on while on, they come whatever [VisitRule] says of the weather, the season, the time
 *   of day and the dice, and come back when they have gone.
 */
data class CreatureCall(val creatures: List<Creature> = emptyList(), val on: Boolean = false) {
    /** Whether anyone is being called at all. */
    val calling: Boolean get() = on && creatures.isNotEmpty()

    /** With [creature] chosen, or no longer: a choice past [MOST] takes the place of the oldest. */
    fun toggled(creature: Creature): CreatureCall = when {
        creature in creatures -> copy(creatures = creatures - creature)
        creatures.size >= MOST -> copy(creatures = creatures.drop(1) + creature)
        else -> copy(creatures = creatures + creature)
    }

    companion object {
        const val MOST = CreatureVisits.MAX_VISITORS
    }
}

/** Who comes when called ([CreatureCall]), and what the garden needs to have for them. */
object CreatureCalls {
    /** One visit and the gap before the next: long enough to watch, short enough to wait for. */
    const val ROUND_MILLIS = 90_000L

    /** How long after a round begins they arrive, and how long before it ends they have gone. */
    const val ARRIVES_AFTER_MILLIS = 1_000L
    const val AWAY_MILLIS = 5_000L

    fun roundOf(now: Long): Long = Math.floorDiv(now, ROUND_MILLIS)

    fun roundStart(round: Long): Long = round * ROUND_MILLIS

    /** The visits of the round [now] falls in, of those of [creatures] the garden has a place for. */
    fun visitsAt(now: Long, creatures: List<Creature>, objects: List<GridObject>, map: GardenMap): List<CreatureVisit> {
        val round = roundOf(now)
        val start = roundStart(round)
        val garden = GardenSnapshot(objects, WeatherType.SUNNY)
        val grid = GardenGrid(map, objects)
        val taken = HashSet<GridCell>()
        return creatures.mapNotNull { creature ->
            VisitRule.forCreature(creature).firstNotNullOfOrNull { rule ->
                CreatureVisits.spotFor(rule, garden, grid, map, round, taken)?.let { spot ->
                    taken += spot.first
                    CreatureVisit(rule, spot.first, spot.second, start + ARRIVES_AFTER_MILLIS, start + ROUND_MILLIS - AWAY_MILLIS)
                }
            }
        }
    }

    /** What is set down on the free [cell] for [creature] to come to, when the garden has nothing for it. */
    fun thingsFor(creature: Creature, cell: GridCell, now: Long): List<GridObject> {
        fun thing(objectId: String, growth: Float = 0f) = GridObject(
            instanceId = "called_${creature.id}_${objectId}_${cell.x}_${cell.y}",
            gridX = cell.x,
            gridY = cell.y,
            objectId = objectId,
            growthProgress = growth,
            lastUpdatedTimestamp = now,
        )
        fun grown(species: PlantSpecies) = listOf(thing(GridObject.TILLED_SOIL_OBJECT_ID), thing(species.objectId, growth = 1f))
        return when (creature) {
            Creature.BUTTERFLY -> grown(PlantSpecies.TULIP)
            Creature.LADYBUG -> grown(PlantSpecies.MINT)
            Creature.BIRD -> listOf(thing("deco_bench"))
            Creature.SQUIRREL -> listOf(thing("deco_rock"))
            Creature.FROG, Creature.DRAGONFLY -> listOf(thing(GridObject.POND_OBJECT_ID))
        }
    }

    /**
     * Where to set something down: a free cell near the middle, with a free cell beside it for
     * those that settle beside what they come to.
     */
    fun freeCell(objects: List<GridObject>, map: GardenMap): GridCell? {
        val grid = GardenGrid(map, objects)
        fun free(x: Int, y: Int) = map.isInside(x, y) && map.isBuildable(x, y) && grid.occupantAt(x, y) == null && grid.floorAt(x, y) == null
        val middle = (map.size - 1) / 2f
        return (0 until map.size).flatMap { y -> (0 until map.size).map { x -> GridCell(x, y) } }
            .filter { (x, y) -> free(x, y) && (free(x + 1, y) || free(x, y + 1) || free(x - 1, y) || free(x, y - 1)) }
            .minWithOrNull(compareBy({ (it.x - middle) * (it.x - middle) + (it.y - middle) * (it.y - middle) }, { it.y }, { it.x }))
    }
}
