package com.studioguu.miniaturegarden.ui.render

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/**
 * Picks the river picture for a tile from its neighbours, so that tiles laid side by side join
 * into one stream.
 *
 * The four neighbours give a mask: [NORTH] = 1, [EAST] = 2, [SOUTH] = 4, [WEST] = 8, each bit
 * set when that neighbour is river as well. North is the cell at gridY - 1, east at gridX + 1.
 * The picture for a mask sits at column `mask % 4`, row `mask / 4` of a 4 x 4 block of square
 * cells. The atlas holds one such block per animation frame, stacked top to bottom; played in a
 * loop they make the water flow.
 */
object RiverAutotile {
    const val NORTH = 1
    const val EAST = 2
    const val SOUTH = 4
    const val WEST = 8

    const val COLUMNS = 4
    const val ROWS = 4

    /** @param isRiver whether the cell at the given grid position is river. */
    inline fun maskAt(x: Int, y: Int, isRiver: (x: Int, y: Int) -> Boolean): Int =
        (if (isRiver(x, y - 1)) NORTH else 0) or
            (if (isRiver(x + 1, y)) EAST else 0) or
            (if (isRiver(x, y + 1)) SOUTH else 0) or
            (if (isRiver(x - 1, y)) WEST else 0)

    fun column(mask: Int): Int = mask % COLUMNS

    fun row(mask: Int): Int = mask / COLUMNS

    /** Milliseconds each animation frame is shown for. */
    const val FRAME_MILLIS = 260L

    /**
     * Which animation frame the tile at ([x], [y]) shows when the garden's water clock stands at
     * [tick].
     *
     * The stream flows towards the east and the south of the grid, which is down the screen, and
     * each tile runs one frame behind the tile upstream of it. So the tiles do not all blink
     * together: a ripple that is in one tile now is in the next one a frame later, and is seen
     * travelling down the river. The atlas is drawn for exactly this offset (see
     * tools/build_terrain_assets.py): with it, the waves of neighbouring tiles join without a
     * seam.
     *
     * The result may be any whole number, negative too; it wraps round when used.
     */
    fun frameFor(tick: Int, x: Int, y: Int): Int = tick - (x + y)

    /** Animation frames in an atlas of the given size: one per square block, at least one. */
    fun frameCount(atlasWidth: Int, atlasHeight: Int): Int =
        if (atlasWidth <= 0) 1 else (atlasHeight / atlasWidth).coerceAtLeast(1)

    /** Top edge, in atlas pixels, of the cell for [mask] in animation frame [frame] (wraps round). */
    fun cellTop(mask: Int, frame: Int, atlasWidth: Int, atlasHeight: Int): Int {
        val frames = frameCount(atlasWidth, atlasHeight)
        val blockHeight = atlasHeight / frames
        return Math.floorMod(frame, frames) * blockHeight + row(mask) * (blockHeight / ROWS)
    }
}

// Reused between draws; all drawing happens on the main thread.
private val groundMatrix = Matrix()
private val groundPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
private val groundSource = FloatArray(8)
private val groundTarget = FloatArray(8)
private val groundSourceRect = Rect()
private val groundDestination = RectF()

/**
 * Draws the river tile for [mask] out of [atlas] onto the ground of tile (x, y).
 *
 * @param frame which animation frame to show; any number, it wraps round.
 */
fun DrawScope.drawRiverTile(atlas: Bitmap, mask: Int, frame: Int, transform: IsometricTransform, x: Int, y: Int) {
    val cellWidth = atlas.width / RiverAutotile.COLUMNS
    val cellHeight = atlas.height / RiverAutotile.frameCount(atlas.width, atlas.height) / RiverAutotile.ROWS
    val left = RiverAutotile.column(mask) * cellWidth
    val top = RiverAutotile.cellTop(mask, frame, atlas.width, atlas.height)
    drawGroundTexture(atlas, transform, x, y, left, top, left + cellWidth, top + cellHeight)
}

/**
 * Lays a flat, seen-from-above picture onto the ground of tile (x, y): the given part of
 * [texture] (all of it by default) is stretched so that its top edge runs along the tile's
 * north side, its right edge along the east side, and so on round.
 */
fun DrawScope.drawGroundTexture(
    texture: Bitmap,
    transform: IsometricTransform,
    x: Int,
    y: Int,
    srcLeft: Int = 0,
    srcTop: Int = 0,
    srcRight: Int = texture.width,
    srcBottom: Int = texture.height,
) {
    val top = transform.worldToScreen(x.toFloat(), y.toFloat())
    val right = transform.worldToScreen(x + 1f, y.toFloat())
    val bottom = transform.worldToScreen(x + 1f, y + 1f)
    val left = transform.worldToScreen(x.toFloat(), y + 1f)

    // Picture corners, clockwise from top-left ...
    groundSource[0] = srcLeft.toFloat(); groundSource[1] = srcTop.toFloat()
    groundSource[2] = srcRight.toFloat(); groundSource[3] = srcTop.toFloat()
    groundSource[4] = srcRight.toFloat(); groundSource[5] = srcBottom.toFloat()
    groundSource[6] = srcLeft.toFloat(); groundSource[7] = srcBottom.toFloat()
    // ... onto the tile's corners, clockwise from the far one.
    groundTarget[0] = top.x; groundTarget[1] = top.y
    groundTarget[2] = right.x; groundTarget[3] = right.y
    groundTarget[4] = bottom.x; groundTarget[5] = bottom.y
    groundTarget[6] = left.x; groundTarget[7] = left.y
    groundMatrix.setPolyToPoly(groundSource, 0, groundTarget, 0, 4)
    groundSourceRect.set(srcLeft, srcTop, srcRight, srcBottom)
    groundDestination.set(groundSourceRect)

    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        native.save()
        native.concat(groundMatrix)
        native.drawBitmap(texture, groundSourceRect, groundDestination, groundPaint)
        native.restore()
    }
}
