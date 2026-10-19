package com.studioguu.miniaturegarden.ui.render

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import com.studioguu.miniaturegarden.data.asset.GroundSlab

/**
 * Where a picture [imageWidth] x [imageHeight] of a cell's slab ([slab]) goes on screen for the
 * middle of its top face to sit on [center] and the face to be [coverage] of a cell
 * [tileWidth] across: left, top, right, bottom.
 */
fun slabBounds(
    imageWidth: Int,
    imageHeight: Int,
    slab: GroundSlab,
    center: Offset,
    tileWidth: Float,
    coverage: Float = 1f,
): FloatArray {
    val scale = slab.scaleFor(imageWidth, tileWidth, coverage)
    val width = imageWidth * scale
    val height = imageHeight * scale
    val left = center.x - slab.anchorX * width
    val top = center.y - slab.anchorY * height
    return floatArrayOf(left, top, left + width, top + height)
}

// Filtered, so that a picture drawn much smaller than it was painted stays smooth.
private val slabPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

/**
 * Draws a cell's slab picture ([bitmap], laid out as [slab]) with its top face on [center],
 * [coverage] of a cell [tileWidth] across, mirrored when [flipped].
 */
fun DrawScope.drawGroundSlab(
    bitmap: Bitmap,
    slab: GroundSlab,
    center: Offset,
    tileWidth: Float,
    coverage: Float = 1f,
    flipped: Boolean = false,
    alpha: Float = 1f,
) {
    val (left, top, right, bottom) = slabBounds(bitmap.width, bitmap.height, slab, center, tileWidth, coverage)
    scale(scaleX = if (flipped) -1f else 1f, scaleY = 1f, pivot = center) {
        drawIntoCanvas { canvas ->
            slabPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
            canvas.nativeCanvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), RectF(left, top, right, bottom), slabPaint)
        }
    }
}
