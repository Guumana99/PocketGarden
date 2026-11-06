package com.studioguu.miniaturegarden.domain.model

import kotlin.math.abs

/** The kinds of place a visitor can stand or settle on ([Perch]). */
enum class PerchKind {
    /** The ground of a cell with nothing standing on it: bare earth, lawn, paving, a bed without a crop. */
    GROUND,

    /** The ridge of the cottage's roof. */
    ROOF,

    /** The rim of a well. */
    WELL_RIM,

    /** The top rail of a fence. */
    FENCE,

    /** The seat of a bench. */
    BENCH,

    /** The weathervane's cockerel. */
    WEATHERVANE,

    /** A flower in bloom ([CreaturePerches.isInBloom]), or a patch of wild flowers. */
    FLOWER,

    /** The leaves at the top of a crop that is not a flower in bloom. */
    LEAF,

    /** In a pond. */
    POND,

    /** The bank of a pond. */
    POND_EDGE,

    /** The bank of a stream. */
    RIVER_EDGE,
}

/**
 * A point a visitor can settle on.
 *
 * @property x where it is over the ground, in grid units, like [CreaturePose.x].
 * @property height how high it is off the ground, in tile widths.
 * @property cell the cell it is drawn in, in depth order: the front cell of what it is on, so that
 *   a visitor there is drawn over it.
 * @property on what it is on; null for a bank.
 */
data class Perch(
    val x: Float,
    val y: Float,
    val height: Float,
    val kind: PerchKind,
    val cell: GridCell,
    val on: GridObject?,
)

/**
 * How high the top of a plant's picture stands, as the screen draws it, for the point a visitor
 * settles on: [height] over its foot and [across] to the right of it, both in tile widths.
 */
data class PlantTop(val across: Float, val height: Float)

/**
 * Where visitors may settle, and which of those places each kind of visitor goes to.
 *
 * Buildings and ornaments have their points set here by hand, from their pictures (the rim of the
 * well, the ridge of the cottage). A plant's point is found from its picture (the top of what is
 * drawn, [PlantTop]) by the screen, which has the pictures; without one it takes a height by kind.
 */
object CreaturePerches {

    /**
     * Where each kind of visitor may be: nowhere else. The ground is walked on; the others are
     * points to settle on. Stone lanterns, rocks, the arched bridge, the greenhouse and the roofs
     * of the hut and the windmill are no one's.
     */
    val ALLOWED: Map<Creature, Set<PerchKind>> = mapOf(
        Creature.BIRD to setOf(PerchKind.GROUND, PerchKind.ROOF, PerchKind.WELL_RIM, PerchKind.FENCE, PerchKind.BENCH),
        Creature.BUTTERFLY to setOf(PerchKind.FLOWER, PerchKind.WEATHERVANE, PerchKind.WELL_RIM, PerchKind.BENCH),
        Creature.SQUIRREL to setOf(PerchKind.GROUND),
        Creature.DRAGONFLY to setOf(PerchKind.FLOWER, PerchKind.WELL_RIM, PerchKind.WEATHERVANE, PerchKind.POND_EDGE, PerchKind.RIVER_EDGE),
        Creature.LADYBUG to setOf(PerchKind.FLOWER, PerchKind.LEAF, PerchKind.WELL_RIM, PerchKind.FENCE, PerchKind.BENCH),
        Creature.FROG to setOf(PerchKind.POND, PerchKind.POND_EDGE, PerchKind.WELL_RIM, PerchKind.GROUND, PerchKind.BENCH),
    )

    fun allows(creature: Creature, kind: PerchKind): Boolean = kind in ALLOWED.getValue(creature)

    /** The flowers a butterfly takes for one, in bloom ([isInBloom]). */
    val FLOWERS: Set<PlantSpecies> = setOf(
        PlantSpecies.COSMOS, PlantSpecies.ROSE, PlantSpecies.TULIP, PlantSpecies.LAVENDER,
        PlantSpecies.SUNFLOWER, PlantSpecies.NANOHANA, PlantSpecies.SNOWDROP,
    )

