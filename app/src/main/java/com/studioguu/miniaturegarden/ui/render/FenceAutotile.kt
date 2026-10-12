package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** What a piece of fence looks like, going by which of its four neighbours are fence too. */
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
     * Whether an arm in [direction] lies behind the post as seen on screen. North and west run
     * up the screen, away from the viewer, and must be drawn before the post; south and east run
     * towards the viewer and are drawn after it.
     */
    fun isBehindPost(direction: Int): Boolean = direction == NORTH || direction == WEST

    /**
     * Where the arm in [direction] ends: the middle of that side of tile ([x], [y]), in grid
     * coordinates. The neighbour's arm towards this tile ends at the very same point, which is
     * what makes the two meet without a gap.
     */
    fun armEnd(x: Int, y: Int, direction: Int): Pair<Float, Float> = when (direction) {
        NORTH -> (x + 0.5f) to y.toFloat()
        EAST -> (x + 1f) to (y + 0.5f)
        SOUTH -> (x + 0.5f) to (y + 1f)
        WEST -> x.toFloat() to (y + 0.5f)
        else -> throw IllegalArgumentException("Not a direction: $direction")
    }
}

// The wood of the fence sprites, so that drawn rails match the posts.
private val WOOD = Color(0xFFA67564)
private val WOOD_SHADE = Color(0xFF8A5C4C)
private val WOOD_LIGHT = Color(0xFFE5B9A0)
private val OUTLINE = Color(0xFF1E1410)

/** The two rails sit at these shares of the post's height, measured to their top edge. */
private val RAIL_HEIGHTS = floatArrayOf(0.78f, 0.42f)
private const val RAIL_THICKNESS = 0.17f

/**
 * Draws the arms of the fence on tile ([x], [y]) that lie [behind] the post, or those in front
 * of it: for each, two rails from the post to the edge of the tile and a picket half-way along.
 *
 * The rails run along the grid, so on screen they slope the way everything in the garden that
 * follows the grid does; the pickets stand upright, like the posts.
 *
 * @param postHeight how tall the post is on screen; rails and pickets are sized from it.
 */
fun DrawScope.drawFenceArms(
    transform: IsometricTransform,
    x: Int,
    y: Int,
    mask: Int,
    behind: Boolean,
    postHeight: Float,
) {
    val centre = transform.worldToScreen(x + 0.5f, y + 0.5f)
    val outline = (postHeight * 0.045f).coerceAtLeast(1f)
    for (direction in FenceAutotile.arms(mask)) {
        if (FenceAutotile.isBehindPost(direction) != behind) continue
        val (endX, endY) = FenceAutotile.armEnd(x, y, direction)
        val end = transform.worldToScreen(endX, endY)
        // The picket of an arm in front goes behind its rails' near end but before the rails
        // themselves would hide it; drawing it first and the rails over it reads as nailed on.
        drawPicket((centre + end) / 2f, postHeight, outline)
        for (share in RAIL_HEIGHTS) {
            val top = Offset(0f, -postHeight * share)
            val bottom = Offset(0f, -postHeight * (share - RAIL_THICKNESS))
            val rail = Path().apply {
                moveTo(centre.x + top.x, centre.y + top.y)
                lineTo(end.x + top.x, end.y + top.y)
                lineTo(end.x + bottom.x, end.y + bottom.y)
                lineTo(centre.x + bottom.x, centre.y + bottom.y)
                close()
            }
            // Rails that run away from the viewer catch less light.
            drawPath(rail, if (behind) WOOD_SHADE else WOOD)
            drawPath(rail, OUTLINE, style = Stroke(width = outline))
            drawLine(WOOD_LIGHT.copy(alpha = 0.7f), centre + top + Offset(0f, outline), end + top + Offset(0f, outline), outline * 0.8f)
        }
    }
}

/** An upright picket with a rounded top, standing on [foot]. */
private fun DrawScope.drawPicket(foot: Offset, postHeight: Float, outline: Float) {
    val width = postHeight * 0.26f
    val height = postHeight * 0.9f
    val topLeft = foot + Offset(-width / 2f, -height)
    val size = Size(width, height)
    val corner = CornerRadius(width / 2f, width / 2f)
    drawRoundRect(WOOD, topLeft, size, corner)
    drawRoundRect(OUTLINE, topLeft, size, corner, style = Stroke(width = outline))
    drawLine(WOOD_LIGHT.copy(alpha = 0.6f), topLeft + Offset(width * 0.3f, width * 0.5f), topLeft + Offset(width * 0.3f, height * 0.6f), outline * 0.8f)
}

/**
 * The shadow the arms of a fence throw in [light]: for each arm, the strip of ground between the
 * rail's foot line and where its top edge falls.
 */
fun DrawScope.drawFenceArmShadows(
    transform: IsometricTransform,
    x: Int,
    y: Int,
    mask: Int,
    postHeight: Float,
    light: SunLight,
) {
    if (light.alpha <= 0f) return
    val centre = transform.worldToScreen(x + 0.5f, y + 0.5f)
    val reach = Offset(light.dx, light.dy) * (postHeight * RAIL_HEIGHTS[0])
    for (direction in FenceAutotile.arms(mask)) {
        val (endX, endY) = FenceAutotile.armEnd(x, y, direction)
        val end = transform.worldToScreen(endX, endY)
        val strip = Path().apply {
            moveTo(centre.x, centre.y)
            lineTo(end.x, end.y)
            lineTo(end.x + reach.x, end.y + reach.y)
            lineTo(centre.x + reach.x, centre.y + reach.y)
            close()
        }
        drawPath(strip, Color.Black.copy(alpha = light.alpha * 0.7f))
    }
}
