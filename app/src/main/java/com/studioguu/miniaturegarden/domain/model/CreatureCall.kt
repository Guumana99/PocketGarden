package com.studioguu.miniaturegarden.domain.model

/**
 * Debug menu: creatures called to the garden, to look at how they move.
 *
 * @property creatures those chosen, at most [MOST] of them, oldest first.
 * @property on while on, they come whatever [VisitRule] says of the weather, the season, the time
 *   of day and the dice, and come back when they have gone.
 * @property since when they were called (epoch millis): the rounds run from then, so that one
 *   called comes at once and stays a whole round, not what is left of one already under way.
 */
data class CreatureCall(val creatures: List<Creature> = emptyList(), val on: Boolean = false, val since: Long = 0L) {
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

    fun roundOf(now: Long, since: Long = 0L): Long = Math.floorDiv(now - since, ROUND_MILLIS)

    fun roundStart(round: Long, since: Long = 0L): Long = since + round * ROUND_MILLIS

    /**
     * The visits of the round [now] falls in, of those of [creatures] the garden has a place for.
     * Nothing of the rules holds them back: not the season, the hour, the weather or the dice.
     *
     * Each goes to its own thing to settle on ([perchFor]), the one nearest the middle of the
     * garden, so that it is in view; only a garden without one has it go wherever its rules find
     * room, as before (11/7: one came to a bench or a stream at the edge of the garden, out of view).
     */
    fun visitsAt(now: Long, creatures: List<Creature>, objects: List<GridObject>, map: GardenMap, since: Long = 0L): List<CreatureVisit> {
        val round = roundOf(now, since)
        val start = roundStart(round, since)
        val garden = GardenSnapshot(objects, WeatherType.SUNNY)
        val grid = GardenGrid(map, objects)
        val taken = HashSet<GridCell>()
        val middle = (map.size - 1) / 2f
        return creatures.mapNotNull { creature ->
            val rule = VisitRule.forCreature(creature).first()
            val perch = garden.settled.filter { isPerchFor(creature, it) }
                .minWithOrNull(compareBy({ (it.gridX - middle) * (it.gridX - middle) + (it.gridY - middle) * (it.gridY - middle) }, { it.gridY }, { it.gridX }))
            val onPerch = perch?.let { thing ->
                // One that walks the ground next to its thing (the frog by the pond, the squirrel by
                // the rock) stands on a cell of its own beside it; the others are on it, or over it.
                val beside = !CreatureRoutes.flies(creature) && CreaturePerches.forCreature(creature, CreaturePerches.pointsOf(thing)).isEmpty()
                val cell = if (beside) {
                    thing.occupiedCells().flatMap { listOf(GridCell(it.x + 1, it.y), GridCell(it.x, it.y + 1), GridCell(it.x - 1, it.y), GridCell(it.x, it.y - 1)) }
                        .firstOrNull { it !in taken && it !in thing.occupiedCells() && CreatureRoutes.canWalk(creature, grid, it.x, it.y) }
                } else {
                    CreatureVisits.frontCell(thing)
                }
                cell?.let { CreatureVisit(rule, it, thing, start + ARRIVES_AFTER_MILLIS, start + ROUND_MILLIS - AWAY_MILLIS) }
            }
            onPerch?.also { taken += it.cell } ?: VisitRule.forCreature(creature).firstNotNullOfOrNull { r ->
                CreatureVisits.spotFor(r, garden, grid, map, round, taken)?.let { spot ->
                    taken += spot.first
                    CreatureVisit(r, spot.first, spot.second, start + ARRIVES_AFTER_MILLIS, start + ROUND_MILLIS - AWAY_MILLIS)
                }
            }
        }
    }

    /**
     * The one thing set down for [creature] to settle on when it is called: the well's rim for
     * the bird, a pond for the dragonfly and the frog, a fence for the ladybird, a tulip in bloom
     * for the butterfly, a rock for the squirrel to run about by.
     */
    fun perchFor(creature: Creature): String = when (creature) {
        Creature.BIRD -> StructureData.WELL.objectId
        Creature.DRAGONFLY, Creature.FROG -> GridObject.POND_OBJECT_ID
        Creature.LADYBUG -> "struct_fence"
        Creature.BUTTERFLY -> PlantSpecies.TULIP.objectId
        Creature.SQUIRREL -> "deco_rock"
    }

    /** Whether [obj] is the thing [creature] is called to ([perchFor]); a tulip only in bloom. */
    fun isPerchFor(creature: Creature, obj: GridObject): Boolean =
        obj.objectId == perchFor(creature) && (!obj.isPlant || CreaturePerches.isInBloom(obj))

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
            else -> listOf(thing(perchFor(creature)))
        }
    }

    /**
     * Where to set something down: a free cell near the middle, with a free cell beside it for
     * those that settle beside what they come to. A lawn or other ground cover leaves a cell free:
     * a garden laid all over with lawn has room, and the thing takes the cover's place
     * ([coverAt]).
     */
    fun freeCell(objects: List<GridObject>, map: GardenMap): GridCell? {
        val grid = GardenGrid(map, objects)
        fun free(x: Int, y: Int) = map.isInside(x, y) && map.isBuildable(x, y) && grid.occupantAt(x, y)?.isGroundCover != false && grid.floorAt(x, y) == null
        val middle = (map.size - 1) / 2f
        return (0 until map.size).flatMap { y -> (0 until map.size).map { x -> GridCell(x, y) } }
            .filter { (x, y) -> free(x, y) && (free(x + 1, y) || free(x, y + 1) || free(x - 1, y) || free(x, y - 1)) }
            .minWithOrNull(compareBy({ (it.x - middle) * (it.x - middle) + (it.y - middle) * (it.y - middle) }, { it.y }, { it.x }))
    }

    /** The ground cover on [cell], to be taken up for what is set down there. */
    fun coverAt(cell: GridCell, objects: List<GridObject>): List<GridObject> =
        objects.filter { it.isGroundCover && it.gridX == cell.x && it.gridY == cell.y }
}
