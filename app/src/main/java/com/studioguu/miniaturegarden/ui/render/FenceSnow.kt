package com.studioguu.miniaturegarden.ui.render

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.studioguu.miniaturegarden.data.asset.AssetManifest
import com.studioguu.miniaturegarden.domain.model.RunKind
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * FB1117: the snow on the fences, read off the picture's own outline. A thin, flat layer lies on
 * what faces up (the top of a rail, of a post, of a bamboo's stick) and caps the points of the
 * white fence's pickets, rather than a heap drawn over the top like cotton wool: the layer follows
 * the top edge of the picture column by column, pooling a little on the shoulders of a point, thin
 * here and there, white on top and blue-grey underneath, as the snow on the roofs and the benches is.
 *
 * Pure arithmetic over ARGB pixels ([cap]), so it can be checked off the device.
 */
object FenceSnow {

    /** How the snow lies on one picture, in the picture's own pixels. */
    data class Look(
        /** How far from a column the highest point is looked for: wider than a point, so the snow bridges to the shoulders. */
        val reach: Int,
        /** How thick the layer is at its thickest. */
        val thick: Float,
        /** How far it stands above the highest point (the hat on a tip). */
        val lift: Float,
    )

    /** How the snow lies on the pictures of [kind], and of its [post] or [rail]. */
    fun lookOf(kind: RunKind, post: Boolean): Look = when (kind) {
        RunKind.WOOD_FENCE -> if (post) Look(1, 4f, 2f) else Look(2, 4.5f, 2f)
        // The white fence's points: a small hat on each tip, shoulders either side.
        RunKind.WHITE_FENCE -> if (post) Look(2, 4f, 2.2f) else Look(3, 4.5f, 3f)
        // Bamboo: a thin layer over the ends of the sticks, not a spike each.
        RunKind.BAMBOO_FENCE -> if (post) Look(1, 4f, 2f) else Look(6, 4f, 2f)
        RunKind.BRICK_EDGING -> Look(1, 3f, 1.5f)
    }

    /** Rows the mask has above the picture's own: the layer stands up over the highest point. */
    const val PAD = 8

    /** The snow's white, a touch blue, and its shade towards the foot of the layer. */
    private const val LIGHT = 0xFFFFFF
    private const val BODY = 0xF2F6FF
    private const val FOOT = 0x96A8C4

    /**
     * The ARGB of the snow over a [width] x [height] picture of [argb], as a [width] x ([height] + [PAD])
     * picture (the extra rows on top, for the layer to stand up in).
     */
    fun cap(argb: IntArray, width: Int, height: Int, look: Look, seed: Int): IntArray {
        val rows = height + PAD
        val out = IntArray(width * rows)
        // The topmost solid pixel of each column.
        val top = FloatArray(width) { x ->
            var y = 0
            while (y < height && ((argb[y * width + x] ushr 24) and 0xFF) < SOLID) y++
            y.toFloat()
        }
        // The surface: the highest top within reach, so that it lies over the shoulders of a point,
        // then smoothed so that it has no edges.
        val high = FloatArray(width) { x ->
            var m = top[x]
            for (d in -look.reach..look.reach) m = min(m, top[(x + d).coerceIn(0, width - 1)])
            m
        }
        val surface = blur(high, max(1, look.reach / 2))
        for (x in 0 until width) {
            if (top[x] >= height) continue
            // Thinner here and there, never gone: it lies unevenly.
            val wander = 0.75f + 0.25f * (0.5f + 0.5f * sin(x * 0.21f + seed * 1.7f))
            val thick = look.thick * wander
            val upper = surface[x] - look.lift * wander + PAD
            val lower = surface[x] + thick + PAD
            val first = floor(upper).toInt().coerceAtLeast(0)
            val last = min(rows - 1, floor(lower).toInt())
            for (y in first..last) {
                val above = (y + 1 - upper).coerceIn(0f, 1f)
                val below = (lower - y).coerceIn(0f, 1f)
                // Fades in over the first pixels' height at its upper edge, and out at its lower.
                val a = above * min(1f, below / 1.4f)
                if (a <= 0f) continue
                val t = ((y - upper) / max(1f, lower - upper)).coerceIn(0f, 1f)
                val rgb = mix(mix(LIGHT, BODY, (t * 2f).coerceAtMost(1f)), FOOT, ((t - 0.55f) * 2.2f).coerceIn(0f, 1f))
                out[y * width + x] = ((a * 255f).toInt().coerceIn(0, 255) shl 24) or rgb
            }
        }
        return out
    }

    private const val SOLID = 90

    private val masks = HashMap<String, ImageBitmap?>()

    /**
     * The snow over [asset] on [page], made from its pixels the first time and kept: the mask
     * and the asset to draw it with (one larger by [PAD] rows above, anchored on the same ground).
     */
    fun maskOf(page: ImageBitmap, asset: AssetManifest, kind: RunKind, post: Boolean): Pair<ImageBitmap, AssetManifest>? {
        val w = asset.width
        val h = asset.height
        val image = masks.getOrPut(asset.assetId) {
            val pixels = IntArray(w * h)
            runCatching { page.asAndroidBitmap().getPixels(pixels, 0, w, asset.atlasX, asset.atlasY, w, h) }.getOrElse { return@getOrPut null }
            val snow = cap(pixels, w, h, lookOf(kind, post), asset.assetId.hashCode())
            Bitmap.createBitmap(snow, w, h + PAD, Bitmap.Config.ARGB_8888).asImageBitmap()
        } ?: return null
        return image to withPad(asset)
    }

    /** [asset] as the mask is drawn: [PAD] rows taller above, with its anchor and attachment on the same pixels. */
    fun withPad(asset: AssetManifest): AssetManifest {
        val h = asset.height.toFloat()
        val taller = h + PAD
        return asset.copy(
            page = 0, atlasX = 0, atlasY = 0,
            height = asset.height + PAD,
            anchorY = (asset.anchorY * h + PAD) / taller,
            attachY = asset.attachY?.let { (it * h + PAD) / taller },
        )
    }

    private fun blur(a: FloatArray, radius: Int): FloatArray = FloatArray(a.size) { i ->
        var sum = 0f
        for (d in -radius..radius) sum += a[(i + d).coerceIn(0, a.size - 1)]
        sum / (2 * radius + 1)
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)

    private fun noise(i: Int): Float {
        val x = sin(i * 12.9898f + 4.1414f) * 43758.547f
        return x - floor(x)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1f - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
