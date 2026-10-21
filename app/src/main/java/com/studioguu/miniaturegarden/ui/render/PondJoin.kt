package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.domain.model.GridCell

/**
 * How ponds side by side join into one pool. The picture (`pond.png`) is one cell of water in a
 * ring of logs and stones; laid next to each other as they are, two ponds were two squares with
 * a double wall of logs between them. Where two ponds share a side, the logs along it are taken
 * away and the water runs through ([Bridge]); where four make a square, the middle where their
 * corners meet is water too ([corners]); and at either end of a join that lies on the pool's
 * outer edge, the logs run on across the join ([Bridge.openBefore], [Bridge.openAfter], [GAP_FROM]),
 * so that the ring of logs runs on round the pool without a gap.
 *
 * Like the river and the fences, nothing of this is stored: it is read from where the ponds are,
 * every time the garden is drawn.
 */
object PondJoin {
    /** Bits of a pond's [mask]: which of its four neighbours is a pond too (as RiverAutotile). */
    const val NORTH = 1
    const val EAST = 2
    const val SOUTH = 4
    const val WEST = 8

    /**
     * Where the logs stand, as a share of the cell from the side they line: the open water of a
     * pond lies between [PondWater.WATER_FROM] and [PondWater.WATER_TO]. A join is painted over
     * from [JOIN_FROM] in the first cell to [JOIN_TO] (past the shared side) in the second, which
     * covers both cells' logs along that side and the tops of them where they stand up above the
     * water; its ends fade out over [JOIN_FADE] into the water of the picture.
     */
    const val JOIN_FROM = 0.6f
    const val JOIN_TO = 1.27f
    const val JOIN_FADE = 0.08f

    /**
     * The gap a join leaves in the ring of logs on the pool's outer edge, along the join: from
     * the first pond's log end to the second's, with a little of each ([GAP_FROM], [GAP_SPAN]).
     * It is closed with the middle of the first pond's own log, moved [GAP_SHIFT] along: the
     * part of the log that ends up over the gap is its middle ([GAP_FROM] - [GAP_SHIFT] on), not
     * its end (#55).
     */
    const val GAP_FROM = 0.74f
    const val GAP_SPAN = 0.52f
    const val GAP_SHIFT = 0.49f

    /** Across the join, where the log on the first (north or west) and the last bank lies, shares of the cell. */
    val BANK_BEFORE = -0.06f..0.26f
    val BANK_AFTER = 0.74f..1.06f

    /**
     * A join between the pond at ([x], [y]) and the next one east of it ([alongX]) or south of it.
     * [openBefore] and [openAfter]: the join's ends, on the north (or west) and on the south (or
     * east), lie on the pool's outer edge, where stones are laid over the ends of the logs.
     */
    data class Bridge(val x: Int, val y: Int, val alongX: Boolean, val openBefore: Boolean, val openAfter: Boolean)

    /** Which of the four neighbours of ([x], [y]) are ponds, as [NORTH] | [EAST] | [SOUTH] | [WEST]. */
    fun mask(x: Int, y: Int, isPond: (Int, Int) -> Boolean): Int =
        (if (isPond(x, y - 1)) NORTH else 0) or
            (if (isPond(x + 1, y)) EAST else 0) or
            (if (isPond(x, y + 1)) SOUTH else 0) or
            (if (isPond(x - 1, y)) WEST else 0)

    /**
     * The joins the pond at ([x], [y]) is the first cell of: to the east and to the south. Each
     * join between two ponds is so found exactly once, from the one to the north or west of it.
     */
    fun bridgesFrom(x: Int, y: Int, isPond: (Int, Int) -> Boolean): List<Bridge> = buildList {
        if (isPond(x + 1, y)) {
            // Along x: its ends are on the north and the south. An end is open unless the cells on
            // that side of both ponds are ponds as well (then the water carries on there).
            add(
                Bridge(
                    x, y, alongX = true,
                    openBefore = !(isPond(x, y - 1) && isPond(x + 1, y - 1)),
                    openAfter = !(isPond(x, y + 1) && isPond(x + 1, y + 1)),
                ),
            )
        }
        if (isPond(x, y + 1)) {
            add(
                Bridge(
                    x, y, alongX = false,
                    openBefore = !(isPond(x - 1, y) && isPond(x - 1, y + 1)),
                    openAfter = !(isPond(x + 1, y) && isPond(x + 1, y + 1)),
                ),
            )
        }
    }

    /**
     * Whether the corner where ([x], [y]) meets the cells east, south and south-east of it lies
     * inside the pool: all four are ponds, and the middle where they meet is water.
     */
    fun cornerInside(x: Int, y: Int, isPond: (Int, Int) -> Boolean): Boolean =
        isPond(x + 1, y) && isPond(x, y + 1) && isPond(x + 1, y + 1)

    /** Every join in a garden whose ponds are on [cells]. */
    fun bridges(cells: Set<GridCell>): List<Bridge> {
        val isPond: (Int, Int) -> Boolean = { x, y -> GridCell(x, y) in cells }
        return cells.sortedWith(compareBy({ it.y }, { it.x })).flatMap { bridgesFrom(it.x, it.y, isPond) }
    }

    /** The pools the ponds on [cells] make: each a set of ponds joined side by side. */
    fun pools(cells: Set<GridCell>): List<Set<GridCell>> {
        val left = cells.toMutableSet()
        val pools = ArrayList<Set<GridCell>>()
        while (left.isNotEmpty()) {
            val start = left.first()
            val pool = HashSet<GridCell>()
            val queue = ArrayDeque(listOf(start))
            left.remove(start)
            while (queue.isNotEmpty()) {
                val cell = queue.removeFirst()
                pool += cell
                for (next in listOf(GridCell(cell.x + 1, cell.y), GridCell(cell.x - 1, cell.y), GridCell(cell.x, cell.y + 1), GridCell(cell.x, cell.y - 1))) {
                    if (left.remove(next)) queue.addLast(next)
                }
            }
            pools += pool
        }
        return pools
    }
}
