package com.studioguu.miniaturegarden.ui.render

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.studioguu.miniaturegarden.data.asset.AssetManifest
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.domain.model.BuildingPaint
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PaintPart

/**
 * Makes the pictures of painted buildings: the building's sprite with the parts its mask marks
 * (`mask_<objectId>`, red the roof, green the walls) in the player's colours. The picture stays
 * the same size and in the same place, so that the lit windows and everything else laid over it
 * stay where they are. Made once for each choice of colours and kept: the garden is drawn many
 * times a second, and a building is painted now and then.
 */
class BuildingPainter {
    private val made = HashMap<String, ImageBitmap>()

    /** The pictures of the painted buildings among [objects], by instance id. */
    fun picturesFor(objects: List<GridObject>, atlas: SpriteAtlas): Map<String, ImageBitmap> =
        objects.filter { BuildingPaint.isPainted(it) && !it.isWorking }.mapNotNull { building ->
            val roof = BuildingPaint.paintOf(building, PaintPart.ROOF)
            val wall = BuildingPaint.paintOf(building, PaintPart.WALL)
            val key = "${building.objectId}|${roof?.id}|${wall?.id}"
            val picture = made[key] ?: paint(building.objectId, roof?.argb, wall?.argb, atlas)?.also { made[key] = it }
            picture?.let { building.instanceId to it }
        }.toMap()

    private fun paint(objectId: String, roof: Long?, wall: Long?, atlas: SpriteAtlas): ImageBitmap? {
        val sprite = atlas[objectId] ?: return null
        val mask = atlas["$MASK_PREFIX$objectId"] ?: return null
        if (mask.width != sprite.width || mask.height != sprite.height) return null
        val pixels = pixelsOf(sprite, atlas)
        val parts = pixelsOf(mask, atlas)
        val shaded = PaintMix.paint(pixels, parts, roof?.toInt(), wall?.toInt())
        return Bitmap.createBitmap(shaded, sprite.width, sprite.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private fun pixelsOf(asset: AssetManifest, atlas: SpriteAtlas): IntArray {
        val out = IntArray(asset.width * asset.height)
        atlas.pages[asset.page].getPixels(out, 0, asset.width, asset.atlasX, asset.atlasY, asset.width, asset.height)
        return out
    }

    companion object {
        const val MASK_PREFIX = "mask_"
    }
}

/**
 * The mixing itself. A painted pixel takes the paint's colour, as much lighter or darker as the
 * pixel is than the part's average, so that the picture's light and the grain of its boards stay:
 * in full for a roof; less for the walls, as dark planks would otherwise show through a pale paint
 * as black lines.
 */
object PaintMix {
    private const val ROOF_SHADE = 1f
    private const val WALL_SHADE = 0.4f

    /** [pixels] (ARGB) with [roof] and [wall] (ARGB, null to leave alone) laid over the parts of [mask]. */
    fun paint(pixels: IntArray, mask: IntArray, roof: Int?, wall: Int?): IntArray {
        val out = pixels.copyOf()
        if (roof != null) lay(out, mask, shift = 16, roof, averageOf(pixels, mask, 16), ROOF_SHADE)
        if (wall != null) lay(out, mask, shift = 8, wall, averageOf(pixels, mask, 8), WALL_SHADE)
        return out
    }

    private fun light(pixel: Int): Float = ((pixel shr 16 and 255) + (pixel shr 8 and 255) + (pixel and 255)) / 3f

    private fun averageOf(pixels: IntArray, mask: IntArray, shift: Int): Float {
        var sum = 0f
        var count = 0
        for (i in pixels.indices) {
            if ((mask[i] shr shift and 255) > 128 && pixels[i] ushr 24 > 128) {
                sum += light(pixels[i])
                count++
            }
        }
        return if (count == 0) 1f else (sum / count).coerceAtLeast(1f)
    }

    private fun lay(out: IntArray, mask: IntArray, shift: Int, colour: Int, average: Float, shadeShare: Float) {
        val red = colour shr 16 and 255
        val green = colour shr 8 and 255
        val blue = colour and 255
        for (i in out.indices) {
            val share = (mask[i] shr shift and 255) / 255f
            if (share <= 0f) continue
            val pixel = out[i]
            val shade = 1f + (light(pixel) / average - 1f) * shadeShare
            fun channel(from: Int, paint: Int) = (from + ((paint * shade).coerceIn(0f, 255f) - from) * share).toInt()
            out[i] = (pixel and 0xFF000000.toInt()) or
                (channel(pixel shr 16 and 255, red) shl 16) or
                (channel(pixel shr 8 and 255, green) shl 8) or
                channel(pixel and 255, blue)
        }
    }
}
