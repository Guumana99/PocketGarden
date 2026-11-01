package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import com.studioguu.miniaturegarden.domain.model.GreenhouseDoor
import com.studioguu.miniaturegarden.domain.model.GreenhouseEdge
import com.studioguu.miniaturegarden.domain.model.GreenhouseSide
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import kotlin.math.abs
import kotlin.math.min

/** What goes over a greenhouse; see [GreenhouseLook.ROOF]. */
enum class GreenhouseRoof {
    /** Open to the sky: nothing over the walls. */
    NONE,

    /** A low gable of thin frames, in the colour of the walls' wood, with the faintest of glass. */
    FRAMES,
}

/** The numbers of the greenhouse's look. Change [ROOF] to change the roof. */
object GreenhouseLook {
    val ROOF = GreenhouseRoof.NONE

    /** How tall the walls stand, in tile widths. */
    const val WALL_HEIGHT = 0.6f

    /** How large a crop is drawn under glass: the tallest (corn, 0.9 of a tile) stays under the walls' top. */
    const val CROP_SCALE = 0.65f

    /** How far a roof over one cell rises above the walls, and how much more each further cell of its span adds, up to [ROOF_MAX_RISE]. */
    const val ROOF_RISE = 0.14f
    const val ROOF_RISE_PER_CELL = 0.06f
    const val ROOF_MAX_RISE = 0.26f

    /** Where in the draw order the parts go, after the things at their cell's depth; a crop is at 0. */
    const val BACK_WALL_DEPTH = -0.3f
    const val BACK_POST_DEPTH = -0.2f
    const val FRONT_WALL_DEPTH = 0.4f
    const val FRONT_POST_DEPTH = 0.5f
    const val ROOF_DEPTH = 0.7f
}

/**
 * A wall along the [side] of [cell], where the next cell is no greenhouse.
 *
 * @property door whether it is the wall with the door: one to each greenhouse.
 */
data class GreenhouseWall(val cell: GridCell, val side: GreenhouseSide, val door: Boolean = false) {
    /** The end of the wall on the left of the screen, on the ground in grid coordinates. */
    val from: Offset
        get() = when (side) {
            GreenhouseSide.NORTH -> Offset(cell.x.toFloat(), cell.y.toFloat())
            GreenhouseSide.EAST -> Offset(cell.x + 1f, cell.y + 1f)
            GreenhouseSide.SOUTH -> Offset(cell.x.toFloat(), cell.y + 1f)
            GreenhouseSide.WEST -> Offset(cell.x.toFloat(), cell.y + 1f)
        }

    /** The end on the right of the screen. */
    val to: Offset
        get() = when (side) {
            GreenhouseSide.NORTH -> Offset(cell.x + 1f, cell.y.toFloat())
            GreenhouseSide.EAST -> Offset(cell.x + 1f, cell.y.toFloat())
            GreenhouseSide.SOUTH -> Offset(cell.x + 1f, cell.y + 1f)
            GreenhouseSide.WEST -> Offset(cell.x.toFloat(), cell.y.toFloat())
        }
}

/**
 * A post at the corner ([x], [y]) of the grid, where the outline turns.
 *
 * @property cell the greenhouse cell it is drawn with, the one nearest the viewer.
 * @property back whether it stands behind what grows in [cell], at the cell's far corner.
 */
data class GreenhousePost(val x: Int, val y: Int, val cell: GridCell, val back: Boolean)

/** A point of the roof: over ([x], [y]) of the grid, [height] tile widths up. */
data class RoofPoint(val x: Float, val y: Float, val height: Float)

/**
 * What of the roof goes with [cell], the front-most cell of the stretch of roof they belong to.
 *
 * @property panes the panes of glass, each four corners round.
 * @property frames the frames, each from one point to another.
 */
data class RoofPiece(val cell: GridCell, val panes: List<List<RoofPoint>>, val frames: List<Pair<RoofPoint, RoofPoint>>)

/**
 * How the greenhouse cells of a garden join up. Like a fence's join, nothing of this is stored: it
 * is read off where the cells are, so an old one-cell greenhouse is simply one of size one, and
 * adding a cell or taking one away changes the walls round it at once.
 *
 * Cells that share a side are one greenhouse, whatever its shape. Walls stand on the sides of its
 * outline, posts where the outline turns, and it has one door ([GreenhouseDoor]). The roof, where
 * it has one, is cut into rectangles, each a gable with its ridge along its longer side.
 */
