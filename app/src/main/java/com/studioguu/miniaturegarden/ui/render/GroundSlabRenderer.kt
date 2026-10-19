package com.studioguu.miniaturegarden.ui.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
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

/** Cells one copy of the flat ground texture spans, so that its repeats never line up with the cells. */
const val FLAT_GROUND_CELLS = 1.5f

/**
 * The screen map of the flat ground texture ([FlatGround]), [textureSize] pixels square: its
 * pixel (i, j) goes to the point of the garden [FLAT_GROUND_CELLS] x i / size along x and as far
 * along y, the six values of x = a i + b j + c, y = d i + e j + f.
 */
fun flatGroundToScreen(transform: IsometricTransform, textureSize: Int): FloatArray {
    val origin = transform.worldToScreen(0f, 0f)
    val alongX = transform.worldToScreen(1f, 0f) - origin
    val alongY = transform.worldToScreen(0f, 1f) - origin
    val k = FLAT_GROUND_CELLS / textureSize
    return floatArrayOf(alongX.x * k, alongY.x * k, origin.x, alongX.y * k, alongY.y * k, origin.y)
}

// The shader for the flat ground, made once for each texture and moved with the camera.
private var flatGroundPaint: Pair<Bitmap, Paint>? = null
private val flatGroundMatrix = Matrix()

/**
 * Fills [area] (the garden) with the flat ground texture, repeated along the garden's two axes:
 * one field of earth with no seam between cells, since the texture tiles ([FlatGround.seamless]).
 */
fun DrawScope.drawFlatGround(texture: Bitmap, transform: IsometricTransform, area: Path) {
    val paint = flatGroundPaint?.takeIf { it.first === texture }?.second
        ?: Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(texture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }.also { flatGroundPaint = texture to it }
    val m = flatGroundToScreen(transform, texture.width)
    flatGroundMatrix.setValues(floatArrayOf(m[0], m[1], m[2], m[3], m[4], m[5], 0f, 0f, 1f))
    paint.shader.setLocalMatrix(flatGroundMatrix)
    drawIntoCanvas { it.nativeCanvas.drawPath(area.asAndroidPath(), paint) }
}
