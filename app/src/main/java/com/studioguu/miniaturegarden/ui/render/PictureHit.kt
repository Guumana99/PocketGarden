package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import com.studioguu.miniaturegarden.data.asset.AssetManifest
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GridObject
import kotlin.math.floor

/**
 * Which standing thing's picture is under a finger (#fb1126 1): what the player sees there. Read
 * off the pictures themselves, so that the clear parts of a picture let the tap through, and of
 * two pictures over one point the one drawn in front wins.
 *
 * What lies flat in the ground (soil, paving, lawn and the other ground covers, water) is left to
 * [TilePicker]; so are work sites, which are drawn in code.
 */
object PictureHit {

    /** A pixel at least this opaque (0..255) is part of the picture: not its soft edge or shadow. */
    const val OPAQUE = 128

    private const val GROUND_PREFIX = "ground_"

    /** A picture and the size it is drawn at, in screen pixels per atlas pixel. */
    data class Shown(val sprite: AssetManifest, val scale: Float)

    /**
     * The front-most thing of [objects] whose picture is opaque at [screen], or null.
     *
     * @param shown the picture [GridObject] is drawn with, and its scale; null for none.
     * @param opaque whether the pixel (x, y) of a picture, counted from its top left, is part of it.
     */
    fun frontMost(
        screen: Offset,
        transform: IsometricTransform,
        grid: GardenGrid,
        objects: List<GridObject>,
        shown: (GridObject) -> Shown?,
        opaque: (AssetManifest, Int, Int) -> Boolean,
    ): GridObject? {
        val boxes = FootprintOrder.boxesOf(grid.objects)
        var best: GridObject? = null
        var bestDepth = -Float.MAX_VALUE
        for (obj in objects) {
            if (obj.isTerrain || obj.isFloor || obj.isPond || obj.isWorking || obj.objectId.startsWith(GROUND_PREFIX)) continue
            val footprint = obj.footprint
            val frontX = obj.gridX + footprint.width - 1
            val frontY = obj.gridY + footprint.height - 1
            // The same order the garden draws in.
            val depth = FootprintOrder.depthOf(
                obj.gridX, obj.gridY, frontX, frontY, LayerPipeline.depthOf(frontX, frontY, obj.heightLevel), boxes,
            )
            if (depth < bestDepth) continue
            val picture = shown(obj) ?: continue
            val base = transform.worldToScreen(
                obj.gridX + footprint.width / 2f,
                obj.gridY + footprint.height / 2f,
                obj.heightLevel.toFloat(),
            )
            if (covers(screen, base, picture, obj.isFlipped, opaque)) {
                best = obj
                bestDepth = depth
            }
        }
        return best
    }

    /** Whether [picture], standing on [base], is opaque at [screen]. */
    fun covers(screen: Offset, base: Offset, picture: Shown, flipped: Boolean, opaque: (AssetManifest, Int, Int) -> Boolean): Boolean {
        val sprite = picture.sprite
        val width = sprite.drawnWidth(picture.scale)
        val height = sprite.drawnHeight(picture.scale)
        if (width <= 0f || height <= 0f) return false
        // A turned picture is mirrored round the line through its foot.
        val x = if (flipped && sprite.isFlippedAllowed) 2f * base.x - screen.x else screen.x
        val across = (x - sprite.drawLeft(base.x, picture.scale)) / width
        val down = (screen.y - sprite.drawTop(base.y, picture.scale)) / height
        if (across < 0f || across >= 1f || down < 0f || down >= 1f) return false
        return opaque(sprite, floor(across * sprite.width).toInt(), floor(down * sprite.height).toInt())
    }
}