class GreenhouseLayout private constructor(
    val walls: List<GreenhouseWall>,
    val posts: List<GreenhousePost>,
    val roof: List<RoofPiece>,
) {
    private val wallsByCell = walls.groupBy { it.cell }
    private val postsByCell = posts.groupBy { it.cell }
    private val roofByCell = roof.groupBy { it.cell }

    fun wallsOf(cell: GridCell): List<GreenhouseWall> = wallsByCell[cell].orEmpty()

    fun postsOf(cell: GridCell): List<GreenhousePost> = postsByCell[cell].orEmpty()

    fun roofOf(cell: GridCell): List<RoofPiece> = roofByCell[cell].orEmpty()

    companion object {
        val EMPTY = GreenhouseLayout(emptyList(), emptyList(), emptyList())

        /**
         * The greenhouses among [objects]. [isInside] says what is inside the garden, for where a
         * door can go ([GreenhouseDoor]).
         */
        fun of(objects: List<GridObject>, isInside: (Int, Int) -> Boolean): GreenhouseLayout {
            val cells = objects.filter { it.isGreenhouse }.mapTo(HashSet()) { GridCell(it.gridX, it.gridY) }
            return of(cells, isInside) { group -> GreenhouseDoor.chosenIn(group, objects) }
        }

        fun of(
            cells: Set<GridCell>,
            isInside: (Int, Int) -> Boolean = { _, _ -> true },
            chosen: (Set<GridCell>) -> GreenhouseSide? = { null },
        ): GreenhouseLayout {
            val walls = ArrayList<GreenhouseWall>()
            val posts = ArrayList<GreenhousePost>()
            val roof = ArrayList<RoofPiece>()
            for (group in GreenhouseDoor.groups(cells)) {
                walls += wallsOf(group, GreenhouseDoor.doorOf(group, chosen(group), isInside))
                posts += postsOf(group)
                roof += rectangles(group).map(::roofOver)
            }
            return GreenhouseLayout(walls, posts, roof)
        }

        /** The walls of [group], with the door in [door]. */
        private fun wallsOf(group: Set<GridCell>, door: GreenhouseEdge): List<GreenhouseWall> =
            GreenhouseDoor.outline(group).map { GreenhouseWall(it.cell, it.side, door = it == door) }

        private fun postsOf(group: Set<GridCell>): List<GreenhousePost> {
            val ends = HashMap<Pair<Int, Int>, MutableList<GreenhouseWall>>()
            for (wall in GreenhouseDoor.outline(group).map { GreenhouseWall(it.cell, it.side) }) {
                for (end in listOf(wall.from, wall.to)) ends.getOrPut(end.x.toInt() to end.y.toInt()) { ArrayList() } += wall
            }
            return ends.filter { (_, walls) -> !straightThrough(walls) }.map { (corner, _) ->
                val (x, y) = corner
                val owner = listOf(GridCell(x - 1, y - 1), GridCell(x, y - 1), GridCell(x - 1, y), GridCell(x, y))
                    .filter { it in group }
                    .maxWith(compareBy({ it.x + it.y }, { it.x }))
                GreenhousePost(x, y, owner, back = owner.x == x && owner.y == y)
            }
        }

        /** Whether two walls just carry on from one to the other: they share the post their pictures end in. */
        private fun straightThrough(walls: List<GreenhouseWall>): Boolean {
            if (walls.size != 2) return false
            val alongX = { wall: GreenhouseWall -> wall.side == GreenhouseSide.NORTH || wall.side == GreenhouseSide.SOUTH }
            return walls[0].side == walls[1].side && alongX(walls[0]) == alongX(walls[1])
        }

        private data class Rectangle(val x: Int, val y: Int, val width: Int, val height: Int) {
            val cells: List<GridCell> get() = (y until y + height).flatMap { cy -> (x until x + width).map { GridCell(it, cy) } }
        }

        /** [group] cut into rectangles, the largest first. */
        private fun rectangles(group: Set<GridCell>): List<Rectangle> {
            val left = group.toMutableSet()
            val found = ArrayList<Rectangle>()
            while (left.isNotEmpty()) {
                val best = left.sortedWith(compareBy({ it.y }, { it.x })).flatMap { rectanglesFrom(it, left) }
                    .maxWith(compareBy<Rectangle> { it.width * it.height }.thenBy { -abs(it.width - it.height) }.thenBy { it.width })
                left -= best.cells.toSet()
                found += best
            }
            return found
        }

        private fun rectanglesFrom(corner: GridCell, cells: Set<GridCell>): List<Rectangle> {
            val found = ArrayList<Rectangle>()
            var width = Int.MAX_VALUE
            var height = 0
            while (GridCell(corner.x, corner.y + height) in cells) {
                var run = 0
                while (run < width && GridCell(corner.x + run, corner.y + height) in cells) run++
                width = run
                height++
                found += Rectangle(corner.x, corner.y, width, height)
            }
            return found
        }

        /** How far the roof over a span of [cells] rises above the walls. */
        fun rise(cells: Int): Float =
            min(GreenhouseLook.ROOF_RISE + (cells - 1) * GreenhouseLook.ROOF_RISE_PER_CELL, GreenhouseLook.ROOF_MAX_RISE)

        /**
         * The gable over [rectangle]. Worked out along its ridge (u) and across it (v), which are
         * x and y where it is at least as wide as deep, and the other way round where it is not.
         */
        private fun roofOver(rectangle: Rectangle): RoofPiece {
            val alongX = rectangle.width >= rectangle.height
            val uStart = if (alongX) rectangle.x else rectangle.y
            val vStart = if (alongX) rectangle.y else rectangle.x
            val length = if (alongX) rectangle.width else rectangle.height
            val span = if (alongX) rectangle.height else rectangle.width
            val ridge = vStart + span / 2f
            val rise = rise(span)
            fun point(u: Float, v: Float): RoofPoint {
                val height = GreenhouseLook.WALL_HEIGHT + rise * (1f - abs(v - ridge) / (span / 2f))
                return if (alongX) RoofPoint(u, v, height) else RoofPoint(v, u, height)
            }
            val uEnd = (uStart + length).toFloat()
            val vEnd = (vStart + span).toFloat()
            val panes = listOf(vStart.toFloat() to ridge, ridge to vEnd).map { (near, far) ->
                listOf(point(uStart.toFloat(), near), point(uEnd, near), point(uEnd, far), point(uStart.toFloat(), far))
            }
            val rafters = (uStart..uStart + length).flatMap { u ->
                listOf(point(u.toFloat(), vStart.toFloat()) to point(u.toFloat(), ridge), point(u.toFloat(), ridge) to point(u.toFloat(), vEnd))
            }
            val beams = listOf(ridge, vStart.toFloat(), vEnd).map { v -> point(uStart.toFloat(), v) to point(uEnd, v) }
            return RoofPiece(rectangle.cells.maxBy { it.x + it.y }, panes, rafters + beams)
        }
    }
}
