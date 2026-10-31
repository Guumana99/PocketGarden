package com.studioguu.miniaturegarden.ui.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/**
 * The four neighbours of a cell as a mask: [NORTH] = 1, [EAST] = 2, [SOUTH] = 4, [WEST] = 8, each
 * bit set when that neighbour is of the same kind. North is the cell at gridY - 1, east at gridX + 1.
 */
object RiverAutotile {
    const val NORTH = 1
    const val EAST = 2
    const val SOUTH = 4
    const val WEST = 8

    /** @param isRiver whether the cell at the given grid position is river. */
    inline fun maskAt(x: Int, y: Int, isRiver: (x: Int, y: Int) -> Boolean): Int =
        (if (isRiver(x, y - 1)) NORTH else 0) or
            (if (isRiver(x + 1, y)) EAST else 0) or
            (if (isRiver(x, y + 1)) SOUTH else 0) or
            (if (isRiver(x - 1, y)) WEST else 0)
}

// Reused between draws; all drawing happens on the main thread.
private val groundMatrix = Matrix()
private val groundPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
private val groundSource = FloatArray(8)
private val groundTarget = FloatArray(8)
private val groundSourceRect = Rect()
private val groundDestination = RectF()

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
    overlay: ((canvas: Canvas, cell: RectF) -> Unit)? = null,
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
        if (overlay == null) {
            native.drawBitmap(texture, groundSourceRect, groundDestination, groundPaint)
        } else {
            // A layer of its own, so that what is laid over the picture can keep to it.
            native.saveLayer(groundDestination, null)
            native.drawBitmap(texture, groundSourceRect, groundDestination, groundPaint)
            overlay(native, groundDestination)
            native.restore()
        }
        native.restore()
    }
}