    /** Whether [obj] is one of the [FLOWERS] at its last stage, in bloom. */
    fun isInBloom(obj: GridObject): Boolean =
        obj.isPlant && obj.growthProgress >= 1f && obj.status != PlantStatus.WITHERED && FLOWERS.any { it.objectId == obj.objectId }

    /**
     * The points set by hand, for each thing that has them, relative to the corner of its
     * footprint: (across, down, height in tile widths). Each was set against its picture with the
     * debug menu's view of the points.
     */
    private val BY_HAND: Map<String, List<Triple<Float, Float, Float>>> = mapOf(
        // Two points on the near rim, left and right of the well's mouth.
        StructureData.WELL.objectId to listOf(Triple(0.5f, 0.82f, WELL_RIM_HEIGHT), Triple(0.82f, 0.5f, WELL_RIM_HEIGHT)),
        // Along the ridge of the roof, away from the chimney.
        StructureData.COTTAGE.objectId to listOf(Triple(1.4f, 1.5f, COTTAGE_RIDGE_HEIGHT), Triple(2.0f, 1.5f, COTTAGE_RIDGE_HEIGHT)),
        BENCH_OBJECT_ID to listOf(Triple(0.5f, 0.5f, BENCH_SEAT_HEIGHT)),
        WeatherOutlook.WEATHERVANE_OBJECT_ID to listOf(Triple(0.5f, 0.5f, WEATHERVANE_TIP_HEIGHT)),
    )

    /** Fence rails, of every kind of fence (not the kerb). */
    private const val FENCE_TOP_HEIGHT = 0.2f

    /**
     * Every point to settle on in [grid] (not the ground, which is walked: [isGround]).
     *
     * @param plantTop the top of a plant's picture as drawn; null where it is not known.
     */
    fun inGarden(grid: GardenGrid, plantTop: (GridObject) -> PlantTop? = { null }): List<Perch> {
        val perches = ArrayList<Perch>()
        for (obj in grid.objects) {
            if (obj.isWorking) continue
            perches += pointsOf(obj, plantTop)
        }
        perches += banks(grid)
        return perches
    }

    /** The points on [obj]. */
    fun pointsOf(obj: GridObject, plantTop: (GridObject) -> PlantTop? = { null }): List<Perch> {
        val front = CreatureVisits.frontCell(obj)
        BY_HAND[obj.objectId]?.let { points ->
            val kind = when (obj.objectId) {
                StructureData.WELL.objectId -> PerchKind.WELL_RIM
                StructureData.COTTAGE.objectId -> PerchKind.ROOF
                BENCH_OBJECT_ID -> PerchKind.BENCH
                else -> PerchKind.WEATHERVANE
            }
            return points.map { (dx, dy, h) -> Perch(obj.gridX + dx, obj.gridY + dy, h, kind, front, obj) }
        }
        if (obj.isFence) return listOf(Perch(obj.gridX + 0.5f, obj.gridY + 0.5f, FENCE_TOP_HEIGHT, PerchKind.FENCE, front, obj))
        if (obj.objectId == WILD_FLOWERS_OBJECT_ID) return listOf(Perch(obj.gridX + 0.5f, obj.gridY + 0.5f, WILD_FLOWER_HEIGHT, PerchKind.FLOWER, front, obj))
        if (obj.isPlant && obj.status != PlantStatus.WITHERED && obj.objectId != PlantSpecies.SHIITAKE.objectId) {
            val kind = if (isInBloom(obj)) PerchKind.FLOWER else PerchKind.LEAF
            val top = plantTop(obj) ?: PlantTop(0f, if (kind == PerchKind.FLOWER) CreatureRoutes.PERCH_ALTITUDE else LEAF_HEIGHT)
            // Across the screen is along the cell's diagonal: x and y move apart, the depth stays.
            return listOf(Perch(obj.gridX + 0.5f + top.across, obj.gridY + 0.5f - top.across, top.height, kind, front, obj))
        }
        return emptyList()
    }

