package com.studioguu.miniaturegarden.domain.model

/**
 * What a building does for the plants round it, just by standing there.
 *
 * @property range how many cells out from the building the effect reaches, in every direction,
 *   diagonals included: 1 means the ring of cells that touch it.
 * @property moistureDrainFactor the soil of a plant in reach dries out this many times as fast;
 *   below 1 is a help.
 * @property growthFactor a healthy plant in reach grows this many times as fast.
 */
enum class StructureEffect(
    val structure: StructureData,
    val range: Int,
    val moistureDrainFactor: Float = 1f,
    val growthFactor: Float = 1f,
) {
    /** Water close at hand: the soil round a well keeps its moisture twice as long. */
    WELL(StructureData.WELL, range = 1, moistureDrainFactor = 0.5f),

    /** Shelter from the wind: what grows in the lee of the hut grows a little faster. */
    HUT(StructureData.HUT, range = 1, growthFactor = 1.1f),

    /**
     * A windmill pumps water up for the beds round it: less than a well gives the cells beside
     * it, but further out, two cells all round.
     */
    WINDMILL(StructureData.WINDMILL, range = 2, moistureDrainFactor = 0.75f);

    /**
     * The cells this effect reaches when its building stands with its first cell at
     * ([gridX], [gridY]): everything within [range] of any cell of the building, without the
     * building's own cells. Cells outside the garden are included; nothing grows there anyway.
     */
    fun cellsInReach(gridX: Int, gridY: Int): Set<GridCell> {
        val own = structure.footprint.cells(gridX, gridY).toSet()
        val reach = HashSet<GridCell>()
        for (cell in own) {
            for (dx in -range..range) for (dy in -range..range) reach += GridCell(cell.x + dx, cell.y + dy)
        }
        return reach - own
    }

    companion object {
        fun of(structure: StructureData): StructureEffect? = entries.firstOrNull { it.structure == structure }

        fun forObjectId(objectId: String): StructureEffect? = StructureData.fromObjectId(objectId)?.let(::of)
    }
}

/**
 * What the place a plant stands in does to it: the sum of the buildings in whose reach it is.
 * With nothing near, both factors are 1 and the plant fares as it would in the open.
 */
data class PlantSurroundings(
    val moistureDrainFactor: Float = 1f,
    val growthFactor: Float = 1f,
) {
    /** Whether anything nearby makes a difference. */
    val helps: Boolean get() = moistureDrainFactor < 1f || growthFactor > 1f

    companion object {
        val OPEN_GROUND = PlantSurroundings()

        /**
         * The surroundings of [plant] in a garden holding [garden].
         *
         * Only finished buildings count: one that is being built, is under wraps or is coming
         * down does nothing yet (or any more). Two buildings of the same kind do not add up: a
         * plant between two wells is as well off as one beside a single well. A well and a hut
         * together give both effects. A running sprinkler ([Sprinkler]) counts like a building
         * that keeps the soil moist.
         */
        fun of(plant: GridObject, garden: List<GridObject>): PlantSurroundings {
            if (!plant.isPlant) return OPEN_GROUND
            val here = GridCell(plant.gridX, plant.gridY)
            var drain = 1f
            var growth = 1f
            for (other in garden) {
                if (other.isWorking) continue
                val effect = StructureEffect.forObjectId(other.objectId) ?: continue
                if (here !in effect.cellsInReach(other.gridX, other.gridY)) continue
                drain = minOf(drain, effect.moistureDrainFactor)
                growth = maxOf(growth, effect.growthFactor)
            }
            if (Sprinkler.waters(plant, garden)) drain = minOf(drain, Sprinkler.MOISTURE_DRAIN_FACTOR)
            return if (drain == 1f && growth == 1f) OPEN_GROUND else PlantSurroundings(drain, growth)
        }
    }
}
