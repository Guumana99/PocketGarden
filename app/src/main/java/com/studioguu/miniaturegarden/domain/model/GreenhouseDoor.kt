package com.studioguu.miniaturegarden.domain.model

/** A side of a cell. [isFront] sides face the viewer: they are drawn over what grows in the cell. */
enum class GreenhouseSide(val dx: Int, val dy: Int, val isFront: Boolean) {
    NORTH(0, -1, false),
    EAST(1, 0, true),
    SOUTH(0, 1, true),
    WEST(-1, 0, false),
}

/** The [side] of [cell]: where a wall of a greenhouse stands. */
data class GreenhouseEdge(val cell: GridCell, val side: GreenhouseSide) {
    /** Across the screen, from the left: grid x and y run down it to the right and to the left. */
    val acrossScreen: Int get() = 2 * (cell.x - cell.y) + side.dx - side.dy
}

/**
 * Where the door of a greenhouse goes. Cells that share a side are one greenhouse, with one door.
 *
 * The door goes in a side of its outline that does not face the garden's wooden frame, which
 * nobody could come in by. The player may choose which way it faces ([GridObject.doorSide], kept on
 * every cell when it is chosen). Until then, and whenever the shape no longer allows the way chosen,
 * it goes in the middle of the sides that face the viewer.
 */
object GreenhouseDoor {
    /** [cells] divided into greenhouses: those that reach one another side by side. */
    fun groups(cells: Set<GridCell>): List<Set<GridCell>> {
        val left = cells.toMutableSet()
        val found = ArrayList<Set<GridCell>>()
        while (left.isNotEmpty()) {
            val first = left.minWith(compareBy({ it.y }, { it.x }))
            left -= first
            val group = linkedSetOf(first)
            val open = ArrayDeque(listOf(first))
            while (open.isNotEmpty()) {
                val cell = open.removeFirst()
                for (side in GreenhouseSide.entries) {
                    val next = GridCell(cell.x + side.dx, cell.y + side.dy)
                    if (left.remove(next)) {
                        group += next
                        open += next
                    }
                }
            }
            found += group
        }
        return found
    }

    /** The sides of [group]'s cells with no more of it beyond them. */
    fun outline(group: Set<GridCell>): List<GreenhouseEdge> = group.flatMap { cell ->
        GreenhouseSide.entries
            .filter { GridCell(cell.x + it.dx, cell.y + it.dy) !in group }
            .map { GreenhouseEdge(cell, it) }
    }

    /** Which of [outline]'s sides could have a door: those with the garden, not its frame, beyond them. */
    private fun clear(group: Set<GridCell>, isInside: (Int, Int) -> Boolean): List<GreenhouseEdge> =
        outline(group).filter { isInside(it.cell.x + it.side.dx, it.cell.y + it.side.dy) }

    /** The ways the door of [group] can face, in the order the button goes through them. */
    fun waysOpen(group: Set<GridCell>, isInside: (Int, Int) -> Boolean): List<GreenhouseSide> {
        val sides = clear(group, isInside).mapTo(HashSet()) { it.side }
        return GreenhouseSide.entries.filter { it in sides }
    }

    private fun middle(edges: List<GreenhouseEdge>): GreenhouseEdge {
        val sorted = edges.sortedWith(compareBy<GreenhouseEdge> { it.acrossScreen }.thenByDescending { it.cell.x + it.cell.y })
        return sorted[(sorted.size - 1) / 2]
    }

    /** The door as it goes with nothing chosen. A greenhouse with its back to two sides of the frame turns to the others. */
    private fun firstChoice(group: Set<GridCell>, isInside: (Int, Int) -> Boolean): GreenhouseEdge {
        val clear = clear(group, isInside)
        val candidates = clear.filter { it.side.isFront }.ifEmpty { clear }.ifEmpty { outline(group).filter { it.side.isFront } }
        return middle(candidates)
    }

    /** The side the door of [group] is in: of the middle cell of that side of the outline. */
    fun doorOf(group: Set<GridCell>, chosen: GreenhouseSide?, isInside: (Int, Int) -> Boolean): GreenhouseEdge {
        val edges = clear(group, isInside).filter { it.side == chosen }
        return if (edges.isEmpty()) firstChoice(group, isInside) else middle(edges)
    }

    /** The way the door of [group] goes after [current], round the ways it can face; null if it has no other. */
    fun nextWay(group: Set<GridCell>, current: GreenhouseSide, isInside: (Int, Int) -> Boolean): GreenhouseSide? {
        val ways = waysOpen(group, isInside)
        if (ways.isEmpty() || ways == listOf(current)) return null
        return ways[(ways.indexOf(current) + 1) % ways.size]
    }

    /** What [objects] have chosen for [group]: the first choice found going through its cells in order. */
    fun chosenIn(group: Set<GridCell>, objects: List<GridObject>): GreenhouseSide? =
        objects.filter { it.isGreenhouse && it.doorSide != null && GridCell(it.gridX, it.gridY) in group }
            .minWithOrNull(compareBy({ it.gridY }, { it.gridX }))?.doorSide
}
