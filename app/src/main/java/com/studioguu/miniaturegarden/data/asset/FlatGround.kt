package com.studioguu.miniaturegarden.data.asset

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns the picture of one slab of ground (`soil_wasteland.png`: a diamond of earth seen from
 * above at an angle, its sides, crumbling rim and dry white tufts round it) into a flat, square
 * texture of the earth alone, for the garden to lay as one seamless field.
 *
 * The top face is unprojected into a square ([textureToImage]) and only its middle is kept
 * ([INSET] off each side): what is left is the cracked earth, without the edges, the tufts or the
 * slab's sides. Then, in four steps (#54), it is made into ground that can be laid over the whole
 * garden with neither a line nor a pattern showing:
 *
 * 1. [seamless]: its far edges are blended into its near ones over a margin, so that it tiles
 *    with no line; each part of the earth appears once in it. (Blending it with copies of itself
 *    moved half way round, as before, showed every patch twice to a tile, and repeating it
 *    mirrored made a kaleidoscope.)
 * 2. [evenOut]: the light and dark larger than a clod are taken out, and the earth's colours drawn
 *    closer together. The face is lighter, browner and crumblier towards its edges than in its
 *    middle; repeated, that made bands across the garden, one to a tile.
 * 3. [field]: a larger texture ([FIELD_SIZE]) is laid from the tile in [FIELD_CELLS] x
 *    [FIELD_CELLS] patches, each taken from its own place in it and blended smoothly into the
 *    next, so that within the field nothing repeats, and it still tiles itself.
 * 4. The garden lays the field over everything at once and lays broad light and dark over that
 *    ([variation]), at another size and angle, so the field's own repeats differ too.
 */
object FlatGround {
    /**
     * Share of the face cut away on every side: the crumbling rim, the tufts standing on it, and
     * (#54, 0.14 before) the lighter, browner earth just inside it.
     */
    const val INSET = 0.2f

    /** Pixels a side of the face as unprojected. */
    const val SIZE = 512

    /** Pixels of a [SIZE] face blended over from its far edge into its near one ([seamless]). */
    const val MARGIN = 96

    /** Pixels a side of the tile [seamless] makes from a [SIZE] face. */
    const val TILE_SIZE = SIZE - MARGIN

    /** How many cells of the garden the tile spans. */
    const val TILE_CELLS = 1.5f

    /** Pixels a side of the field laid from the tile ([field]): the texture the garden repeats. */
    const val FIELD_SIZE = 1024

    /**
     * The way the field is made, as a number: raised whenever [flatten] changes what it makes, so
     * that a field kept from before (AssetRepository's cache) is not read back.
     */
    const val FIELD_VERSION = 1

    /** Patches a side of the field, each taken from its own place in the tile. */
    const val FIELD_CELLS = 6

    /** How large the light and dark [evenOut] takes out are, at least: a box blur's radius in pixels, three passes. */
    const val EVEN_OUT_RADIUS = 32

    /** How much of the earth's colour away from its plain grey [evenOut] keeps. */
    const val COLOUR_KEPT = 0.45f

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

