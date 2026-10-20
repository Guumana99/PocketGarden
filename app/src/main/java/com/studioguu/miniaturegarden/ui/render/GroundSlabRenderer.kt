package com.studioguu.miniaturegarden.ui.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
import com.studioguu.miniaturegarden.data.asset.FlatGround
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

/**
 * Cells one copy of the flat ground texture spans, so that its repeats never line up with the
 * cells: the field ([FlatGround.field]) is [FlatGround.FIELD_SIZE] pixels laid from a tile that
 * spans [FlatGround.TILE_CELLS] cells in [FlatGround.TILE_SIZE] pixels, about 3.7 cells (#54;
 * the tile itself, 1.5 cells, was repeated before).
 */
const val FLAT_GROUND_CELLS = FlatGround.TILE_CELLS * FlatGround.FIELD_SIZE / FlatGround.TILE_SIZE

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
 * Cells one copy of the ground's broad light and dark ([FlatGround.variation]) spans: much larger
 * than the earth's own repeat and no multiple of it, so the two never fall in step.
 */
const val GROUND_VARIATION_CELLS = 4.3f

/** How far the broad light and dark is turned from the garden's axes (radians), so its repeats do not line up with the earth's. */
const val GROUND_VARIATION_TURN = 0.41f

/**
 * The screen map of the [FlatGround.variation] texture, [textureSize] pixels square: like
 * [flatGroundToScreen], [GROUND_VARIATION_CELLS] a copy and turned by [GROUND_VARIATION_TURN].
 */
fun groundVariationToScreen(transform: IsometricTransform, textureSize: Int): FloatArray {
    val origin = transform.worldToScreen(0f, 0f)
    val alongX = transform.worldToScreen(1f, 0f) - origin
    val alongY = transform.worldToScreen(0f, 1f) - origin
    val k = GROUND_VARIATION_CELLS / textureSize
    val c = kotlin.math.cos(GROUND_VARIATION_TURN)
    val s = kotlin.math.sin(GROUND_VARIATION_TURN)
    // Texture (i, j) to garden (x, y): turned, then scaled; then the garden to the screen.
    val ux = alongX * c + alongY * s
    val uy = alongY * c - alongX * s
    return floatArrayOf(ux.x * k, uy.x * k, origin.x, ux.y * k, uy.y * k, origin.y)
}

// The broad light and dark, made once, and laid in overlay so that the middle grey changes nothing.
private val groundVariationPaint: Paint by lazy {
    val size = FlatGround.VARIATION_SIZE
    val bitmap = Bitmap.createBitmap(FlatGround.variation(size), size, size, Bitmap.Config.ARGB_8888)
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    }
}
private val groundVariationMatrix = Matrix()

/**
 * Fills [area] (the garden) with the flat ground texture, repeated along the garden's two axes:
 * one field of earth with no seam between cells, since the texture tiles ([FlatGround.seamless]).
 * Over it, broad soft light and dark ([FlatGround.variation]) at a larger size and an angle,
 * so that the repeats of the earth are each a little different and form no grid.
 */
fun DrawScope.drawFlatGround(texture: Bitmap, transform: IsometricTransform, area: Path) {
    val paint = flatGroundPaint?.takeIf { it.first === texture }?.second
        ?: Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(texture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }.also { flatGroundPaint = texture to it }
    val m = flatGroundToScreen(transform, texture.width)
    flatGroundMatrix.setValues(floatArrayOf(m[0], m[1], m[2], m[3], m[4], m[5], 0f, 0f, 1f))
    paint.shader.setLocalMatrix(flatGroundMatrix)
    val v = groundVariationToScreen(transform, FlatGround.VARIATION_SIZE)
    groundVariationMatrix.setValues(floatArrayOf(v[0], v[1], v[2], v[3], v[4], v[5], 0f, 0f, 1f))
    groundVariationPaint.shader.setLocalMatrix(groundVariationMatrix)
    drawIntoCanvas {
        val path = area.asAndroidPath()
        it.nativeCanvas.drawPath(path, paint)
        it.nativeCanvas.drawPath(path, groundVariationPaint)
    }
}

// Shaders for textures laid over the ground by fillWithTexture, one per picture.
private val worldTexturePaints = HashMap<Bitmap, Paint>()
private val worldTextureMatrix = Matrix()

/**
 * Fills [area] with [texture] laid flat on the ground along the garden's axes, one copy every
 * [cellsPerCopy] cells from the garden's corner; [texture] tiles, so the copies meet without a
 * seam (the pond's water where ponds join, #54, [FlatGround.tileable]).
 */
fun DrawScope.fillWithTexture(texture: Bitmap, transform: IsometricTransform, area: Path, cellsPerCopy: Float) {
    val paint = worldTexturePaints.getOrPut(texture) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(texture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }
    val origin = transform.worldToScreen(0f, 0f)
    val alongX = transform.worldToScreen(1f, 0f) - origin
    val alongY = transform.worldToScreen(0f, 1f) - origin
    val k = cellsPerCopy / texture.width
    worldTextureMatrix.setValues(floatArrayOf(alongX.x * k, alongY.x * k, origin.x, alongX.y * k, alongY.y * k, origin.y, 0f, 0f, 1f))
    paint.shader.setLocalMatrix(worldTextureMatrix)
    drawIntoCanvas { it.nativeCanvas.drawPath(area.asAndroidPath(), paint) }
}
