package com.studioguu.miniaturegarden.data.asset

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

/**
 * Turns the picture of one slab of ground (`soil_wasteland.png`: a diamond of earth seen from
 * above at an angle, its sides, crumbling rim and dry white tufts round it) into a flat, square
 * texture of the earth alone, for the garden to lay as one seamless field.
 *
 * The top face is unprojected into a square ([textureToImage]) and only its middle is kept
 * ([INSET] off each side): what is left is the cracked earth, without the edges, the tufts or the
 * slab's sides. It is then made to tile ([seamless]): its far edges run on into its near ones,
 * so the garden can repeat it across every cell at once with no line anywhere. (Repeating it
 * mirrored instead made a kaleidoscope of the earth.)
 */
object FlatGround {
    /** Share of the face cut away on every side: the crumbling rim and the tufts standing on it. */
    const val INSET = 0.14f

    /** Pixels a side of the flat texture. */
    const val SIZE = 512

    /**
     * The affine map from a pixel (i, j) of the square texture [size] across to the point of the
     * picture ([imageWidth] x [imageHeight], laid out as [slab]) it is taken from, as the six
     * values a, b, c, d, e, f of x = a i + b j + c, y = d i + e j + f.
     *
     * Texture across (i) runs along the face from its top corner to its right one, down (j) from
     * its top corner to its left one: the garden's x and y.
     */
    fun textureToImage(imageWidth: Int, imageHeight: Int, slab: GroundSlab, size: Int = SIZE): FloatArray {
        val cx = slab.anchorX * imageWidth
        val cy = slab.anchorY * imageHeight
        val halfWidth = slab.topWidth * imageWidth / 2f
        // The face is a 2:1 diamond: half as tall as it is wide.
        val halfHeight = halfWidth / 2f
        val topX = cx
        val topY = cy - halfHeight
        // Along x: top corner to right corner. Along y: top corner to left corner.
        val ux = halfWidth
        val uy = halfHeight
        val vx = -halfWidth
        val vy = halfHeight
        val step = (1f - 2f * INSET) / size
        return floatArrayOf(
            ux * step, vx * step, topX + (ux + vx) * INSET,
            uy * step, vy * step, topY + (uy + vy) * INSET,
        )
    }

    /** The flat texture of [source]'s earth, [size] pixels square. */
    fun flatten(source: Bitmap, slab: GroundSlab = GroundSlab.WASTELAND, size: Int = SIZE): Bitmap {
        val m = textureToImage(source.width, source.height, slab, size)
        val toImage = Matrix().apply { setValues(floatArrayOf(m[0], m[1], m[2], m[3], m[4], m[5], 0f, 0f, 1f)) }
        val toTexture = Matrix().also { toImage.invert(it) }
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source, toTexture, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        val pixels = IntArray(size * size)
        out.getPixels(pixels, 0, size, 0, 0, size, size)
        out.setPixels(seamless(pixels, size), 0, size, 0, 0, size, size)
        return out
    }

    /**
     * [pixels] (ARGB, [size] square) made opaque and tileable. Where the earth of the picture
     * is see-through it is filled with the earth's own average colour; then the texture is
     * blended with copies of itself moved half way round, each weighted by how far it is from its own
     * edges, so that at the edges only the moved copies show, whose middles run on unbroken
     * across them.
     */
    fun seamless(pixels: IntArray, size: Int): IntArray {
        require(pixels.size == size * size)
        // The earth's average colour, by how much of each pixel there is.
        var weight = 0.0
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        for (p in pixels) {
            val a = (p ushr 24) / 255.0
            weight += a
            sumR += ((p shr 16) and 0xFF) * a
            sumG += ((p shr 8) and 0xFF) * a
            sumB += (p and 0xFF) * a
        }
        val meanR = if (weight > 0) sumR / weight else 0.0
        val meanG = if (weight > 0) sumG / weight else 0.0
        val meanB = if (weight > 0) sumB / weight else 0.0
        val r = DoubleArray(pixels.size)
        val g = DoubleArray(pixels.size)
        val b = DoubleArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) / 255.0
            r[i] = ((p shr 16) and 0xFF) * a + meanR * (1 - a)
            g[i] = ((p shr 8) and 0xFF) * a + meanG * (1 - a)
            b[i] = (p and 0xFF) * a + meanB * (1 - a)
        }
        // How far a row or column is from the nearer edge: 0 at the edge, 1 in the middle.
        val middle = DoubleArray(size) { minOf(it + 0.5, size - it - 0.5) / (size / 2.0) }
        val half = size / 2
        val out = IntArray(pixels.size)
        // Four copies: as it is, moved half way along x, along y, and along both. Along each axis
        // a copy counts by how far that pixel of it is from its own edges, so at an edge of the
        // texture only the copies whose middle lies there show, and across the edge they run on.
        val shifts = intArrayOf(0, half)
        for (y in 0 until size) for (x in 0 until size) {
            var total = 0.0
            var mr = 0.0
            var mg = 0.0
            var mb = 0.0
            for (sy in shifts) for (sx in shifts) {
                val px = (x + sx) % size
                val py = (y + sy) % size
                val w = middle[px] * middle[py]
                val from = py * size + px
                total += w
                mr += r[from] * w
                mg += g[from] * w
                mb += b[from] * w
            }
            fun channel(v: Double) = (v / total).toInt().coerceIn(0, 255)
            out[y * size + x] = (0xFF shl 24) or (channel(mr) shl 16) or (channel(mg) shl 8) or channel(mb)
        }
        return out
    }
}