    /** The flat ground of [source]'s earth: a field [FIELD_SIZE] pixels square that tiles. */
    fun flatten(source: Bitmap, slab: GroundSlab = GroundSlab.WASTELAND): Bitmap {
        val size = SIZE
        val m = textureToImage(source.width, source.height, slab, size)
        val toImage = Matrix().apply { setValues(floatArrayOf(m[0], m[1], m[2], m[3], m[4], m[5], 0f, 0f, 1f)) }
        val toTexture = Matrix().also { toImage.invert(it) }
        val face = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(face).drawBitmap(source, toTexture, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        val pixels = IntArray(size * size)
        face.getPixels(pixels, 0, size, 0, 0, size, size)
        face.recycle()
        val tileSize = tileSize(size)
        val tile = evenOut(seamless(pixels, size), tileSize)
        val out = Bitmap.createBitmap(FIELD_SIZE, FIELD_SIZE, Bitmap.Config.ARGB_8888)
        out.setPixels(field(tile, tileSize), 0, FIELD_SIZE, 0, 0, FIELD_SIZE, FIELD_SIZE)
        return out
    }

    /**
     * The part of [source]'s top face (laid out as [slab]) from [from] to [to] of the way along
     * both of its axes, unprojected into a square [size] pixels across: the face as seen from
     * straight above. The pond's open water, for one (#54).
     */
    fun region(source: Bitmap, slab: GroundSlab, from: Float, to: Float, size: Int): Bitmap {
        val halfWidth = slab.topWidth * source.width / 2f
        val halfHeight = halfWidth / 2f
        val topX = slab.anchorX * source.width
        val topY = slab.anchorY * source.height - halfHeight
        // Along x: top corner to right corner. Along y: top corner to left corner.
        val step = (to - from) / size
        val values = floatArrayOf(
            halfWidth * step, -halfWidth * step, topX + (halfWidth - halfWidth) * from,
            halfHeight * step, halfHeight * step, topY + (halfHeight + halfHeight) * from,
            0f, 0f, 1f,
        )
        val toImage = Matrix().apply { setValues(values) }
        val toTexture = Matrix().also { toImage.invert(it) }
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source, toTexture, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /**
     * [square] made into a tile that repeats with neither a seam nor a pattern ([seamless], then
     * [evenOut] over [radius], all its colour kept): the pond's water where ponds join (#54).
     * Repeated mirrored as it was, the light falling across the water came out in bands.
     */
    fun tileable(square: Bitmap, radius: Int): Bitmap {
        val size = square.width
        val pixels = IntArray(size * size)
        square.getPixels(pixels, 0, size, 0, 0, size, size)
        val tileSize = tileSize(size)
        val tile = evenOut(seamless(pixels, size), tileSize, radius, colourKept = 1f)
        return Bitmap.createBitmap(tile, tileSize, tileSize, Bitmap.Config.ARGB_8888)
    }

    /** The side of the tile [seamless] makes from a face [size] across. */
    fun tileSize(size: Int): Int = size - margin(size)

    private fun margin(size: Int): Int = size * MARGIN / SIZE

    /**
     * [pixels] (ARGB, [size] square) made opaque and tileable, as a tile [tileSize] across.
     * Where the earth of the picture is see-through it is filled with the earth's own average
     * colour. Then, along each axis, what lies past the tile's far edge (the last [MARGIN] of the
     * face) is blended into its first pixels, so that across the tile's edge the earth runs on.
     *
     * The blend keeps the earth's grain (#54): it weighs the tile's own pixels by [weight] (sin² of
     * how far in they are) and those brought round by the rest, and scales the difference from
     * the mean colour back up by 1 / sqrt(sum of the squared weights). Averaged plainly, the
     * blended strip was smoother than the rest and showed as a line where the tiles met.
     */
    fun seamless(pixels: IntArray, size: Int): IntArray {
        require(pixels.size == size * size)
        val earth = Earth.of(pixels)
        val tile = tileSize(size)
        val margin = margin(size)
        // Along x first (all the face's rows, a tile wide), then along y.
        val across = Array(3) { FloatArray(size * tile) }
        for (y in 0 until size) for (x in 0 until tile) {
            val b = Blend.at(x, margin)
            for (c in 0..2) {
                val own = earth.channels[c][y * size + x]
                across[c][y * tile + x] = if (b.other > 0f) {
                    b.mix(own, earth.channels[c][y * size + x + tile], earth.mean[c])
                } else {
                    own
                }
            }
        }
        val out = IntArray(tile * tile)
        for (y in 0 until tile) for (x in 0 until tile) {
            val b = Blend.at(y, margin)
            var argb = 0xFF shl 24
            for (c in 0..2) {
                val own = across[c][y * tile + x]
                val v = if (b.other > 0f) b.mix(own, across[c][(y + tile) * tile + x], earth.mean[c]) else own
                argb = argb or (v.roundToInt().coerceIn(0, 255) shl (16 - 8 * c))
            }
            out[y * tile + x] = argb
        }
        return out
    }

    /** The weights of a tile's own pixel and of the one brought round onto it, and the gain that keeps the grain. */
    private class Blend(val own: Float, val other: Float, val gain: Float) {
        fun mix(a: Float, b: Float, mean: Float): Float = mean + ((a - mean) * own + (b - mean) * other) * gain

        companion object {
            private val PLAIN = Blend(1f, 0f, 1f)

            fun at(i: Int, margin: Int): Blend {
                if (i >= margin) return PLAIN
                val own = weight((i + 0.5) / margin).toFloat()
                val other = 1f - own
                return Blend(own, other, (1.0 / sqrt((own * own + other * other).toDouble())).toFloat())
            }
        }
    }

    /**
     * How much a tile's own pixel counts in a blended strip, [t] of the way across it (0 at the
     * tile's edge, 1 where the strip ends): sin², from 0 to 1 smoothly. What is brought round
     * from the far edge counts the rest, 1 - this.
     */
    fun weight(t: Double): Double {
        val s = sin(t.coerceIn(0.0, 1.0) * PI / 2)
        return s * s
    }

    /**
     * [tile] ([size] square, tiling) with its light and dark larger than [EVEN_OUT_RADIUS] taken
     * out (less a blur of itself that wraps round, so it still tiles), and its colours drawn
     * towards the earth's own tint, [COLOUR_KEPT] of their difference from their grey kept. Only
     * the clods, cracks and pebbles are left to repeat, and those the eye does not count.
     */
    fun evenOut(tile: IntArray, size: Int, radius: Int = EVEN_OUT_RADIUS, colourKept: Float = COLOUR_KEPT): IntArray {
        require(tile.size == size * size)
        val earth = Earth.of(tile)
        val low = Array(3) { c ->
            var v = earth.channels[c]
            repeat(3) { v = blurWrapped(v, size, radius) }
            v
        }
        val meanGrey = (earth.mean[0] + earth.mean[1] + earth.mean[2]) / 3f
        val out = IntArray(size * size)
        val v = FloatArray(3)
        for (i in 0 until size * size) {
            for (c in 0..2) v[c] = earth.mean[c] + earth.channels[c][i] - low[c][i]
            val grey = (v[0] + v[1] + v[2]) / 3f
            var argb = 0xFF shl 24
            for (c in 0..2) {
                // Its grey as it is; its tint lessened, towards the earth's own.
                val tinted = grey + (v[c] - grey) * colourKept + (earth.mean[c] - meanGrey) * (1f - colourKept)
                argb = argb or (tinted.roundToInt().coerceIn(0, 255) shl (16 - 8 * c))
            }
            out[i] = argb
        }
        return out
    }

    /** [values] ([size] square) blurred along both axes by a box [radius] each way, wrapping round its edges. */
    private fun blurWrapped(values: FloatArray, size: Int, radius: Int): FloatArray {
        val span = 2 * radius + 1
        val across = FloatArray(values.size)
        for (y in 0 until size) {
            val row = y * size
            var sum = 0f
            for (k in -radius..radius) sum += values[row + k.mod(size)]
            for (x in 0 until size) {
                across[row + x] = sum / span
                sum += values[row + (x + radius + 1).mod(size)] - values[row + (x - radius).mod(size)]
            }
        }
        val out = FloatArray(values.size)
        for (x in 0 until size) {
            var sum = 0f
            for (k in -radius..radius) sum += across[k.mod(size) * size + x]
            for (y in 0 until size) {
                out[y * size + x] = sum / span
                sum += across[(y + radius + 1).mod(size) * size + x] - across[(y - radius).mod(size) * size + x]
            }
        }
        return out
    }

    /**
     * A field [fieldSize] square laid from [tile] ([tileSize] square, tiling): [cells] x [cells]
     * patches, each the tile taken from its own place (fixed by [seed]), every pixel a smooth mix
     * of the four patches round it ([weight], the grain kept as in [seamless]). The patches wrap
     * round the field's edges, so the field tiles too; within it, the tile is never seen repeating.
     */
    fun field(tile: IntArray, tileSize: Int, fieldSize: Int = FIELD_SIZE, cells: Int = FIELD_CELLS, seed: Int = 54): IntArray {
        require(tile.size == tileSize * tileSize)
        val earth = Earth.of(tile)
        val offsets = IntArray(cells * cells * 2) { i -> patchHash(i, seed) % tileSize }
        val cell = fieldSize.toFloat() / cells
        // Which patches a row or column lies between, and how far it is from the first: the same
        // along every row, so worked out once (a million sines otherwise, on a slow phone seconds).
        val first = IntArray(fieldSize) { floor(it / cell - 0.5f).toInt() }
        val towards = FloatArray(fieldSize) { (weight((it / cell - 0.5f - first[it]).toDouble())).toFloat() }
        val out = IntArray(fieldSize * fieldSize)
        val (red, green, blue) = earth.channels
        val (meanR, meanG, meanB) = earth.mean
        for (y in 0 until fieldSize) {
            val y0 = first[y]
            val wy = towards[y]
            for (x in 0 until fieldSize) {
                val x0 = first[x]
                val wx = towards[x]
                var r = 0f
                var g = 0f
                var b = 0f
                var squares = 0f
                for (dy in 0..1) for (dx in 0..1) {
                    val w = (if (dx == 1) wx else 1f - wx) * (if (dy == 1) wy else 1f - wy)
                    if (w <= 0f) continue
                    val patch = ((y0 + dy).mod(cells) * cells + (x0 + dx).mod(cells)) * 2
                    val sx = (x + offsets[patch]) % tileSize
                    val sy = (y + offsets[patch + 1]) % tileSize
                    val i = sy * tileSize + sx
                    r += (red[i] - meanR) * w
                    g += (green[i] - meanG) * w
                    b += (blue[i] - meanB) * w
                    squares += w * w
                }
                val gain = 1f / sqrt(squares)
                fun channel(mean: Float, v: Float) = (mean + v * gain).roundToInt().coerceIn(0, 255)
                out[y * fieldSize + x] = (0xFF shl 24) or (channel(meanR, r) shl 16) or (channel(meanG, g) shl 8) or channel(meanB, b)
            }
        }
        return out
    }

    private fun patchHash(i: Int, seed: Int): Int {
        var h = i * 374761393 + seed * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7FFFFFFF
    }

    /** Pixels made opaque over the earth's own mean colour, channel by channel, and that mean. */
    private class Earth(val channels: Array<FloatArray>, val mean: FloatArray) {
        companion object {
            fun of(pixels: IntArray): Earth {
                var weight = 0.0
                val sum = DoubleArray(3)
                for (p in pixels) {
                    val a = (p ushr 24) / 255.0
                    weight += a
                    for (c in 0..2) sum[c] += ((p shr (16 - 8 * c)) and 0xFF) * a
                }
                val mean = FloatArray(3) { if (weight > 0) (sum[it] / weight).toFloat() else 0f }
                val channels = Array(3) { FloatArray(pixels.size) }
                for (i in pixels.indices) {
                    val p = pixels[i]
                    val a = (p ushr 24) / 255f
                    for (c in 0..2) channels[c][i] = ((p shr (16 - 8 * c)) and 0xFF) * a + mean[c] * (1 - a)
                }
                return Earth(channels, mean)
            }
        }
    }

    /** Pixels a side of the [variation] texture. */
    const val VARIATION_SIZE = 64

    /**
     * Broad, soft light and dark over the ground, to lay over the repeated earth (#54): grey
     * around the middle (128), [VARIATION_SIZE] square, tiling. Laid much larger than the field's
     * own repeat and at an angle to it, it makes no two repeats of the field look alike. Smooth
     * noise from a few octaves of a lattice of fixed random values, each lattice whole in the
     * square so that it wraps.
     */
    fun variation(size: Int = VARIATION_SIZE, seed: Int = 54): IntArray {
        val octaves = listOf(4 to 0.6, 8 to 0.3, 16 to 0.1)
        val out = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            var v = 0.0
            for ((cells, amount) in octaves) {
                val fx = x.toDouble() * cells / size
                val fy = y.toDouble() * cells / size
                val x0 = floor(fx).toInt()
                val y0 = floor(fy).toInt()
                val tx = smooth(fx - x0)
                val ty = smooth(fy - y0)
                fun at(i: Int, j: Int) = lattice(i.mod(cells), j.mod(cells), cells, seed)
                val top = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * tx
                val bottom = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * tx
                v += (top + (bottom - top) * ty) * amount
            }
            // -1..1 to a grey, kept gentle.
            val grey = (128 + v * VARIATION_STRENGTH).roundToInt().coerceIn(0, 255)
            out[y * size + x] = (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
        }
        return out
    }

    /** How far the [variation] strays from the middle grey, at most. */
    const val VARIATION_STRENGTH = 46.0

    private fun smooth(t: Double) = t * t * (3 - 2 * t)

    /** A fixed value from -1 to 1 for lattice point ([i], [j]) of a lattice [cells] across. */
    private fun lattice(i: Int, j: Int, cells: Int, seed: Int): Double {
        var h = (i * 73856093) xor (j * 19349663) xor (cells * 83492791) xor (seed * 2654435761L).toInt()
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return ((h and 0xFFFF) / 65535.0) * 2 - 1
    }
}
