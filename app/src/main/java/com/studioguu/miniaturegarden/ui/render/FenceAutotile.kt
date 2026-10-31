package com.studioguu.miniaturegarden.ui.render

/** What a section of a run looks like, going by which of its four neighbours are its own kind. */
enum class FenceShape {
    /** No fence next to it: a short section standing on its own. */
    SINGLE,

    /** One neighbour: the end of a run. */
    END,

    /** Two opposite neighbours: part of a straight run. */
    STRAIGHT,

    /** Two neighbours at a right angle. */
    CORNER,

    /** Three neighbours. */
    TEE,

    /** All four. */
    CROSS,
}

/**
 * Works out how a fence joins up with the fences round it, so that the player only ever places
 * "a fence" and a run, a corner or a crossing comes out by itself.
 *
 * The four neighbours give a mask with the same bits as the river's (see [RiverAutotile]):
 * [NORTH] = 1 (the cell at gridY - 1), [EAST] = 2 (gridX + 1), [SOUTH] = 4, [WEST] = 8. Nothing
 * about the join is stored: it is read off the garden each time, so adding or removing a fence
 * changes its neighbours at once, and a saved garden needs nothing beyond where its fences are.
 */
object FenceAutotile {
    const val NORTH = RiverAutotile.NORTH
    const val EAST = RiverAutotile.EAST
    const val SOUTH = RiverAutotile.SOUTH
    const val WEST = RiverAutotile.WEST

    /** Every direction, in the order they are drawn round a post. */
    val DIRECTIONS = listOf(NORTH, EAST, SOUTH, WEST)

    /** @param isFence whether the cell at the given grid position holds a fence. */
    inline fun maskAt(x: Int, y: Int, isFence: (x: Int, y: Int) -> Boolean): Int =
        RiverAutotile.maskAt(x, y, isFence)

    fun shapeOf(mask: Int): FenceShape = when (Integer.bitCount(mask and 0xF)) {
        0 -> FenceShape.SINGLE
        1 -> FenceShape.END
        2 -> if (mask == (NORTH or SOUTH) || mask == (EAST or WEST)) FenceShape.STRAIGHT else FenceShape.CORNER
        3 -> FenceShape.TEE
        else -> FenceShape.CROSS
    }

    /** The directions in which the fence with this mask reaches out to a neighbour. */
    fun arms(mask: Int): List<Int> = DIRECTIONS.filter { mask and it != 0 }

    /**
     * The directions in which the section with this mask has a rail of its own to draw. The rail
     * between two sections is one picture, so it belongs to the one of them that is drawn first:
     * east and south. Both posts go over it ([RAIL_DEPTH], [POST_DEPTH]).
     */
    fun railsFrom(mask: Int): List<Int> = arms(mask).filter { it == EAST || it == SOUTH }

    /**
     * Where a section's own rails and its post go after the things at its depth. Ahead of the next
     * depth, so that what stands in front of a rail still covers it, and with the post last, so
     * that it stands in front of the rail leaving it as well as of those reaching it.
     */
    const val RAIL_DEPTH = 0.3f
    const val POST_DEPTH = 0.6f

    /** The middle of the tile next to ([x], [y]) in [direction], in grid coordinates: where a rail from this post ends. */
    fun nextPost(x: Int, y: Int, direction: Int): Pair<Float, Float> = when (direction) {
        NORTH -> (x + 0.5f) to (y - 0.5f)
        EAST -> (x + 1.5f) to (y + 0.5f)
        SOUTH -> (x + 0.5f) to (y + 1.5f)
        WEST -> (x - 0.5f) to (y + 0.5f)
        else -> throw IllegalArgumentException("Not a direction: $direction")
    }
}
