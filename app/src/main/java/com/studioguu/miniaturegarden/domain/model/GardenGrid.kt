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
            objectId.startsWith(GridObject.FLOOR_ID_PREFIX) -> FLOOR
            else -> OBJECT
        }
    }
}

/**
 * The garden as a whole: the terrain plus everything on it. A cell has two layers, a floor
 * (paving) and an occupant (whatever stands there); every object counts on all the cells of
 * its footprint.
 */
class GardenGrid(
    val map: GardenMap,
    objects: List<GridObject>,
) {
    private val floors: Map<GridCell, GridObject> = buildMap {
        for (obj in objects) if (obj.isFloor) for (cell in obj.occupiedCells()) put(cell, obj)
    }
    private val occupants: Map<GridCell, GridObject> = buildMap {
        for (obj in objects) if (!obj.isFloor) for (cell in obj.occupiedCells()) put(cell, obj)
    }

    /** What stands on (x, y), or the river tile there; whichever of the object's cells that is. */
    fun occupantAt(x: Int, y: Int): GridObject? = occupants[GridCell(x, y)]

    /** The paving on (x, y), if any. */
    fun floorAt(x: Int, y: Int): GridObject? = floors[GridCell(x, y)]

    /** What a tap on (x, y) means: the thing standing there, or else the paving under it. */
    fun objectAt(x: Int, y: Int): GridObject? = occupantAt(x, y) ?: floorAt(x, y)

    /**
     * Whether something of [kind] with [footprint] fits with its first cell at
     * (originX, originY). Every cell must be inside the grid and not river, and its layer must
     * be free: paving only needs the floor layer, so it can go under something already standing
     * there, and anything can be stood on paving.
     */
    fun checkPlacement(
        footprint: Footprint,
        originX: Int,
        originY: Int,
        kind: PlacementKind = PlacementKind.OBJECT,
    ): PlacementCheck {
        val cells = footprint.cells(originX, originY)
        return when {
            cells.any { !map.isInside(it.x, it.y) } -> PlacementCheck.OUT_OF_BOUNDS
            cells.any { !map.isBuildable(it.x, it.y) } -> PlacementCheck.NOT_BUILDABLE
            cells.any { occupants[it]?.isTerrain == true } -> PlacementCheck.NOT_BUILDABLE
            kind != PlacementKind.OBJECT && cells.any { it in floors } -> PlacementCheck.OCCUPIED
            kind != PlacementKind.FLOOR && cells.any { it in occupants } -> PlacementCheck.OCCUPIED
            else -> PlacementCheck.OK
        }
    }
}
