package com.studioguu.miniaturegarden.domain.model

/** Why something can or cannot be placed. */
enum class PlacementCheck {
    OK,

    /** Part of the footprint lies outside the grid. */
    OUT_OF_BOUNDS,

    /** Part of the footprint is on river, whether from the map or laid by the player. */
    NOT_BUILDABLE,

    /** Part of the footprint is already covered by another object. */
    OCCUPIED,
}

/** Which layer of a cell something goes into. */
enum class PlacementKind {
    /** Stands on the ground: plants, decorations, structures. One per cell. */
    OBJECT,

    /** Paving laid on the ground. One per cell, and objects may stand on it. */
    FLOOR,

    /** Replaces the ground itself (river). Takes the whole cell: no floor, nothing on top. */
    TERRAIN;

    companion object {
        fun forObjectId(objectId: String): PlacementKind = when {
            objectId.startsWith(GridObject.TERRAIN_ID_PREFIX) -> TERRAIN
            // A pond is ground too: no paving under it, nothing standing in it.
            objectId == GridObject.POND_OBJECT_ID -> TERRAIN
            objectId.startsWith(GridObject.FLOOR_ID_PREFIX) -> FLOOR
            else -> OBJECT
        }
    }
}

/**
 * The garden as a whole: the terrain plus everything on it. A cell has two layers, a floor
 * (paving) and an occupant (whatever stands there); every object counts on all the cells of
 * its footprint.
 *
 * A lawn is an occupant too, with one exception (#55): a fence may stand on a lawn, and a lawn
 * be sown under a fence ([sharesCellWithLawn]). Such a cell holds both; the fence is what stands
 * there ([occupantAt]), and the lawn lies under it ([lawnAt]).
 */
class GardenGrid(
    val map: GardenMap,
    objects: List<GridObject>,
) {
    private val floors: Map<GridCell, GridObject> = buildMap {
        for (obj in objects) if (obj.isFloor) for (cell in obj.occupiedCells()) put(cell, obj)
    }
    private val lawns: Map<GridCell, GridObject> = buildMap {
        for (obj in objects) if (LawnGrowth.isLawn(obj)) for (cell in obj.occupiedCells()) put(cell, obj)
    }
    private val standing: Map<GridCell, GridObject> = buildMap {
        for (obj in objects) if (!obj.isFloor && !LawnGrowth.isLawn(obj)) for (cell in obj.occupiedCells()) put(cell, obj)
    }

    /**
     * What stands on (x, y), or the river tile there; whichever of the object's cells that is.
     * A lawn counts when nothing else stands on it.
     */
    fun occupantAt(x: Int, y: Int): GridObject? = GridCell(x, y).let { standing[it] ?: lawns[it] }

    /** The lawn on (x, y), if any, whether or not a fence stands on it. */
    fun lawnAt(x: Int, y: Int): GridObject? = lawns[GridCell(x, y)]

    /** The paving on (x, y), if any. */
    fun floorAt(x: Int, y: Int): GridObject? = floors[GridCell(x, y)]

    /** Whether (x, y) is soil a seed can go into: tilled, or a greenhouse bed. */
    fun isTilled(x: Int, y: Int): Boolean = floorAt(x, y)?.let { it.isTilledSoil || it.isGreenhouse } == true

    /**
     * What the ground of (x, y) is, for what may be planted there ([PlantGround]): water, then
     * whatever is laid on the cell (a bed, a greenhouse, paving, a lawn, ground cover), and
     * open earth where nothing is (the wasteland, or ground the hoe has begun to break). Whatever
     * stands there otherwise (a plant, a bench) does not change it.
     */
    fun groundAt(x: Int, y: Int): Ground {
        val cell = GridCell(x, y)
        val floor = floors[cell]
        val thing = standing[cell]
        return when {
            !map.isBuildable(x, y) || thing?.isTerrain == true || thing?.isPond == true -> Ground.WATER
            floor?.isGreenhouse == true -> Ground.GREENHOUSE
            floor?.isTilledSoil == true -> Ground.TILLED_SOIL
            floor != null -> Ground.PAVING
            cell in lawns -> Ground.LAWN
            thing?.isGroundCover == true -> Ground.GROUND_COVER
            else -> Ground.BARE_EARTH
        }
    }

    /** Whether (x, y) is under glass, where anything grows whatever the season. */
    fun isUnderGlass(x: Int, y: Int): Boolean = floorAt(x, y)?.isGreenhouse == true

    /** Whether a symbol building stands in the garden, finished or not. */
    val hasSymbol: Boolean = objects.any { it.isSymbol }

    /** What a tap on (x, y) means: the thing standing there, or else the paving under it. */
    fun objectAt(x: Int, y: Int): GridObject? = occupantAt(x, y) ?: floorAt(x, y)

    /**
     * Whether something of [kind] with [footprint] fits with its first cell at
     * (originX, originY). Every cell must be inside the grid and not river, and its layer must
     * be free: paving only needs the floor layer, so it can go under something already standing
     * there, and anything can be stood on paving. Given its [objectId], a fence may also go onto
     * a lawn and a lawn under a fence.
     */
    fun checkPlacement(
        footprint: Footprint,
        originX: Int,
        originY: Int,
        kind: PlacementKind = PlacementKind.OBJECT,
        objectId: String? = null,
    ): PlacementCheck {
        val cells = footprint.cells(originX, originY)
        return when {
            cells.any { !map.isInside(it.x, it.y) } -> PlacementCheck.OUT_OF_BOUNDS
            cells.any { !map.isBuildable(it.x, it.y) } -> PlacementCheck.NOT_BUILDABLE
            cells.any { standing[it]?.isTerrain == true } -> PlacementCheck.NOT_BUILDABLE
            // Nor is anything laid under or stood in a pond, though it can be taken away again.
            cells.any { standing[it]?.isPond == true } -> PlacementCheck.OCCUPIED
            kind != PlacementKind.OBJECT && cells.any { it in floors } -> PlacementCheck.OCCUPIED
            kind != PlacementKind.FLOOR && cells.any { taken(it, objectId) } -> PlacementCheck.OCCUPIED
            else -> PlacementCheck.OK
        }
    }

    /** Whether [cell] has no room for [objectId] (null: something that shares with nothing). */
    private fun taken(cell: GridCell, objectId: String?): Boolean {
        val placingLawn = objectId == LawnGrowth.OBJECT_ID
        val placingFence = objectId != null && sharesCellWithLawn(objectId)
        val thing = standing[cell]
        return when {
            thing != null -> !(placingLawn && thing.isFence)
            cell in lawns -> !placingFence
            else -> false
        }
    }

    companion object {
        /** Whether [objectId] can share its cell with a lawn: a fence (#55). */
        fun sharesCellWithLawn(objectId: String): Boolean = FencePart.fromObjectId(objectId) != null
    }
}
