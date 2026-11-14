package com.studioguu.miniaturegarden.ui.render

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Snow read off a picture itself (#fb-1113 2-5), rather than drawn as bands along hand-set lines:
 * where it lies, and how thick, comes from the picture's own pixels, so it follows the shape of
 * each roof and stone, and turns with it (it is drawn exactly as the picture is, mirrored or not).
 *
 * - [Kind.ROOF]: over the roof faces (outlines in shares of the picture), thicker on the ledge
 *   just under each course of tiles or shingles (a pixel lighter than the dark line a few rows above
 *   it) and thinner over the shadowed edges, in soft patches: it gathers on the steps of the roof
 *   and lies thin here and there, not as one even sheet.
 * - [Kind.TOP]: a light dusting on what faces up: the first few rows of the picture under open air
 *   in each column (the top of a stone, the seat and back rail of a bench), fading downwards, in
 *   patches; [Spec.amount] says how much at most.
 *
 * Pure arithmetic over ARGB pixels, so it can be checked off the device; [PictureSnow] makes and
 * keeps the masks and draws them.
 */
object SnowCover {

    enum class Kind { ROOF, TOP }

    /**
     * @property faces for a roof, its faces as outlines of (x, y) in shares of the picture's width
     *   and height; snow lies only inside them.
     * @property amount how opaque the snow is where it lies thickest, 0 to 1.
     * @property floor for a roof, how thin it lies at the least where it lies at all.
     */
    data class Spec(
        val kind: Kind,
        val amount: Float,
        val faces: List<List<Pair<Float, Float>>> = emptyList(),
        val floor: Float = 0f,
        val solid: Boolean = false,
        /** For a dusting, how far down from open air it reaches, as a share of the picture's height. */
        val reach: Float = TOP_REACH,
        /**
         * For a dusting, the faces that look straight up (a bench's seat and the top of its back,
         * FB1114b), as outlines like [faces]: on their lit wood the snow lies clearly white, not
         * as a dusting; their dark seams and edges keep only the dusting.
         */
        val caps: List<List<Pair<Float, Float>>> = emptyList(),
        /** How thick the snow on [caps] is at the least (0 to 1; at the most it is solid): where it lies thinnest. */
        val capFloor: Float = 0f,
    )

    /** How far down from open air a dusting reaches, as a share of the picture's height. */
    const val TOP_REACH = 0.2f

    /** The alpha (0 to 1) of the snow over each pixel of a [width] x [height] picture of [argb]. */
    fun alphaOf(argb: IntArray, width: Int, height: Int, spec: Spec, seed: Int): FloatArray {
        val out = FloatArray(width * height)
        val lum = FloatArray(width * height) { i -> luminance(argb[i]) }
        when (spec.kind) {
            Kind.ROOF -> roof(argb, lum, width, height, spec, seed, out)
            Kind.TOP -> top(argb, lum, width, height, spec, seed, out)
        }
        return blur(out, width, height)
    }

    private fun roof(argb: IntArray, lum: FloatArray, w: Int, h: Int, spec: Spec, seed: Int, out: FloatArray) {
        val faces = spec.faces.map { face -> face.map { (x, y) -> x * w to y * h } }
        val inside = BooleanArray(w * h)
        var sum = 0.0
        var count = 0
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (alpha(argb[i]) < OPAQUE) continue
            if (faces.none { contains(it, x + 0.5f, y + 0.5f) }) continue
            inside[i] = true
            sum += lum[i]
            count++
        }
        if (count == 0) return
        val mean = (sum / count).toFloat()
        val step = max(2, h / 160)
        val patch = max(4f, w * 0.07f)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!inside[i]) continue
            val above = if (y >= step && alpha(argb[i - step * w]) >= OPAQUE) lum[i - step * w] else lum[i]
            // Lighter than a few rows above: just under a course's dark edge, on the step where snow sits.
            val ledge = ((lum[i] - above) * 5f).coerceIn(-1f, 1f)
            val lit = ((lum[i] - mean) * 2.5f).coerceIn(-1f, 1f)
            // Wide soft patches where it lies thinner, and a finer grain over them.
            val thin = smooth(valueNoise(x / patch, y / patch, seed))
            val grain = valueNoise(x / (patch * 0.3f), y / (patch * 0.3f), seed + 101)
            // A solid cover (the cottage's) lies thicker all over, its thin patches fewer.
            var a = if (spec.solid) {
                1.06f + 0.2f * ledge + 0.05f * lit - 0.22f * max(0f, 0.55f - thin) * 2f - 0.06f * grain
            } else {
                1f + 0.25f * ledge + 0.08f * lit - 0.36f * max(0f, 0.55f - thin) * 2f - 0.08f * grain
            }
            a = a.coerceIn(spec.floor, 1f)
            out[i] = a * spec.amount
        }
    }

    private fun top(argb: IntArray, lum: FloatArray, w: Int, h: Int, spec: Spec, seed: Int, out: FloatArray) {
        val reach = max(3f, h * spec.reach)
        val patch = max(4f, w * 0.12f)
        val capped = capsOf(argb, lum, w, h, spec)
        for (x in 0 until w) {
            var sinceAir = Int.MAX_VALUE / 2
            for (y in 0 until h) {
                val i = y * w + x
                if (alpha(argb[i]) < OPAQUE) {
                    sinceAir = 0
                    continue
                }
                sinceAir++
                val facing = exp(-(sinceAir - 1) / reach)
                if (capped[i]) {
                    // A face looking straight up: white all over it, a little thinner in patches.
                    val spots = smooth(valueNoise(x / (patch * 0.6f), y / (patch * 0.6f), seed + 7))
                    out[i] = spec.capFloor + (1f - spec.capFloor) * spots
                    continue
                }
                if (facing < 0.05f) continue
                // A lit face takes more of it than a dark one; patches leave bare stone between.
                val lit = (0.65f + lum[i] * 0.8f).coerceIn(0.4f, 1f)
                val spots = smooth(valueNoise(x / patch, y / patch, seed))
                val a = facing * lit * (0.3f + 0.9f * spots)
                out[i] = (a * spec.amount).coerceIn(0f, spec.amount)
            }
        }
    }

    /**
     * Which pixels of the picture lie on [Spec.caps] and are lit wood, not one of the dark seams
     * between the planks or a plank's shadowed edge: at least [CAP_LIT] of the faces' mean lightness.
     */
    private fun capsOf(argb: IntArray, lum: FloatArray, w: Int, h: Int, spec: Spec): BooleanArray {
        val out = BooleanArray(w * h)
        if (spec.caps.isEmpty()) return out
        val caps = spec.caps.map { face -> face.map { (x, y) -> x * w to y * h } }
        var sum = 0.0
        var count = 0
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (alpha(argb[i]) < OPAQUE) continue
            if (caps.none { contains(it, x + 0.5f, y + 0.5f) }) continue
            out[i] = true
            sum += lum[i]
            count++
        }
        if (count == 0) return out
        val least = (sum / count).toFloat() * CAP_LIT
        for (i in out.indices) if (out[i] && lum[i] < least) out[i] = false
        return out
    }

    /** How light a pixel on a face looking up is at the least, as a share of the faces' mean, to take the snow. */
    const val CAP_LIT = 0.78f

    /** A 3 x 3 box blur, so that the snow's edges are soft rather than a pixel's line. */
    private fun blur(a: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx
                val yy = y + dy
                if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                s += a[yy * w + xx]
                n++
            }
            // Never spread past where it lay: the picture's edge stays the snow's edge.
            out[y * w + x] = if (a[y * w + x] == 0f) 0f else s / n
        }
        return out
    }

    /** Whether (x, y) is inside the outline [poly] (even-odd). */
    internal fun contains(poly: List<Pair<Float, Float>>, x: Float, y: Float): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val (xi, yi) = poly[i]
            val (xj, yj) = poly[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    private const val OPAQUE = 128

    private fun alpha(c: Int) = (c ushr 24) and 0xFF

    private fun luminance(c: Int): Float {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)

    /** Smooth noise 0..1 over the plane, the same for the same (x, y, seed). */
    internal fun valueNoise(x: Float, y: Float, seed: Int): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = smooth(x - x0)
        val fy = smooth(y - y0)
        fun at(i: Int, j: Int): Float {
            var h = i * 374761393 + j * 668265263 + seed * 1442695041
            h = (h xor (h ushr 13)) * 1274126177
            return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
        }
        val top = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * fx
        val bottom = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * fx
        return min(1f, max(0f, top + (bottom - top) * fy))
    }
}
