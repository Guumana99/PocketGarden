package com.studioguu.miniaturegarden.domain.model

/** Why something can or cannot be placed. */
enum class PlacementCheck {
    OK,

    /** Part of the footprint lies outside the grid. */
    OUT_OF_BOUNDS,

    /** Part of the footprint is on terrain that cannot be built on, such as the river. */
    NOT_BUILDABLE,

    /** Part of the footprint is already covered by another object. */
    OCCUPIED,
}

/**
 * The garden as a whole: the terrain plus everything standing on it. Answers the questions that
 * need both, with every object counted on all the cells of its footprint.
 */
class GardenGrid(
    val map: GardenMap,
    objects: List<GridObject>,
) {
    private val occupants: Map<GridCell, GridObject> = buildMap {
        for (obj in objects) for (cell in obj.occupiedCells()) put(cell, obj)
    }

    /** The object covering (x, y), whichever of its cells that is. */
    fun objectAt(x: Int, y: Int): GridObject? = occupants[GridCell(x, y)]

    /**
     * Whether an object with [footprint] fits with its first cell at (originX, originY): every
     * cell must be inside the grid, buildable and free.
     */
    fun checkPlacement(footprint: Footprint, originX: Int, originY: Int): PlacementCheck {
        val cells = footprint.cells(originX, originY)
        return when {
            cells.any { !map.isInside(it.x, it.y) } -> PlacementCheck.OUT_OF_BOUNDS
            cells.any { !map.isBuildable(it.x, it.y) } -> PlacementCheck.NOT_BUILDABLE
            cells.any { it in occupants } -> PlacementCheck.OCCUPIED
            else -> PlacementCheck.OK
        }
    }
}