    /**
     * Points on the banks of ponds and streams: on each side of a water cell that meets dry land,
     * just on the land side, at the ground. A pond's bank belongs to the pond.
     */
    private fun banks(grid: GardenGrid): List<Perch> {
        val perches = ArrayList<Perch>()
        val size = grid.map.size
        for (y in 0 until size) for (x in 0 until size) {
            val kind = waterAt(grid, x, y) ?: continue
            for ((dx, dy) in SIDES) {
                val nx = x + dx
                val ny = y + dy
                if (!grid.map.isInside(nx, ny) || waterAt(grid, nx, ny) != null) continue
                if (!isGround(grid, nx, ny)) continue
                val px = x + 0.5f + dx * BANK_REACH
                val py = y + 0.5f + dy * BANK_REACH
                perches += Perch(px, py, 0f, if (kind == PerchKind.POND) PerchKind.POND_EDGE else PerchKind.RIVER_EDGE, GridCell(nx, ny), null)
            }
        }
        return perches
    }

    /** [PerchKind.POND] for a pond, [PerchKind.RIVER_EDGE] for a stream, null for dry land. */
    private fun waterAt(grid: GardenGrid, x: Int, y: Int): PerchKind? {
        if (!grid.map.isInside(x, y)) return null
        if (!grid.map.isBuildable(x, y)) return PerchKind.RIVER_EDGE
        val thing = grid.occupantAt(x, y) ?: return null
        return when {
            thing.isPond -> PerchKind.POND
            thing.isTerrain -> PerchKind.RIVER_EDGE
            else -> null
        }
    }

    /**
     * Whether (x, y) is [PerchKind.GROUND]: inside the garden, dry, outside a greenhouse, with
     * nothing standing on it. What lies flat (lawn, pebbles, wild flowers, tilled soil without a
     * crop) is ground.
     */
    fun isGround(grid: GardenGrid, x: Int, y: Int): Boolean {
        if (!grid.map.isInside(x, y) || !grid.map.isBuildable(x, y)) return false
        if (grid.floorAt(x, y)?.isGreenhouse == true) return false
        val thing = grid.occupantAt(x, y) ?: return true
        return !thing.isWorking && thing.isGroundCover
    }

    /** The points [creature] may settle on among [perches]. */
    fun forCreature(creature: Creature, perches: List<Perch>): List<Perch> = perches.filter { allows(creature, it.kind) }

    /** The points of [perches] on [obj]. */
    fun on(obj: GridObject, perches: List<Perch>): List<Perch> = perches.filter { it.on?.instanceId == obj.instanceId }

    /** The points of [perches] within [reach] cells of (x, y), nearest first. */
    fun near(x: Float, y: Float, reach: Float, perches: List<Perch>): List<Perch> =
        perches.filter { abs(it.x - x) <= reach && abs(it.y - y) <= reach }.sortedBy { abs(it.x - x) + abs(it.y - y) }

    private val SIDES = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)

    /** How far from a water cell's middle its bank point is: just over its edge, on the land. */
    private const val BANK_REACH = 0.58f

    const val WILD_FLOWERS_OBJECT_ID = "ground_flowers"
    private const val BENCH_OBJECT_ID = "deco_bench"

    /** Heights set by hand, in tile widths (a height step is an eighth of one). */
    const val WELL_RIM_HEIGHT = 0.3f
    const val COTTAGE_RIDGE_HEIGHT = 1.83f
    const val BENCH_SEAT_HEIGHT = 0.1f
    const val WEATHERVANE_TIP_HEIGHT = 0.6f
    private const val WILD_FLOWER_HEIGHT = 0.04f
    private const val LEAF_HEIGHT = 0.05f
}
